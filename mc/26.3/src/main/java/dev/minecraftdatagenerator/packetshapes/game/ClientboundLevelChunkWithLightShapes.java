package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * ClientboundLevelChunkWithLightPacket(int x[i32], int z[i32], ClientboundLevelChunkPacketData chunkData,
 * ClientboundLightUpdatePacketData lightData).
 * <p>
 * {@code chunkData} is {@code (Map&lt;Heightmap.Types, long[]&gt; heightmaps, byte[&le;2MiB] buffer,
 * List&lt;BlockEntityInfo(byte packedXZ, short y, BlockEntityType type, Optional&lt;CompoundTag&gt;
 * tag)&gt; blockEntities)}. Its canonical constructor is private, so the only way to build one is from a
 * real {@link LevelChunk} — the chunk blob therefore cannot be swept field by field and is taken from
 * the overworld.
 * <p>
 * {@code lightData} however has a public canonical constructor, so the light half is synthesized across
 * its full branch set (shared with the light-update provider): empty, sky-only, block-only, empty-section
 * markers, multi-long BitSets and update-list sizes 0/1/2/6.
 * <p>
 * Covered: the record constructor is used directly so {@code x}/{@code z} sweep the chunk-coordinate
 * spread (plain i32 here, unlike the var-ints of ClientboundLightUpdatePacket), crossed with the light
 * payload variants; plus the two live-engine shapes (null filters = full light, empty filters = none).
 */
public class ClientboundLevelChunkWithLightShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundLevelChunkWithLightPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        LevelChunk chunk = ctx.overworld.getChunk(0, 0);
        LevelLightEngine engine = ctx.overworld.getLightEngine();
        ClientboundLevelChunkPacketData chunkData = new ClientboundLevelChunkPacketData(chunk);

        List<Packet<?>> out = new ArrayList<>();

        // every synthesized light payload at the origin chunk
        for (ClientboundLightUpdatePacketData light : ClientboundLightUpdateShapes.lightData()) {
            out.add(new ClientboundLevelChunkWithLightPacket(0, 0, chunkData, light));
        }
        // x/z spread (written as plain i32) against a minimal light payload
        ClientboundLightUpdatePacketData base = ClientboundLightUpdateShapes.lightData().get(0);
        for (int x : ctx.chunkCoords()) {
            for (int z : ctx.chunkCoords()) {
                out.add(new ClientboundLevelChunkWithLightPacket(x, z, chunkData, base));
            }
        }
        // live-engine anchors
        out.add(new ClientboundLevelChunkWithLightPacket(chunk, engine, null, null));
        out.add(new ClientboundLevelChunkWithLightPacket(chunk, engine, new BitSet(), new BitSet()));
        return out;
    }
}
