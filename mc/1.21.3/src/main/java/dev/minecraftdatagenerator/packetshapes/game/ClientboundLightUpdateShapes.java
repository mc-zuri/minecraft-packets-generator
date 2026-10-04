package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * ClientboundLightUpdatePacket(int x[varint], int z[varint], ClientboundLightUpdatePacketData lightData).
 * <p>
 * {@code lightData} is {@code (BitSet skyYMask, BitSet blockYMask, BitSet emptySkyYMask,
 * BitSet emptyBlockYMask, List&lt;byte[2048]&gt; skyUpdates, List&lt;byte[2048]&gt; blockUpdates)} — four
 * BitSets each written as a var-int-prefixed long array, then two var-int-prefixed lists of nibble
 * arrays.
 * <p>
 * In 1.21.3 both {@code ClientboundLightUpdatePacketData} and {@code ClientboundLightUpdatePacket} only
 * expose the live-light-engine constructor (the wire constructors are {@code FriendlyByteBuf}-based and
 * private/internal), and a live engine only ever produces one shape. The payload spread is therefore
 * synthesized on the wire: each variant is written field by field into a buffer in exactly the layout
 * {@code ClientboundLightUpdatePacketData.write} produces and then read back through the packet's own
 * {@code STREAM_CODEC}, so every shape is a genuine, codec-valid packet.
 * <p>
 * Covered: chunk x/z over the chunk-coordinate spread (var-int encoded); all-empty light data; sky-only
 * and block-only updates; the "empty section" masks with no payload; mixed masks; a full-height update;
 * set bits beyond index 63 so the BitSet long array grows past one element; update-list sizes 0/1/2/6;
 * and distinct nibble-array contents (zeros, 0xFF, incrementing, and other fills).
 * <p>
 * NOTE: the {@code skyUpdates}/{@code blockUpdates} list length is kept equal to the cardinality of the
 * matching mask, and every nibble array is exactly 2048 bytes, because the client pairs them up while
 * walking the mask and {@code DataLayer} rejects any other length. Two live-engine-derived shapes are
 * kept at the end as sanity anchors.
 */
public class ClientboundLightUpdateShapes implements PacketShapes {
    /** Same element codec the packet data uses: a var-int-prefixed byte array capped at one nibble layer. */
    static final StreamCodec<ByteBuf, byte[]> DATA_LAYER_STREAM_CODEC = ByteBufCodecs.byteArray(2048);

    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundLightUpdatePacket.class;
    }

    /**
     * The six wire fields of {@code ClientboundLightUpdatePacketData}, in wire order. 1.21.3 has no public
     * canonical constructor for that class, so a shape is expressed as its wire form and decoded back.
     */
    record LightPayload(BitSet skyYMask, BitSet blockYMask, BitSet emptySkyYMask, BitSet emptyBlockYMask,
                        List<byte[]> skyUpdates, List<byte[]> blockUpdates) {
        void write(FriendlyByteBuf out) {
            out.writeBitSet(skyYMask);
            out.writeBitSet(blockYMask);
            out.writeBitSet(emptySkyYMask);
            out.writeBitSet(emptyBlockYMask);
            out.writeCollection(skyUpdates, DATA_LAYER_STREAM_CODEC);
            out.writeCollection(blockUpdates, DATA_LAYER_STREAM_CODEC);
        }
    }

    static BitSet bits(int... indices) {
        BitSet set = new BitSet();
        for (int i : indices) set.set(i);
        return set;
    }

    static byte[] nibbles(int fill) {
        byte[] data = new byte[2048];
        if (fill == -2) {
            for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        } else {
            java.util.Arrays.fill(data, (byte) fill);
        }
        return data;
    }

    private static List<byte[]> layers(int count) {
        List<byte[]> out = new ArrayList<>();
        int[] fills = {0, -1, -2, 15, 1, -16};
        for (int i = 0; i < count; i++) out.add(nibbles(fills[i % fills.length]));
        return out;
    }

    /** Light payloads whose mask cardinality matches its update-list size. Shared with the chunk packet. */
    static List<LightPayload> lightData() {
        List<LightPayload> out = new ArrayList<>();

        // nothing at all
        out.add(new LightPayload(
                new BitSet(), new BitSet(), new BitSet(), new BitSet(), List.of(), List.of()));
        // only "these sections are empty" markers, no payload
        out.add(new LightPayload(
                new BitSet(), new BitSet(), bits(0), bits(0), List.of(), List.of()));
        out.add(new LightPayload(
                new BitSet(), new BitSet(), bits(0, 1, 2, 3, 25), bits(4, 5), List.of(), List.of()));
        // sky only, one section
        out.add(new LightPayload(
                bits(0), new BitSet(), new BitSet(), new BitSet(), layers(1), List.of()));
        // block only, two sections
        out.add(new LightPayload(
                new BitSet(), bits(0, 1), new BitSet(), new BitSet(), List.of(), layers(2)));
        // both, plus empty markers
        out.add(new LightPayload(
                bits(1, 2), bits(0), bits(3), bits(4, 5), layers(2), layers(1)));
        // a full-height update (six sections of payload on both layers)
        out.add(new LightPayload(
                bits(0, 1, 2, 3, 4, 5), bits(0, 1, 2, 3, 4, 5), new BitSet(), new BitSet(),
                layers(6), layers(6)));
        // set bits past 63 so each BitSet needs more than one long on the wire
        out.add(new LightPayload(
                bits(64), bits(63, 64), bits(127), bits(128), layers(1), layers(2)));
        return out;
    }

    /** Encodes {@code (varint x, varint z, lightData)} and reads it back through the packet codec. */
    private static ClientboundLightUpdatePacket packet(int x, int z, LightPayload payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(x);
        buf.writeVarInt(z);
        payload.write(buf);
        return ClientboundLightUpdatePacket.STREAM_CODEC.decode(buf);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        // lightData variants at the origin chunk
        for (LightPayload data : lightData()) {
            out.add(packet(0, 0, data));
        }
        // x/z var-int spread against a fixed minimal payload
        LightPayload base = lightData().get(0);
        for (int x : ctx.chunkCoords()) {
            for (int z : ctx.chunkCoords()) {
                out.add(packet(x, z, base));
            }
        }
        // shapes built from the live light engine, as anchors against the synthesized ones
        LevelLightEngine engine = ctx.overworld.getLightEngine();
        out.add(new ClientboundLightUpdatePacket(new ChunkPos(0, 0), engine, null, null));
        out.add(new ClientboundLightUpdatePacket(new ChunkPos(0, 0), engine, new BitSet(), new BitSet()));
        return out;
    }
}
