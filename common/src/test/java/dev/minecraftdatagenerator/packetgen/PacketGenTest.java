package dev.minecraftdatagenerator.packetgen;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.math.BigInteger;
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
    private static ReflectionShapes engine() { return new ReflectionShapes((t, p, d) -> null, 6); }
    public static void main(String[] args) throws Exception {
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
        ShapePlan nested = engine().plan(Nested.class);
        check(nested.size().equals(BigInteger.valueOf(22)), "both booleans and every optional enum alternative");
        Set<Nested> values = new HashSet<>(); for (int i = 0; i < 22; i++) values.add((Nested) nested.create(BigInteger.valueOf(i)));
        check(values.size() == 22 && values.contains(new Nested(true, Optional.of(Ten.J))), "nested tail retained");
        Type generic = Types.class.getRecordComponents()[0].getGenericType();
        check(engine().plan(generic).size().equals(BigInteger.TWO), "record type variable resolved");
        ShapePlan list = engine().plan(Types.class.getRecordComponents()[1].getGenericType());
        check(list.size().equals(BigInteger.valueOf(7)), "collection element products, including mixed pairs");
        Set<Object> listValues = new HashSet<>();
        for (int i = 0; i < 7; i++) listValues.add(list.create(BigInteger.valueOf(i)));
        check(listValues.contains(List.of(false, true)) && listValues.contains(List.of(true, false)), "mixed collection values");
        ShapePlan array = engine().plan(int[].class);
        check(ArrayCount(array) == 183, "primitive array lengths 0, 1, 2");
        check(((int[]) array.create(BigInteger.ONE)).length == 1, "nonempty primitive arrays");
        ShapePlan huge = ShapePlan.product("huge", Collections.nCopies(100, ShapePlan.values("bit", false, true)), a -> a);
        check(huge.size().equals(BigInteger.TWO.pow(100)), "combination count has no long overflow");
        Object[] varied = (Object[]) huge.create(BigInteger.ONE);
        check(Arrays.stream(varied).allMatch(Boolean.TRUE::equals), "bounded prefix varies all 100 fields");
        Object[] tail = (Object[]) huge.create(huge.size().subtract(BigInteger.ONE));
        check(tail.length == 100 && tail[99].equals(true) && tail[0].equals(false), "seek directly to huge tail");
        ShapePlan uneven = ShapePlan.product("uneven", List.of(ShapePlan.values("a", 0, 1, 2),
                ShapePlan.values("b", 0, 1, 2, 3, 4), ShapePlan.values("c", 0, 1, 2, 3, 4, 5, 6)), Arrays::asList);
        Set<Object> combinations = new HashSet<>();
        for (int i = 0; i < 105; i++) combinations.add(uneven.create(BigInteger.valueOf(i)));
        check(combinations.size() == 105, "spread traversal remains bijective for unequal dimensions");
        ShapePlan branches = ShapePlan.union("branches", List.of(huge, ShapePlan.values("short", "one", "two"), ShapePlan.values("empty")));
        check(branches.create(BigInteger.ONE).equals("one") && branches.create(BigInteger.valueOf(3)).equals("two"), "small alternatives cannot starve behind a huge branch");
        check(((Object[]) branches.create(branches.size().subtract(BigInteger.ONE))).length == 100, "interleaved union supports large direct seeks");
        check(ReflectionShapes.longValues().containsAll(List.of(Long.MIN_VALUE, Long.MAX_VALUE, -1L, 1L << 56)), "full VarLong domain");
        check(Arrays.equals(StaticFixtures.rsaPublicKey(2048).getEncoded(), StaticFixtures.rsaPublicKey(2048).getEncoded()), "public wire fixtures survive restart");
        ShapePlan payload = new ReflectionShapes((t, p, d) -> t == TestByteBuf.class ? ShapePlan.leaves("buffer", List.of(TestByteBuf::new)) : null, 6).plan(Payload.class);
        check(payload.size().equals(BigInteger.valueOf(8)) && payload.create(BigInteger.ZERO) instanceof Payload, "data constructors may accept a payload buffer alongside other fields");
        ShapePlan defaulted = engine().plan(PacketGenTest.class.getDeclaredField("defaulted").getGenericType());
        check(defaulted.size().equals(BigInteger.valueOf(7)) && ((List<?>) defaulted.create(BigInteger.valueOf(6))).size() == 2, "factory-only collection implementations retain nonempty products");
        ShapePlan seeded = ShapePlan.seeded("regressions", List.of(ShapePlan.values("seeds", 0, 1, 0, 1, 0)), ShapePlan.values("reflection", 1, 0, 1, 0));
        Path seedsOut = Files.createTempDirectory("packetgen-seeds-");
        JsonObject seedRun = PacketCorpus.run(fake("seed-v1", seeded), seedsOut, new PacketCorpus.Options(2, 2, false, null));
        JsonObject seedPacket = seedRun.getAsJsonArray("packets").get(0).getAsJsonObject();
        check(seedPacket.get("next").getAsInt() == 7 && seedPacket.get("seedAttemptsCompleted").getAsInt() == 5, "all regression seeds plus bounded reflection are visited");
        JsonObject seedResume = PacketCorpus.run(fake("seed-v1", seeded), seedsOut, new PacketCorpus.Options(2, 2, true, null));
        check(seedResume.getAsJsonArray("packets").get(0).getAsJsonObject().get("next").getAsInt() == 9, "resume only adds the next reflective budget");
        Path smokeOut = Files.createTempDirectory("packetgen-smoke-");
        JsonObject smoke = PacketCorpus.run(fake("seed-v1", seeded), smokeOut, new PacketCorpus.Options(2, 2, false, null, true));
        check(smoke.getAsJsonArray("packets").get(0).getAsJsonObject().get("next").getAsInt() == 2, "only explicit smoke mode truncates regression seeds");
        JsonObject promoted = PacketCorpus.run(fake("seed-v1", seeded), smokeOut, new PacketCorpus.Options(2, 2, true, null));
        check(promoted.getAsJsonArray("packets").get(0).getAsJsonObject().get("next").getAsInt() == 7, "normal resume finishes seeds after smoke prefix");
        Path out = Files.createTempDirectory("packetgen-test-");
        PacketAdapter fake = fake("fixture-v1");
        JsonObject first = PacketCorpus.run(fake, out, new PacketCorpus.Options(2, 1, false, null));
        check(first.get("status").getAsString().equals("incomplete"), "budget exhaustion is incomplete");
        JsonObject next = PacketCorpus.run(fake, out, new PacketCorpus.Options(2, 1, true, null));
        check(next.get("status").getAsString().equals("complete"), "resume visits remaining combinations");
        JsonObject p = next.getAsJsonArray("packets").get(0).getAsJsonObject();
        check(p.get("next").getAsString().equals("4") && p.get("samples").getAsInt() == 2 && p.get("duplicates").getAsInt() == 2, "dedup preserves all four attempts and distinct types");
        int provenance = 0;
        for (JsonElement c : p.getAsJsonArray("chunks")) {
            Path file = out.resolve("packet-corpus/status/toServer/example/" + c.getAsJsonObject().get("json").getAsString());
            provenance += JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("provenance").size();
        }
        check(provenance == 4, "duplicate provenance retained across chunks and resume");
        JsonObject changed = PacketCorpus.run(fake("fixture-v2"), out, new PacketCorpus.Options(1, 1, true, null));
        check(changed.get("status").getAsString().equals("failed"), "changed fingerprint is rejected");
        JsonObject same = PacketCorpus.run(fake, out, new PacketCorpus.Options(1, 1, true, null));
        check(same.get("status").getAsString().equals("complete"), "failed resume did not corrupt checkpoint");
        Path part = out.resolve("packet-corpus/status/toServer/example/part-000000.bin");
        byte[] original = Files.readAllBytes(part); Files.write(part, new byte[]{42});
        JsonObject damaged = PacketCorpus.run(fake, out, new PacketCorpus.Options(1, 1, true, null));
        check(damaged.get("status").getAsString().equals("failed"), "modified committed bytes are rejected");
        Files.write(part, original);
        Path checkpoint = part.resolveSibling("checkpoint.json"); String saved = Files.readString(checkpoint);
        JsonObject edited = JsonParser.parseString(saved).getAsJsonObject(); edited.addProperty("next", "3"); PacketCorpus.atomicJson(checkpoint, edited);
        check(PacketCorpus.run(fake, out, new PacketCorpus.Options(1, 1, true, null)).get("status").getAsString().equals("failed"), "checkpoint cannot skip or replay committed ordinals");
        Files.writeString(checkpoint, saved);
        Path emptyOut = Files.createTempDirectory("packetgen-empty-");
        JsonObject empty = PacketCorpus.run(new PacketAdapter() {
            public String version() { return "test"; }
            public String fingerprint() { return "empty"; }
            public List<Entry> inventory() { return List.of(new Entry("status", "toServer", 0, "empty", "", Integer.class, null)); }
            public Plan shapes(Entry e) { return new Plan(ShapePlan.values("unresolved"), List.of("missing fixture")); }
            public JsonElement dump(Object value) { throw new AssertionError(); }
            public Encoded encode(Entry e, Object value) { throw new AssertionError(); }
        }, emptyOut, new PacketCorpus.Options(1, 1, false, null));
        check(empty.get("status").getAsString().equals("failed") && empty.getAsJsonObject("summary").get("selectedWithoutSamples").getAsInt() == 1, "zero samples cannot pass as complete");
        Path qualityOut = Files.createTempDirectory("packetgen-quality-");
        PacketAdapter quality = fake("source-quality", ShapePlan.values("candidates", 0, -1, -2));
        PacketCorpus.run(quality, qualityOut, new PacketCorpus.Options(2, 2, false, null));
        JsonObject qualityRun = PacketCorpus.run(quality, qualityOut, new PacketCorpus.Options(2, 2, true, null));
        JsonObject qualityPacket = qualityRun.getAsJsonArray("packets").get(0).getAsJsonObject();
        check(qualityPacket.get("samples").getAsInt() == 1 && qualityPacket.get("rejected").getAsInt() == 2,
                "unstable and invalid vanilla samples are rejected, including after resume");
        int retained = 0;
        for (JsonElement c : qualityPacket.getAsJsonArray("chunks")) {
            Path file = qualityOut.resolve("packet-corpus/status/toServer/example/" + c.getAsJsonObject().get("json").getAsString());
            JsonObject records = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (JsonElement origin : records.getAsJsonArray("provenance")) {
                JsonObject row = origin.getAsJsonObject();
                if (row.has("rejectedSource")) {
                    check(row.has("rejectedHex") && row.get("outcome").getAsString().equals("rejected"), "rejected source retains bytes and evidence");
                    retained++;
                }
            }
        }
        check(retained == 2, "all source rejections remain reviewable");
        Path skippedOut = Files.createTempDirectory("packetgen-skipped-");
        PacketAdapter skipped = fake("skipped", ShapePlan.values("skips", -3, -3));
        JsonObject partialSkip = PacketCorpus.run(skipped, skippedOut, new PacketCorpus.Options(1, 1, false, null));
        check(partialSkip.get("status").getAsString().equals("incomplete"), "a skipped prefix does not establish all candidates are skipped");
        JsonObject skippedRun = PacketCorpus.run(skipped, skippedOut, new PacketCorpus.Options(1, 1, true, null));
        JsonObject skippedPacket = skippedRun.getAsJsonArray("packets").get(0).getAsJsonObject();
        check(skippedPacket.get("status").getAsString().equals("encoderSkipped") && skippedPacket.get("encoderSkipped").getAsInt() == 2,
                "explicit vanilla encoder skips are retained and counted across resume");
        JsonObject mixedSkip = PacketCorpus.run(fake("mixed", ShapePlan.values("mixed", -3, -1)), Files.createTempDirectory("packetgen-mixed-"), new PacketCorpus.Options(2, 2, false, null));
        check(mixedSkip.get("status").getAsString().equals("failed"), "a source failure cannot be classified as an encoder skip");
        System.out.println("Packet generator regression checks passed (domains, generic types, large products, resume, provenance, fingerprints, source validity).");
    }
    private static int ArrayCount(ShapePlan p) { return p.size().intValueExact(); }
    private static PacketAdapter fake(String fingerprint) {
        return fake(fingerprint, ShapePlan.values("values", 0, 0, 1, 1));
    }
    private static PacketAdapter fake(String fingerprint, ShapePlan plan) {
        return new PacketAdapter() {
            public String version() { return "test"; }
            public String fingerprint() { return fingerprint; }
            public List<Entry> inventory() { return List.of(new Entry("status", "toServer", 0, "example", "", Integer.class, null)); }
            public Plan shapes(Entry e) { return new Plan(plan, List.of()); }
            public JsonElement dump(Object value) { return new JsonPrimitive((Integer) value); }
            public Encoded encode(Entry e, Object value) throws Exception {
                if ((int) value == -3) throw new EncoderSkipped(new IllegalStateException("context disabled"));
                JsonObject metadata = new JsonObject(); JsonArray trace = new JsonArray(), op = new JsonArray();
                op.add(0); op.add(4); op.add((int) value == 0 ? "i32" : "f32"); op.add(0); trace.add(op);
                metadata.add("trace", trace); metadata.add("primitiveTrace", trace.deepCopy()); metadata.add("reads", new JsonArray());
                JsonObject coverage = new JsonObject(); coverage.addProperty("complete", true); metadata.add("traceCoverage", coverage);
                metadata.addProperty("fullyConsumed", (int) value != -2); metadata.addProperty("roundTrip", (int) value >= 0); metadata.addProperty("traceFormatVersion", 2);
                return new Encoded(new byte[4], metadata);
            }
        };
    }
}
