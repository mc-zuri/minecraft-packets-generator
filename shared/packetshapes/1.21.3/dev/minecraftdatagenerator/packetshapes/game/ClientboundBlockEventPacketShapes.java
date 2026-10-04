package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * ClientboundBlockEventPacket(BlockPos pos, Block block, int b0, int b1) — the write order on the wire is
 * {@code pos(blockpos i64), b0(u8), b1(u8), block(registry varint id)}.
 * <p>
 * b0/b1 go through {@code writeByte} / {@code readUnsignedByte}, so they only round-trip in 0..255; the
 * spread covers both ends plus the 127/128 sign boundary. Full cross of position x b0 x b1 x block,
 * where the block spread is taken from the live BLOCK registry at ids straddling the VarInt byte-length
 * boundaries (10 x 5 x 5 x 4 = 1000 shapes).
 */
public class ClientboundBlockEventPacketShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundBlockEventPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Registry<Block> blockRegistry = ctx.registries.lookupOrThrow(Registries.BLOCK);
        int size = blockRegistry.size();
        LinkedHashSet<Block> blocks = new LinkedHashSet<>();
        for (int id : new int[]{0, 127, 128, size - 1}) {
            if (id >= 0 && id < size) {
                Block block = blockRegistry.byId(id);
                if (block != null) blocks.add(block);
            }
        }
        if (blocks.isEmpty()) blocks.add(ctx.anyOf(Registries.BLOCK));

        // writeByte truncates to 8 bits and readUnsignedByte returns 0..255, so only 0..255 round-trips.
        List<Integer> unsignedBytes = List.of(0, 1, 127, 128, 255);

        return ctx.cross(
                a -> new ClientboundBlockEventPacket((BlockPos) a[0], (Block) a[3], (int) a[1], (int) a[2]),
                ctx.blockPos(), unsignedBytes, unsignedBytes, new ArrayList<>(blocks));
    }
}
