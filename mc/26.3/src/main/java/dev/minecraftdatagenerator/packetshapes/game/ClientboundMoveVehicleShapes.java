package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundMoveVehiclePacket(PositionAndRotation movingTo) — 26.3 folded the former
 * {@code (Vec3 position, float yRot, float xRot)} fields into a single {@link PositionAndRotation},
 * whose stream codec is still exactly {@code 3xf64 position, f32 yRot, f32 xRot}.
 * <p>
 * Full cross of a domain-correct position spread (world-border X/Z, the full legal Y range) with the
 * yaw-in-degrees and pitch-in-degrees spreads, plus per-axis f64 corner sweeps and raw f32 corner
 * sweeps of both rotations (nothing here is clamped on the wire, so the IEEE extremes are legal).
 */
public class ClientboundMoveVehicleShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundMoveVehiclePacket.class;
    }

    private static ClientboundMoveVehiclePacket mk(Vec3 pos, float yRot, float xRot) {
        return new ClientboundMoveVehiclePacket(PositionAndRotation.of(pos, yRot, xRot));
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Vec3> positions = List.of(
                new Vec3(0, 0, 0),
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
        // Raw f32 corner sweep of each rotation field.
        for (float f : ctx.floats()) {
            out.add(mk(Vec3.ZERO, f, 0f));
            out.add(mk(Vec3.ZERO, 0f, f));
        }
        return out;
    }
}
