package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
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
 * tag)&gt; blockEntities)}. Its only public constructor takes a real {@link LevelChunk}, so the chunk blob
 * cannot be swept field by field and is taken from the overworld.
 * <p>
 * In 1.21 the packet itself has no {@code (x, z, chunkData, lightData)} constructor either — that is a
 * 26.3 addition; the only public constructor derives x/z from a live chunk and scrapes the light half off
 * the live light engine, which yields exactly one shape. So the varied shapes are synthesized on the wire
 * instead: {@code x}/{@code z} plus the live chunk blob plus a hand-built light payload are written in
 * exactly the layout {@code write} produces and read back through the packet's own {@code STREAM_CODEC},
 * so every shape is a genuine, codec-valid packet.
 * <p>
 * Covered: {@code x}/{@code z} over the chunk-coordinate spread (plain i32 here, unlike the var-ints of
 * ClientboundLightUpdatePacket), crossed with the full synthesized light-payload branch set shared with
 * the light-update provider (empty, sky-only, block-only, empty-section markers, mixed, full-height,
 * multi-long BitSets, update-list sizes 0/1/2/6); plus the two live-engine shapes (null filters = full
 * light, empty filters = none) as anchors.
 */
public class ClientboundLevelChunkWithLightShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundLevelChunkWithLightPacket.class;
    }

    /** Encodes {@code (i32 x, i32 z, chunkData, lightData)} and reads it back through the packet codec. */
    private static ClientboundLevelChunkWithLightPacket packet(ShapeCtx ctx, int x, int z,
                                                               ClientboundLevelChunkPacketData chunkData,
                                                               ClientboundLightUpdateShapes.LightPayload light) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), ctx.registries);
        buf.writeInt(x);
        buf.writeInt(z);
        chunkData.write(buf);
        light.write(buf);
        return ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(buf);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        LevelChunk chunk = ctx.overworld.getChunk(0, 0);
        LevelLightEngine engine = ctx.overworld.getLightEngine();
        ClientboundLevelChunkPacketData chunkData = new ClientboundLevelChunkPacketData(chunk);

        List<Packet<?>> out = new ArrayList<>();

        // every synthesized light payload at the origin chunk
        for (ClientboundLightUpdateShapes.LightPayload light : ClientboundLightUpdateShapes.lightData()) {
            out.add(packet(ctx, 0, 0, chunkData, light));
        }
        // x/z spread (written as plain i32) against a minimal light payload
        ClientboundLightUpdateShapes.LightPayload base = ClientboundLightUpdateShapes.lightData().get(0);
        for (int x : ctx.chunkCoords()) {
            for (int z : ctx.chunkCoords()) {
                out.add(packet(ctx, x, z, chunkData, base));
            }
        }
        // live-engine anchors
        out.add(new ClientboundLevelChunkWithLightPacket(chunk, engine, null, null));
        out.add(new ClientboundLevelChunkWithLightPacket(chunk, engine, new BitSet(), new BitSet()));
        return out;
    }
}
