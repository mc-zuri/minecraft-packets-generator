package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;

import java.util.List;

/** ClientboundContainerClosePacket(int containerId) — a single {@code writeContainerId}, which in 26.3 is
 *  a plain VarInt ({@code FriendlyByteBuf.writeContainerId -> VarInt.write}), so the full VarInt
 *  byte-length spread applies. */
public class ClientboundContainerCloseShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundContainerClosePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), ClientboundContainerClosePacket::new);
    }
}
