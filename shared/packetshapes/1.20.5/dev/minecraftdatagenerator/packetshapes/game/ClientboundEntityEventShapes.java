package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;

import java.util.List;

/**
 * ClientboundEntityEventPacket(Entity entity, byte eventId) — wire body is {@code entityId:int,
 * eventId:byte}. {@code entityId} is written with {@code writeInt}, i.e. a fixed-width big-endian i32
 * with no VarInt byte-length boundaries, so varying it produces no new wire shape; the sample entity's
 * id is used and the event id is swept over its byte edge cases.
 */
public class ClientboundEntityEventShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundEntityEventPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.bytes(), b -> new ClientboundEntityEventPacket(ctx.sampleEntity, b));
    }
}
