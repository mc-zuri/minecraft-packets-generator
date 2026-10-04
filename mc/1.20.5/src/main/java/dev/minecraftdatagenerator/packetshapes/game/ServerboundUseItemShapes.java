package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;

import java.util.List;

/**
 * ServerboundUseItemPacket(InteractionHand hand, int sequence).
 * <p>
 * Wire: {@code idMapper(InteractionHand)} (varint 0/1) then {@code varInt(sequence)}. The trailing
 * {@code yRot}/{@code xRot} floats were added in 1.21.2, so 1.20.5 has only the two fields.
 * <p>
 * Full cartesian over the categorical hand x the VarInt byte-length boundaries for the sequence.
 */
public class ServerboundUseItemShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundUseItemPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundUseItemPacket((InteractionHand) a[0], (int) a[1]),
                ctx.enumAll(InteractionHand.class), ctx.varInts());
    }
}
