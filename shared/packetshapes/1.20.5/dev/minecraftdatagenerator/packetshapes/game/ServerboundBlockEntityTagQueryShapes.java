package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;

import java.util.List;

/** ServerboundBlockEntityTagQueryPacket(int transactionId[varint], BlockPos pos) — full cross-product
 *  of every VarInt byte-length boundary and the domain-correct BlockPos corner positions. */
public class ServerboundBlockEntityTagQueryShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundBlockEntityTagQueryPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundBlockEntityTagQueryPacket((int) a[0], (net.minecraft.core.BlockPos) a[1]),
                ctx.varInts(), ctx.blockPos());
    }
}
