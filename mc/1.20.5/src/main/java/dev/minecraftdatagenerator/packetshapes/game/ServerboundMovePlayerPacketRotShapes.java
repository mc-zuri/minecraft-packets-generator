package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundMovePlayerPacket.Rot(float yRot, float xRot, boolean onGround) — wire body is
 * {@code f32 yRot, f32 xRot, u8 onGround}.
 * <p>
 * 1.20.5 writes a single {@code u8} carrying only {@code onGround} (0 or 1); the
 * {@code horizontalCollision} bit was added to that byte in 1.21.2, so there is no second flag bit
 * to enumerate here.
 * <p>
 * Full cartesian of every yaw in {@code rotations()} plus the f32 extremes x every pitch in
 * {@code pitches()} plus 3.5 and the f32 extremes x both {@code onGround} values (9 x 8 x 2 = 144).
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
                a -> new ServerboundMovePlayerPacket.Rot((float) a[0], (float) a[1], (boolean) a[2]),
                yRots, xRots, ctx.bools());
    }
}
