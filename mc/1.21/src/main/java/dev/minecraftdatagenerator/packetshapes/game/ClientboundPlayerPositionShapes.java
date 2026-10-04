package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.RelativeMovement;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * ClientboundPlayerPositionPacket(double x, double y, double z, float yRot, float xRot,
 * Set&lt;RelativeMovement&gt; relativeArguments, int id).
 * <p>
 * Wire: {@code x}/{@code y}/{@code z} f64, {@code yRot} f32, {@code xRot} f32, then
 * {@code RelativeMovement.pack(...)} as a single <b>unsigned byte</b> bit mask over the five
 * {@link RelativeMovement} constants (X=0, Y=1, Z=2, Y_ROT=3, X_ROT=4), then {@code id} as a varint.
 * <p>
 * 1.21.2 restructured this into {@code (int id, PositionMoveRotation change, Set&lt;Relative&gt;)} with a
 * delta-movement vector and a nine-bit i32 mask; 1.21 has no delta vector and only the five bits, so
 * the relative dimension is smaller and there is no {@code deltaMovement} sweep.
 * <p>
 * Coverage: the bit mask gets the empty set, every single bit on its own and the full set, crossed
 * with a position/rotation spread and two teleport ids; then per-field sweeps of x/z over the
 * world-border horizontals, y over the legal world Y range, each coordinate over the f64 corner
 * cases, yRot over the rotation degrees, xRot over the legal pitch range, and {@code id} over the
 * VarInt byte-length boundaries.
 */
public class ClientboundPlayerPositionShapes implements PacketShapes {
    private record Pos(double x, double y, double z, float yRot, float xRot) {
    }

    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlayerPositionPacket.class;
    }

    private static Packet<?> p(Pos pos, Set<RelativeMovement> rel, int id) {
        return new ClientboundPlayerPositionPacket(
                pos.x(), pos.y(), pos.z(), pos.yRot(), pos.xRot(), rel, id);
    }

    /** none, every single bit, all bits — the rule for a packed bit field. */
    private static List<Set<RelativeMovement>> relativeSubsets() {
        List<Set<RelativeMovement>> out = new ArrayList<>();
        out.add(Set.of());
        for (RelativeMovement r : RelativeMovement.values()) out.add(EnumSet.of(r));
        out.add(RelativeMovement.ALL);
        return out;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Pos base = new Pos(0, 64, 0, 0f, 0f);
        List<Pos> positions = List.of(
                base,
                new Pos(1.5, -64.0, 2.5, 90f, -45f),
                new Pos(-30000000, 319, 30000000, 180f, 90f));
        List<Set<RelativeMovement>> relatives = relativeSubsets();
        Set<RelativeMovement> none = Set.of();

        // 2 ids x 3 positions x 7 relative subsets = 42 shapes
        List<Packet<?>> out = new ArrayList<>();
        for (int id : List.of(0, 1)) {
            for (Pos pos : positions) {
                for (Set<RelativeMovement> rel : relatives) {
                    out.add(p(pos, rel, id));
                }
            }
        }

        // position sweeps
        for (int h : ctx.horizontals()) {
            out.add(p(new Pos(h, 64, 0, 0f, 0f), none, 0));
            out.add(p(new Pos(0, 64, h, 0f, 0f), none, 0));
        }
        for (int y : ctx.worldYs()) {
            out.add(p(new Pos(0, y, 0, 0f, 0f), none, 0));
        }
        for (double d : ctx.doubles()) {
            out.add(p(new Pos(d, 64, 0, 0f, 0f), none, 0));
            out.add(p(new Pos(0, d, 0, 0f, 0f), none, 0));
            out.add(p(new Pos(0, 64, d, 0f, 0f), none, 0));
        }
        // rotation sweeps
        for (float yRot : ctx.rotations()) {
            out.add(p(new Pos(0, 64, 0, yRot, 0f), none, 0));
        }
        for (float xRot : ctx.pitches()) {
            out.add(p(new Pos(0, 64, 0, 0f, xRot), none, 0));
        }
        // teleport id: VarInt byte-length boundaries
        for (int id : ctx.varInts()) {
            out.add(p(base, RelativeMovement.ALL, id));
        }
        return out;
    }
}
