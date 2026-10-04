package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundMoveVehiclePacket(PositionAndRotation movingTo, boolean onGround) — 26.3 folded the former
 * {@code (Vec3 position, float yRot, float xRot)} fields into a single {@link PositionAndRotation},
 * whose stream codec is {@code Vec3.STREAM_CODEC} (three plain f64, not the low-precision form)
 * followed by {@code f32 yRot, f32 xRot}; the packet then appends the onGround bool.
 * <p>
 * Full cartesian of a domain-correct position spread (build-range and packed-limit Y, world-border X/Z,
 * fractional offsets and the f64 extremes) x every yaw in {@code rotations()} plus the f32 extremes x
 * every pitch in {@code pitches()} plus 3.5 and the f32 extremes x both booleans
 * (8 x 9 x 8 x 2 = 1152).
 */
public class ServerboundMoveVehicleShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundMoveVehiclePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Vec3> positions = List.of(
                Vec3.ZERO,
                new Vec3(0.5, 64.5, -0.5),
                new Vec3(1, -64, 2),
                new Vec3(30000000, 319, 30000000),
                new Vec3(-30000000, -64, -30000000),
                new Vec3(0, -2032, 0),
                new Vec3(0, 2031, 0),
                new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 3.5));

        List<Float> yRots = new ArrayList<>(ctx.rotations());
        yRots.add(Float.MAX_VALUE);
        yRots.add(-Float.MAX_VALUE);

        List<Float> xRots = new ArrayList<>(ctx.pitches());
        xRots.add(3.5f);
        xRots.add(Float.MAX_VALUE);
        xRots.add(-Float.MAX_VALUE);

        return ctx.cross(
                a -> new ServerboundMoveVehiclePacket(
                        PositionAndRotation.of((Vec3) a[0], (float) a[1], (float) a[2]), (boolean) a[3]),
                positions, yRots, xRots, ctx.bools());
    }
}
