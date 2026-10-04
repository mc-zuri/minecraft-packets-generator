package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundTeleportEntityPacket(Entity entity) — 1.21's only public constructor reads every field
 * off a live entity: {@code id:varint}, {@code x}/{@code y}/{@code z} f64 from
 * {@code entity.trackingPosition()}, {@code yRot}/{@code xRot} as packed byte angles
 * ({@code (byte)(int)(deg * 256 / 360)}) and {@code onGround} as a bool.
 * <p>
 * 1.21.2 replaced this with an explicit {@code (int id, PositionMoveRotation, Set&lt;Relative&gt;,
 * boolean)} constructor; here the only way to reach a given wire shape is to put the sample entity
 * into that state first, so every shape is produced by mutating {@link ShapeCtx#sampleEntity} and
 * snapshotting it. The entity's original transform is restored afterwards because the same instance
 * is shared with the other providers.
 * <p>
 * Coverage: the cartesian of a position spread x a rotation spread x both {@code onGround} values,
 * then per-field sweeps — x/z over the world-border horizontals, y over the legal world Y range,
 * each coordinate over the f64 corner cases, and the two rotations over degree values chosen to hit
 * every corner of the packed angle byte (0, 64, 127, -128, -64, -1) as well as the cardinal degrees.
 * The entity id is fixed by the sample entity, so it cannot be swept.
 */
public class ClientboundTeleportEntityShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundTeleportEntityPacket.class;
    }

    private record Rot(float yRot, float xRot) {
    }

    /** Degrees whose packed byte hits each corner of the signed-byte angle range. */
    private static List<Float> angleDegrees() {
        List<Float> out = new ArrayList<>();
        for (byte b : List.of((byte) 0, (byte) 64, (byte) 127, (byte) -128, (byte) -64, (byte) -1)) {
            out.add(b * 360.0f / 256.0f);
        }
        return out;
    }

    private static Packet<?> snapshot(Entity e, Vec3 pos, Rot rot, boolean onGround) {
        e.setPos(pos.x, pos.y, pos.z);
        e.setYRot(rot.yRot());
        e.setXRot(rot.xRot());
        e.setOnGround(onGround);
        return new ClientboundTeleportEntityPacket(e);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Entity e = ctx.sampleEntity;
        if (e == null) return List.of();

        Vec3 originalPos = e.position();
        float originalYRot = e.getYRot();
        float originalXRot = e.getXRot();
        boolean originalOnGround = e.onGround();

        List<Packet<?>> out = new ArrayList<>();
        try {
            List<Vec3> positions = List.of(
                    Vec3.ZERO,
                    new Vec3(1.5, -64.0, 2.5),
                    new Vec3(-30000000, 319, 30000000));
            List<Rot> rotations = List.of(
                    new Rot(0f, 0f),
                    new Rot(90f, -45f),
                    new Rot(180f, 90f),
                    new Rot(359.9f, -90f));

            // 3 positions x 4 rotations x 2 onGround = 24 shapes
            for (Vec3 pos : positions) {
                for (Rot rot : rotations) {
                    for (boolean onGround : ctx.bools()) {
                        out.add(snapshot(e, pos, rot, onGround));
                    }
                }
            }

            Rot flat = new Rot(0f, 0f);
            // position sweeps
            for (int h : ctx.horizontals()) {
                out.add(snapshot(e, new Vec3(h, 64, 0), flat, false));
                out.add(snapshot(e, new Vec3(0, 64, h), flat, false));
            }
            for (int y : ctx.worldYs()) {
                out.add(snapshot(e, new Vec3(0, y, 0), flat, false));
            }
            for (double d : ctx.doubles()) {
                out.add(snapshot(e, new Vec3(d, 64, 0), flat, false));
                out.add(snapshot(e, new Vec3(0, d, 0), flat, false));
                out.add(snapshot(e, new Vec3(0, 64, d), flat, false));
            }
            // rotation sweeps: every packed-angle-byte corner, then the cardinal degrees
            for (float deg : angleDegrees()) {
                out.add(snapshot(e, Vec3.ZERO, new Rot(deg, 0f), false));
                out.add(snapshot(e, Vec3.ZERO, new Rot(0f, deg), false));
            }
            for (float yRot : ctx.rotations()) {
                out.add(snapshot(e, Vec3.ZERO, new Rot(yRot, 0f), false));
            }
            for (float xRot : ctx.pitches()) {
                out.add(snapshot(e, Vec3.ZERO, new Rot(0f, xRot), false));
            }
        } finally {
            // the sample entity is shared with every other provider - put it back as it was
            e.setPos(originalPos.x, originalPos.y, originalPos.z);
            e.setYRot(originalYRot);
            e.setXRot(originalXRot);
            e.setOnGround(originalOnGround);
        }
        return out;
    }
}
