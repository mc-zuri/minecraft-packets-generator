package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundGameTestHighlightPosPacket;

import java.util.List;

/**
 * ClientboundGameTestHighlightPosPacket(BlockPos absolutePos, BlockPos relativePos) — two BlockPos
 * fields (each a packed long). Full cross-product of the BlockPos corner cases.
 */
public class ClientboundGameTestHighlightPosShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundGameTestHighlightPosPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundGameTestHighlightPosPacket((net.minecraft.core.BlockPos) a[0], (net.minecraft.core.BlockPos) a[1]),
                ctx.blockPos(), ctx.blockPos());
    }
}
