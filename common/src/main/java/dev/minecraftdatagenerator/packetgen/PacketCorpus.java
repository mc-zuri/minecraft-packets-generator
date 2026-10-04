package dev.minecraftdatagenerator.packetgen;

import com.google.gson.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Manifest-driven, append-only chunks. A checkpoint commits both bytes and provenance together. */
public final class PacketCorpus {
    public record Options(int budget, int chunkSize, boolean resume, String filter, boolean smoke) {
        public Options(int budget, int chunkSize, boolean resume, String filter) { this(budget, chunkSize, resume, filter, false); }
        public Options { if (budget < 1 || chunkSize < 1) throw new IllegalArgumentException("positive budgets required"); }
        public static Options environment() {
            return new Options(Integer.parseInt(System.getenv().getOrDefault("PACKETGEN_MAX_SHAPES", "256")),
                    Integer.parseInt(System.getenv().getOrDefault("PACKETGEN_CHUNK_SIZE", "256")),
                    "1".equals(System.getenv("PACKETGEN_RESUME")), System.getenv("PACKETGEN_FILTER"),
                    "1".equals(System.getenv("PACKETGEN_SMOKE")));
        }
    }
    public static JsonObject run(PacketAdapter adapter, Path output, Options options) throws Exception {
        Path root = output.resolve("packet-corpus");
        Files.createDirectories(root);
        List<PacketAdapter.Entry> entries = new ArrayList<>(adapter.inventory());
        entries.sort(Comparator.comparing(PacketAdapter.Entry::key));
        Set<String> keys = new HashSet<>();
        for (PacketAdapter.Entry e : entries) if (!keys.add(e.key())) throw new IllegalStateException("duplicate registration " + e.key());
        String fingerprint = adapter.fingerprint();
        JsonObject report = new JsonObject();
        report.addProperty("formatVersion", 3);
        report.addProperty("version", adapter.version());
        report.addProperty("fingerprint", fingerprint);
        report.addProperty("samplingProfile", options.smoke() ? "smoke" : "regression-and-reflection");
        report.addProperty("reflectionBudget", options.budget());
        report.add("adapter", adapter.metadata());
        report.addProperty("root", "packet-corpus");
        JsonArray packets = new JsonArray(); report.add("packets", packets);
        int completed = 0, failed = 0, pending = 0, filtered = 0, skipped = 0;
        for (PacketAdapter.Entry entry : entries) {
            JsonObject result = identity(entry);
            result.addProperty("class", entry.packetClass() == null ? "unresolved" : entry.packetClass().getName());
            packets.add(result);
            if (options.filter() != null && !entry.path().contains(options.filter())) { result.addProperty("status", "filtered"); filtered++; continue; }
            try {
                PacketAdapter.Plan plan = adapter.shapes(entry);
                JsonArray gaps = new JsonArray(); plan.gaps().forEach(gaps::add); result.add("gaps", gaps);
                JsonObject progress = generate(adapter, entry, plan.shapes(), root, fingerprint, options);
                progress.entrySet().forEach(e -> result.add(e.getKey(), e.getValue()));
                result.addProperty("domainCoverage", gaps.isEmpty() ? "declared" : "has-unresolved-domains");
                String status = result.get("status").getAsString();
                if (status.equals("complete")) completed++;
                else if (status.equals("encoderSkipped")) skipped++;
                else if (status.equals("incomplete")) pending++;
                else failed++;
            } catch (Exception e) { result.addProperty("status", "failed"); result.addProperty("error", Reflect.error(e)); failed++; }
            report.addProperty("status", "running");
            atomicJson(output.resolve("packetCorpus.json"), report);
        }
        JsonObject summary = new JsonObject();
        summary.addProperty("registrations", entries.size()); summary.addProperty("complete", completed);
        summary.addProperty("failed", failed); summary.addProperty("incomplete", pending); summary.addProperty("filtered", filtered);
        summary.addProperty("encoderSkipped", skipped);
        summary.addProperty("packetsWithDomainGaps", java.util.stream.StreamSupport.stream(packets.spliterator(), false).map(JsonElement::getAsJsonObject).filter(p -> p.has("gaps") && !p.getAsJsonArray("gaps").isEmpty()).count());
        long sampled = java.util.stream.StreamSupport.stream(packets.spliterator(), false).map(JsonElement::getAsJsonObject).filter(p -> p.has("samples") && p.get("samples").getAsLong() > 0).count();
        summary.addProperty("registrationsWithSamples", sampled);
        summary.addProperty("selectedWithoutSamples", entries.size() - filtered - sampled);
        Set<String> wireIds = new HashSet<>(), sampledWireIds = new HashSet<>();
        for (JsonElement element : packets) {
            JsonObject p = element.getAsJsonObject(); String id = p.get("state").getAsString() + "/" + p.get("direction").getAsString() + "/" + p.get("id").getAsString();
            wireIds.add(id);
            if (p.has("samples") && p.get("samples").getAsLong() > 0) sampledWireIds.add(id);
        }
        summary.addProperty("wireRegistrations", wireIds.size()); summary.addProperty("wireRegistrationsWithSamples", sampledWireIds.size());
        report.add("summary", summary);
        report.addProperty("status", failed > 0 ? "failed" : pending > 0 || filtered > 0 ? "incomplete" : "complete");
        atomicJson(output.resolve("packetCorpus.json"), report);
        return report;
    }

    private static JsonObject generate(PacketAdapter adapter, PacketAdapter.Entry entry, ShapePlan plan, Path root, String fingerprint, Options options) throws Exception {
        Path dir = root.resolve(entry.path()).normalize();
        if (!dir.startsWith(root.normalize())) throw new IllegalArgumentException("packet path escaped corpus");
        Files.createDirectories(dir);
        Path checkpoint = dir.resolve("checkpoint.json");
        String signature = hash((fingerprint + "\n" + entry.key() + "\n" + plan.description()).getBytes(StandardCharsets.UTF_8));
        JsonObject state = new JsonObject(); JsonArray chunks = new JsonArray();
        BigInteger next = BigInteger.ZERO;
        long samples = 0, rejected = 0, duplicates = 0, incompleteTraces = 0, sourceFailures = 0, encoderSkipped = 0;
        Map<String, Long> seen = new HashMap<>();
        if (Files.exists(checkpoint)) {
            if (!options.resume()) throw new IllegalStateException("corpus exists; use PACKETGEN_RESUME=1 or a new output directory");
            state = JsonParser.parseString(Files.readString(checkpoint)).getAsJsonObject();
            if (!signature.equals(state.get("signature").getAsString())) throw new IllegalStateException("checkpoint fingerprint changed");
            next = new BigInteger(state.get("next").getAsString()); chunks = state.getAsJsonArray("chunks");
            samples = state.get("samples").getAsLong(); rejected = state.get("rejected").getAsLong(); duplicates = state.get("duplicates").getAsLong();
            incompleteTraces = state.get("incompleteTraces").getAsLong(); sourceFailures = state.get("sourceFailures").getAsLong();
            if (next.signum() < 0 || next.compareTo(plan.size()) > 0 || !plan.size().toString().equals(state.get("total").getAsString())) throw new IOException("checkpoint domain bounds differ");
            BigInteger committedNext = BigInteger.ZERO; long committedSamples = 0, committedRejected = 0, committedDuplicates = 0;
            for (JsonElement c : chunks) {
                JsonObject chunk = c.getAsJsonObject(); Path json = dir.resolve(chunk.get("json").getAsString()); Path bin = dir.resolve(chunk.get("bin").getAsString());
                if (!hash(Files.readAllBytes(json)).equals(chunk.get("jsonSha256").getAsString()) || !hash(Files.readAllBytes(bin)).equals(chunk.get("binSha256").getAsString())) throw new IOException("committed chunk changed: " + json);
                JsonObject records = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
                for (JsonElement sample : records.getAsJsonArray("shapes")) {
                    JsonObject s = sample.getAsJsonObject();
                    if (s.get("sampleId").getAsLong() != committedSamples++) throw new IOException("committed sample sequence differs");
                    seen.put(s.get("dedupKey").getAsString(), s.get("sampleId").getAsLong());
                }
                for (JsonElement origin : records.getAsJsonArray("provenance")) {
                    JsonObject p = origin.getAsJsonObject();
                    if (!p.get("ordinal").getAsString().equals(committedNext.toString())) throw new IOException("committed ordinal sequence differs");
                    committedNext = committedNext.add(BigInteger.ONE);
                    switch (p.get("outcome").getAsString()) {
                        case "rejected" -> { committedRejected++; if (p.has("encoderSkipped") && p.get("encoderSkipped").getAsBoolean()) encoderSkipped++; }
                        case "duplicate" -> committedDuplicates++;
                        case "encoded" -> { }
                        default -> throw new IOException("unknown committed outcome");
                    }
                }
            }
            if (!next.equals(committedNext) || samples != committedSamples || rejected != committedRejected || duplicates != committedDuplicates) throw new IOException("checkpoint progress differs from committed provenance");
        }
        long encodeNanos = 0, dumpNanos = 0, constructNanos = 0;
        BigInteger limit = (options.smoke() ? next : next.max(plan.requiredPrefix()))
                .add(BigInteger.valueOf(options.budget())).min(plan.size());
        while (next.compareTo(limit) < 0) {
            JsonObject chunk = identity(entry); JsonArray shapes = new JsonArray(), provenance = new JsonArray();
            ByteArrayOutputStream binary = new ByteArrayOutputStream();
            int count = limit.subtract(next).min(BigInteger.valueOf(options.chunkSize())).intValueExact();
            for (int n = 0; n < count; n++, next = next.add(BigInteger.ONE)) {
                JsonObject origin = new JsonObject(); origin.addProperty("ordinal", next.toString()); provenance.add(origin);
                try {
                    long t = System.nanoTime(); Object value = plan.create(next); constructNanos += System.nanoTime() - t;
                    if (value == null) throw new IllegalArgumentException("factory returned null");
                    t = System.nanoTime(); JsonElement java = adapter.dump(value); dumpNanos += System.nanoTime() - t; origin.add("java", java);
                    t = System.nanoTime(); PacketAdapter.Encoded encoded = adapter.encode(entry, value); encodeNanos += System.nanoTime() - t;
                    JsonObject evidence = encoded.evidence().deepCopy();
                    byte[] frame = frame(entry.id(), encoded.body());
                    // A positive comparison corpus needs a vanilla-validated oracle.
                    // Keep invalid/noncanonical attempts and their wire evidence in private
                    // provenance instead of publishing them as serializer regressions.
                    if (!evidence.get("fullyConsumed").getAsBoolean() || !evidence.get("roundTrip").getAsBoolean()
                            || evidence.has("decodeError") || evidence.has("reencodeError")) {
                        origin.add("rejectedSource", evidence);
                        origin.addProperty("rejectedHex", HexFormat.of().formatHex(frame));
                        throw new IllegalArgumentException("vanilla source did not decode, consume and re-encode identically");
                    }
                    // Include read and source evidence. Identical bytes alone do not imply identical Java write types.
                    String key = hash(concat(frame, evidence.toString().getBytes(StandardCharsets.UTF_8)));
                    Long previous = seen.get(key);
                    if (previous != null) { origin.addProperty("sampleId", previous); origin.addProperty("outcome", "duplicate"); duplicates++; }
                    else {
                        long id = samples++; seen.put(key, id); origin.addProperty("sampleId", id); origin.addProperty("outcome", "encoded");
                        evidence.addProperty("sampleId", id); evidence.addProperty("dedupKey", key); evidence.addProperty("i", shapes.size());
                        evidence.addProperty("bytes", frame.length); evidence.addProperty("hex", HexFormat.of().formatHex(frame)); evidence.add("java", java);
                        shapes.add(evidence); varint(binary, frame.length); binary.writeBytes(frame);
                        if (!evidence.getAsJsonObject("traceCoverage").get("complete").getAsBoolean()) incompleteTraces++;
                        if (!evidence.get("fullyConsumed").getAsBoolean() || evidence.has("decodeError")) sourceFailures++;
                    }
                } catch (Exception e) {
                    origin.addProperty("outcome", "rejected"); origin.addProperty("error", Reflect.error(e)); rejected++;
                    if (e instanceof PacketAdapter.EncoderSkipped) { origin.addProperty("encoderSkipped", true); encoderSkipped++; }
                }
            }
            chunk.addProperty("status", shapes.isEmpty() ? "noNewSamples" : "ok"); chunk.addProperty("shapeCount", shapes.size());
            chunk.add("shapes", shapes); chunk.add("provenance", provenance);
            String stem = String.format(Locale.ROOT, "part-%06d", chunks.size());
            byte[] json = chunk.toString().getBytes(StandardCharsets.UTF_8), bin = binary.toByteArray();
            atomic(dir.resolve(stem + ".bin"), bin); atomic(dir.resolve(stem + ".json"), json);
            JsonObject part = new JsonObject(); part.addProperty("json", stem + ".json"); part.addProperty("bin", stem + ".bin");
            part.addProperty("jsonSha256", hash(json)); part.addProperty("binSha256", hash(bin)); part.addProperty("samples", shapes.size()); chunks.add(part);
            state.addProperty("signature", signature); state.addProperty("next", next.toString()); state.addProperty("total", plan.size().toString());
            state.addProperty("samples", samples); state.addProperty("rejected", rejected); state.addProperty("duplicates", duplicates);
            state.addProperty("incompleteTraces", incompleteTraces); state.addProperty("sourceFailures", sourceFailures); state.add("chunks", chunks);
            atomicJson(checkpoint, state);
        }
        JsonObject result = state.deepCopy();
        result.addProperty("total", plan.size().toString()); result.addProperty("next", next.toString()); result.addProperty("samples", samples);
        result.addProperty("seedAttemptsTotal", plan.requiredPrefix().toString());
        result.addProperty("seedAttemptsCompleted", next.min(plan.requiredPrefix()).toString());
        result.addProperty("remaining", plan.size().subtract(next).toString()); result.addProperty("path", entry.path());
        result.addProperty("encoderSkipped", encoderSkipped);
        boolean allSkipped = encoderSkipped > 0 && BigInteger.valueOf(encoderSkipped).equals(plan.size()) && next.equals(plan.size());
        result.addProperty("status", allSkipped ? "encoderSkipped" : samples == 0 && next.compareTo(plan.size()) >= 0 ? "noSamples" : incompleteTraces > 0 || sourceFailures > 0 ? "failed" : next.compareTo(plan.size()) < 0 ? "incomplete" : "complete");
        result.addProperty("constructionNanosThisRun", constructNanos); result.addProperty("encodingNanosThisRun", encodeNanos); result.addProperty("dumpNanosThisRun", dumpNanos);
        if (!result.has("chunks")) result.add("chunks", chunks);
        return result;
    }

    public static JsonObject identity(PacketAdapter.Entry e) {
        JsonObject out = new JsonObject(); out.addProperty("state", e.state()); out.addProperty("direction", e.direction());
        out.addProperty("packet", e.name()); out.addProperty("id", e.id()); out.addProperty("context", e.context()); return out;
    }
    public static byte[] frame(int id, byte[] body) { ByteArrayOutputStream out = new ByteArrayOutputStream(); varint(out, id); out.writeBytes(body); return out.toByteArray(); }
    private static void varint(ByteArrayOutputStream out, int value) { do { int b = value & 127; value >>>= 7; out.write(value == 0 ? b : b | 128); } while (value != 0); }
    private static byte[] concat(byte[] a, byte[] b) { byte[] out = Arrays.copyOf(a, a.length + b.length); System.arraycopy(b, 0, out, a.length, b.length); return out; }
    public static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    public static void atomicJson(Path file, JsonObject value) throws IOException { atomic(file, value.toString().getBytes(StandardCharsets.UTF_8)); }
    private static void atomic(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent()); Path tmp = file.resolveSibling(file.getFileName() + ".tmp"); Files.write(tmp, bytes);
        for (int attempt = 0; ; attempt++) {
            try {
                try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
                return;
            } catch (AccessDeniedException e) {
                // Windows readers may temporarily omit FILE_SHARE_DELETE. Preserve the committed
                // file and retry the rename, rather than deleting it or losing the checkpoint.
                if (attempt >= 29) throw e;
                try { Thread.sleep(Math.min(50, 10L * (attempt + 1))); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("interrupted committing " + file, interrupted); }
            }
        }
    }
}
