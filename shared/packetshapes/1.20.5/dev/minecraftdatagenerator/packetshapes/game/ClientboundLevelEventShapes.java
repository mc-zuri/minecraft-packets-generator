package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;

import java.util.List;

/**
 * ClientboundLevelEventPacket(int type, BlockPos pos, int data, boolean globalEvent) — wire body is
 * {@code type:int, pos:blockPos, data:int, globalEvent:boolean}. Full cross-product of the corner cases
 * across all four fields.
 */
public class ClientboundLevelEventShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundLevelEventPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundLevelEventPacket((int) a[0], (BlockPos) a[1], (int) a[2], (boolean) a[3]),
                ctx.ints(), ctx.blockPos(), ctx.ints(), ctx.bools());
    }
}
