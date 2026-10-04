package dev.minecraftdatagenerator.packetshapes.configuration;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ServerboundAcceptCodeOfConductPacket;

import java.util.List;

/** ServerboundAcceptCodeOfConductPacket() — empty body (StreamCodec.unit(INSTANCE)); one shape. */
public class ServerboundAcceptCodeOfConductPacketShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundAcceptCodeOfConductPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.list(ServerboundAcceptCodeOfConductPacket.INSTANCE);
    }
}
