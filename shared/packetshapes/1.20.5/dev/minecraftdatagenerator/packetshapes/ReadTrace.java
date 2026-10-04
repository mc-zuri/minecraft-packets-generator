package dev.minecraftdatagenerator.packetshapes;

import com.google.gson.JsonArray;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Map;
import java.util.TreeMap;

/**
 * The buffer a shape is decoded from, recording which fixed-width reads vanilla's decoder makes.
 * <p>
 * {@link WireTrace} shows what vanilla WROTE, but a write does not say whether a byte is signed:
 * {@code writeByte(-1)} and {@code writeByte(255)} put the same bits on the wire. The decoder does -
 * it calls {@code readByte()} or {@code readUnsignedByte()} - so this is the authority the
 * validator uses for i8/u8, i16/u16 and i32/u32, falling back to the write argument only for shapes
 * vanilla itself could not decode.
 * <p>
 * Like WireTrace it must be the innermost source buffer, so reads made through any FriendlyByteBuf
 * wrapper still land here.
 */
public final class ReadTrace extends FriendlyByteBuf {
    /** offset -> kind of the fixed-width read made there. */
    private final Map<Integer, String> reads = new TreeMap<>();

    public ReadTrace(byte[] body) {
        super(Unpooled.wrappedBuffer(body));
    }

    private void add(String kind) {
        reads.put(readerIndex(), kind);
    }

    @Override
    public byte readByte() {
        add("i8");
        return super.readByte();
    }

    @Override
    public short readUnsignedByte() {
        add("u8");
        return super.readUnsignedByte();
    }

    @Override
    public short readShort() {
        add("i16");
        return super.readShort();
    }

    @Override
    public int readUnsignedShort() {
        add("u16");
        return super.readUnsignedShort();
    }

    @Override
    public int readInt() {
        add("i32");
        return super.readInt();
    }

    @Override
    public long readUnsignedInt() {
        add("u32");
        return super.readUnsignedInt();
    }

    @Override
    public boolean readBoolean() {
        add("bool");
        return super.readBoolean();
    }

    /** {@code [[offset, kind], ...]}, offsets relative to the packet body. */
    public JsonArray toJson() {
        JsonArray out = new JsonArray();
        for (Map.Entry<Integer, String> e : reads.entrySet()) {
            JsonArray r = new JsonArray();
            r.add(e.getKey());
            r.add(e.getValue());
            out.add(r);
        }
        return out;
    }
}
