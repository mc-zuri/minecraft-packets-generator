package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundHurtAnimationPacket(int id[varint], float yaw) — {@code id} is an entity id written as a
 * var-int, {@code yaw} is the hurt direction in degrees written as an f32.
 * <p>
 * Full cross-product of the var-int byte-length boundaries against the degree spread, plus a raw float
 * edge sweep on {@code yaw}.
 */
public class ClientboundHurtAnimationShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundHurtAnimationPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundHurtAnimationPacket((int) a[0], (float) a[1]),
                ctx.varInts(), ctx.rotations()));
        for (float f : ctx.floats()) {
            out.add(new ClientboundHurtAnimationPacket(1, f));
        }
        return out;
    }
}
