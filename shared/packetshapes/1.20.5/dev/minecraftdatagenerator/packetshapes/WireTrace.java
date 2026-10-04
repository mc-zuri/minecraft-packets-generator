package dev.minecraftdatagenerator.packetshapes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


/**
 * The buffer a shape is encoded into, recording every primitive write vanilla's STREAM_CODEC makes.
 * <p>
 * A byte-exact round trip through protocol.json only proves each field has the right width: an
 * {@code i32} declared where vanilla writes an {@code f32}, or an {@code i8} where it writes an
 * unsigned byte, round-trips just as well. This trace is vanilla's side of the stronger check - for
 * each span of the body, what KIND of write produced it and with what argument - so the validator
 * can compare it against the leaf type protocol.json reads there.
 * <p>
 * Semantic writers are identified by explicit mixin scopes, so the
 * same class works unchanged in every version module: bytes from {@link VarInt#write} and
 * {@link VarLong#write} become one var-int / var-long, a length prefix plus payload from
 * {@link Utf8String#write} becomes one string, and a long from {@code writeBlockPos} is unpacked with
 * vanilla's own {@link BlockPos} accessors, so the validator can check the bit layout of
 * minecraft-data's {@code position} type against real coordinates instead of just repacking it.
 * <p>
 * This must be the SOURCE of the RegistryFriendlyByteBuf handed to the codec, not a wrapper around
 * it: FriendlyByteBuf's own helpers (writeVarInt, writeUtf, ...) pass {@code this.source} to the
 * static writers, so only the innermost buffer sees every byte.
 */
public final class WireTrace extends FriendlyByteBuf {
    private static final ThreadLocal<ArrayDeque<String>> SCOPES = new ThreadLocal<>();

    public static void begin() { SCOPES.set(new ArrayDeque<>()); }
    public static void end() { SCOPES.remove(); }
    public static void enter(String kind) {
        var scopes = SCOPES.get();
        if (scopes != null) scopes.push(kind);
    }
    public static void leave() {
        var scopes = SCOPES.get();
        if (scopes != null && !scopes.isEmpty()) scopes.pop();
    }

    /** One primitive write: [offset, length, kind, value, owning semantic writer or null]. */
    private record Raw(int off, int len, String kind, Object value, String owner) {}

    private final List<Raw> raw = new ArrayList<>();
    private boolean patched;
    private boolean exposedView;

    public WireTrace() {
        super(Unpooled.buffer());
    }

    /** True if the codec patched already-written bytes, which would make the offsets unreliable. */
    public boolean patched() {
        return patched;
    }

    // ---- classification ------------------------------------------------------------------------

    private static String owner() {
        var scopes = SCOPES.get();
        return scopes == null || scopes.isEmpty() ? null : scopes.getLast();
    }

    /** Coverage is checked independently of semantic grouping. */
    public JsonObject coverage() {
        JsonObject out = new JsonObject();
        int cursor = 0;
        boolean contiguous = true;
        for (Raw r : raw) {
            if (r.off != cursor || r.len < 0) contiguous = false;
            cursor = r.off + r.len;
        }
        out.addProperty("bodyBytes", writerIndex());
        out.addProperty("coveredBytes", cursor);
        out.addProperty("complete", contiguous && cursor == writerIndex() && !patched && !exposedView);
        out.addProperty("patched", patched);
        out.addProperty("exposedView", exposedView);
        return out;
    }

    public JsonArray primitives() {
        JsonArray out = new JsonArray();
        for (Raw r : raw) out.add(entry(r.off, r.len, r.kind, primitive(r)));
        return out;
    }

    private void add(int len, String kind, Object value) {
        raw.add(new Raw(writerIndex(), len, kind, value, owner()));
    }

    // ---- primitive writes ----------------------------------------------------------------------

    @Override
    public FriendlyByteBuf writeBoolean(boolean value) {
        add(1, "bool", value);
        return super.writeBoolean(value);
    }

    @Override
    public FriendlyByteBuf writeByte(int value) {
        add(1, "i8", value);
        return super.writeByte(value);
    }

    @Override
    public FriendlyByteBuf writeShort(int value) {
        add(2, "i16", value);
        return super.writeShort(value);
    }

    @Override
    public FriendlyByteBuf writeShortLE(int value) {
        add(2, "li16", value);
        return super.writeShortLE(value);
    }

    @Override
    public FriendlyByteBuf writeMedium(int value) {
        add(3, "i24", value);
        return super.writeMedium(value);
    }

    @Override
    public FriendlyByteBuf writeMediumLE(int value) {
        add(3, "li24", value);
        return super.writeMediumLE(value);
    }

    @Override
    public FriendlyByteBuf writeInt(int value) {
        add(4, "i32", value);
        return super.writeInt(value);
    }

    @Override
    public FriendlyByteBuf writeIntLE(int value) {
        add(4, "li32", value);
        return super.writeIntLE(value);
    }

    @Override
    public FriendlyByteBuf writeLong(long value) {
        add(8, "i64", value);
        return super.writeLong(value);
    }

    @Override
    public FriendlyByteBuf writeLongLE(long value) {
        add(8, "li64", value);
        return super.writeLongLE(value);
    }

    @Override
    public FriendlyByteBuf writeChar(int value) {
        add(2, "char", value);
        return super.writeChar(value);
    }

    @Override
    public FriendlyByteBuf writeFloat(float value) {
        add(4, "f32", value);
        return super.writeFloat(value);
    }

    @Override
    public FriendlyByteBuf writeDouble(double value) {
        add(8, "f64", value);
        return super.writeDouble(value);
    }

    @Override
    public FriendlyByteBuf writeBytes(ByteBuf src) {
        byte[] b = new byte[src.readableBytes()];
        src.getBytes(src.readerIndex(), b);
        add(b.length, "bytes", b);
        return super.writeBytes(src);
    }

    @Override
    public FriendlyByteBuf writeBytes(ByteBuf src, int length) {
        byte[] b = new byte[length];
        src.getBytes(src.readerIndex(), b);
        add(length, "bytes", b);
        return super.writeBytes(src, length);
    }

    @Override
    public FriendlyByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        byte[] b = new byte[length];
        src.getBytes(srcIndex, b);
        add(length, "bytes", b);
        return super.writeBytes(src, srcIndex, length);
    }

    @Override
    public FriendlyByteBuf writeBytes(byte[] src) {
        add(src.length, "bytes", src.clone());
        return super.writeBytes(src);
    }

    @Override
    public FriendlyByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        byte[] b = new byte[length];
        System.arraycopy(src, srcIndex, b, 0, length);
        add(length, "bytes", b);
        return super.writeBytes(src, srcIndex, length);
    }

    @Override
    public FriendlyByteBuf writeBytes(ByteBuffer src) {
        byte[] b = new byte[src.remaining()];
        src.duplicate().get(b);
        add(b.length, "bytes", b);
        return super.writeBytes(src);
    }

    @Override
    public FriendlyByteBuf writeZero(int length) {
        add(length, "bytes", new byte[length]);
        return super.writeZero(length);
    }

    @Override
    public int writeCharSequence(CharSequence sequence, Charset charset) {
        byte[] b = sequence.toString().getBytes(charset);
        add(b.length, "bytes", b);
        return super.writeCharSequence(sequence, charset);
    }

    // ---- patching writes: legal, but they move bytes behind the recorded offsets --------------

    @Override
    public FriendlyByteBuf setByte(int index, int value) {
        patched = true;
        return super.setByte(index, value);
    }

    @Override
    public FriendlyByteBuf setShort(int index, int value) {
        patched = true;
        return super.setShort(index, value);
    }

    @Override
    public FriendlyByteBuf setMedium(int index, int value) {
        patched = true;
        return super.setMedium(index, value);
    }

    @Override
    public FriendlyByteBuf setInt(int index, int value) {
        patched = true;
        return super.setInt(index, value);
    }

    @Override
    public FriendlyByteBuf setLong(int index, long value) {
        patched = true;
        return super.setLong(index, value);
    }

    @Override
    public FriendlyByteBuf setBytes(int index, byte[] src) {
        patched = true;
        return super.setBytes(index, src);
    }

    @Override
    public FriendlyByteBuf setBytes(int index, ByteBuf src) {
        patched = true;
        return super.setBytes(index, src);
    }

    @Override public FriendlyByteBuf setBoolean(int index, boolean value) { patched = true; return super.setBoolean(index, value); }
    @Override public FriendlyByteBuf setShortLE(int index, int value) { patched = true; return super.setShortLE(index, value); }
    @Override public FriendlyByteBuf setMediumLE(int index, int value) { patched = true; return super.setMediumLE(index, value); }
    @Override public FriendlyByteBuf setIntLE(int index, int value) { patched = true; return super.setIntLE(index, value); }
    @Override public FriendlyByteBuf setLongLE(int index, long value) { patched = true; return super.setLongLE(index, value); }
    @Override public FriendlyByteBuf setChar(int index, int value) { patched = true; return super.setChar(index, value); }
    @Override public FriendlyByteBuf setFloat(int index, float value) { patched = true; return super.setFloat(index, value); }
    @Override public FriendlyByteBuf setDouble(int index, double value) { patched = true; return super.setDouble(index, value); }
    @Override public FriendlyByteBuf setBytes(int index, byte[] src, int srcIndex, int length) { patched = true; return super.setBytes(index, src, srcIndex, length); }
    @Override public FriendlyByteBuf setBytes(int index, ByteBuf src, int length) { patched = true; return super.setBytes(index, src, length); }
    @Override public FriendlyByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) { patched = true; return super.setBytes(index, src, srcIndex, length); }
    @Override public FriendlyByteBuf setBytes(int index, ByteBuffer src) { patched = true; return super.setBytes(index, src); }
    @Override public FriendlyByteBuf setZero(int index, int length) { patched = true; return super.setZero(index, length); }
    @Override public ByteBuf slice() { exposedView = true; return super.slice(); }
    @Override public ByteBuf slice(int index, int length) { exposedView = true; return super.slice(index, length); }
    @Override public ByteBuf retainedSlice() { exposedView = true; return super.retainedSlice(); }
    @Override public ByteBuf retainedSlice(int index, int length) { exposedView = true; return super.retainedSlice(index, length); }
    @Override public ByteBuf duplicate() { exposedView = true; return super.duplicate(); }
    @Override public ByteBuf retainedDuplicate() { exposedView = true; return super.retainedDuplicate(); }
    @Override public ByteBuf readSlice(int length) { exposedView = true; return super.readSlice(length); }
    @Override public ByteBuf readRetainedSlice(int length) { exposedView = true; return super.readRetainedSlice(length); }
    @Override public byte[] array() { exposedView = true; return super.array(); }
    @Override public ByteBuffer nioBuffer() { exposedView = true; return super.nioBuffer(); }
    @Override public ByteBuffer nioBuffer(int index, int length) { exposedView = true; return super.nioBuffer(index, length); }
    @Override public ByteBuffer internalNioBuffer(int index, int length) { exposedView = true; return super.internalNioBuffer(index, length); }
    @Override public ByteBuffer[] nioBuffers() { exposedView = true; return super.nioBuffers(); }
    @Override public ByteBuffer[] nioBuffers(int index, int length) { exposedView = true; return super.nioBuffers(index, length); }
    @Override public FriendlyByteBuf writerIndex(int index) { patched = true; return super.writerIndex(index); }
    @Override public FriendlyByteBuf setIndex(int reader, int writer) { patched = true; return super.setIndex(reader, writer); }
    @Override public FriendlyByteBuf resetWriterIndex() { patched = true; return super.resetWriterIndex(); }

    @Override public ByteBuf unwrap() { exposedView = true; return super.unwrap(); }
    @Override public long memoryAddress() { exposedView = true; return super.memoryAddress(); }
    @Override public FriendlyByteBuf clear() { patched = true; return super.clear(); }
    @Override public FriendlyByteBuf discardReadBytes() { patched = true; return super.discardReadBytes(); }
    @Override public FriendlyByteBuf discardSomeReadBytes() { patched = true; return super.discardSomeReadBytes(); }
    @Override public int setCharSequence(int index, CharSequence value, Charset charset) { patched = true; return super.setCharSequence(index, value, charset); }
    @Override public int setBytes(int index, java.io.InputStream input, int length) throws java.io.IOException { patched = true; return super.setBytes(index, input, length); }
    @Override public int setBytes(int index, java.nio.channels.ScatteringByteChannel input, int length) throws java.io.IOException { patched = true; return super.setBytes(index, input, length); }
    @Override public int setBytes(int index, java.nio.channels.FileChannel input, long position, int length) throws java.io.IOException { patched = true; return super.setBytes(index, input, position, length); }

    // ---- export --------------------------------------------------------------------------------

    /**
     * The trace as {@code [[offset, length, kind, value], ...]}, offsets relative to the packet body.
     * Consecutive bytes owned by one semantic writer are merged into a single entry; everything else
     * is emitted one entry per primitive write. Floats go out as their raw bits (JSON has no NaN) and
     * longs as strings (JSON numbers lose precision past 2^53).
     */
    public JsonArray toJson() {
        JsonArray out = new JsonArray();
        int i = 0;
        while (i < raw.size()) {
            Raw r = raw.get(i);
            if (("varint".equals(r.owner) || "varlong".equals(r.owner)) && varEnd(i) > i) {
                int end = varEnd(i);
                long v = varValue(i, end);
                out.add(entry(r.off, span(i, end), r.owner,
                        "varint".equals(r.owner) ? (Object) (int) v : (Object) Long.toString(v)));
                i = end;
            } else if ("string".equals(r.owner) && varEnd(i) > i) {
                int lenEnd = varEnd(i);
                int n = (int) varValue(i, lenEnd);
                byte[] payload = new byte[n];
                int got = 0;
                int j = lenEnd;
                // an empty string still writes its (empty) payload, so zero-length writes belong to it
                while (j < raw.size() && "string".equals(raw.get(j).owner) && (got < n || raw.get(j).len == 0)) {
                    byte[] b = bytesOf(raw.get(j));
                    System.arraycopy(b, 0, payload, got, Math.min(b.length, n - got));
                    got += b.length;
                    j++;
                }
                out.add(entry(r.off, span(i, j), "string", new String(payload, StandardCharsets.UTF_8)));
                i = j;
            } else if ("blockpos".equals(r.owner) && "i64".equals(r.kind)) {
                long p = (Long) r.value;
                JsonArray xyz = new JsonArray();
                xyz.add(BlockPos.getX(p));
                xyz.add(BlockPos.getY(p));
                xyz.add(BlockPos.getZ(p));
                out.add(entry(r.off, 8, "blockpos", xyz));
                i++;
            } else {
                out.add(entry(r.off, r.len, r.kind, primitive(r)));
                i++;
            }
        }
        return out;
    }

    /**
     * Index just past the var-int/var-long starting at {@code i}: through the first byte without
     * bit 7. Returns {@code i} itself if the write there is not a byte at all.
     */
    private int varEnd(int i) {
        int j = i;
        while (j < raw.size()) {
            Raw r = raw.get(j);
            if (!"i8".equals(r.kind)) break;
            j++;
            if (((Integer) r.value & 0x80) == 0) break;
        }
        return j;
    }

    private long varValue(int from, int to) {
        long v = 0;
        int shift = 0;
        for (int k = from; k < to; k++) {
            v |= (long) ((Integer) raw.get(k).value & 0x7F) << shift;
            shift += 7;
        }
        return v;
    }

    private int span(int from, int to) {
        Raw last = raw.get(to - 1);
        return last.off + last.len - raw.get(from).off;
    }

    private static byte[] bytesOf(Raw r) {
        if (r.value instanceof byte[] b) return b;
        return new byte[]{(byte) (int) (Integer) r.value};
    }

    private static Object primitive(Raw r) {
        return switch (r.kind) {
            case "f32" -> Float.floatToRawIntBits((Float) r.value);
            case "f64" -> Long.toString(Double.doubleToRawLongBits((Double) r.value));
            case "i64", "li64" -> Long.toString((Long) r.value);
            case "bytes" -> hex((byte[]) r.value);
            default -> r.value;
        };
    }

    private static JsonArray entry(int off, int len, String kind, Object value) {
        JsonArray e = new JsonArray();
        e.add(off);
        e.add(len);
        e.add(kind);
        switch (value) {
            case JsonArray a -> e.add(a);
            case Boolean b -> e.add(b);
            case Number n -> e.add(n);
            default -> e.add(String.valueOf(value));
        }
        return e;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
