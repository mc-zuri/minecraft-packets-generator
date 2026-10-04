package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundUseItemPacket(InteractionHand hand, int sequence, float yRot, float xRot).
 * <p>
 * Wire: {@code idMapper(InteractionHand)} (varint 0/1), {@code varInt(sequence)}, {@code float(yRot)},
 * {@code float(xRot)}.
 * <p>
 * Full cartesian over the categorical hand x the domain-correct rotation spreads — {@code yRot} is a
 * yaw in degrees ({@link ShapeCtx#rotations()}) and {@code xRot} a pitch clamped to -90..90
 * ({@link ShapeCtx#pitches()}) — at a baseline sequence, then a sequence sweep over the VarInt
 * byte-length boundaries for both hands.
 */
public class ServerboundUseItemShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundUseItemPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ServerboundUseItemPacket((InteractionHand) a[0], 0, (float) a[1], (float) a[2]),
                ctx.enumAll(InteractionHand.class), ctx.rotations(), ctx.pitches()));
        for (InteractionHand hand : ctx.enumAll(InteractionHand.class)) {
            for (int sequence : ctx.varInts()) {
                out.add(new ServerboundUseItemPacket(hand, sequence, 0f, 0f));
            }
        }
        return out;
    }
}
