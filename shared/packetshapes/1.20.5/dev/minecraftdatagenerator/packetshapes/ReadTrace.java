package dev.minecraftdatagenerator.packetshapes;

import com.google.gson.JsonArray;
import dev.minecraftdatagenerator.packetgen.TraceOp;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The buffer a shape is decoded from, recording every read vanilla's decoder makes: offset,
 * length and kind.
 * <p>
 * {@link WireTrace} shows what vanilla WROTE, but a write does not say whether a byte is signed:
 * {@code writeByte(-1)} and {@code writeByte(255)} put the same bits on the wire. The decoder does -
 * it calls {@code readByte()} or {@code readUnsignedByte()} - so this is the authority for i8/u8,
 * i16/u16 and i32/u32. Bulk and skipped reads are recorded too ({@code Utf8String.read} consumes
 * its payload by moving the reader index), so the reads partition the whole body.
 * <p>
 * Like WireTrace it must be the innermost source buffer, so reads made through any FriendlyByteBuf
 * wrapper still land here. Only the outermost call is recorded when one read delegates to another.
 */
public final class ReadTrace extends FriendlyByteBuf {
    private static final Set<String> FIXED = Set.of("i8", "u8", "i16", "u16", "i32", "u32", "bool");
    private final List<TraceOp> ops = new ArrayList<>();
    private int depth;
    private boolean rewound;

    public ReadTrace(byte[] body) {
        super(Unpooled.wrappedBuffer(body));
    }

    public List<TraceOp> ops() { return ops; }
    public boolean rewound() { return rewound; }

    private int begin() { return depth++ == 0 ? readerIndex() : -1; }

    private void end(int start, String kind) {
        if (--depth != 0 || start < 0) return;
        int length = readerIndex() - start;
        if (length < 0) rewound = true;
        else if (length > 0 || !kind.equals("skip")) ops.add(new TraceOp(start, length, kind, null));
    }

    /** The legacy {@code [[offset, kind], ...]} view of fixed-width reads, kept for the compatibility generators. */
    public JsonArray toJson() {
        JsonArray out = new JsonArray();
        for (TraceOp op : ops) if (FIXED.contains(op.kind())) {
            JsonArray r = new JsonArray();
            r.add(op.off());
            r.add(op.kind());
            out.add(r);
        }
        return out;
    }

    @Override public boolean readBoolean() { int s = begin(); try { return super.readBoolean(); } finally { end(s, "bool"); } }
    @Override public byte readByte() { int s = begin(); try { return super.readByte(); } finally { end(s, "i8"); } }
    @Override public short readUnsignedByte() { int s = begin(); try { return super.readUnsignedByte(); } finally { end(s, "u8"); } }
    @Override public short readShort() { int s = begin(); try { return super.readShort(); } finally { end(s, "i16"); } }
    @Override public short readShortLE() { int s = begin(); try { return super.readShortLE(); } finally { end(s, "li16"); } }
    @Override public int readUnsignedShort() { int s = begin(); try { return super.readUnsignedShort(); } finally { end(s, "u16"); } }
    @Override public int readUnsignedShortLE() { int s = begin(); try { return super.readUnsignedShortLE(); } finally { end(s, "lu16"); } }
    @Override public int readMedium() { int s = begin(); try { return super.readMedium(); } finally { end(s, "i24"); } }
    @Override public int readMediumLE() { int s = begin(); try { return super.readMediumLE(); } finally { end(s, "li24"); } }
    @Override public int readUnsignedMedium() { int s = begin(); try { return super.readUnsignedMedium(); } finally { end(s, "u24"); } }
    @Override public int readUnsignedMediumLE() { int s = begin(); try { return super.readUnsignedMediumLE(); } finally { end(s, "lu24"); } }
    @Override public int readInt() { int s = begin(); try { return super.readInt(); } finally { end(s, "i32"); } }
    @Override public int readIntLE() { int s = begin(); try { return super.readIntLE(); } finally { end(s, "li32"); } }
    @Override public long readUnsignedInt() { int s = begin(); try { return super.readUnsignedInt(); } finally { end(s, "u32"); } }
    @Override public long readUnsignedIntLE() { int s = begin(); try { return super.readUnsignedIntLE(); } finally { end(s, "lu32"); } }
    @Override public long readLong() { int s = begin(); try { return super.readLong(); } finally { end(s, "i64"); } }
    @Override public long readLongLE() { int s = begin(); try { return super.readLongLE(); } finally { end(s, "li64"); } }
    @Override public char readChar() { int s = begin(); try { return super.readChar(); } finally { end(s, "char"); } }
    @Override public float readFloat() { int s = begin(); try { return super.readFloat(); } finally { end(s, "f32"); } }
    @Override public double readDouble() { int s = begin(); try { return super.readDouble(); } finally { end(s, "f64"); } }

    @Override public ByteBuf readBytes(int length) { int s = begin(); try { return super.readBytes(length); } finally { end(s, "bytes"); } }
    @Override public ByteBuf readSlice(int length) { int s = begin(); try { return super.readSlice(length); } finally { end(s, "bytes"); } }
    @Override public ByteBuf readRetainedSlice(int length) { int s = begin(); try { return super.readRetainedSlice(length); } finally { end(s, "bytes"); } }
    @Override public FriendlyByteBuf readBytes(ByteBuf dst) { int s = begin(); try { return super.readBytes(dst); } finally { end(s, "bytes"); } }
    @Override public FriendlyByteBuf readBytes(ByteBuf dst, int length) { int s = begin(); try { return super.readBytes(dst, length); } finally { end(s, "bytes"); } }
    @Override public FriendlyByteBuf readBytes(ByteBuf dst, int index, int length) { int s = begin(); try { return super.readBytes(dst, index, length); } finally { end(s, "bytes"); } }
    @Override public FriendlyByteBuf readBytes(byte[] dst) { int s = begin(); try { return super.readBytes(dst); } finally { end(s, "bytes"); } }
    @Override public FriendlyByteBuf readBytes(byte[] dst, int index, int length) { int s = begin(); try { return super.readBytes(dst, index, length); } finally { end(s, "bytes"); } }
    @Override public FriendlyByteBuf readBytes(ByteBuffer dst) { int s = begin(); try { return super.readBytes(dst); } finally { end(s, "bytes"); } }
    @Override public FriendlyByteBuf readBytes(OutputStream out, int length) throws IOException { int s = begin(); try { return super.readBytes(out, length); } finally { end(s, "bytes"); } }
    @Override public int readBytes(GatheringByteChannel out, int length) throws IOException { int s = begin(); try { return super.readBytes(out, length); } finally { end(s, "bytes"); } }
    @Override public int readBytes(FileChannel out, long position, int length) throws IOException { int s = begin(); try { return super.readBytes(out, position, length); } finally { end(s, "bytes"); } }
    @Override public CharSequence readCharSequence(int length, Charset charset) { int s = begin(); try { return super.readCharSequence(length, charset); } finally { end(s, "bytes"); } }

    // Index moves consume (or rewind) bytes without a typed read, e.g. a string payload.
    @Override public FriendlyByteBuf skipBytes(int length) { int s = begin(); try { return super.skipBytes(length); } finally { end(s, "skip"); } }
    @Override public FriendlyByteBuf readerIndex(int index) { int s = begin(); try { return super.readerIndex(index); } finally { end(s, "skip"); } }
    @Override public FriendlyByteBuf resetReaderIndex() { int s = begin(); try { return super.resetReaderIndex(); } finally { end(s, "skip"); } }
}
