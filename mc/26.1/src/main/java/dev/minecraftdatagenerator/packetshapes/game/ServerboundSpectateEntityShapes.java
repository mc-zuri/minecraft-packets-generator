package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSpectateEntityPacket;

import java.util.List;

/**
 * ServerboundSpectateEntityPacket(int entityId) — single varInt field; all int edge cases.
 */
public class ServerboundSpectateEntityShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSpectateEntityPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.ints(), ServerboundSpectateEntityPacket::new);
    }
}
