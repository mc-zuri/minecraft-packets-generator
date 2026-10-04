package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;

import java.util.List;

/** ServerboundPaddleBoatPacket(boolean left, boolean right) — two booleans; full cross (4 shapes). */
public class ServerboundPaddleBoatShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPaddleBoatPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundPaddleBoatPacket((boolean) a[0], (boolean) a[1]),
                ctx.bools(), ctx.bools());
    }
}
