package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;

import java.util.List;

/**
 * ServerboundPickItemFromBlockPacket(BlockPos pos[packed i64], boolean includeData).
 * <p>
 * Full cartesian of the domain-correct BlockPos spread (Y sweep + world-border X/Z) x both booleans
 * (10x2 = 20).
 */
public class ServerboundPickItemFromBlockShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPickItemFromBlockPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundPickItemFromBlockPacket((BlockPos) a[0], (boolean) a[1]),
                ctx.blockPos(), ctx.bools());
    }
}
