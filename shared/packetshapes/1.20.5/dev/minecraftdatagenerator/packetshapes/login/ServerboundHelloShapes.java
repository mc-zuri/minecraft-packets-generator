package dev.minecraftdatagenerator.packetshapes.login;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;

import java.util.List;
import java.util.UUID;

/**
 * ServerboundHelloPacket(String name, UUID profileId).
 * <p>
 * Wire: {@code writeUtf(name, 16)} (varint byte length then UTF-8, capped at 16 chars) and a raw
 * 16-byte UUID (two longs).
 * <p>
 * Full cartesian over the UTF-8 corner-case names — every one is within the 16-char cap, and they
 * exercise 1-, 2-, 3- and 4-byte code points against the byte-length prefix — x the UUID spread.
 */
public class ServerboundHelloShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundHelloPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundHelloPacket((String) a[0], (UUID) a[1]),
                ctx.utf8(), ctx.uuids());
    }
}
