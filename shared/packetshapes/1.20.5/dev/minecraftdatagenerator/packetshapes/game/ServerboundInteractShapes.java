package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundInteractPacket — the pre-26.1 <b>ActionType discriminator</b> form (26.1 flattened it to a
 * fixed record with a single LpVec3 location). Wire body:
 * {@code entityId:varint, action:enum varint (INTERACT / ATTACK / INTERACT_AT)}, then the per-branch
 * fields ({@code INTERACT}: {@code hand:enum varint}; {@code INTERACT_AT}: {@code x:f32, y:f32, z:f32,
 * hand:enum varint}; {@code ATTACK}: none), then {@code usingSecondaryAction:bool}.
 * <p>
 * The packet's own constructors are private; only the static factories are reachable and they read the
 * entity id off an {@link Entity}. The sample entity's id is therefore reassigned with
 * {@link Entity#setId(int)} before each shape is built — the factories capture {@code getId()} eagerly,
 * so every shape keeps the id it was built with.
 * <p>
 * Coverage:
 * <ul>
 *   <li>full cartesian of all three action branches x both {@link InteractionHand} constants x both
 *       {@code usingSecondaryAction} values (the hand is only present on the two interact branches);</li>
 *   <li>{@code entityId} over every VarInt byte-length boundary, on each of the three branches;</li>
 *   <li>the {@code INTERACT_AT} location swept one axis at a time over the f32 edge cases (the Vec3 is
 *       written as three plain floats here, not an LpVec3), plus mixed-sign and world-border vectors.</li>
 * </ul>
 * Note the decoder reads the location <i>before</i> the hand while the record's parameter order is the
 * reverse, so the byte order is taken from {@code InteractionAtLocationAction.write}.
 */
public class ServerboundInteractShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundInteractPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Entity e = ctx.sampleEntity;
        List<Packet<?>> out = new ArrayList<>();

        // ---- all three branches x both hands x both secondary flags ---------------------------
        e.setId(1);
        Vec3 baseLoc = new Vec3(1.5, -2.5, 3.5);
        for (boolean secondary : ctx.bools()) {
            out.add(ServerboundInteractPacket.createAttackPacket(e, secondary));
            for (InteractionHand hand : ctx.enumAll(InteractionHand.class)) {
                out.add(ServerboundInteractPacket.createInteractionPacket(e, secondary, hand));
                out.add(ServerboundInteractPacket.createInteractionPacket(e, secondary, hand, baseLoc));
            }
        }

        // ---- entityId over the VarInt byte-length boundaries, on every branch ------------------
        for (int id : ctx.varInts()) {
            e.setId(id);
            out.add(ServerboundInteractPacket.createAttackPacket(e, false));
            out.add(ServerboundInteractPacket.createInteractionPacket(e, false, InteractionHand.MAIN_HAND));
            out.add(ServerboundInteractPacket.createInteractionPacket(e, false, InteractionHand.MAIN_HAND, baseLoc));
        }

        // ---- INTERACT_AT location: one axis at a time over the f32 edge cases ------------------
        e.setId(1);
        for (float f : ctx.floats()) {
            out.add(ServerboundInteractPacket.createInteractionPacket(e, false, InteractionHand.MAIN_HAND,
                    new Vec3(f, 0, 0)));
            out.add(ServerboundInteractPacket.createInteractionPacket(e, false, InteractionHand.MAIN_HAND,
                    new Vec3(0, f, 0)));
            out.add(ServerboundInteractPacket.createInteractionPacket(e, false, InteractionHand.MAIN_HAND,
                    new Vec3(0, 0, f)));
        }
        for (Vec3 loc : List.of(Vec3.ZERO, new Vec3(-1.5, 2.5, -3.5),
                new Vec3(-30000000.0, 319.0, 30000000.0))) {
            out.add(ServerboundInteractPacket.createInteractionPacket(e, false, InteractionHand.MAIN_HAND, loc));
        }

        return out;
    }
}
