package dev.minecraftdatagenerator.packetshapes.status;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;

import java.util.List;

/** ServerboundStatusRequestPacket — empty body (StreamCodec.unit(INSTANCE)); one shape. */
public class ServerboundStatusRequestShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundStatusRequestPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.list(ServerboundStatusRequestPacket.INSTANCE);
    }
}
