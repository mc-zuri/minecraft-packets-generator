package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
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
 * arrays. It has a public canonical constructor, so the payload is synthesized directly here rather
 * than being scraped off the live light engine (a live engine only ever produces one shape).
 * <p>
 * Covered: chunk x/z over the chunk-coordinate spread (var-int encoded); all-empty light data; sky-only
 * and block-only updates; the "empty section" masks with no payload; set bits beyond index 63 so the
 * BitSet long array grows past one element; update-list sizes 0/1/2/6; and distinct nibble-array
 * contents (zeros, 0xFF, incrementing).
 * <p>
 * NOTE: the {@code skyUpdates}/{@code blockUpdates} list length is kept equal to the cardinality of the
 * matching mask, and every nibble array is exactly 2048 bytes, because the client pairs them up while
 * walking the mask and {@code DataLayer} rejects any other length. One live-engine-derived shape is
 * kept at the end as a sanity anchor.
 */
public class ClientboundLightUpdateShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundLightUpdatePacket.class;
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
    static List<ClientboundLightUpdatePacketData> lightData() {
        List<ClientboundLightUpdatePacketData> out = new ArrayList<>();

        // nothing at all
        out.add(new ClientboundLightUpdatePacketData(
                new BitSet(), new BitSet(), new BitSet(), new BitSet(), List.of(), List.of()));
        // only "these sections are empty" markers, no payload
        out.add(new ClientboundLightUpdatePacketData(
                new BitSet(), new BitSet(), bits(0), bits(0), List.of(), List.of()));
        out.add(new ClientboundLightUpdatePacketData(
                new BitSet(), new BitSet(), bits(0, 1, 2, 3, 25), bits(4, 5), List.of(), List.of()));
        // sky only, one section
        out.add(new ClientboundLightUpdatePacketData(
                bits(0), new BitSet(), new BitSet(), new BitSet(), layers(1), List.of()));
        // block only, two sections
        out.add(new ClientboundLightUpdatePacketData(
                new BitSet(), bits(0, 1), new BitSet(), new BitSet(), List.of(), layers(2)));
        // both, plus empty markers
        out.add(new ClientboundLightUpdatePacketData(
                bits(1, 2), bits(0), bits(3), bits(4, 5), layers(2), layers(1)));
        // a full-height update (26 sections for a -64..319 world plus the two border sections)
        out.add(new ClientboundLightUpdatePacketData(
                bits(0, 1, 2, 3, 4, 5), bits(0, 1, 2, 3, 4, 5), new BitSet(), new BitSet(),
                layers(6), layers(6)));
        // set bits past 63 so each BitSet needs more than one long on the wire
        out.add(new ClientboundLightUpdatePacketData(
                bits(64), bits(63, 64), bits(127), bits(128), layers(1), layers(2)));
        return out;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        // lightData variants at the origin chunk
        for (ClientboundLightUpdatePacketData data : lightData()) {
            out.add(new ClientboundLightUpdatePacket(0, 0, data));
        }
        // x/z var-int spread against a fixed minimal payload
        ClientboundLightUpdatePacketData base = lightData().get(0);
        for (int x : ctx.chunkCoords()) {
            for (int z : ctx.chunkCoords()) {
                out.add(new ClientboundLightUpdatePacket(x, z, base));
            }
        }
        // one shape built from the live light engine, as an anchor against the synthesized ones
        LevelLightEngine engine = ctx.overworld.getLightEngine();
        out.add(new ClientboundLightUpdatePacket(new ChunkPos(0, 0), engine, null, null));
        out.add(new ClientboundLightUpdatePacket(new ChunkPos(0, 0), engine, new BitSet(), new BitSet()));
        return out;
    }
}
