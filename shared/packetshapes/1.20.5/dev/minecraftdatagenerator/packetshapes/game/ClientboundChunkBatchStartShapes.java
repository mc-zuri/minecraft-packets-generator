package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;

import java.util.List;

/** ClientboundChunkBatchStartPacket — empty body (StreamCodec.unit(INSTANCE)); single shape. */
public class ClientboundChunkBatchStartShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundChunkBatchStartPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.list(ClientboundChunkBatchStartPacket.INSTANCE);
    }
}
