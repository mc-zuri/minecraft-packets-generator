package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;

import java.util.List;

/**
 * ServerboundJigsawGeneratePacket(BlockPos pos[packed i64], int levels[VarInt], boolean keepJigsaws).
 * <p>
 * Full cartesian of the domain-correct BlockPos spread x every VarInt byte-length boundary for
 * {@code levels} x both booleans (10x13x2 = 260).
 */
public class ServerboundJigsawGenerateShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundJigsawGeneratePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundJigsawGeneratePacket((BlockPos) a[0], (int) a[1], (boolean) a[2]),
                ctx.blockPos(), ctx.varInts(), ctx.bools());
    }
}
