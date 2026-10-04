package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;

import java.util.List;

/** ClientboundChunkBatchFinishedPacket(int batchSize) — a single {@code writeVarInt}; swept over every
 *  VarInt byte-length boundary (1..5 bytes) plus the negative forms. */
public class ClientboundChunkBatchFinishedShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundChunkBatchFinishedPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), ClientboundChunkBatchFinishedPacket::new);
    }
}
