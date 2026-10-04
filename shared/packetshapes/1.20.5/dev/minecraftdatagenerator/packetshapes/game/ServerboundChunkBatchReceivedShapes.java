package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;

import java.util.List;

/** ServerboundChunkBatchReceivedPacket(float desiredChunksPerTick) — single writeFloat field; all float edges. */
public class ServerboundChunkBatchReceivedShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundChunkBatchReceivedPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.floats(), ServerboundChunkBatchReceivedPacket::new);
    }
}
