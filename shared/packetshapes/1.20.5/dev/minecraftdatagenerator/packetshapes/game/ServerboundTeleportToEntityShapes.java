package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;

import java.util.List;

/**
 * ServerboundTeleportToEntityPacket(UUID uuid) — single UUID field; all UUID corner cases.
 */
public class ServerboundTeleportToEntityShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundTeleportToEntityPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.uuids(), ServerboundTeleportToEntityPacket::new);
    }
}
