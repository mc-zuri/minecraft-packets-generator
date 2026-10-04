package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;

import java.util.List;

/** ServerboundConfigurationAcknowledgedPacket — empty body (StreamCodec.unit(INSTANCE)); one shape. */
public class ServerboundConfigurationAcknowledgedShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundConfigurationAcknowledgedPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.list(ServerboundConfigurationAcknowledgedPacket.INSTANCE);
    }
}
