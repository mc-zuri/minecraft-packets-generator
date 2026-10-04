package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;

import java.util.List;

/**
 * ClientboundBlockDestructionPacket(int id, BlockPos pos, int progress).
 * <p>
 * Wire body: id:varint, pos:packed-long block position, progress:unsigned byte.
 * <p>
 * Full cross-product of the VarInt byte-length boundaries for the breaker id, the domain-correct
 * block positions (Y sweep plus the world-border X/Z) and the progress spread: the destroy stages
 * 0..9 are the only values vanilla sends, but the field is a raw unsigned byte, so 10, 127, 128 and
 * 255 are included too. Values above 255 are omitted - {@code writeByte} truncates them, so they are
 * not a distinguishable wire form.
 */
public class ClientboundBlockDestructionPacketShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundBlockDestructionPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Integer> progress = List.of(0, 1, 5, 9, 10, 127, 128, 255);
        return ctx.cross(
                a -> new ClientboundBlockDestructionPacket((int) a[0], (BlockPos) a[1], (int) a[2]),
                ctx.varInts(), ctx.blockPos(), progress);
    }
}
