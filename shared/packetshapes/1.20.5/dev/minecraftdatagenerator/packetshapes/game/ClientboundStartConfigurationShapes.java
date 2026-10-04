package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;

import java.util.List;

/** ClientboundStartConfigurationPacket — empty body (StreamCodec.unit(INSTANCE)); one shape. */
public class ClientboundStartConfigurationShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundStartConfigurationPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.list(ClientboundStartConfigurationPacket.INSTANCE);
    }
}
