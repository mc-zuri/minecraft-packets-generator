package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket.ChunkBiomeData;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundChunksBiomesPacket(List&lt;ChunkBiomeData&gt; chunkBiomeData) — a plain list of
 * ChunkBiomeData(ChunkPos pos[packed i64], byte[] buffer[varint length + raw bytes, max 2 MiB]).
 * <p>
 * The buffer is an opaque paletted-biome blob the codec never interprets ({@code ByteBufCodecs.byteArray}
 * only bounds its length), so it is varied by length only: 0, 1, 127 and 128 bytes, which straddles the
 * VarInt byte-length boundary of the length prefix.
 * <p>
 * Covers: list sizes 0, 1 and 2; every chunk-coordinate corner (including the border-derived extremes)
 * crossed X x Z; and every buffer length.
 */
public class ClientboundChunksBiomesShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundChunksBiomesPacket.class;
    }

    private static byte[] buffer(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[i] = (byte) i;
        return bytes;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        byte[] baseBuffer = buffer(3);

        // empty list
        out.add(new ClientboundChunksBiomesPacket(List.of()));

        // single entry, full cross of chunk X and Z corner values
        for (int x : ctx.chunkCoords()) {
            for (int z : ctx.chunkCoords()) {
                out.add(new ClientboundChunksBiomesPacket(
                        List.of(new ChunkBiomeData(new ChunkPos(x, z), baseBuffer))));
            }
        }

        // single entry, buffer length sweep (0/1 byte and the 127/128 varint-prefix boundary)
        for (int length : new int[]{0, 1, 127, 128}) {
            out.add(new ClientboundChunksBiomesPacket(
                    List.of(new ChunkBiomeData(ChunkPos.ZERO, buffer(length)))));
        }

        // two entries, mixing buffer lengths and positions
        out.add(new ClientboundChunksBiomesPacket(List.of(
                new ChunkBiomeData(ChunkPos.ZERO, buffer(0)),
                new ChunkBiomeData(new ChunkPos(1, -2), buffer(128)))));

        return out;
    }
}
