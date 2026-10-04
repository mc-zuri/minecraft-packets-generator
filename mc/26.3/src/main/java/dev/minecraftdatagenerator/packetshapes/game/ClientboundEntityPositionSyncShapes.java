package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundEntityPositionSyncPacket(int id[varint], PositionPath position, float yRot, float xRot,
 * boolean onGround).
 * <p>
 * 26.3 replaced the old {@code PositionMoveRotation} payload with {@link PositionPath}, a sealed type
 * dispatched on a {@code PositionPath.Type} id var-int: {@code LINEAR(0)} carries a single Vec3
 * (3&times;f64) end position, {@code STEPPED(1)} carries a var-int-prefixed list of
 * {@code PositionStep(Vec3 position, varint tickOffset)}.
 * <p>
 * Covered: both dispatch branches (LINEAR and STEPPED, the latter with 1, 2 and 3 steps and var-int
 * tickOffset boundaries) crossed with both {@code onGround} states, plus a var-int boundary sweep on
 * {@code id} and degree sweeps on {@code yRot} (rotations) and {@code xRot} (pitches) with raw float
 * edges appended.
 * <p>
 * OMITTED: a zero-length STEPPED list. {@code PositionPath.Stepped(List)} derives its end position from
 * {@code steps.getLast()} and the client's decoder does the same, so an empty list throws on decode.
 */
public class ClientboundEntityPositionSyncShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundEntityPositionSyncPacket.class;
    }

    private static List<PositionPath> paths() {
        return List.of(
                // LINEAR
                PositionPath.of(new Vec3(0, 0, 0)),
                PositionPath.of(new Vec3(1.5, -64.0, 2.5)),
                PositionPath.of(new Vec3(-3.0E7, 319.0, 3.0E7)),
                PositionPath.of(new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 3.5)),
                // STEPPED: 1, 2 and 3 steps, with var-int tickOffset boundaries
                PositionPath.stepped(List.of(new PositionStep(new Vec3(0, 0, 0), 0))),
                PositionPath.stepped(List.of(
                        new PositionStep(new Vec3(1.5, -64.0, 2.5), 127),
                        new PositionStep(new Vec3(-3.0E7, 319.0, 3.0E7), 128))),
                PositionPath.stepped(List.of(
                        new PositionStep(new Vec3(0, 0, 0), 16384),
                        new PositionStep(new Vec3(1, 2031, -1), Integer.MAX_VALUE),
                        new PositionStep(new Vec3(-1, -2032, 1), -1))));
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundEntityPositionSyncPacket(
                        (int) a[0], (PositionPath) a[1], 0f, 0f, (boolean) a[2]),
                ctx.intsSmall(), paths(), ctx.bools()));

        PositionPath base = PositionPath.of(new Vec3(1, 2, 3));

        // id: var-int byte-length boundaries
        for (int id : ctx.varInts()) {
            out.add(new ClientboundEntityPositionSyncPacket(id, base, 0f, 0f, false));
        }
        // yRot in degrees (cardinals + wrap), then raw float edges
        for (float f : ctx.rotations()) {
            out.add(new ClientboundEntityPositionSyncPacket(1, base, f, 0f, false));
        }
        for (float f : ctx.floats()) {
            out.add(new ClientboundEntityPositionSyncPacket(1, base, f, 0f, false));
        }
        // xRot: legal pitch range, then raw float edges
        for (float f : ctx.pitches()) {
            out.add(new ClientboundEntityPositionSyncPacket(1, base, 0f, f, false));
        }
        for (float f : ctx.floats()) {
            out.add(new ClientboundEntityPositionSyncPacket(1, base, 0f, f, false));
        }
        return out;
    }
}
