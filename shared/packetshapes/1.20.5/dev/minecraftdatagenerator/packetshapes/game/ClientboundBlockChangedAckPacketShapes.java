package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;

import java.util.List;

/**
 * ClientboundBlockChangedAckPacket(int sequence).
 * <p>
 * Wire body: sequence:varint. The only field, so it sweeps every VarInt byte-length boundary
 * (1..5 bytes) plus the negative forms, which always encode as the full 5 bytes.
 */
public class ClientboundBlockChangedAckPacketShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundBlockChangedAckPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), ClientboundBlockChangedAckPacket::new);
    }
}
