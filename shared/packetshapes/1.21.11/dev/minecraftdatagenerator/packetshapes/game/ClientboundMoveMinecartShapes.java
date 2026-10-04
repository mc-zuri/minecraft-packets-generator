package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundMoveMinecartPacket(int entityId[varint], List&lt;MinecartStep&gt; lerpSteps) — a varint id
 * plus a length-prefixed list of
 * {@code MinecartStep(Vec3 position[3xf64], Vec3 movement[3xf64], f32 yRot[ROTATION_BYTE],
 * f32 xRot[ROTATION_BYTE], f32 weight)}.
 * <p>
 * Covers list cardinality 0/1/2/3, a VarInt boundary sweep of the entity id, and a per-field sweep of
 * every MinecartStep component: both Vec3s per axis over the f64 corner cases and the domain-correct
 * world coordinates, both rotations over the degree spreads, and the weight over the f32 corners.
 * <p>
 * NOTE: yRot/xRot go through ByteBufCodecs.ROTATION_BYTE (degrees packed into one byte), so the wire
 * value is quantized; every float is still a legal encode, and the degree spreads are used so the
 * quantization steps land on distinguishable bytes.
 */
public class ClientboundMoveMinecartShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundMoveMinecartPacket.class;
    }

    private static NewMinecartBehavior.MinecartStep step(Vec3 pos, Vec3 movement, float yRot, float xRot, float weight) {
        return new NewMinecartBehavior.MinecartStep(pos, movement, yRot, xRot, weight);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        NewMinecartBehavior.MinecartStep zero = NewMinecartBehavior.MinecartStep.ZERO;
        NewMinecartBehavior.MinecartStep a = step(new Vec3(1, -64, 2), new Vec3(0.5, 0, -0.5), 90.0f, 0.0f, 1.0f);
        NewMinecartBehavior.MinecartStep b = step(new Vec3(30000000, 319, -30000000), new Vec3(-1, 1, -1), 180.0f, 45.0f, 0.25f);

        // Collection cardinality 0/1/2/3 crossed with the entity-id VarInt boundaries.
        List<List<NewMinecartBehavior.MinecartStep>> stepLists = List.of(
                List.<NewMinecartBehavior.MinecartStep>of(),
                List.of(zero),
                List.of(a, b),
                List.of(zero, a, b));
        for (int id : ctx.varInts()) {
            for (List<NewMinecartBehavior.MinecartStep> steps : stepLists) {
                out.add(new ClientboundMoveMinecartPacket(id, steps));
            }
        }

        // Per-field sweeps of a single step.
        List<NewMinecartBehavior.MinecartStep> variants = new ArrayList<>();
        for (double d : ctx.doubles()) {
            variants.add(step(new Vec3(d, 0, 0), Vec3.ZERO, 0f, 0f, 0f));
            variants.add(step(new Vec3(0, d, 0), Vec3.ZERO, 0f, 0f, 0f));
            variants.add(step(new Vec3(0, 0, d), Vec3.ZERO, 0f, 0f, 0f));
            variants.add(step(Vec3.ZERO, new Vec3(d, 0, 0), 0f, 0f, 0f));
            variants.add(step(Vec3.ZERO, new Vec3(0, d, 0), 0f, 0f, 0f));
            variants.add(step(Vec3.ZERO, new Vec3(0, 0, d), 0f, 0f, 0f));
        }
        for (int y : ctx.worldYs()) {
            variants.add(step(new Vec3(0, y, 0), Vec3.ZERO, 0f, 0f, 0f));
        }
        for (int h : ctx.horizontals()) {
            variants.add(step(new Vec3(h, 0, h), Vec3.ZERO, 0f, 0f, 0f));
        }
        for (float yRot : ctx.rotations()) {
            variants.add(step(Vec3.ZERO, Vec3.ZERO, yRot, 0f, 0f));
        }
        for (float xRot : ctx.pitches()) {
            variants.add(step(Vec3.ZERO, Vec3.ZERO, 0f, xRot, 0f));
        }
        for (float w : ctx.floats()) {
            variants.add(step(Vec3.ZERO, Vec3.ZERO, 0f, 0f, w));
        }
        for (NewMinecartBehavior.MinecartStep s : variants) {
            out.add(new ClientboundMoveMinecartPacket(1, List.of(s)));
        }
        return out;
    }
}
