package dev.minecraftdatagenerator.packetgen;

import com.google.gson.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Standalone regression suite, intentionally runnable without any Minecraft JAR. */
public final class PacketGenTest {
    enum Ten { A, B, C, D, E, F, G, H, I, J }
    record Nested(boolean flag, Optional<Ten> value) {}
    record Generic<T>(T value) {}
    record Types(Generic<Boolean> generic, List<Boolean> list, Map<Boolean, Ten> map, int[] array) {}
    static final class TestByteBuf {}
    record Payload(String channel, TestByteBuf data) {}
    record Partial(boolean flag, Optional<Runnable> task) {}
    record Tree(int value, List<Tree> children) {}
    static final class DefaultedList<T> extends ArrayList<T> {
        private DefaultedList(int unused) {}
        public static <T> DefaultedList<T> of() { return new DefaultedList<>(0); }
    }
    private DefaultedList<Boolean> defaulted;
    static final class Tracker {
        final Map<Integer, Object> entries = new HashMap<>();
        Tracker(Object owner) {}
    }
    record TrackerEntry(int id, String value) { public int getId() { return id; } }
    static final class MobSpawnS2CPacket {
        Tracker tracker;
        List<TrackerEntry> entries = List.of(new TrackerEntry(7, "received"));
    }
    static final class ChunkDataS2CPacket {
        byte[] field_8637;
        static byte[] field_8641 = { 1, 2, 3, 99 };
        int field_8640 = 3;
    }
    static final class ChunkDeltaUpdateS2CPacket {
        byte[] field_8598 = { 0x12, 0x34, 0x00, 0x10 };
        int field_8599 = 1;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static ReflectionShapes engine() { return new ReflectionShapes((t, p, d, e) -> null, 6); }

    /** Every value of an exhaustive run, in odometer order. */
    private static List<Object> enumerate(ShapePlan plan) {
        Sampler sampler = new Sampler(plan, Sampler.Settings.exhaustive(Sampler.Stage.EXHAUSTIVE, 1));
        List<Object> out = new ArrayList<>();
        Sampler.Attempt a;
        while ((a = sampler.next()) != null) {
            if (a.error != null) throw new AssertionError(a.error);
            out.add(a.value);
            sampler.feedback(a, Sampler.Outcome.ACCEPTED);
        }
        check("exhaustive".equals(sampler.stop()), "odometer ends exhaustively, not " + sampler.stop());
        return out;
    }

    /** A staged run that accepts every distinct value; returns accepted values. */
    private static List<Object> staged(ShapePlan plan, long cap, boolean coverageOnly, Sampler[] out) {
        Sampler sampler = new Sampler(plan, Sampler.Settings.staged(cap, coverageOnly, 7));
        if (out != null) out[0] = sampler;
        List<Object> accepted = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Sampler.Attempt a;
        while ((a = sampler.next()) != null) {
            if (a.error != null) { sampler.feedback(a, Sampler.Outcome.REJECTED); continue; }
            String key = a.value instanceof Object[] array ? Arrays.deepToString(array) : String.valueOf(a.value);
            boolean fresh = seen.add(key);
            if (fresh) accepted.add(a.value);
            sampler.feedback(a, fresh ? Sampler.Outcome.ACCEPTED : Sampler.Outcome.DUPLICATE);
        }
        return accepted;
    }

    public static void main(String[] args) throws Exception {
        legacyReceiveState();
        domains();
        coverage();
        gaps();
        streams();
        System.out.println("Packet generator regression checks passed (domains, coverage stages, gaps, streams, validation, external candidates).");
    }

    private static void legacyReceiveState() throws Exception {
        MobSpawnS2CPacket received = new MobSpawnS2CPacket();
        check(LegacyReceiveState.restore(received) != null, "restore tracker from received entries");
        check(received.tracker.entries.get(7).equals(received.entries.get(0)), "preserve decoded metadata");
        check(LegacyReceiveState.restore(received) == null, "never replace populated tracker");
        ChunkDataS2CPacket chunkReceived = new ChunkDataS2CPacket();
        check(LegacyReceiveState.restore(chunkReceived) != null, "retain received compressed cache");
        ChunkDataS2CPacket.field_8641[0] = 77;
        check(Arrays.equals(chunkReceived.field_8637, new byte[] { 1, 2, 3 }), "scratch cache copied at decoded length");
        ChunkDeltaUpdateS2CPacket delta = new ChunkDeltaUpdateS2CPacket();
        LegacyReceiveState.validateSource(delta);
        delta.field_8599 = 2;
        try { LegacyReceiveState.validateSource(delta); throw new AssertionError("inconsistent source count accepted"); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().contains("four-byte records"), "source constraint recorded"); }
    }

    private static void domains() throws Exception {
        ShapePlan nested = engine().plan(Nested.class);
        check(nested.size().equals(BigInteger.valueOf(22)), "both booleans and every optional enum alternative");
        List<Object> values = enumerate(nested);
        check(values.size() == 22 && new HashSet<>(values).size() == 22 && values.contains(new Nested(true, Optional.of(Ten.J))), "odometer visits every value once");
        Type generic = Types.class.getRecordComponents()[0].getGenericType();
        check(engine().plan(generic).size().equals(BigInteger.TWO), "record type variable resolved");
        ShapePlan list = engine().plan(Types.class.getRecordComponents()[1].getGenericType());
        check(list.size().equals(BigInteger.valueOf(7)), "collection element products, including mixed pairs");
        Set<Object> listValues = new HashSet<>(enumerate(list));
        check(listValues.contains(List.of(false, true)) && listValues.contains(List.of(true, false)), "mixed collection values");
        check(engine().plan(int[].class).size().intValueExact() == 183, "primitive array lengths 0, 1, 2");
        ShapePlan uneven = ShapePlan.product("uneven", List.of(ShapePlan.values("a", 0, 1, 2),
                ShapePlan.values("b", 0, 1, 2, 3, 4), ShapePlan.values("c", 0, 1, 2, 3, 4, 5, 6)), Arrays::asList);
        check(new HashSet<>(enumerate(uneven)).size() == 105, "exhaustive traversal is bijective for unequal dimensions");
        ShapePlan payload = new ReflectionShapes((t, p, d, e) -> t == TestByteBuf.class ? ShapePlan.leaves("buffer", List.of(TestByteBuf::new)) : null, 6).plan(Payload.class);
        check(payload.size().equals(BigInteger.valueOf(8)) && enumerate(payload).get(0) instanceof Payload, "data constructors may accept a payload buffer alongside other fields");
        ShapePlan defaulted = engine().plan(PacketGenTest.class.getDeclaredField("defaulted").getGenericType());
        check(defaulted.size().equals(BigInteger.valueOf(7)) && enumerate(defaulted).stream().anyMatch(v -> ((List<?>) v).size() == 2), "factory-only collection implementations retain nonempty products");
        check(ReflectionShapes.longValues().containsAll(List.of(Long.MIN_VALUE, Long.MAX_VALUE, -1L, 1L << 56)), "full VarLong domain");
        check(Arrays.equals(StaticFixtures.rsaPublicKey(2048).getEncoded(), StaticFixtures.rsaPublicKey(2048).getEncoded()), "public wire fixtures survive restart");
        // Node identity follows the position, not the (cached) plan: two list slots are two nodes.
        NodeTable nodes = new NodeTable();
        int listNode = nodes.child(0, 2), first = nodes.child(listNode, 0), second = nodes.child(listNode, 1);
        check(first != second && nodes.child(listNode, 1) == second && nodes.parent(second) == listNode, "interned node ids per slot");
    }

    private static void coverage() {
        // The old ordinal prefix moved slow fields in lockstep: with three booleans behind wide
        // fields only the all-false/all-true triples appeared. Pairwise coverage must show every pair.
        ShapePlan move = ShapePlan.product("move", List.of(
                ShapePlan.values("x", 0d, -0d, 1d, -1d, 3.5d, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, 1e300, -1e300),
                ShapePlan.values("y", 0d, -0d, 1d, -1d, 3.5d, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, 1e300, -1e300),
                ShapePlan.values("z", 0d, -0d, 1d, -1d, 3.5d, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, 1e300, -1e300),
                ShapePlan.values("changeLook", false, true), ShapePlan.values("changePosition", false, true), ShapePlan.values("onGround", false, true)), a -> a);
        Sampler[] holder = new Sampler[1];
        List<Object> samples = staged(move, 100_000, true, holder);
        Set<String> triples = new HashSet<>(), pairs = new HashSet<>();
        for (Object s : samples) {
            Object[] a = (Object[]) s;
            triples.add(a[3] + "," + a[4] + "," + a[5]);
            pairs.add("a" + a[3] + a[4]); pairs.add("b" + a[3] + a[5]); pairs.add("c" + a[4] + a[5]);
        }
        JsonObject cov = holder[0].coverage();
        check(pairs.size() == 12, "every boolean pair occurs: " + pairs);
        check(cov.getAsJsonObject("pairs").get("uncovered").getAsLong() == 0 && "pairwise-covered".equals(holder[0].stop()), "pairwise stage completes: " + cov);
        check(samples.size() < 400, "pairwise stays small: " + samples.size());
        List<Object> filled = staged(move, 2000, false, holder);
        for (Object s : filled) { Object[] a = (Object[]) s; triples.add(a[3] + "," + a[4] + "," + a[5]); }
        check(filled.size() == 2000 && "budget".equals(holder[0].stop()) && triples.size() == 8, "random fill reaches the cap and all boolean triples");

        // A huge first branch cannot starve the others: each-choice visits every union branch.
        ShapePlan huge = ShapePlan.product("huge", Collections.nCopies(100, ShapePlan.values("bit", false, true)), a -> a);
        check(huge.size().equals(BigInteger.TWO.pow(100)), "combination count has no long overflow");
        ShapePlan branches = ShapePlan.union("branches", List.of(huge, ShapePlan.values("short", "one", "two"), ShapePlan.values("empty")));
        check(branches.size().equals(BigInteger.TWO.pow(100).add(BigInteger.TWO)), "empty alternatives are pruned");
        List<Object> reached = staged(branches, 1000, true, holder);
        check(reached.contains("one") && reached.contains("two") && reached.stream().anyMatch(v -> v instanceof Object[]), "every branch and value reached");
        JsonObject choices = holder[0].coverage().getAsJsonObject("choices");
        // root branches 2, the short leaf 2, 100 bits x 2
        check(choices.get("uncovered").getAsLong() == 0 && choices.get("total").getAsLong() == 204, "each-choice covers every alternative: " + choices);
        List<Object> bits = staged(huge, 100_000, true, holder);
        check(holder[0].coverage().getAsJsonObject("pairs").get("uncovered").getAsLong() == 0 && bits.size() < 200, "100 binary factors are pairwise covered in few samples: " + bits.size());

        // A field that never reaches the wire (only `a` is encoded) is detected as inert, and random
        // fill stops once no new frames appear instead of spending the attempt ceiling.
        ShapePlan inertPlan = ShapePlan.product("legacy", List.of(ShapePlan.values("a", false, true),
                ShapePlan.values("b", 0, 1, 2, 3, 4, 5, 6, 7, 8, 9), ShapePlan.values("c", 0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
                ShapePlan.values("d", 0, 1, 2, 3, 4, 5, 6, 7, 8, 9), ShapePlan.values("e", 0, 1, 2, 3, 4, 5, 6, 7, 8, 9)), a -> a);
        Sampler inertSampler = new Sampler(inertPlan, Sampler.Settings.staged(100_000, false, 5));
        Set<Object> frames = new HashSet<>();
        Sampler.Attempt attempt;
        while ((attempt = inertSampler.next()) != null) {
            boolean fresh = frames.add(((Object[]) attempt.value)[0]);
            inertSampler.feedback(attempt, fresh ? Sampler.Outcome.ACCEPTED : Sampler.Outcome.DUPLICATE);
        }
        JsonObject inertCoverage = inertSampler.coverage();
        check("saturated".equals(inertSampler.stop()) && frames.size() == 2 && inertCoverage.getAsJsonObject("inert").get("nodes").getAsInt() == 4
                && inertSampler.attempts() < 5000, "inert fields detected and random fill saturates: " + inertCoverage);

        // Rejected alternatives become infeasible after a retry instead of looping.
        ShapePlan picky = ShapePlan.product("picky", List.of(ShapePlan.values("a", 0, 1, 2), ShapePlan.values("b", 0, 1, 2)), a -> {
            if ((int) a[0] == 2) throw new IllegalArgumentException("a=2 is invalid");
            return Arrays.asList(a);
        });
        Sampler rejecting = new Sampler(picky, Sampler.Settings.staged(1000, true, 3));
        Sampler.Attempt a;
        while ((a = rejecting.next()) != null) rejecting.feedback(a, a.error != null ? Sampler.Outcome.REJECTED : Sampler.Outcome.ACCEPTED);
        JsonObject rc = rejecting.coverage();
        check(rc.getAsJsonObject("choices").get("infeasible").getAsLong() == 1 && "pairwise-covered".equals(rejecting.stop()), "invalid choice recorded as infeasible: " + rc);

        // Deterministic for a fixed seed.
        check(deepEquals(staged(move, 500, false, null), staged(move, 500, false, null)), "seeded sampling is reproducible");
    }

    private static boolean deepEquals(List<Object> a, List<Object> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!Arrays.deepEquals(new Object[]{a.get(i)}, new Object[]{b.get(i)})) return false;
        return true;
    }

    private static void gaps() throws Exception {
        ReflectionShapes partialEngine = engine();
        ShapePlan partial = partialEngine.plan(Partial.class);
        check(partial.size().equals(BigInteger.TWO), "unbuildable optional value keeps the record and its absent branch");
        check(partialEngine.unresolved().stream().anyMatch(g -> g.contains("optional value unavailable")), "pruned alternative recorded: " + partialEngine.unresolved());
        ReflectionShapes treeEngine = new ReflectionShapes((t, p, d, e) -> null, 3);
        ShapePlan tree = treeEngine.plan(Tree.class);
        check(tree.size().signum() > 0 && !treeEngine.terminal().isEmpty() && treeEngine.unresolved().isEmpty(), "recursive record terminates with minimal values: " + treeEngine.unresolved());
        // depth 2 trees: 13 values x the terminal (empty) child list; depth 1 lists: 1 + 13 + 13^2
        check(tree.size().equals(BigInteger.valueOf(13L * 183)) && enumerate(tree).size() == 13 * 183, "terminal values build");
        check(ShapePlan.first(tree) instanceof Tree, "terminal plan builds a value");
    }

    // ---- streams -------------------------------------------------------------------------------

    private static PacketCorpus.Options options(Path out, Path work, long cap, String filter, boolean force, Path candidates) {
        return new PacketCorpus.Options(out, work, 10, cap, false, Map.of(), filter, false, force, 0, 64L << 20, 60, 3, candidates, 16);
    }

    private static void streams() throws Exception {
        Path root = Files.createTempDirectory("packetgen-stream-");
        Path out = root.resolve("packets"), work = root.resolve("work");
        // 0 and 1 encode as i32, so duplicates collapse; -1 re-encodes differently; -2 leaves trailing bytes; -3 is skipped.
        ShapePlan values = ShapePlan.values("values", 0, 0, 1, 1, -1, -2);
        JsonObject summary = PacketCorpus.run(fake("v1", values, List.of("", "context-1")), options(out, work, 100, null, false, null));
        Path file = out.resolve("test/status/toServer/example.mcpk.zst");
        check(Files.isRegularFile(file) && Files.isRegularFile(work.resolve("summary.json")), "stream and summary written");
        Stream stream = read(file);
        check(stream.header.get("packet").getAsString().equals("example") && stream.header.getAsJsonArray("registrations").size() == 2, "header identity and registrations");
        check(stream.plans.size() == 2 && stream.coverage.size() == 2, "one plan and one coverage record per registration");
        check(stream.samples == 4 && stream.aliases == 2, "two unique samples per context; the second context aliases the first: " + stream.samples + "/" + stream.aliases);
        JsonObject first = stream.coverage.get(0);
        check(first.get("stop").getAsString().equals("exhaustive") && first.get("accepted").getAsLong() == 2 && first.get("duplicates").getAsLong() == 2, "dedup keeps first occurrence: " + first);
        JsonObject rejections = first.getAsJsonObject("rejections");
        check(rejections.has("non-canonical") && rejections.has("trailing-bytes"), "rejections by reason: " + rejections);
        check(stream.trailerSha.equals(stream.framesSha), "trailer hash covers all frames");
        check(stream.offsetsConsistent, "shape sizes reconstruct every body");
        check(summary.get("status").getAsString().equals("complete"), "complete status: " + summary.get("status"));

        JsonObject again = PacketCorpus.run(fake("v1", values, List.of("", "context-1")), options(out, work, 100, null, false, null));
        check(again.getAsJsonArray("files").get(0).getAsJsonObject().get("status").getAsString().equals("unchanged"), "unchanged file is kept");
        JsonObject shrink = PacketCorpus.run(fake("v1", values, List.of("", "context-1")), options(out, work, 50, null, false, null));
        check(shrink.get("status").getAsString().equals("failed"), "smaller cap refused without force");
        JsonObject forced = PacketCorpus.run(fake("v1", values, List.of("", "context-1")), options(out, work, 50, null, true, null));
        check(forced.get("status").getAsString().equals("complete"), "force allows a smaller cap");
        JsonObject changed = PacketCorpus.run(fake("v2", values, List.of("")), options(out, work, 100, null, false, null));
        check(changed.getAsJsonArray("files").get(0).getAsJsonObject().get("status").getAsString().equals("complete") && read(file).header.getAsJsonArray("registrations").size() == 1, "changed fingerprint regenerates");

        Path skippedOut = root.resolve("skipped");
        JsonObject skipped = PacketCorpus.run(fake("skip", ShapePlan.values("skips", -3, -3), List.of("")), options(skippedOut, root.resolve("work-skip"), 100, null, false, null));
        JsonObject skippedRow = skipped.getAsJsonArray("files").get(0).getAsJsonObject().getAsJsonArray("registrations").get(0).getAsJsonObject();
        check(skippedRow.get("stop").getAsString().equals("encoder-skipped") && skippedRow.get("status").getAsString().equals("complete"), "explicit encoder skips: " + skippedRow);
        JsonObject empty = PacketCorpus.run(fake("empty", ShapePlan.values("none"), List.of("")), options(root.resolve("empty"), root.resolve("work-empty"), 100, null, false, null));
        check(empty.get("status").getAsString().equals("failed"), "zero samples cannot pass as complete");
        JsonObject filtered = PacketCorpus.run(fake("v1", values, List.of("")), options(root.resolve("filtered"), root.resolve("work-filtered"), 100, "play/", false, null));
        check(filtered.get("status").getAsString().equals("incomplete") && !Files.exists(root.resolve("filtered/test")), "filter excludes files");

        // External candidates: canonical, non-canonical, wrong id, trailing bytes.
        Path candidates = root.resolve("candidates");
        Path frames = candidates.resolve("schema/test/status/toServer/0.frames");
        Files.createDirectories(frames.getParent());
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        writeFrame(data, PacketCorpus.frame(0, int32(5)));
        writeFrame(data, PacketCorpus.frame(0, int32(-1)));
        writeFrame(data, PacketCorpus.frame(1, int32(5)));
        writeFrame(data, PacketCorpus.frame(0, new byte[]{0, 0, 0, 7, 9}));
        Files.write(frames, data.toByteArray());
        Path extOut = root.resolve("external"), extWork = root.resolve("work-external");
        PacketCorpus.run(fake("ext", ShapePlan.values("values", 0), List.of("")), options(extOut, extWork, 100, null, false, candidates));
        List<String> results = Files.readAllLines(extWork.resolve("external-results/schema/test/status/toServer/0.results.ndjson"));
        List<String> outcomes = results.stream().map(l -> JsonParser.parseString(l).getAsJsonObject().get("outcome").getAsString() + ":" +
                Optional.ofNullable(JsonParser.parseString(l).getAsJsonObject().get("reason")).map(JsonElement::getAsString).orElse("")).toList();
        check(outcomes.equals(List.of("accepted:", "non-canonical:", "rejected:wrong-id", "rejected:trailing-bytes")), "external outcomes: " + outcomes);
        Stream ext = read(extOut.resolve("test/status/toServer/example.mcpk.zst"));
        check(ext.header.getAsJsonArray("sources").toString().contains("external:schema") && ext.decodeOnly == 1 && ext.samples == 4, "external samples and decode-only original: " + ext.samples + " " + ext.decodeOnly);
    }

    private static byte[] int32(int v) { return ByteBuffer.allocate(4).putInt(v).array(); }
    private static void writeFrame(ByteArrayOutputStream out, byte[] frame) throws Exception { PacketStream.uv(out, frame.length); out.write(frame); }

    /** Decoded test view of a stream. */
    private static final class Stream {
        JsonObject header; List<JsonObject> plans = new ArrayList<>(), coverage = new ArrayList<>();
        long samples, aliases, decodeOnly; String trailerSha, framesSha; boolean offsetsConsistent = true;
    }

    private static Stream read(Path file) throws Exception {
        Stream s = new Stream();
        Map<Integer, String[]> shapes = new HashMap<>();
        List<String> kinds = null;
        java.security.MessageDigest frames = java.security.MessageDigest.getInstance("SHA-256");
        try (PacketStream.Reader reader = new PacketStream.Reader(file)) {
            PacketStream.Record r;
            while ((r = reader.next()) != null) {
                switch (r.type()) {
                    case PacketStream.HEADER -> { s.header = r.json(); kinds = new ArrayList<>(); for (JsonElement k : s.header.getAsJsonArray("kinds")) kinds.add(k.getAsString()); }
                    case PacketStream.PLAN -> s.plans.add(r.json());
                    case PacketStream.COVERAGE -> s.coverage.add(r.json());
                    case PacketStream.TRAILER -> s.trailerSha = r.json().get("framesSha256").getAsString();
                    case PacketStream.SHAPE -> {
                        java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(r.payload());
                        int id = (int) PacketStream.readUv(in); in.read(); int n = (int) PacketStream.readUv(in);
                        String[] writes = new String[n];
                        for (int i = 0; i < n; i++) { writes[i] = kinds.get(in.read()); in.read(); }
                        shapes.put(id, writes);
                    }
                    case PacketStream.SAMPLE -> {
                        s.samples++;
                        java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(r.payload());
                        int regs = (int) PacketStream.readUv(in); for (int i = 0; i < regs; i++) PacketStream.readUv(in);
                        PacketStream.readUv(in); in.read(); int flags = (int) PacketStream.readUv(in);
                        if ((flags & PacketStream.F_ALIAS) != 0) { s.aliases++; continue; }
                        if ((flags & PacketStream.F_DECODE_ONLY) != 0) s.decodeOnly++;
                        String[] shape = shapes.get((int) PacketStream.readUv(in));
                        byte[] frame = in.readNBytes((int) PacketStream.readUv(in));
                        frames.update(frame);
                        int idLength = PacketCorpus.readVarint(frame, 0)[1], total = 0;
                        for (String kind : shape) total += PacketStream.variable(kind) ? (int) PacketStream.readUv(in) : TraceAlign.FIXED_SIZE.get(kind);
                        if (total != frame.length - idLength) s.offsetsConsistent = false;
                    }
                    default -> throw new AssertionError("unknown record " + r.type());
                }
            }
        }
        s.framesSha = HexFormat.of().formatHex(frames.digest());
        return s;
    }

    /** Integers on the wire as one big-endian i32; -1 re-encodes as -9 (unstable), -2 adds a trailing byte, -3 is skipped. */
    private static PacketAdapter fake(String fingerprint, ShapePlan plan, List<String> contexts) {
        return new PacketAdapter() {
            public String version() { return "test"; }
            public String fingerprint() { return fingerprint; }
            public List<Entry> inventory() {
                List<Entry> out = new ArrayList<>();
                for (String c : contexts) out.add(new Entry("status", "toServer", 0, "example", c, Integer.class, null));
                return out;
            }
            public Plan shapes(Entry e) { return new Plan(null, plan, List.of(), List.of()); }
            public JsonElement dump(Object value) { return new JsonPrimitive(String.valueOf(value)); }
            public byte[] encode(Entry e, Object value) throws Exception {
                int v = (int) value;
                if (v == -3) throw new EncoderSkipped(new IllegalStateException("context disabled"));
                return v == -2 ? new byte[]{0, 0, 0, 2, 0} : int32(v);
            }
            public Traced encodeTraced(Entry e, Object value) {
                int v = (int) value;
                return new Traced(int32(v == -1 ? -9 : v), List.of(new TraceOp(0, 4, "i32", v)), true);
            }
            public Decoded decode(Entry e, byte[] body) {
                int v = ByteBuffer.wrap(body).getInt();
                return new Decoded(v, List.of(new TraceOp(0, 4, "i32", null)), body.length == 4, false, null);
            }
        };
    }
}
