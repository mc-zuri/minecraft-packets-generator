package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;

import java.util.List;

/**
 * ServerboundPickItemFromEntityPacket(int id[VarInt], boolean includeData).
 * <p>
 * Full cartesian of every VarInt byte-length boundary x both booleans (13x2 = 26).
 */
public class ServerboundPickItemFromEntityShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPickItemFromEntityPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundPickItemFromEntityPacket((int) a[0], (boolean) a[1]),
                ctx.varInts(), ctx.bools());
    }
}
