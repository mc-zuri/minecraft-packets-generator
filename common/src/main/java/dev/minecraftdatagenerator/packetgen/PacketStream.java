package dev.minecraftdatagenerator.packetgen;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * One packet's corpus: a zstd stream of length-prefixed binary records.
 * <pre>
 * "MCPK" u16be formatVersion
 * record := type:u8 length:uvarint payload[length]
 * 0x01 HEADER   JSON   identity, registrations, generator and sampler settings, kind table
 * 0x02 PLAN     JSON   per registration: domain, mode, gaps, decision-node table
 * 0x10 SHAPE    binary shapeId, flags, n, (writeKind:u8 readKind:u8)*n
 * 0x11 SAMPLE   binary registrations, source, stage, flags, shape, frame, variable sizes, block
 *                      positions, then optional unsigned arguments, decisions, dump, read partition
 *                      and canonical reference (see doc/packet-stream-format.md)
 * 0x03 COVERAGE JSON   per registration: stop reason, counts, choice/pair coverage, rejections
 * 0x04 TRAILER  JSON   totals and the SHA-256 of all frames in sample order
 * </pre>
 * HEADER and PLAN are only complete after generation, so the body records are spooled to an
 * uncompressed temporary file while sampling. The final file is one zstd frame (some readers,
 * Node's zlib among them, stop after the first frame) holding HEADER, PLAN and then the body.
 */
public final class PacketStream {
    private PacketStream() {}
    static final byte[] MAGIC = "MCPK".getBytes(StandardCharsets.US_ASCII);
    static final int FORMAT_VERSION = 1;
    static final int HEADER = 0x01, PLAN = 0x02, COVERAGE = 0x03, TRAILER = 0x04, SHAPE = 0x10, SAMPLE = 0x11;
    static final int F_DECISIONS = 1, F_DUMP = 2, F_LOSSY = 4, F_DECODE_ONLY = 8, F_CANONICALIZED = 16, F_ALIAS = 32,
            F_MISALIGNED = 64, F_RESTORED = 128, F_WRITE_INCOMPLETE = 256, F_READ_INCOMPLETE = 512, F_UNSIGNED_ARGUMENTS = 1024;
    /** Initial kind table; kinds first seen at runtime are appended and listed in HEADER. */
    static final List<String> KINDS = List.of("none", "mixed", "bool", "i8", "u8", "i16", "u16", "li16", "lu16", "char",
            "i24", "u24", "li24", "lu24", "i32", "u32", "li32", "lu32", "f32", "i64", "li64", "f64",
            "varint", "varlong", "string", "blockpos", "bytes", "skip");

    static boolean variable(String kind) { return !TraceAlign.FIXED_SIZE.containsKey(kind); }

    /** File name for a packet name, percent-encoded like Python's {@code quote(name, safe='-_.')}. */
    static String fileName(String packet) {
        if (packet.isEmpty() || packet.equals(".") || packet.equals("..")) throw new IllegalArgumentException("invalid packet name " + packet);
        StringBuilder out = new StringBuilder();
        for (byte b : packet.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || "-_.~".indexOf(c) >= 0) out.append(c);
            else out.append('%').append(String.format(Locale.ROOT, "%02X", b & 0xff));
        }
        return out + ".mcpk.zst";
    }

    /**
     * A sample to write; {@code sizes} has one length per variable-size write op of its shape.
     * {@code unsignedArguments} lists the i8/i16/i24 write ops whose vanilla argument was above the
     * signed range (a masked value such as 255): the bytes alone cannot tell -1 from 255.
     */
    record Sample(int[] registrations, int source, int stage, int flags, int shape, byte[] frame, int[] sizes,
                  int[] positions, int[] unsignedArguments, int[] decisions, String dump, int[] readPartition, long reference) {}

    static final class Writer implements Closeable {
        private final Path target, bodyFile;
        private final OutputStream body;
        private final int level;
        private final ByteArrayOutputStream record = new ByteArrayOutputStream();
        private final List<String> kinds = new ArrayList<>(KINDS);
        private final Map<String, Integer> kindIndex = new HashMap<>();
        private final Map<String, Integer> shapes = new HashMap<>();
        private final List<String[]> shapeKinds = new ArrayList<>();
        private final MessageDigest frames;
        private long samples, frameBytes;
        private boolean finished;

        Writer(Path target, Path scratch, int level) throws Exception {
            this.target = target; this.level = level;
            for (int i = 0; i < kinds.size(); i++) kindIndex.put(kinds.get(i), i);
            Files.createDirectories(scratch);
            bodyFile = Files.createTempFile(scratch, "body-", ".bin");
            body = new BufferedOutputStream(Files.newOutputStream(bodyFile), 1 << 16);
            frames = MessageDigest.getInstance("SHA-256");
        }

        long samples() { return samples; }
        long frameBytes() { return frameBytes; }
        List<String> kinds() { return kinds; }
        String[] shape(int id) { return shapeKinds.get(id); }

        private int kind(String kind) {
            Integer index = kindIndex.get(kind);
            if (index != null) return index;
            if (kinds.size() >= 255) throw new IllegalStateException("too many op kinds");
            kinds.add(kind); kindIndex.put(kind, kinds.size() - 1);
            return kinds.size() - 1;
        }

        /** Interns a wire shape: pairs of (write kind, read kind). New shapes are written before first use. */
        int shape(String[] writeKinds, String[] readKinds) throws IOException {
            StringBuilder key = new StringBuilder();
            for (int i = 0; i < writeKinds.length; i++) key.append(writeKinds[i]).append(':').append(readKinds[i]).append(',');
            Integer known = shapes.get(key.toString());
            if (known != null) return known;
            int id = shapes.size();
            shapes.put(key.toString(), id);
            shapeKinds.add(writeKinds.clone());
            record.reset();
            uv(record, id); record.write(0); uv(record, writeKinds.length);
            for (int i = 0; i < writeKinds.length; i++) { record.write(kind(writeKinds[i])); record.write(kind(readKinds[i])); }
            emit(SHAPE);
            return id;
        }

        long sample(Sample s) throws IOException {
            record.reset();
            uv(record, s.registrations().length);
            for (int r : s.registrations()) uv(record, r);
            uv(record, s.source()); record.write(s.stage()); uv(record, s.flags());
            if ((s.flags() & F_ALIAS) != 0) {
                uv(record, s.reference());
            } else {
                uv(record, s.shape());
                uv(record, s.frame().length); record.write(s.frame());
                for (int size : s.sizes()) uv(record, size);
                for (int p : s.positions()) sv(record, p);
                if ((s.flags() & F_UNSIGNED_ARGUMENTS) != 0) {
                    uv(record, s.unsignedArguments().length);
                    for (int op : s.unsignedArguments()) uv(record, op);
                }
                if ((s.flags() & F_DECISIONS) != 0) {
                    uv(record, s.decisions().length / 2);
                    for (int d : s.decisions()) uv(record, d);
                }
                if ((s.flags() & F_DUMP) != 0) { byte[] json = s.dump().getBytes(StandardCharsets.UTF_8); uv(record, json.length); record.write(json); }
                if ((s.flags() & F_MISALIGNED) != 0) {
                    uv(record, s.readPartition().length / 2);
                    for (int i = 0; i < s.readPartition().length; i += 2) { record.write(s.readPartition()[i]); uv(record, s.readPartition()[i + 1]); }
                }
                // index of the canonical sample plus one; 0 when it is unknown
                if ((s.flags() & F_DECODE_ONLY) != 0) uv(record, s.reference() + 1);
                frames.update(s.frame());
                frameBytes += s.frame().length;
            }
            emit(SAMPLE);
            return samples++;
        }

        int kindCode(String kind) { return kind(kind); }

        void json(int type, JsonObject value) throws IOException {
            record.reset(); record.write(value.toString().getBytes(StandardCharsets.UTF_8)); emit(type);
        }

        private void emit(int type) throws IOException {
            body.write(type); uv(body, record.size()); record.writeTo(body);
        }

        /** Writes the trailer, then assembles HEADER/PLAN and the body into {@code target} atomically. */
        void finish(JsonObject header, List<JsonObject> plans) throws Exception {
            JsonObject trailer = new JsonObject();
            trailer.addProperty("samples", samples); trailer.addProperty("shapes", shapes.size()); trailer.addProperty("frameBytes", frameBytes);
            trailer.addProperty("framesSha256", HexFormat.of().formatHex(frames.digest()));
            json(TRAILER, trailer);
            body.close();
            com.google.gson.JsonArray table = new com.google.gson.JsonArray(); kinds.forEach(table::add);
            header.add("kinds", table);
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            try (OutputStream file = zstd(new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16), level)) {
                file.write(MAGIC); file.write(FORMAT_VERSION >>> 8); file.write(FORMAT_VERSION);
                writeRecord(file, HEADER, header.toString().getBytes(StandardCharsets.UTF_8));
                for (JsonObject plan : plans) writeRecord(file, PLAN, plan.toString().getBytes(StandardCharsets.UTF_8));
                Files.copy(bodyFile, file);
            }
            PacketCorpus.move(tmp, target);
            Files.deleteIfExists(bodyFile);
            finished = true;
        }

        @Override public void close() throws IOException {
            if (finished) return;
            try { body.close(); } finally { Files.deleteIfExists(bodyFile); }
        }
    }

    private static OutputStream zstd(OutputStream out, int level) throws IOException {
        ZstdOutputStream z = new ZstdOutputStream(out, level);
        z.setChecksum(true);
        return z;
    }

    private static void writeRecord(OutputStream out, int type, byte[] payload) throws IOException {
        out.write(type); uv(out, payload.length); out.write(payload);
    }

    static void uv(OutputStream out, long value) throws IOException {
        while ((value & ~0x7FL) != 0) { out.write((int) (value & 0x7F) | 0x80); value >>>= 7; }
        out.write((int) value);
    }
    static void sv(OutputStream out, long value) throws IOException { uv(out, (value << 1) ^ (value >> 63)); }

    /** A record as read back: its type and payload. */
    record Record(int type, byte[] payload) {
        JsonObject json() { return JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject(); }
    }

    /** Sequential reader; used to check resumable files and by tests. */
    static final class Reader implements Closeable {
        private final InputStream in;
        Reader(Path file) throws IOException {
            in = new BufferedInputStream(new ZstdInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16)), 1 << 16);
            byte[] magic = in.readNBytes(4);
            if (!Arrays.equals(magic, MAGIC)) throw new IOException("not a packet stream: " + file);
            int version = (in.read() << 8) | in.read();
            if (version != FORMAT_VERSION) throw new IOException("unsupported packet stream version " + version);
        }
        Record next() throws IOException {
            int type = in.read();
            if (type < 0) return null;
            long length = readUv(in);
            if (length > Integer.MAX_VALUE) throw new IOException("record too large");
            byte[] payload = in.readNBytes((int) length);
            if (payload.length != length) throw new EOFException("truncated record");
            return new Record(type, payload);
        }
        @Override public void close() throws IOException { in.close(); }
    }

    static long readUv(InputStream in) throws IOException {
        long value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            int b = in.read();
            if (b < 0) throw new EOFException("truncated varint");
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("varint too long");
    }

    /** The HEADER of an existing file, or null if it cannot be read. */
    static JsonObject header(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try (Reader reader = new Reader(file)) {
            Record first = reader.next();
            return first != null && first.type() == HEADER ? first.json() : null;
        } catch (Exception e) { return null; }
    }
}
