package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundMoveVehiclePacket(Vec3 position, float yRot, float xRot).
 * <p>
 * Wire form in 26.2 is {@code Vec3.STREAM_CODEC position (3xf64), f32 yRot, f32 xRot} — the three
 * fields are written directly; the single {@code PositionAndRotation} wrapper is a 26.3 refactor and
 * does not exist here (the bytes are identical either way).
 * <p>
 * Covered: the full cross of a domain-correct position spread (world-border X/Z, the full legal Y
 * range, fractional offsets and the f64 extremes) x {@code rotations()} x {@code pitches()}
 * (7 x 7 x 5 = 245), plus per-axis f64 corner sweeps (18), {@code horizontals()} on X and Z (10),
 * {@code worldYs()} on Y (9) and raw f32 corner sweeps of both rotation fields (12). Nothing here is
 * clamped on the wire, so the IEEE extremes are legal. Total 294.
 */
public class ClientboundMoveVehicleShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundMoveVehiclePacket.class;
    }

    private static ClientboundMoveVehiclePacket mk(Vec3 pos, float yRot, float xRot) {
        return new ClientboundMoveVehiclePacket(pos, yRot, xRot);
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
                new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 1));

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> mk((Vec3) a[0], (float) a[1], (float) a[2]),
                positions, ctx.rotations(), ctx.pitches()));
        // Per-axis f64 corner sweep.
        for (double d : ctx.doubles()) {
            out.add(mk(new Vec3(d, 0, 0), 0f, 0f));
            out.add(mk(new Vec3(0, d, 0), 0f, 0f));
            out.add(mk(new Vec3(0, 0, d), 0f, 0f));
        }
        // X/Z over the world border, Y over the full legal vertical range.
        for (int h : ctx.horizontals()) {
            out.add(mk(new Vec3(h, 64, 0), 0f, 0f));
            out.add(mk(new Vec3(0, 64, h), 0f, 0f));
        }
        for (int y : ctx.worldYs()) {
            out.add(mk(new Vec3(0, y, 0), 0f, 0f));
        }
        // Raw f32 corner sweep of each rotation field.
        for (float f : ctx.floats()) {
            out.add(mk(Vec3.ZERO, f, 0f));
            out.add(mk(Vec3.ZERO, 0f, f));
        }
        return out;
    }
}
