package dev.minecraftdatagenerator.packetshapes.login;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket;

import java.util.List;

/** ServerboundLoginAcknowledgedPacket — empty body (StreamCodec.unit(INSTANCE)); one shape. */
public class ServerboundLoginAcknowledgedShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundLoginAcknowledgedPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.list(ServerboundLoginAcknowledgedPacket.INSTANCE);
    }
}
