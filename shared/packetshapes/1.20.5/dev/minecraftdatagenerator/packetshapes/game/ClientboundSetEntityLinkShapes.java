package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;

import java.util.List;

/**
 * ClientboundSetEntityLinkPacket(Entity source, @Nullable Entity dest) — wire body is
 * {@code sourceId:i32, destId:i32}, both derived from entity ids (dest null encodes destId 0).
 * Both ids are written with {@code writeInt}, i.e. fixed-width big-endian i32 — they have no
 * VarInt byte-length boundaries, so a numeric sweep would produce no new wire shape. The only
 * distinguishable forms are the null-dest (destId 0) and non-null-dest branches, both covered.
 */
public class ClientboundSetEntityLinkShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetEntityLinkPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.list(
                new ClientboundSetEntityLinkPacket(ctx.sampleEntity, null),
                new ClientboundSetEntityLinkPacket(ctx.sampleEntity, ctx.sampleEntity));
    }
}
