package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;

import java.util.List;

/**
 * ClientboundSetChunkCacheRadiusPacket(int radius) — single {@code radius:varInt} field.
 * Public single-int constructor, no clamping on either side, so the full VarInt byte-length
 * boundary sweep is used (every varint width, plus the negative 5-byte forms).
 */
public class ClientboundSetChunkCacheRadiusShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetChunkCacheRadiusPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), ClientboundSetChunkCacheRadiusPacket::new);
    }
}
