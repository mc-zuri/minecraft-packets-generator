package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ClientboundAddEntityPacket(int id, UUID uuid, double x, double y, double z, float xRot, float yRot,
 * EntityType&lt;?&gt; type, int data, Vec3 movement, double yHeadRot).
 * <p>
 * Wire body, in encode order: id:varint, uuid:16 bytes, type:registry-id varint, x:double, y:double,
 * z:double, movement:LpVec3, xRot:byte, yRot:byte, yHeadRot:byte, data:varint.
 * <p>
 * The three rotations reach the wire through {@code Mth.packDegrees} (a byte), so they are crossed in
 * full (the degree spread maps onto the packed bytes 0, 64, 127, -128, -64, -1). Everything else is a
 * per-field sweep over a neutral baseline, because 11 fields cannot be crossed inside the budget.
 * <p>
 * {@code movement} is an LpVec3, which has three distinguishable wire forms: a single zero byte when
 * the chessboard magnitude is below {@code ABS_MIN_VALUE}, six bytes when the ceil'd magnitude fits in
 * the two scale bits (1..3), and six bytes plus a varint continuation above that. All three are
 * covered, up to {@code ABS_MAX_VALUE}. NaN and out-of-range components are omitted: the encoder
 * silently sanitises/clamps them, so they are not distinguishable wire forms.
 */
public class ClientboundAddEntityPacketShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundAddEntityPacket.class;
    }

    private static ClientboundAddEntityPacket make(int id, UUID uuid, double x, double y, double z,
                                                   float xRot, float yRot, EntityType<?> type, int data,
                                                   Vec3 movement, double yHeadRot) {
        return new ClientboundAddEntityPacket(id, uuid, x, y, z, xRot, yRot, type, data, movement, yHeadRot);
    }

    /** Entity types spanning the registry-id varint width boundaries. */
    private static List<EntityType<?>> entityTypes(ShapeCtx ctx) {
        Registry<EntityType<?>> reg = ctx.registries.lookupOrThrow(Registries.ENTITY_TYPE);
        List<EntityType<?>> out = new ArrayList<>();
        for (int id : List.of(0, 1, 127, 128, reg.size() - 1)) {
            EntityType<?> t = reg.byId(id);
            if (t != null && !out.contains(t)) out.add(t);
        }
        if (out.isEmpty()) out.add(ctx.anyOf(Registries.ENTITY_TYPE));
        return out;
    }

    /** Degrees whose {@code packDegrees} images are the packed bytes 0, 64, 127, -128, -64, -1. */
    private static List<Float> degrees() {
        return List.of(0f, 90f, 179f, 180f, 270f, 359.9f);
    }

    /** One value per LpVec3 wire form, plus sign and magnitude extremes. */
    private static List<Vec3> movements() {
        return List.of(
                Vec3.ZERO,                                   // zero byte
                new Vec3(1.0E-6, 0.0, 0.0),                  // below ABS_MIN_VALUE -> zero byte
                new Vec3(0.5, -0.5, 1.0),                    // scale 1, inline
                new Vec3(-1.0, 1.0, -1.0),                   // scale 1, all components at the extremes
                new Vec3(3.0, -3.0, 2.0),                    // scale 3, the widest inline scale
                new Vec3(-2.5, 3.5, -4.0),                   // scale 4 -> varint continuation
                new Vec3(0.0, 1000.0, 0.0),                  // large scale continuation
                new Vec3(1.7179869183E10, -1.7179869183E10, 0.0)); // ABS_MAX_VALUE
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<EntityType<?>> types = entityTypes(ctx);
        EntityType<?> type = types.get(0);
        UUID u = new UUID(0L, 0L);
        Vec3 zero = Vec3.ZERO;
        List<Packet<?>> out = new ArrayList<>();

        // Baseline: everything at its neutral value.
        out.add(make(0, u, 0, 0, 0, 0f, 0f, type, 0, zero, 0));

        // Full cross of the three packed-degree bytes.
        for (float xRot : degrees())
            for (float yRot : degrees())
                for (float yHeadRot : degrees())
                    out.add(make(0, u, 0, 0, 0, xRot, yRot, type, 0, zero, yHeadRot));

        // Per-field sweeps.
        for (int id : ctx.varInts()) out.add(make(id, u, 0, 0, 0, 0f, 0f, type, 0, zero, 0));
        for (UUID uuid : ctx.uuids()) out.add(make(0, uuid, 0, 0, 0, 0f, 0f, type, 0, zero, 0));
        for (EntityType<?> t : types) out.add(make(0, u, 0, 0, 0, 0f, 0f, t, 0, zero, 0));
        for (int data : ctx.varInts()) out.add(make(0, u, 0, 0, 0, 0f, 0f, type, data, zero, 0));
        for (Vec3 mv : movements()) out.add(make(0, u, 0, 0, 0, 0f, 0f, type, 0, mv, 0));

        // x/z are plain doubles on the wire, so sweep both the IEEE corner cases and the world border.
        for (double v : ctx.doubles()) out.add(make(0, u, v, 0, 0, 0f, 0f, type, 0, zero, 0));
        for (double v : ctx.doubles()) out.add(make(0, u, 0, 0, v, 0f, 0f, type, 0, zero, 0));
        for (int v : ctx.horizontals()) out.add(make(0, u, v + 0.5, 0, 0, 0f, 0f, type, 0, zero, 0));
        for (int v : ctx.horizontals()) out.add(make(0, u, 0, 0, v + 0.5, 0f, 0f, type, 0, zero, 0));
        // y likewise, plus the legal vertical range.
        for (double v : ctx.doubles()) out.add(make(0, u, 0, v, 0, 0f, 0f, type, 0, zero, 0));
        for (int v : ctx.worldYs()) out.add(make(0, u, 0, v + 0.5, 0, 0f, 0f, type, 0, zero, 0));

        return out;
    }
}
