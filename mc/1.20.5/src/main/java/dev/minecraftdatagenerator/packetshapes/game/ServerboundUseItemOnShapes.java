package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundUseItemOnPacket(InteractionHand hand, BlockHitResult hitResult, int sequence).
 * <p>
 * Wire: {@code idMapper(InteractionHand)} (varint 0/1), then {@code BlockHitResult.STREAM_CODEC} =
 * {@code BlockPos}, {@code idMapper(Direction)} (varint 0..5), three floats (the click location
 * relative to the block corner), {@code bool(inside)}, then {@code varInt(sequence)}. 1.20.5's
 * {@code BlockHitResult} has no {@code worldBorderHit} flag - that trailing bool was added in 1.21.2 -
 * so there is no such dimension here.
 * <p>
 * Full categorical cartesian: hand (2) x direction (6) x inside (2) x every
 * domain-correct block position (10) = 240 shapes. Then per-field sweeps: each of the three click
 * offsets over float corner cases, and the sequence over the VarInt byte-length boundaries.
 */
public class ServerboundUseItemOnShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundUseItemOnPacket.class;
    }

    /** The codec transmits {@code location - blockPos} as a float, so build the location from the offsets. */
    private static BlockHitResult hit(BlockPos pos, Direction dir, boolean inside,
                                      float ox, float oy, float oz) {
        Vec3 loc = new Vec3(pos.getX() + (double) ox, pos.getY() + (double) oy, pos.getZ() + (double) oz);
        return new BlockHitResult(loc, dir, pos, inside);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        BlockPos basePos = new BlockPos(0, 0, 0);

        out.addAll(ctx.cross(a -> new ServerboundUseItemOnPacket((InteractionHand) a[0],
                        hit((BlockPos) a[3], (Direction) a[1], (boolean) a[2], 0.5f, 0.5f, 0.5f),
                        0),
                ctx.enumAll(InteractionHand.class), ctx.enumAll(Direction.class),
                ctx.bools(), ctx.blockPos()));

        // click-offset sweep, one axis at a time (basePos keeps the float exactly recoverable)
        List<Float> offsets = List.of(0f, 1f, 0.5f, -1f, 3.5f);
        for (float o : offsets) {
            out.add(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                    hit(basePos, Direction.UP, false, o, 0f, 0f), 0));
            out.add(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                    hit(basePos, Direction.UP, false, 0f, o, 0f), 0));
            out.add(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                    hit(basePos, Direction.UP, false, 0f, 0f, o), 0));
        }
        // sequence sweep over the VarInt byte-length boundaries
        for (int sequence : ctx.varInts()) {
            out.add(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                    hit(basePos, Direction.UP, false, 0.5f, 0.5f, 0.5f), sequence));
        }
        return out;
    }
}
