package dev.minecraftdatagenerator.packetgen;

import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.*;

/**
 * Chooses which values of one registration's domain are attempted, in stages that guarantee
 * structural coverage before volume:
 * <ul>
 *   <li>{@code exhaustive}: an odometer over the decision tree, for domains within the threshold;</li>
 *   <li>{@code each-choice}: every alternative of every decision node at least once, varying one
 *       factor from the sample in which the node was first seen;</li>
 *   <li>{@code pairwise}: every compatible pair of alternatives among the first tracked nodes;</li>
 *   <li>{@code random}: seeded random decisions until the accepted-sample cap.</li>
 * </ul>
 * The driver reports each attempt's outcome through {@link #feedback}; rejected targets are retried
 * once and then recorded as infeasible instead of being retried forever.
 */
final class Sampler {
    enum Stage {
        SEED("seed"), EXHAUSTIVE("exhaustive"), EACH_CHOICE("each-choice"), PAIRWISE("pairwise"), RANDOM("random"), EXTERNAL("external");
        final String id;
        Stage(String id) { this.id = id; }
    }
    enum Outcome { ACCEPTED, DUPLICATE, REJECTED, SKIPPED }

    /**
     * @param exhaustive   enumerate the whole domain; the cap does not apply
     * @param cap          accepted samples after which a staged registration stops
     * @param coverageOnly stop after pairwise coverage instead of filling to the cap with random samples
     * @param randomOnly   skip the coverage stages (calibration)
     */
    record Settings(boolean exhaustive, Stage exhaustiveStage, long cap, boolean coverageOnly, boolean randomOnly, long seed,
                    long maxAttempts, int maxTrackedNodes, int maxPairNodes, int maxPairArity) {
        static Settings exhaustive(Stage stage, long seed) {
            return new Settings(true, stage, Long.MAX_VALUE, false, false, seed, Long.MAX_VALUE, 8192, 128, 32);
        }
        static Settings staged(long cap, boolean coverageOnly, long seed) {
            return new Settings(false, Stage.EXHAUSTIVE, cap, coverageOnly, false, seed, Math.max(4 * cap, cap + 4096), 8192, 128, 32);
        }
        static Settings random(long attempts, long seed) {
            return new Settings(false, Stage.EXHAUSTIVE, attempts, false, true, seed, attempts, 8192, 128, 32);
        }
    }

    /** One build of the plan. {@code trail} holds (node, choice, arity) triples in visit order. */
    static final class Attempt {
        final Stage stage; final Object value; final Exception error; final int[] trail;
        final long target; final boolean retry;
        Attempt(Stage stage, Object value, Exception error, int[] trail, long target, boolean retry) {
            this.stage = stage; this.value = value; this.error = error; this.trail = trail; this.target = target; this.retry = retry;
        }
        /** (node, choice) pairs of real decisions; single-alternative nodes are omitted. */
        int[] decisions() {
            int n = 0;
            for (int i = 0; i < trail.length; i += 3) if (trail[i + 2] > 1) n++;
            int[] out = new int[2 * n];
            for (int i = 0, k = 0; i < trail.length; i += 3) if (trail[i + 2] > 1) { out[k++] = trail[i]; out[k++] = trail[i + 1]; }
            return out;
        }
    }

    private interface Policy { int choose(int node, int arity, Build build); }

    private final class Build implements ShapePlan.Chooser {
        final Policy policy;
        final IntList trail = new IntList();
        final IntList tracked = new IntList();
        Build(Policy policy) { this.policy = policy; }
        public int child(int parent, int slot) { return nodes.child(parent, slot); }
        public void product(int node, String label, List<String> dims) { nodes.describe(node, ShapePlan.Kind.PRODUCT, label, dims); }
        public int choose(int node, ShapePlan.Kind kind, String label, List<String> alternatives) {
            nodes.describe(node, kind, label, alternatives);
            int arity = alternatives.size();
            int choice = arity <= 1 ? 0 : policy.choose(node, arity, this);
            if (choice < 0 || choice >= arity) choice = 0;
            trail.add(node); trail.add(choice); trail.add(arity);
            int value = valueIndex(node, choice);
            if (value >= 0) tracked.add(value);
            return choice;
        }
    }

    private final ShapePlan plan;
    final NodeTable nodes = new NodeTable();
    private final Settings settings;
    private final SplittableRandom random;
    private Stage stage;
    private String stop;
    private final EnumMap<Stage, long[]> stageCounts = new EnumMap<>(Stage.class);
    private long attempts, accepted, duplicates, rejected, skipped;
    private final Set<Long> attemptedVectors = new HashSet<>();

    // exhaustive odometer
    private int[] lastTrail;
    private boolean started;

    // each-choice
    private final Map<Integer, int[]> witness = new HashMap<>();
    private final Map<Integer, BitSet> coveredChoices = new HashMap<>(), infeasibleChoices = new HashMap<>();
    private final ArrayDeque<long[]> targets = new ArrayDeque<>();
    private final List<int[]> acceptedTrails = new ArrayList<>();
    private int untrackedNodes;
    private boolean baselineDone;
    // A node whose every one-factor flip re-encodes to an existing frame never reaches the wire
    // (e.g. a legacy bean field the base packet does not write); it is held at its witness choice.
    private final Map<Integer, Integer> witnessChoice = new HashMap<>();
    private final Map<Integer, int[]> flips = new HashMap<>();
    private final Set<Integer> inert = new HashSet<>();
    private long windowStart = -1, windowAccepted;

    // pairwise
    private Map<Integer, Integer> trackedIndex = Map.of();
    private int[] trackedNodes = new int[0], valueBase = new int[0], valueCount = new int[0], valueNode = new int[0];
    private int values;
    private BitSet pairDone, pairCovered, pairInfeasible;
    private long pairTotal, pairExcluded;
    private int pairCursor;
    private int[] usage = new int[0];
    private final Map<Integer, Integer> pairFailures = new HashMap<>();

    Sampler(ShapePlan plan, Settings settings) {
        this.plan = plan; this.settings = settings;
        this.random = new SplittableRandom(settings.seed());
        this.stage = settings.exhaustive() ? settings.exhaustiveStage() : settings.randomOnly() ? Stage.RANDOM : Stage.EACH_CHOICE;
        for (Stage s : Stage.values()) stageCounts.put(s, new long[4]);
        if (plan.size().signum() == 0) stop = "no-domain";
    }

    String stop() { return stop; }
    long accepted() { return accepted; }
    long attempts() { return attempts; }
    void stop(String reason) { if (stop == null) stop = reason; }

    /** The next attempt, or null once a stop reason is set. */
    Attempt next() {
        while (stop == null) {
            if (attempts >= settings.maxAttempts()) { stop("attempts"); break; }
            if (!settings.exhaustive() && accepted >= settings.cap()) { stop(settings.randomOnly() ? "calibrated" : "budget"); break; }
            if (attempts >= 16 && skipped == attempts) { stop("encoder-skipped"); break; }
            switch (stage) {
                case EXHAUSTIVE, SEED -> {
                    int[] prefix = odometer();
                    if (prefix == null) { stop(attempts > 0 && skipped == attempts ? "encoder-skipped" : "exhaustive"); break; }
                    int[] replay = prefix;
                    Attempt a = build(stage, (node, arity, b) -> {
                        int position = b.trail.size() / 3 * 2;
                        return position + 1 < replay.length && replay[position] == node ? replay[position + 1] : 0;
                    }, -1, false);
                    lastTrail = a.trail;
                    return a;
                }
                case EACH_CHOICE -> {
                    if (!baselineDone) { baselineDone = true; return build(stage, (node, arity, b) -> 0, -1, false); }
                    long[] target = nextTarget();
                    if (target == null) { startPairwise(); stage = Stage.PAIRWISE; continue; }
                    int node = (int) target[0], choice = (int) target[1];
                    boolean retry = target[2] != 0;
                    Map<Integer, Integer> forced = new HashMap<>();
                    if (retry) forced.putAll(nodes.branchPath(node));
                    else { int[] w = witness.get(node); for (int i = 0; i < w.length; i += 3) forced.put(w[i], w[i + 1]); }
                    forced.put(node, choice);
                    Policy fallback = retry ? this::randomChoice : (n, arity, b) -> 0;
                    return build(stage, (n, arity, b) -> { Integer f = forced.get(n); return f != null ? f : fallback.choose(n, arity, b); },
                            pack(node, choice), retry);
                }
                case PAIRWISE -> {
                    int pair = nextPair();
                    if (pair < 0) {
                        if (settings.coverageOnly()) { stop("pairwise-covered"); break; }
                        stage = Stage.RANDOM; continue;
                    }
                    int[] ij = unpair(pair);
                    int a = ij[0], b = ij[1];
                    Map<Integer, Integer> forced = new HashMap<>(nodes.branchPath(valueNode[a]));
                    forced.putAll(nodes.branchPath(valueNode[b]));
                    forced.put(valueNode[a], a - valueBase[trackedIndex.get(valueNode[a])]);
                    forced.put(valueNode[b], b - valueBase[trackedIndex.get(valueNode[b])]);
                    return build(stage, (n, arity, build) -> {
                        Integer f = forced.get(n);
                        return f != null ? f : greedy(n, arity, build, a, b);
                    }, pair, false);
                }
                case RANDOM -> {
                    // Random fill ends once it stops finding new frames: fewer than 1% of a window of
                    // attempts produced one, so the reachable bytes are (nearly) exhausted.
                    long window = Math.max(2000, accepted / 10);
                    if (windowStart < 0) { windowStart = attempts; windowAccepted = accepted; }
                    else if (attempts - windowStart >= window) {
                        if ((accepted - windowAccepted) * 100 < window) { stop("saturated"); break; }
                        windowStart = attempts; windowAccepted = accepted;
                    }
                    Attempt a = build(stage, this::randomChoice, -1, false);
                    if (a.error == null && a.target == Long.MIN_VALUE) { feedback(a, Outcome.DUPLICATE); continue; }
                    return a;
                }
                default -> throw new IllegalStateException(stage.id);
            }
        }
        return null;
    }

    private Attempt build(Stage stage, Policy policy, long target, boolean retry) {
        Build b = new Build(policy);
        Object value = null; Exception error = null;
        try { value = plan.build(b, NodeTable.ROOT); }
        catch (Exception e) { error = e; }
        catch (StackOverflowError e) { error = new IllegalStateException("construction recursed too deeply", e); }
        int[] trail = b.trail.toArray();
        attempts++; stageCounts.get(stage)[0]++;
        discover(trail);
        boolean repeated = !attemptedVectors.add(vectorHash(trail));
        // Random decisions can repeat a vector already attempted; report it without re-encoding.
        return new Attempt(stage, value, error, trail, stage == Stage.RANDOM && repeated ? Long.MIN_VALUE : target, retry);
    }

    void feedback(Attempt a, Outcome outcome) {
        long[] counts = stageCounts.get(a.stage);
        if (a.stage == Stage.EACH_CHOICE && a.target >= 0) {
            int node = (int) (a.target >>> 32), choice = (int) a.target;
            Integer w = witnessChoice.get(node);
            if (w != null && w != choice && (outcome == Outcome.ACCEPTED || outcome == Outcome.DUPLICATE)) {
                int[] f = flips.computeIfAbsent(node, k -> new int[2]);
                f[0]++;
                if (outcome == Outcome.DUPLICATE) f[1]++;
            }
        }
        switch (outcome) {
            case ACCEPTED -> { accepted++; counts[1]++; }
            case DUPLICATE -> { duplicates++; counts[2]++; }
            case REJECTED -> { rejected++; counts[3]++; }
            case SKIPPED -> { skipped++; rejected++; counts[3]++; }
        }
        if (outcome == Outcome.ACCEPTED || outcome == Outcome.DUPLICATE) markCovered(a.trail);
        // A target counts as reached only if a kept sample contains it; an accepted build that
        // took another path (or a rejected one) is a failed try, never an endless retry.
        if (a.stage == Stage.EACH_CHOICE && a.target >= 0) {
            int node = (int) (a.target >>> 32), choice = (int) a.target;
            if (bit(coveredChoices, node, choice)) return;
            if (!a.retry) targets.addFirst(new long[]{node, choice, 1});
            else infeasibleChoices.computeIfAbsent(node, k -> new BitSet()).set(choice);
        } else if (a.stage == Stage.PAIRWISE && a.target >= 0) {
            int pair = (int) a.target;
            if (pairDone.get(pair)) return;
            if (pairFailures.merge(pair, 1, Integer::sum) >= 3) { pairInfeasible.set(pair); pairDone.set(pair); }
        }
    }

    // ---- exhaustive ------------------------------------------------------------------------

    /** The next replay prefix as (node, choice) pairs, or null when the tree is exhausted. */
    private int[] odometer() {
        if (!started) { started = true; return new int[0]; }
        if (lastTrail == null) return null;
        for (int i = lastTrail.length - 3; i >= 0; i -= 3) {
            if (lastTrail[i + 1] + 1 < lastTrail[i + 2]) {
                int[] prefix = new int[(i / 3 + 1) * 2];
                for (int k = 0, j = 0; k < i; k += 3) { prefix[j++] = lastTrail[k]; prefix[j++] = lastTrail[k + 1]; }
                prefix[prefix.length - 2] = lastTrail[i]; prefix[prefix.length - 1] = lastTrail[i + 1] + 1;
                return prefix;
            }
        }
        return null;
    }

    // ---- each-choice ---------------------------------------------------------------------------

    private void discover(int[] trail) {
        if (settings.exhaustive()) return;
        for (int i = 0; i < trail.length; i += 3) {
            int node = trail[i], arity = trail[i + 2];
            if (arity < 2 || witness.containsKey(node)) continue;
            if (witness.size() >= settings.maxTrackedNodes()) { witness.putIfAbsent(node, null); untrackedNodes++; continue; }
            witness.put(node, trail);
            witnessChoice.put(node, trail[i + 1]);
            for (int c = 0; c < arity; c++) targets.addLast(new long[]{node, c, 0});
        }
    }

    private long[] nextTarget() {
        while (!targets.isEmpty()) {
            long[] t = targets.pollFirst();
            int node = (int) t[0], choice = (int) t[1];
            if (witness.get(node) == null) continue;
            if (bit(coveredChoices, node, choice) || bit(infeasibleChoices, node, choice)) continue;
            return t;
        }
        return null;
    }

    private void markCovered(int[] trail) {
        if (settings.exhaustive()) return;
        IntList tracked = new IntList();
        for (int i = 0; i < trail.length; i += 3) {
            if (trail[i + 2] < 2) continue;
            coveredChoices.computeIfAbsent(trail[i], k -> new BitSet()).set(trail[i + 1]);
            int v = valueIndex(trail[i], trail[i + 1]);
            if (v >= 0) tracked.add(v);
        }
        if (pairDone == null) { acceptedTrails.add(trail); return; }
        int[] vs = tracked.toArray();
        for (int x = 0; x < vs.length; x++) {
            usage[vs[x]]++;
            for (int y = x + 1; y < vs.length; y++) {
                if (vs[x] == vs[y]) continue;
                int p = pair(vs[x], vs[y]);
                if (p >= 0 && !pairDone.get(p)) { pairCovered.set(p); pairDone.set(p); }
            }
        }
    }

    // ---- pairwise --------------------------------------------------------------------------

    private void startPairwise() {
        for (Map.Entry<Integer, int[]> e : flips.entrySet()) {
            int arity = nodes.arity(e.getKey());
            if (e.getValue()[0] >= arity - 1 && e.getValue()[1] == e.getValue()[0]) inert.add(e.getKey());
        }
        List<Integer> chosen = new ArrayList<>();
        for (Map.Entry<Integer, int[]> e : new TreeMap<>(witness).entrySet()) {
            if (e.getValue() != null && !inert.contains(e.getKey()) && nodes.arity(e.getKey()) >= 2 && chosen.size() < settings.maxPairNodes()) chosen.add(e.getKey());
        }
        trackedNodes = chosen.stream().mapToInt(Integer::intValue).toArray();
        trackedIndex = new HashMap<>();
        valueBase = new int[trackedNodes.length]; valueCount = new int[trackedNodes.length];
        IntList owner = new IntList();
        for (int t = 0; t < trackedNodes.length; t++) {
            trackedIndex.put(trackedNodes[t], t);
            valueBase[t] = values; valueCount[t] = Math.min(nodes.arity(trackedNodes[t]), settings.maxPairArity());
            for (int c = 0; c < valueCount[t]; c++) owner.add(trackedNodes[t]);
            values += valueCount[t];
        }
        valueNode = owner.toArray();
        usage = new int[values];
        long total = (long) values * (values - 1) / 2;
        pairDone = new BitSet((int) total); pairCovered = new BitSet(); pairInfeasible = new BitSet();
        // Exclude pairs that can never share a sample: same node, or different branches of a union.
        for (int a = 0; a < trackedNodes.length; a++) for (int b = a; b < trackedNodes.length; b++) {
            int na = trackedNodes[a], nb = trackedNodes[b];
            Map<Integer, Integer> pa = nodes.branchPath(na), pb = nodes.branchPath(nb);
            boolean conflict = a == b;
            for (Map.Entry<Integer, Integer> e : pa.entrySet()) {
                Integer other = pb.get(e.getKey());
                if (other != null && !other.equals(e.getValue())) conflict = true;
            }
            Integer aUnderB = pa.get(nb), bUnderA = pb.get(na);
            for (int ca = 0; ca < valueCount[a]; ca++) for (int cb = a == b ? ca + 1 : 0; cb < valueCount[b]; cb++) {
                int va = valueBase[a] + ca, vb = valueBase[b] + cb;
                boolean excluded = conflict || aUnderB != null && aUnderB != cb || bUnderA != null && bUnderA != ca;
                boolean infeasible = !excluded && (bit(infeasibleChoices, na, ca) || bit(infeasibleChoices, nb, cb));
                int p = pair(va, vb);
                if (excluded) { pairDone.set(p); pairExcluded++; }
                else if (infeasible) { pairDone.set(p); pairInfeasible.set(p); }
            }
        }
        pairTotal = total - pairExcluded;
        for (int[] trail : acceptedTrails) markCovered(trail);
        acceptedTrails.clear();
    }

    private int valueIndex(int node, int choice) {
        Integer t = trackedIndex.get(node);
        return t == null || choice >= valueCount[t] ? -1 : valueBase[t] + choice;
    }

    private int nextPair() {
        if (pairDone == null) return -1;
        long total = (long) values * (values - 1) / 2;
        int p = pairDone.nextClearBit(pairCursor);
        if (p >= total) return -1;
        pairCursor = p;
        return p;
    }

    /** Picks the alternative that covers most open pairs with the values already decided in this build. */
    private int greedy(int node, int arity, Build build, int forcedA, int forcedB) {
        Integer t = trackedIndex.get(node);
        if (t == null) return randomChoice(node, arity, build);
        int[] decided = build.tracked.toArray();
        int best = -1, bestScore = -1, bestUsage = Integer.MAX_VALUE;
        for (int c = 0; c < valueCount[t]; c++) {
            if (bit(infeasibleChoices, node, c)) continue;
            int v = valueBase[t] + c, score = 0;
            for (int d : decided) if (open(v, d)) score++;
            if (open(v, forcedA)) score++;
            if (open(v, forcedB)) score++;
            if (score > bestScore || score == bestScore && (usage[v] < bestUsage || usage[v] == bestUsage && random.nextBoolean())) {
                best = c; bestScore = score; bestUsage = usage[v];
            }
        }
        return best < 0 ? random.nextInt(arity) : best;
    }

    private int randomChoice(int node, int arity, Build build) {
        if (inert.contains(node)) { Integer w = witnessChoice.get(node); return w == null ? 0 : w; }
        return random.nextInt(arity);
    }

    private boolean open(int a, int b) {
        if (a == b) return false;
        int p = pair(a, b);
        return p >= 0 && !pairDone.get(p);
    }

    private static int pair(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return (int) ((long) hi * (hi - 1) / 2 + lo);
    }

    private static int[] unpair(int p) {
        int hi = (int) ((1 + Math.sqrt(1 + 8.0 * p)) / 2);
        while ((long) hi * (hi - 1) / 2 > p) hi--;
        while ((long) (hi + 1) * hi / 2 <= p) hi++;
        return new int[]{(int) (p - (long) hi * (hi - 1) / 2), hi};
    }

    // ---- reporting -----------------------------------------------------------------------------

    JsonObject coverage() {
        JsonObject out = new JsonObject();
        out.addProperty("mode", settings.exhaustive() ? "exhaustive" : settings.randomOnly() ? "calibration" : "staged");
        out.addProperty("domain", plan.size().toString());
        if (!settings.exhaustive() && !settings.randomOnly()) {
            out.addProperty("cap", settings.cap());
            out.addProperty("coverageOnly", settings.coverageOnly());
        }
        out.addProperty("stop", stop == null ? "running" : stop);
        out.addProperty("attempts", attempts); out.addProperty("accepted", accepted);
        out.addProperty("duplicates", duplicates); out.addProperty("rejected", rejected); out.addProperty("encoderSkipped", skipped);
        JsonObject stages = new JsonObject();
        for (Map.Entry<Stage, long[]> e : stageCounts.entrySet()) {
            long[] c = e.getValue();
            if (c[0] == 0) continue;
            JsonObject s = new JsonObject();
            s.addProperty("attempts", c[0]); s.addProperty("accepted", c[1]); s.addProperty("duplicates", c[2]); s.addProperty("rejected", c[3]);
            stages.add(e.getKey().id, s);
        }
        out.add("stages", stages);
        out.addProperty("nodes", nodes.size());
        if (!settings.exhaustive()) {
            long total = 0, covered = 0, infeasible = 0;
            for (Map.Entry<Integer, int[]> e : witness.entrySet()) {
                if (e.getValue() == null) continue;
                int node = e.getKey(), arity = nodes.arity(node);
                total += arity;
                BitSet c = coveredChoices.get(node), x = infeasibleChoices.get(node);
                for (int k = 0; k < arity; k++) {
                    if (c != null && c.get(k)) covered++;
                    else if (x != null && x.get(k)) infeasible++;
                }
            }
            JsonObject choices = new JsonObject();
            choices.addProperty("total", total); choices.addProperty("covered", covered); choices.addProperty("infeasible", infeasible);
            choices.addProperty("uncovered", total - covered - infeasible); choices.addProperty("untrackedNodes", untrackedNodes);
            out.add("choices", choices);
            JsonObject inertJson = new JsonObject();
            inertJson.addProperty("nodes", inert.size());
            com.google.gson.JsonArray paths = new com.google.gson.JsonArray();
            inert.stream().sorted().limit(32).forEach(n -> paths.add(nodes.path(n)));
            inertJson.add("paths", paths);
            out.add("inert", inertJson);
            if (pairDone != null) {
                JsonObject pairs = new JsonObject();
                pairs.addProperty("nodes", trackedNodes.length); pairs.addProperty("values", values);
                pairs.addProperty("total", pairTotal); pairs.addProperty("covered", pairCovered.cardinality());
                pairs.addProperty("infeasible", pairInfeasible.cardinality());
                pairs.addProperty("uncovered", pairTotal - pairCovered.cardinality() - pairInfeasible.cardinality());
                out.add("pairs", pairs);
            }
        }
        return out;
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static long pack(int node, int choice) { return ((long) node << 32) | (choice & 0xffffffffL); }
    private static boolean bit(Map<Integer, BitSet> sets, int node, int choice) { BitSet b = sets.get(node); return b != null && b.get(choice); }

    static long vectorHash(int[] trail) {
        long h = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < trail.length; i += 3) {
            h = (h ^ trail[i]) * 0xBF58476D1CE4E5B9L;
            h = (h ^ trail[i + 1]) * 0x94D049BB133111EBL;
            h ^= h >>> 31;
        }
        return h;
    }

    /** Growable int array; avoids boxing on the per-decision path. */
    static final class IntList {
        private int[] data = new int[32];
        private int size;
        void add(int v) { if (size == data.length) data = Arrays.copyOf(data, size * 2); data[size++] = v; }
        int size() { return size; }
        int get(int i) { return data[i]; }
        int[] toArray() { return Arrays.copyOf(data, size); }
    }

}
