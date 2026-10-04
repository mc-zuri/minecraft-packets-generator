package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;

import java.util.List;

/**
 * ServerboundSetCommandMinecartPacket(int entity, String command, boolean trackOutput).
 * Wire: varInt(entity id), utf(command), bool(trackOutput).
 * <p>
 * Full cartesian: VarInt boundary spread for the entity id (13) x the UTF-8 string spread (6) x both
 * booleans = 156 shapes.
 */
public class ServerboundSetCommandMinecartShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSetCommandMinecartPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundSetCommandMinecartPacket((int) a[0], (String) a[1], (boolean) a[2]),
                ctx.varInts(), ctx.utf8(), ctx.bools());
    }
}
