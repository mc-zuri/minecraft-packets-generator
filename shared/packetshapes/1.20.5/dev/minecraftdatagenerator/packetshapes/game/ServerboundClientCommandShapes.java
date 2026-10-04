package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;

import java.util.List;

/** ServerboundClientCommandPacket(Action action) — single enum field (writeEnum); all three actions. */
public class ServerboundClientCommandShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundClientCommandPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.enumAll(ServerboundClientCommandPacket.Action.class),
                ServerboundClientCommandPacket::new);
    }
}
