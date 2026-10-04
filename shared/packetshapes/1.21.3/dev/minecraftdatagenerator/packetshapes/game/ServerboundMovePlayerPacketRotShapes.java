package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundMovePlayerPacket.Rot(float yRot, float xRot, boolean onGround,
 * boolean horizontalCollision) — wire body is {@code f32 yRot, f32 xRot, u8 flags}, where the flags
 * byte packs bit 0 = onGround and bit 1 = horizontalCollision.
 * <p>
 * Full cartesian of every yaw in {@code rotations()} plus the f32 extremes x every pitch in
 * {@code pitches()} plus 3.5 and the f32 extremes x both flag bits (9 x 8 x 2 x 2 = 288).
 */
public class ServerboundMovePlayerPacketRotShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundMovePlayerPacket.Rot.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Float> yRots = new ArrayList<>(ctx.rotations());
        yRots.add(Float.MAX_VALUE);
        yRots.add(-Float.MAX_VALUE);

        List<Float> xRots = new ArrayList<>(ctx.pitches());
        xRots.add(3.5f);
        xRots.add(Float.MAX_VALUE);
        xRots.add(-Float.MAX_VALUE);

        return ctx.cross(
                a -> new ServerboundMovePlayerPacket.Rot((float) a[0], (float) a[1],
                        (boolean) a[2], (boolean) a[3]),
                yRots, xRots, ctx.bools(), ctx.bools());
    }
}
