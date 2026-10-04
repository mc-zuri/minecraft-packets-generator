package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

import java.util.List;

/**
 * ServerboundContainerClosePacket(int containerId).
 * <p>
 * {@code FriendlyByteBuf.writeContainerId} is a plain VarInt in 1.20.5, so the single field is swept over
 * every VarInt byte-length boundary (1..5 bytes) plus the negative forms.
 */
public class ServerboundContainerCloseShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundContainerClosePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), ServerboundContainerClosePacket::new);
    }
}
