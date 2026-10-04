package dev.minecraftdatagenerator.packetshapes.configuration;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientboundResetChatPacket;

import java.util.List;

/** ClientboundResetChatPacket — empty body (StreamCodec.unit(INSTANCE)); one shape. */
public class ClientboundResetChatPacketShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundResetChatPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.list(ClientboundResetChatPacket.INSTANCE);
    }
}
