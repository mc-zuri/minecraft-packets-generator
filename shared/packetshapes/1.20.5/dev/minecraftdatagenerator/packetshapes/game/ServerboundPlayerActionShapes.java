package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundPlayerActionPacket(Action action[VarInt enum ordinal], BlockPos pos[packed i64],
 * Direction direction[u8 get3DDataValue], int sequence[VarInt]).
 * <p>
 * {@code Action} (9 constants) and {@code Direction} (6 constants) are categorical, so they are fully
 * crossed with each other and with the domain-correct BlockPos spread: 9 x 10 x 6 = 540 shapes at a
 * zero baseline sequence. Folding the VarInt {@code sequence} into that cross would be 7020 shapes, so
 * it is demoted to a per-field sweep over every VarInt byte-length boundary, appended on top.
 */
public class ServerboundPlayerActionShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPlayerActionPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ServerboundPlayerActionPacket(
                        (ServerboundPlayerActionPacket.Action) a[0], (BlockPos) a[1], (Direction) a[2], 0),
                ctx.enumAll(ServerboundPlayerActionPacket.Action.class),
                ctx.blockPos(),
                ctx.enumAll(Direction.class)));

        // sequence sweep over every VarInt byte-length boundary, at a fixed baseline.
        for (int seq : ctx.varInts()) {
            out.add(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    BlockPos.ZERO, Direction.DOWN, seq));
        }
        return out;
    }
}
