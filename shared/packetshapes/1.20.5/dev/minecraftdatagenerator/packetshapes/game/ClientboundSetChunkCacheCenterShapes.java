package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetChunkCacheCenterPacket(int x, int z) — wire body is {@code x:varInt, z:varInt},
 * both <b>chunk</b> coordinates. Full cross of the domain-correct chunk-coordinate spread (origin,
 * +-1 and the +-1,875,000 world-border-derived extremes), plus a VarInt byte-length boundary sweep
 * on each axis so every varint width appears on the wire.
 */
public class ClientboundSetChunkCacheCenterShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetChunkCacheCenterPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundSetChunkCacheCenterPacket((int) a[0], (int) a[1]),
                ctx.chunkCoords(), ctx.chunkCoords()));
        for (int v : ctx.varInts()) {
            out.add(new ClientboundSetChunkCacheCenterPacket(v, 0));
            out.add(new ClientboundSetChunkCacheCenterPacket(0, v));
        }
        return out;
    }
}
