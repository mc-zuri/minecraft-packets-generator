package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * ClientboundTeleportEntityPacket(int id [varint], PositionMoveRotation change,
 * Set&lt;Relative&gt; relatives [i32 bitmask], boolean onGround [bool]).
 * <p>
 * {@code PositionMoveRotation} is {@code (Vec3 position [3x f64], Vec3 deltaMovement [3x f64],
 * float yRot [f32], float xRot [f32])}. {@code Relative.SET_STREAM_CODEC} packs the set into a plain
 * i32 bitmask, one bit per constant (X=0 .. ROTATE_DELTA=8), so every subset is encodable.
 * <p>
 * Coverage:
 * <ul>
 *   <li>cartesian: small entity ids x a PositionMoveRotation spread x the bit-flag spread
 *       (empty, each single bit, all bits) x both onGround values;</li>
 *   <li>sweeps: entity id over the VarInt byte-length boundaries, position x/z over the world-border
 *       horizontals, position y over the legal world Y range, deltaMovement over the double edges,
 *       yRot over the rotation degrees and xRot over the legal pitch range.</li>
 * </ul>
 */
public class ClientboundTeleportEntityShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundTeleportEntityPacket.class;
    }

    private static List<Set<Relative>> relativeSubsets() {
        List<Set<Relative>> out = new ArrayList<>();
        out.add(Set.of());                                   // bitmask 0
        for (Relative r : Relative.values()) out.add(EnumSet.of(r));  // each single bit
        out.add(Relative.ALL);                               // every bit
        return out;
    }

    private static PositionMoveRotation pmr(Vec3 pos, Vec3 delta, float yRot, float xRot) {
        return new PositionMoveRotation(pos, delta, yRot, xRot);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<PositionMoveRotation> changes = List.of(
                pmr(Vec3.ZERO, Vec3.ZERO, 0f, 0f),
                pmr(new Vec3(1.5, -64.0, 2.5), new Vec3(-1, 1, -1), 90f, -45f),
                pmr(new Vec3(-30000000, 319, 30000000), new Vec3(3.5, 3.5, 3.5), 180f, 90f));
        List<Set<Relative>> relatives = relativeSubsets();

        // 3 ids x 3 changes x 11 relative subsets x 2 onGround = 198 shapes
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundTeleportEntityPacket(
                        (int) a[0], (PositionMoveRotation) a[1], castRel(a[2]), (boolean) a[3]),
                ctx.intsSmall(), changes, relatives, ctx.bools()));

        PositionMoveRotation base = changes.get(0);
        Set<Relative> none = Set.of();

        // entity id sweep
        for (int id : ctx.varInts()) {
            out.add(new ClientboundTeleportEntityPacket(id, base, none, false));
        }
        // position sweeps
        for (int h : ctx.horizontals()) {
            out.add(new ClientboundTeleportEntityPacket(0,
                    pmr(new Vec3(h, 0, 0), Vec3.ZERO, 0f, 0f), none, false));
            out.add(new ClientboundTeleportEntityPacket(0,
                    pmr(new Vec3(0, 0, h), Vec3.ZERO, 0f, 0f), none, false));
        }
        for (int y : ctx.worldYs()) {
            out.add(new ClientboundTeleportEntityPacket(0,
                    pmr(new Vec3(0, y, 0), Vec3.ZERO, 0f, 0f), none, false));
        }
        // deltaMovement sweep
        for (double d : ctx.doubles()) {
            out.add(new ClientboundTeleportEntityPacket(0,
                    pmr(Vec3.ZERO, new Vec3(d, 0, 0), 0f, 0f), none, false));
            out.add(new ClientboundTeleportEntityPacket(0,
                    pmr(Vec3.ZERO, new Vec3(0, d, 0), 0f, 0f), none, false));
            out.add(new ClientboundTeleportEntityPacket(0,
                    pmr(Vec3.ZERO, new Vec3(0, 0, d), 0f, 0f), none, false));
        }
        // rotation sweeps
        for (float yRot : ctx.rotations()) {
            out.add(new ClientboundTeleportEntityPacket(0,
                    pmr(Vec3.ZERO, Vec3.ZERO, yRot, 0f), none, false));
        }
        for (float xRot : ctx.pitches()) {
            out.add(new ClientboundTeleportEntityPacket(0,
                    pmr(Vec3.ZERO, Vec3.ZERO, 0f, xRot), none, false));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Set<Relative> castRel(Object o) {
        return (Set<Relative>) o;
    }
}
