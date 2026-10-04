package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;

import java.util.List;

/** ServerboundClientTickEndPacket — empty record (StreamCodec.unit(INSTANCE)); one shape. */
public class ServerboundClientTickEndShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundClientTickEndPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.list(ServerboundClientTickEndPacket.INSTANCE);
    }
}
