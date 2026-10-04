package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

/**
 * ClientboundForgetLevelChunkPacket(ChunkPos pos) — a single field written as a packed i64
 * ({@code writeChunkPos}: z in the high word, x in the low word). Full cross-product of the
 * domain-correct chunk-coordinate spread (0, +-1 and the +-1,875,000 world-border chunks) on both axes.
 */
public class ClientboundForgetLevelChunkShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundForgetLevelChunkPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundForgetLevelChunkPacket(new ChunkPos((int) a[0], (int) a[1])),
                ctx.chunkCoords(), ctx.chunkCoords());
    }
}
