package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundEntityPositionSyncPacket(int id[varint], PositionMoveRotation values, boolean onGround).
 * <p>
 * Wire form in 26.2 is {@code varint id, <PositionMoveRotation>, bool onGround}, where
 * {@code PositionMoveRotation.STREAM_CODEC} is {@code Vec3 position (3xf64), Vec3 deltaMovement
 * (3xf64), f32 yRot, f32 xRot} — a flat, non-dispatched payload (no sealed path type here; that is a
 * 26.3 addition).
 * <p>
 * Covered: the full cartesian of a domain-correct position spread (world-border X/Z, the full legal Y
 * range, fractional offsets and the f64 extremes) x a delta-movement spread x every yaw in
 * {@code rotations()} x every pitch in {@code pitches()} x both {@code onGround} values
 * (8 x 4 x 7 x 5 x 2 = 2240). Appended per-field sweeps: a var-int boundary sweep on {@code id},
 * per-axis f64 corners for both vectors, {@code horizontals()} on position X/Z, {@code worldYs()} on
 * position Y, and raw f32 corners in each rotation slot. Nothing in this packet is clamped on the
 * wire, so the IEEE extremes are legal encodings.
 */
public class ClientboundEntityPositionSyncShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundEntityPositionSyncPacket.class;
    }

    private static ClientboundEntityPositionSyncPacket mk(int id, Vec3 pos, Vec3 delta,
                                                          float yRot, float xRot, boolean onGround) {
        return new ClientboundEntityPositionSyncPacket(id, new PositionMoveRotation(pos, delta, yRot, xRot), onGround);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Vec3> positions = List.of(
                Vec3.ZERO,
                new Vec3(0.5, 63.5, -0.5),
                new Vec3(1, -1, 3.5),
                new Vec3(30000000, 319, 30000000),
                new Vec3(-30000000, -64, -30000000),
                new Vec3(30000000, 2031, -30000000),
                new Vec3(-30000000, -2032, 30000000),
                new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 3.5));

        List<Vec3> deltas = List.of(
                Vec3.ZERO,
                new Vec3(0.1, -0.0784, 0.1),
                new Vec3(1, -1, 1),
                new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 3.5));

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> mk(1, (Vec3) a[0], (Vec3) a[1], (float) a[2], (float) a[3], (boolean) a[4]),
                positions, deltas, ctx.rotations(), ctx.pitches(), ctx.bools()));

        Vec3 basePos = new Vec3(1, 64, -1);

        // entity id: every VarInt byte-length boundary.
        for (int id : ctx.varInts()) {
            out.add(mk(id, basePos, Vec3.ZERO, 0f, 0f, false));
        }
        // per-axis f64 corners, in the position vector then in the delta vector.
        for (double d : ctx.doubles()) {
            out.add(mk(1, new Vec3(d, 0, 0), Vec3.ZERO, 0f, 0f, false));
            out.add(mk(1, new Vec3(0, d, 0), Vec3.ZERO, 0f, 0f, false));
            out.add(mk(1, new Vec3(0, 0, d), Vec3.ZERO, 0f, 0f, false));
            out.add(mk(1, basePos, new Vec3(d, 0, 0), 0f, 0f, false));
            out.add(mk(1, basePos, new Vec3(0, d, 0), 0f, 0f, false));
            out.add(mk(1, basePos, new Vec3(0, 0, d), 0f, 0f, false));
        }
        // position X/Z over the world border, position Y over the full legal vertical range.
        for (int h : ctx.horizontals()) {
            out.add(mk(1, new Vec3(h, 64, 0), Vec3.ZERO, 0f, 0f, false));
            out.add(mk(1, new Vec3(0, 64, h), Vec3.ZERO, 0f, 0f, false));
        }
        for (int y : ctx.worldYs()) {
            out.add(mk(1, new Vec3(0, y, 0), Vec3.ZERO, 0f, 0f, false));
        }
        // raw f32 corners in each rotation slot.
        for (float f : ctx.floats()) {
            out.add(mk(1, basePos, Vec3.ZERO, f, 0f, false));
            out.add(mk(1, basePos, Vec3.ZERO, 0f, f, false));
        }
        return out;
    }
}
