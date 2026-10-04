package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;

import java.util.List;

/** ServerboundPunchPacket — empty body (StreamCodec.unit(INSTANCE)); one shape. */
public class ServerboundPunchShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPunchPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.list(ServerboundPunchPacket.INSTANCE);
    }
}
