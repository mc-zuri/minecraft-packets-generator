package dev.minecraftdatagenerator.packetgen;

import com.google.gson.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Generates one {@link PacketStream} file per packet name. Candidates come from three producers -
 * handwritten seeds (always enumerated in full), external frames, and the reflective
 * {@link Sampler} - and every one passes the same server-side validation: vanilla decode with a
 * read trace, no trailing bytes, then a traced vanilla re-encode that must reproduce the bytes.
 */
public final class PacketCorpus {
    /**
     * @param threshold domains up to this size are enumerated exhaustively
     * @param cap       accepted reflective samples per registration above the threshold
     * @param caps      per-registration-path cap overrides
     */
    public record Options(Path output, Path work, long threshold, long cap, boolean coverageOnly, Map<String, Long> caps,
                          String filter, boolean planOnly, boolean force, long seed, long maxFrameBytes, long maxSeconds, int level,
                          Path candidates, int calibration) {
        public static Options environment(Path work) throws IOException {
            Map<String, String> env = System.getenv();
            Path output = env.containsKey("PACKETGEN_OUTPUT_DIR") && !env.get("PACKETGEN_OUTPUT_DIR").isBlank() ? Path.of(env.get("PACKETGEN_OUTPUT_DIR")) : work.resolve("packets");
            long cap = Long.parseLong(env.getOrDefault("PACKETGEN_CAP", "10000"));
            boolean coverageOnly = "1".equals(env.get("PACKETGEN_COVERAGE_ONLY"));
            Map<String, Long> caps = new TreeMap<>();
            String decisions = env.get("PACKETGEN_DECISIONS");
            if (decisions != null && !decisions.isBlank()) {
                JsonObject d = JsonParser.parseString(Files.readString(Path.of(decisions))).getAsJsonObject();
                if (d.has("cap")) cap = d.get("cap").getAsLong();
                if (d.has("coverageOnly")) coverageOnly = d.get("coverageOnly").getAsBoolean();
                if (d.has("caps")) for (Map.Entry<String, JsonElement> e : d.getAsJsonObject("caps").entrySet()) caps.put(e.getKey(), e.getValue().getAsLong());
            }
            String candidates = env.get("PACKETGEN_CANDIDATES_DIR");
            return new Options(output.toAbsolutePath().normalize(), work, Long.parseLong(env.getOrDefault("PACKETGEN_THRESHOLD", "100000")), cap, coverageOnly, caps,
                    env.get("PACKETGEN_FILTER"), "1".equals(env.get("PACKETGEN_PLAN_ONLY")), "1".equals(env.get("PACKETGEN_FORCE")),
                    Long.parseLong(env.getOrDefault("PACKETGEN_SEED", "0")), Long.parseLong(env.getOrDefault("PACKETGEN_MAX_FRAME_BYTES", String.valueOf(64L << 20))),
                    Long.parseLong(env.getOrDefault("PACKETGEN_MAX_SECONDS", "60")),
                    Integer.parseInt(env.getOrDefault("PACKETGEN_ZSTD_LEVEL", "10")),
                    candidates == null || candidates.isBlank() ? null : Path.of(candidates), 16);
        }
        public Options { if (threshold < 0 || cap < 1 || maxFrameBytes < 1 || maxSeconds < 1) throw new IllegalArgumentException("positive cap, frame and time ceilings required"); }
        long capFor(PacketAdapter.Entry entry) { return caps.getOrDefault(entry.path(), cap); }
    }

    static final List<String> STAGES = Arrays.stream(Sampler.Stage.values()).map(s -> s.id).toList();

    public static JsonObject run(PacketAdapter adapter, Options options) throws Exception {
        List<PacketAdapter.Entry> entries = new ArrayList<>(adapter.inventory());
        entries.sort(Comparator.comparing(PacketAdapter.Entry::key));
        Set<String> keys = new HashSet<>();
        for (PacketAdapter.Entry e : entries) if (!keys.add(e.key())) throw new IllegalStateException("duplicate registration " + e.key());
        Map<String, List<PacketAdapter.Entry>> files = new LinkedHashMap<>();
        for (PacketAdapter.Entry e : entries) files.computeIfAbsent(e.file(), k -> new ArrayList<>()).add(e);
        String fingerprint = adapter.fingerprint();
        Files.createDirectories(options.work());
        JsonObject summary = new JsonObject();
        summary.addProperty("formatVersion", 1);
        summary.addProperty("version", adapter.version());
        summary.addProperty("fingerprint", fingerprint);
        summary.addProperty("mode", options.planOnly() ? "plan" : "generate");
        summary.addProperty("output", options.output().toString());
        summary.add("settings", settings(options));
        summary.add("adapter", adapter.metadata());
        JsonArray fileRows = new JsonArray(); summary.add("files", fileRows);
        int failed = 0, incomplete = 0, complete = 0, filtered = 0, unchanged = 0;
        for (Map.Entry<String, List<PacketAdapter.Entry>> group : files.entrySet()) {
            List<PacketAdapter.Entry> members = group.getValue();
            JsonObject row = new JsonObject(); row.addProperty("file", group.getKey()); fileRows.add(row);
            if (options.filter() != null && members.stream().noneMatch(e -> e.path().contains(options.filter()))) { row.addProperty("status", "filtered"); filtered++; continue; }
            PacketAdapter.Entry first = members.get(0);
            Path target = options.output().resolve(adapter.version()).resolve(first.state()).resolve(first.direction()).resolve(PacketStream.fileName(first.name()));
            row.addProperty("path", options.output().relativize(target).toString().replace('\\', '/'));
            try {
                String status = options.planOnly() ? plan(adapter, members, options, row) : generate(adapter, members, target, fingerprint, options, row);
                row.addProperty("status", status);
                switch (status) {
                    case "complete" -> complete++;
                    case "unchanged" -> unchanged++;
                    case "incomplete" -> incomplete++;
                    default -> failed++;
                }
            } catch (Exception e) {
                row.addProperty("status", "failed"); row.addProperty("error", Reflect.error(e)); failed++;
                System.out.println("[packetgen] " + group.getKey() + " failed: " + Reflect.error(e));
            }
            summary.addProperty("status", "running");
            atomicJson(options.work().resolve(options.planOnly() ? "plan.json" : "summary.json"), summary);
        }
        JsonObject totals = new JsonObject();
        totals.addProperty("files", files.size()); totals.addProperty("complete", complete); totals.addProperty("unchanged", unchanged);
        totals.addProperty("incomplete", incomplete); totals.addProperty("failed", failed); totals.addProperty("filtered", filtered);
        long samples = 0, bytes = 0;
        for (JsonElement f : fileRows) {
            JsonObject r = f.getAsJsonObject();
            if (r.has("samples")) samples += r.get("samples").getAsLong();
            if (r.has("bytes")) bytes += r.get("bytes").getAsLong();
        }
        totals.addProperty("samples", samples); totals.addProperty("bytes", bytes);
        summary.add("totals", totals);
        summary.addProperty("status", failed > 0 ? "failed" : incomplete > 0 || filtered > 0 ? "incomplete" : "complete");
        atomicJson(options.work().resolve(options.planOnly() ? "plan.json" : "summary.json"), summary);
        return summary;
    }

    private static JsonObject settings(Options options) {
        JsonObject s = new JsonObject();
        s.addProperty("threshold", options.threshold()); s.addProperty("cap", options.cap()); s.addProperty("coverageOnly", options.coverageOnly());
        s.addProperty("seed", options.seed()); s.addProperty("maxFrameBytes", options.maxFrameBytes()); s.addProperty("maxSeconds", options.maxSeconds());
        JsonObject caps = new JsonObject(); options.caps().forEach(caps::addProperty); s.add("caps", caps);
        if (options.candidates() != null) s.addProperty("candidates", options.candidates().toString());
        return s;
    }

    /** Settings that determine one file's content; an existing file with equal settings is kept. */
    private static JsonObject sampler(List<PacketAdapter.Entry> members, Options options) throws Exception {
        JsonObject s = new JsonObject();
        s.addProperty("threshold", options.threshold()); s.addProperty("coverageOnly", options.coverageOnly());
        s.addProperty("seed", options.seed()); s.addProperty("maxFrameBytes", options.maxFrameBytes()); s.addProperty("maxSeconds", options.maxSeconds());
        JsonArray caps = new JsonArray(); for (PacketAdapter.Entry e : members) caps.add(options.capFor(e)); s.add("caps", caps);
        s.addProperty("candidates", candidateDigest(members, options));
        return s;
    }

    // ---- generation ----------------------------------------------------------------------------

    private static String generate(PacketAdapter adapter, List<PacketAdapter.Entry> members, Path target, String fingerprint, Options options, JsonObject row) throws Exception {
        JsonObject sampler = sampler(members, options);
        JsonObject existing = PacketStream.header(target);
        if (existing != null && existing.has("generator") && fingerprint.equals(existing.getAsJsonObject("generator").get("fingerprint").getAsString())) {
            if (sampler.equals(existing.get("sampler")) && existing.has("status")) {
                row.addProperty("bytes", Files.size(target)); row.addProperty("samples", existing.get("samples").getAsLong());
                row.addProperty("previousStatus", existing.get("status").getAsString());
                return "unchanged";
            }
            JsonArray before = existing.getAsJsonObject("sampler").getAsJsonArray("caps"), after = sampler.getAsJsonArray("caps");
            for (int i = 0; i < Math.min(before.size(), after.size()); i++) {
                if (after.get(i).getAsLong() < before.get(i).getAsLong() && !options.force())
                    throw new IllegalStateException("would replace " + target.getFileName() + " with a smaller cap; set PACKETGEN_FORCE=1 to allow");
            }
        }
        PacketAdapter.Entry first = members.get(0);
        long started = System.nanoTime();
        try (PacketStream.Writer writer = new PacketStream.Writer(target, options.work().resolve("tmp"), options.level())) {
            FileRun file = new FileRun(adapter, writer, options);
            JsonArray registrations = new JsonArray(), rows = new JsonArray();
            List<JsonObject> plans = new ArrayList<>();
            String status = "complete";
            for (int r = 0; r < members.size(); r++) {
                PacketAdapter.Entry entry = members.get(r);
                JsonObject reg = new JsonObject();
                reg.addProperty("reg", r); reg.addProperty("networkId", entry.id()); reg.addProperty("context", entry.context());
                reg.addProperty("class", entry.packetClass() == null ? "unresolved" : entry.packetClass().getName());
                reg.addProperty("cap", options.capFor(entry));
                registrations.add(reg);
                RegistrationRun run = new RegistrationRun(file, entry, r);
                run.execute();
                run.coverage.addProperty("status", run.status());
                plans.add(run.plan);
                writer.json(PacketStream.COVERAGE, run.coverage);
                JsonObject regRow = run.summary();
                rows.add(regRow);
                status = worse(status, regRow.get("status").getAsString());
            }
            JsonObject header = new JsonObject();
            header.addProperty("format", "minecraft-packet-stream"); header.addProperty("formatVersion", PacketStream.FORMAT_VERSION);
            header.addProperty("edition", "java"); header.addProperty("version", adapter.version());
            header.addProperty("state", first.state()); header.addProperty("direction", first.direction()); header.addProperty("packet", first.name());
            JsonObject generator = new JsonObject(); generator.addProperty("fingerprint", fingerprint); header.add("generator", generator);
            header.add("sampler", sampler);
            header.add("registrations", registrations);
            JsonArray stages = new JsonArray(); STAGES.forEach(stages::add); header.add("stages", stages);
            JsonArray sources = new JsonArray(); file.sources.forEach(sources::add); header.add("sources", sources);
            header.addProperty("samples", writer.samples()); header.addProperty("status", status);
            writer.finish(header, plans);
            row.add("registrations", rows); row.addProperty("samples", writer.samples()); row.addProperty("frameBytes", writer.frameBytes());
            row.addProperty("bytes", Files.size(target)); row.addProperty("elapsedMs", (System.nanoTime() - started) / 1_000_000);
            System.out.println("[packetgen] " + first.file() + ": " + writer.samples() + " samples, " + Files.size(target) + " bytes, " + status
                    + " (" + (System.nanoTime() - started) / 1_000_000 + " ms)");
            return status;
        }
    }

    private static String worse(String a, String b) {
        List<String> order = List.of("complete", "incomplete", "failed");
        return order.indexOf(b) > order.indexOf(a) ? b : a;
    }

    /** State shared by all registrations written into one file. */
    private static final class FileRun {
        final PacketAdapter adapter; final PacketStream.Writer writer; final Options options;
        final Map<Key, Long> samples = new HashMap<>();
        final List<String> sources = new ArrayList<>(List.of("seed", "reflective"));
        FileRun(PacketAdapter adapter, PacketStream.Writer writer, Options options) { this.adapter = adapter; this.writer = writer; this.options = options; }
        int source(String name) { int i = sources.indexOf(name); if (i >= 0) return i; sources.add(name); return sources.size() - 1; }
    }

    private record Key(long a, long b) {}

    /** The outcome of validating one candidate. */
    private record Validated(Sampler.Outcome outcome, String reason, String detail, byte[] body, byte[] canonical,
                             PacketAdapter.Traced traced, PacketAdapter.Decoded decoded) {}

    private static final class RegistrationRun {
        final FileRun file; final PacketAdapter adapter; final PacketAdapter.Entry entry; final int reg; final Options options;
        final Set<Key> seen = new HashSet<>();
        final Map<String, long[]> rejections = new TreeMap<>();
        final Map<String, String> rejectionExamples = new TreeMap<>();
        final List<String> lossy = new ArrayList<>();
        JsonObject plan = new JsonObject(), coverage = new JsonObject();
        long accepted, aliases, writeIncomplete, readIncomplete, misaligned, lossyCount, frameBytes, attemptsChecked, seeds, external;
        String error;
        Sampler sampler;

        RegistrationRun(FileRun file, PacketAdapter.Entry entry, int reg) {
            this.file = file; this.adapter = file.adapter; this.entry = entry; this.reg = reg; this.options = file.options;
        }

        void execute() {
            long started = System.nanoTime();
            plan.addProperty("reg", reg);
            coverage.addProperty("reg", reg);
            PacketAdapter.Plan shapes;
            try { shapes = adapter.shapes(entry); }
            catch (Exception e) {
                error = "plan: " + Reflect.error(e);
                plan.addProperty("error", error); coverage.addProperty("stop", "failed"); coverage.addProperty("error", error);
                return;
            }
            JsonArray unresolved = new JsonArray(), terminal = new JsonArray();
            shapes.unresolved().forEach(unresolved::add); shapes.terminal().forEach(terminal::add);
            plan.add("unresolved", unresolved); plan.add("terminal", terminal);
            // 1. seeds, in full
            if (shapes.seeds() != null) {
                Sampler seedSampler = new Sampler(shapes.seeds(), Sampler.Settings.exhaustive(Sampler.Stage.SEED, options.seed()));
                drive(seedSampler, file.source("seed"));
                seeds = seedSampler.accepted();
                plan.add("seedNodes", seedSampler.nodes.toJson());
                coverage.add("seeds", seedSampler.coverage());
            }
            // 2. external candidates
            try { external(); } catch (Exception e) { record("external", Reflect.error(e), null); }
            // 3. reflective sampling
            BigInteger domain = shapes.exploration().size();
            long cap = options.capFor(entry);
            boolean exhaustive = domain.compareTo(BigInteger.valueOf(Math.max(options.threshold(), cap))) <= 0;
            long seed = options.seed() ^ entry.key().hashCode() * 0x9E3779B97F4A7C15L;
            sampler = new Sampler(shapes.exploration(), exhaustive ? Sampler.Settings.exhaustive(Sampler.Stage.EXHAUSTIVE, seed) : Sampler.Settings.staged(cap, options.coverageOnly(), seed));
            plan.addProperty("domain", domain.toString());
            plan.addProperty("mode", exhaustive ? "exhaustive" : "staged");
            plan.addProperty("cap", cap);
            drive(sampler, file.source("reflective"));
            plan.add("nodes", sampler.nodes.toJson());
            JsonObject reflective = sampler.coverage();
            reflective.entrySet().forEach(e -> coverage.add(e.getKey(), e.getValue()));
            coverage.addProperty("seedSamples", seeds); coverage.addProperty("externalSamples", external);
            coverage.addProperty("accepted", accepted); coverage.addProperty("aliases", aliases);
            coverage.addProperty("frameBytes", frameBytes);
            coverage.addProperty("writeTraceIncomplete", writeIncomplete); coverage.addProperty("readTraceIncomplete", readIncomplete);
            coverage.addProperty("misaligned", misaligned);
            JsonObject lossyJson = new JsonObject(); lossyJson.addProperty("samples", lossyCount);
            JsonArray lossyChoices = new JsonArray(); lossy.stream().limit(64).forEach(lossyChoices::add); lossyJson.add("choices", lossyChoices);
            coverage.add("lossy", lossyJson);
            JsonObject rejected = new JsonObject();
            for (Map.Entry<String, long[]> e : rejections.entrySet()) {
                JsonObject r = new JsonObject(); r.addProperty("count", e.getValue()[0]);
                String example = rejectionExamples.get(e.getKey()); if (example != null) r.addProperty("example", example);
                rejected.add(e.getKey(), r);
            }
            coverage.add("rejections", rejected);
            JsonObject gaps = new JsonObject(); gaps.addProperty("unresolved", unresolved.size()); gaps.addProperty("terminal", terminal.size());
            coverage.add("gaps", gaps);
            coverage.addProperty("elapsedMs", (System.nanoTime() - started) / 1_000_000);
        }

        String status() {
            if (error != null || writeIncomplete > 0) return "failed";
            String stop = sampler == null ? "failed" : sampler.stop();
            if (accepted == 0 && !"encoder-skipped".equals(stop)) return "failed";
            if (plan.has("unresolved") && plan.getAsJsonArray("unresolved").size() > 0) return "incomplete";
            JsonObject choices = coverage.has("choices") ? coverage.getAsJsonObject("choices") : null;
            JsonObject pairs = coverage.has("pairs") ? coverage.getAsJsonObject("pairs") : null;
            if (choices != null && choices.get("uncovered").getAsLong() > 0) return "incomplete";
            if (Set.of("exhaustive", "budget", "pairwise-covered", "saturated", "encoder-skipped").contains(stop)) return "complete";
            // An attempt, time or byte ceiling only counts once every choice and tracked pair was covered.
            boolean covered = choices != null && (pairs == null || pairs.get("uncovered").getAsLong() == 0) && "staged".equals(plan.has("mode") ? plan.get("mode").getAsString() : "");
            return Set.of("attempts", "time", "bytes").contains(stop) && covered ? "complete" : "incomplete";
        }

        JsonObject summary() {
            JsonObject row = new JsonObject();
            row.addProperty("key", entry.key()); row.addProperty("path", entry.path()); row.addProperty("id", entry.id()); row.addProperty("context", entry.context());
            row.addProperty("status", status());
            if (error != null) row.addProperty("error", error);
            for (String k : List.of("mode", "domain", "cap")) if (plan.has(k)) row.add(k, plan.get(k));
            for (String k : List.of("stop", "attempts", "accepted", "seedSamples", "externalSamples", "duplicates", "rejected", "encoderSkipped", "choices", "pairs", "gaps", "elapsedMs"))
                if (coverage.has(k)) row.add(k, coverage.get(k));
            return row;
        }

        // ---- producers -------------------------------------------------------------------------

        private void drive(Sampler s, int source) {
            long lastLog = System.nanoTime(), deadline = lastLog + options.maxSeconds() * 1_000_000_000L;
            Sampler.Attempt attempt;
            while ((attempt = s.next()) != null) {
                Sampler.Outcome outcome;
                if (attempt.error != null) { outcome = Sampler.Outcome.REJECTED; record("construct", Reflect.error(attempt.error), null); }
                else outcome = accept(attempt.value, attempt, source);
                s.feedback(attempt, outcome);
                if (frameBytes >= options.maxFrameBytes()) s.stop("bytes");
                // Some constructors allocate by an int argument (hundreds of MB per attempt); bound the time too.
                if (System.nanoTime() > deadline) s.stop("time");
                if (System.nanoTime() - lastLog > 15_000_000_000L) {
                    lastLog = System.nanoTime();
                    System.out.println("[packetgen] " + entry.path() + ": " + s.coverage().get("stages") + " accepted " + accepted);
                }
            }
        }

        /** Validates an object candidate and writes it when new. */
        private Sampler.Outcome accept(Object value, Sampler.Attempt attempt, int source) {
            Validated v = validateObject(value);
            if (v.outcome() != Sampler.Outcome.ACCEPTED) {
                if (v.outcome() == Sampler.Outcome.SKIPPED) record("encoder-skipped", v.detail(), null);
                else record(v.reason(), v.detail(), v.body());
                return v.outcome();
            }
            String dump = null; boolean lossyValue = false;
            boolean checkLoss = attempt.stage == Sampler.Stage.SEED || attempt.stage == Sampler.Stage.EACH_CHOICE
                    || attempt.stage == Sampler.Stage.PAIRWISE && attemptsChecked % 16 == 0 || attempt.stage == Sampler.Stage.RANDOM && attemptsChecked % 64 == 0
                    || attempt.stage == Sampler.Stage.EXHAUSTIVE && attemptsChecked % 64 == 0;
            attemptsChecked++;
            if (checkLoss) {
                JsonElement decoded = adapter.dump(v.decoded().value());
                lossyValue = !decoded.equals(adapter.dump(value));
                if (lossyValue || attempt.stage == Sampler.Stage.SEED) dump = decoded.toString();
                if (lossyValue) {
                    lossyCount++;
                    if (attempt.stage == Sampler.Stage.EACH_CHOICE && attempt.target >= 0) {
                        int node = (int) (attempt.target >>> 32), choice = (int) attempt.target;
                        lossy.add(sampler.nodes.path(node) + "=" + choice);
                    }
                }
            }
            int flags = (lossyValue ? PacketStream.F_LOSSY : 0);
            return write(v, source, attempt.stage, flags, attempt.decisions(), dump, -1) ? Sampler.Outcome.ACCEPTED : Sampler.Outcome.DUPLICATE;
        }

        private Validated validateObject(Object value) {
            byte[] body;
            try { body = adapter.encode(entry, value); }
            catch (PacketAdapter.EncoderSkipped e) { return new Validated(Sampler.Outcome.SKIPPED, "encoder-skipped", Reflect.error(e.getCause()), null, null, null, null); }
            catch (Exception | StackOverflowError e) { return new Validated(Sampler.Outcome.REJECTED, "encode", Reflect.error(e), null, null, null, null); }
            return validateBody(body);
        }

        /** Decode, require full consumption, re-encode traced. A different re-encoding is reported, not accepted. */
        private Validated validateBody(byte[] body) {
            PacketAdapter.Decoded decoded;
            try { decoded = adapter.decode(entry, body); }
            catch (Exception | StackOverflowError e) { return new Validated(Sampler.Outcome.REJECTED, "decode", Reflect.error(e), body, null, null, null); }
            if (!decoded.consumed()) return new Validated(Sampler.Outcome.REJECTED, "trailing-bytes", "decoder left unread bytes", body, null, null, decoded);
            PacketAdapter.Traced traced;
            try { traced = adapter.encodeTraced(entry, decoded.value()); }
            catch (Exception | StackOverflowError e) { return new Validated(Sampler.Outcome.REJECTED, "reencode", Reflect.error(e), body, null, null, decoded); }
            if (!Arrays.equals(body, traced.body())) return new Validated(Sampler.Outcome.REJECTED, "non-canonical", "re-encoding differs from input", body, traced.body(), traced, decoded);
            return new Validated(Sampler.Outcome.ACCEPTED, null, null, body, body, traced, decoded);
        }

        /** Writes a validated sample unless it duplicates one in this registration; returns true if new here. */
        private boolean write(Validated v, int source, Sampler.Stage stage, int flags, int[] decisions, String dump, long reference) {
            try {
                PacketAdapter.Traced traced = v.traced();
                TraceAlign.Result align = TraceAlign.align(traced.writes(), v.decoded().reads(), v.body().length, v.decoded().rewound());
                String[] writeKinds = traced.writes().stream().map(TraceOp::kind).toArray(String[]::new);
                int shape = file.writer.shape(writeKinds, align.readKinds());
                int[] partition = new int[0];
                if (align.misaligned()) {
                    List<TraceOp> reads = v.decoded().reads().stream().filter(op -> op.len() > 0).toList();
                    partition = new int[reads.size() * 2];
                    for (int i = 0; i < reads.size(); i++) { partition[2 * i] = file.writer.kindCode(reads.get(i).kind()); partition[2 * i + 1] = reads.get(i).len(); }
                }
                byte[] frame = frame(entry.id(), v.body());
                Key key = key(frame, shape, partition);
                if (!seen.add(key)) return false;
                Long existing = file.samples.get(key);
                if (existing != null) {
                    // Identical evidence from another protocol context: reference it instead of storing it again.
                    file.writer.sample(new PacketStream.Sample(new int[]{reg}, source, stage.ordinal(), PacketStream.F_ALIAS, 0, null, null, null, null, null, null, null, existing));
                    aliases++; accepted++;
                    return true;
                }
                List<Integer> sizes = new ArrayList<>(), positions = new ArrayList<>(), unsigned = new ArrayList<>();
                for (int i = 0; i < traced.writes().size(); i++) {
                    TraceOp op = traced.writes().get(i);
                    if (PacketStream.variable(op.kind())) sizes.add(op.len());
                    if (op.kind().equals("blockpos")) for (int c : op.value() instanceof int[] xyz ? xyz : new int[3]) positions.add(c);
                    if (unsignedArgument(op)) unsigned.add(i);
                }
                if (!unsigned.isEmpty()) flags |= PacketStream.F_UNSIGNED_ARGUMENTS;
                if (align.misaligned()) { flags |= PacketStream.F_MISALIGNED; misaligned++; }
                if (!traced.complete()) { flags |= PacketStream.F_WRITE_INCOMPLETE; writeIncomplete++; }
                if (!align.readComplete()) { flags |= PacketStream.F_READ_INCOMPLETE; readIncomplete++; }
                if (v.decoded().restored() != null) flags |= PacketStream.F_RESTORED;
                if (decisions != null && decisions.length > 0) flags |= PacketStream.F_DECISIONS;
                if (dump != null) flags |= PacketStream.F_DUMP;
                long index = file.writer.sample(new PacketStream.Sample(new int[]{reg}, source, stage.ordinal(), flags, shape, frame,
                        sizes.stream().mapToInt(Integer::intValue).toArray(), positions.stream().mapToInt(Integer::intValue).toArray(),
                        unsigned.stream().mapToInt(Integer::intValue).toArray(), decisions, dump, partition, reference));
                file.samples.put(key, index);
                accepted++; frameBytes += frame.length;
                return true;
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }

        private void record(String reason, String detail, byte[] body) {
            String category = reason.equals("construct") || reason.equals("encode") || reason.equals("decode") || reason.equals("reencode")
                    ? reason + ": " + (detail == null ? "?" : detail.split(":", 2)[0]) : reason;
            rejections.computeIfAbsent(category, k -> new long[1])[0]++;
            if (!rejectionExamples.containsKey(category)) {
                String text = detail == null ? "" : detail.length() > 300 ? detail.substring(0, 300) + "..." : detail;
                if (body != null) text += " | hex " + HexFormat.of().formatHex(body, 0, Math.min(body.length, 256)) + (body.length > 256 ? "..." : "");
                rejectionExamples.put(category, text);
            }
        }

        // ---- external candidates ---------------------------------------------------------------

        /**
         * Frames produced outside the game (for example from a protocol schema). Each is decoded and
         * re-encoded by vanilla. A frame that re-encodes differently is stored as its canonical
         * re-encoding plus a decode-only sample of the original; every outcome is reported back.
         */
        private void external() throws Exception {
            if (options.candidates() == null || !Files.isDirectory(options.candidates())) return;
            List<Path> producers;
            try (var list = Files.list(options.candidates())) { producers = list.filter(Files::isDirectory).sorted().toList(); }
            for (Path producer : producers) {
                Path frames = producer.resolve(adapter.version()).resolve(entry.state()).resolve(entry.direction()).resolve(entry.id() + ".frames");
                if (!Files.isRegularFile(frames)) continue;
                String name = "external:" + producer.getFileName();
                int source = file.source(name);
                Path results = options.work().resolve("external-results").resolve(producer.getFileName().toString()).resolve(adapter.version())
                        .resolve(entry.state()).resolve(entry.direction()).resolve(entry.id() + (entry.context().isEmpty() ? "" : "-" + entry.context()) + ".results.ndjson");
                Files.createDirectories(results.getParent());
                byte[] data = Files.readAllBytes(frames);
                try (BufferedWriter out = Files.newBufferedWriter(results, StandardCharsets.UTF_8)) {
                    int offset = 0, index = 0;
                    while (offset < data.length) {
                        int[] length = readVarint(data, offset);
                        int start = length[1], end = start + length[0];
                        JsonObject result = new JsonObject(); result.addProperty("index", index++); result.addProperty("offset", offset);
                        if (length[0] < 1 || end > data.length) { result.addProperty("outcome", "rejected"); result.addProperty("reason", "truncated-frame"); out.write(result + "\n"); break; }
                        byte[] frame = Arrays.copyOfRange(data, start, end);
                        offset = end;
                        int[] id = readVarint(frame, 0);
                        if (id[0] != entry.id()) { result.addProperty("outcome", "rejected"); result.addProperty("reason", "wrong-id"); out.write(result + "\n"); continue; }
                        byte[] body = Arrays.copyOfRange(frame, id[1], frame.length);
                        Validated v = validateBody(body);
                        if (v.outcome() == Sampler.Outcome.ACCEPTED) {
                            boolean added = write(v, source, Sampler.Stage.EXTERNAL, 0, null, adapter.dump(v.decoded().value()).toString(), -1);
                            if (added) external++;
                            result.addProperty("outcome", added ? "accepted" : "duplicate");
                        } else if (v.reason().equals("non-canonical")) {
                            Validated canonical = validateBody(v.canonical());
                            if (canonical.outcome() != Sampler.Outcome.ACCEPTED) {
                                result.addProperty("outcome", "rejected"); result.addProperty("reason", "unstable-canonical");
                            } else {
                                write(canonical, source, Sampler.Stage.EXTERNAL, PacketStream.F_CANONICALIZED, null, adapter.dump(canonical.decoded().value()).toString(), -1);
                                Long reference = file.samples.get(key(frame(entry.id(), canonical.body()), lastShape(canonical), new int[0]));
                                decodeOnly(v, source, reference == null ? -1 : reference);
                                result.addProperty("outcome", "non-canonical");
                                result.addProperty("canonicalHex", HexFormat.of().formatHex(frame(entry.id(), v.canonical())));
                                result.addProperty("firstDifference", Arrays.mismatch(body, v.canonical()));
                            }
                        } else {
                            result.addProperty("outcome", "rejected"); result.addProperty("reason", v.reason());
                            if (v.detail() != null) result.addProperty("detail", v.detail());
                        }
                        out.write(result + "\n");
                    }
                }
            }
        }

        private int lastShape(Validated v) throws IOException {
            TraceAlign.Result align = TraceAlign.align(v.traced().writes(), v.decoded().reads(), v.body().length, v.decoded().rewound());
            return file.writer.shape(v.traced().writes().stream().map(TraceOp::kind).toArray(String[]::new), align.readKinds());
        }

        /** The original bytes of a non-canonical external frame, described by its reads only. */
        private void decodeOnly(Validated v, int source, long canonical) throws IOException {
            List<TraceOp> reads = v.decoded().reads().stream().filter(op -> op.len() > 0).toList();
            String[] kinds = reads.stream().map(TraceOp::kind).toArray(String[]::new);
            int shape = file.writer.shape(kinds, kinds);
            int[] sizes = reads.stream().filter(op -> PacketStream.variable(op.kind())).mapToInt(TraceOp::len).toArray();
            int flags = PacketStream.F_DECODE_ONLY | PacketStream.F_DUMP;
            file.writer.sample(new PacketStream.Sample(new int[]{reg}, source, Sampler.Stage.EXTERNAL.ordinal(), flags, shape, frame(entry.id(), v.body()),
                    sizes, new int[0], new int[0], null, adapter.dump(v.decoded().value()).toString(), null, canonical));
        }
    }

    /** A narrow write whose argument was above its signed range, e.g. {@code writeByte(255)}. */
    static boolean unsignedArgument(TraceOp op) {
        int bits = switch (op.kind()) { case "i8" -> 8; case "i16", "li16" -> 16; case "i24", "li24" -> 24; default -> 0; };
        return bits > 0 && op.value() instanceof Number n && n.longValue() >= 1L << (bits - 1);
    }

    private static String candidateDigest(List<PacketAdapter.Entry> members, Options options) throws Exception {
        if (options.candidates() == null || !Files.isDirectory(options.candidates())) return "";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var walk = Files.walk(options.candidates())) {
            Set<String> wanted = new HashSet<>();
            for (PacketAdapter.Entry e : members) wanted.add(e.state() + "/" + e.direction() + "/" + e.id() + ".frames");
            for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                String rel = options.candidates().relativize(p).toString().replace('\\', '/');
                if (wanted.stream().noneMatch(rel::endsWith)) continue;
                digest.update(rel.getBytes(StandardCharsets.UTF_8)); digest.update(Files.readAllBytes(p));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    // ---- plan pass -----------------------------------------------------------------------------

    /** Domain sizes and a short calibration per registration, for the runner's budget prompt. */
    private static String plan(PacketAdapter adapter, List<PacketAdapter.Entry> members, Options options, JsonObject row) throws Exception {
        JsonArray rows = new JsonArray(); row.add("registrations", rows);
        for (PacketAdapter.Entry entry : members) {
            JsonObject r = new JsonObject(); rows.add(r);
            r.addProperty("key", entry.key()); r.addProperty("path", entry.path()); r.addProperty("id", entry.id()); r.addProperty("context", entry.context());
            try {
                PacketAdapter.Plan shapes = adapter.shapes(entry);
                BigInteger domain = shapes.exploration().size();
                r.addProperty("domain", domain.toString());
                r.addProperty("seeds", shapes.seeds() == null ? "0" : shapes.seeds().size().toString());
                r.addProperty("unresolved", shapes.unresolved().size()); r.addProperty("terminal", shapes.terminal().size());
                JsonArray unresolvedPaths = new JsonArray(); shapes.unresolved().stream().limit(40).forEach(unresolvedPaths::add);
                r.add("unresolvedPaths", unresolvedPaths);
                r.addProperty("aboveThreshold", domain.compareTo(BigInteger.valueOf(options.threshold())) > 0);
                // The first attempts load classes and warm the JIT; only the rest are timed.
                int warmup = 4;
                Sampler sampler = new Sampler(shapes.exploration(), Sampler.Settings.random(options.calibration() + warmup, options.seed()));
                long started = System.nanoTime(), bytes = 0, accepted = 0, attempts = 0;
                Sampler.Attempt a;
                while ((a = sampler.next()) != null) {
                    if (sampler.attempts() == warmup + 1) { started = System.nanoTime(); attempts = 0; accepted = 0; bytes = 0; }
                    attempts++;
                    Sampler.Outcome outcome = Sampler.Outcome.REJECTED;
                    if (a.error == null) {
                        try {
                            byte[] body = adapter.encode(entry, a.value);
                            PacketAdapter.Decoded d = adapter.decode(entry, body);
                            PacketAdapter.Traced t = adapter.encodeTraced(entry, d.value());
                            if (d.consumed() && Arrays.equals(body, t.body())) { outcome = Sampler.Outcome.ACCEPTED; accepted++; bytes += body.length + 2; }
                        } catch (PacketAdapter.EncoderSkipped e) { outcome = Sampler.Outcome.SKIPPED; }
                        catch (Exception | StackOverflowError ignored) {}
                    }
                    sampler.feedback(a, outcome);
                }
                JsonObject calibration = new JsonObject();
                calibration.addProperty("attempts", attempts); calibration.addProperty("accepted", accepted);
                calibration.addProperty("microsPerAttempt", attempts == 0 ? 0 : (System.nanoTime() - started) / 1000 / attempts);
                calibration.addProperty("meanFrameBytes", accepted == 0 ? 0 : bytes / accepted);
                calibration.addProperty("nodes", sampler.nodes.size());
                r.add("calibration", calibration);
            } catch (Exception e) { r.addProperty("error", Reflect.error(e)); }
        }
        return "complete";
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static Key key(byte[] frame, int shape, int[] partition) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(frame);
            digest.update(new byte[]{(byte) (shape >>> 24), (byte) (shape >>> 16), (byte) (shape >>> 8), (byte) shape});
            for (int p : partition) digest.update(new byte[]{(byte) (p >>> 24), (byte) (p >>> 16), (byte) (p >>> 8), (byte) p});
            byte[] h = digest.digest();
            return new Key(java.nio.ByteBuffer.wrap(h, 0, 8).getLong(), java.nio.ByteBuffer.wrap(h, 8, 8).getLong());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static byte[] frame(int id, byte[] body) { ByteArrayOutputStream out = new ByteArrayOutputStream(); varint(out, id); out.writeBytes(body); return out.toByteArray(); }
    private static void varint(ByteArrayOutputStream out, int value) { do { int b = value & 127; value >>>= 7; out.write(value == 0 ? b : b | 128); } while (value != 0); }
    /** {value, next offset}. */
    static int[] readVarint(byte[] data, int offset) {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            if (offset >= data.length) return new int[]{-1, offset};
            int b = data[offset++] & 0xff;
            value |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) return new int[]{value, offset};
        }
        return new int[]{-1, offset};
    }
    public static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    public static void atomicJson(Path file, JsonObject value) throws IOException {
        Files.createDirectories(file.getParent()); Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, value.toString().getBytes(StandardCharsets.UTF_8)); move(tmp, file);
    }
    static void move(Path tmp, Path file) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
                return;
            } catch (AccessDeniedException e) {
                // Windows readers may temporarily omit FILE_SHARE_DELETE. Preserve the committed
                // file and retry the rename, rather than deleting it or losing the previous version.
                if (attempt >= 29) throw e;
                try { Thread.sleep(Math.min(50, 10L * (attempt + 1))); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("interrupted committing " + file, interrupted); }
            }
        }
    }
}
