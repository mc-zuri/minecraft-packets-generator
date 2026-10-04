package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundRotateHeadPacket(Entity entity, byte yHeadRot) — wire fields {@code entityId:varint,
 * yHeadRot:i8} (a packed rotation byte). Swept over the packed-angle spread plus the raw i8 extremes.
 * <p>
 * The only public constructor takes a live {@link net.minecraft.world.entity.Entity}, so the packet is
 * built from {@link ShapeCtx#sampleEntity} and the private final {@code entityId} field is overwritten
 * reflectively; the full {@code entityId} (VarInt byte-length boundaries) x {@code yHeadRot} cartesian
 * is emitted.
 */
public class ClientboundRotateHeadShapes implements PacketShapes {
    private static final Field ENTITY_ID;

    static {
        try {
            Field f = ClientboundRotateHeadPacket.class.getDeclaredField("entityId");
            f.setAccessible(true);
            ENTITY_ID = f;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to reflect ClientboundRotateHeadPacket.entityId", e);
        }
    }

    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundRotateHeadPacket.class;
    }

    private static ClientboundRotateHeadPacket mk(ShapeCtx ctx, int entityId, byte yHeadRot) {
        ClientboundRotateHeadPacket packet = new ClientboundRotateHeadPacket(ctx.sampleEntity, yHeadRot);
        try {
            ENTITY_ID.setInt(packet, entityId);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set ClientboundRotateHeadPacket.entityId", e);
        }
        return packet;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Byte> values = new ArrayList<>(ctx.angles());
        for (Byte b : ctx.bytes()) if (!values.contains(b)) values.add(b);
        // full cartesian: 13 var-int id boundaries x ~9 rotation bytes.
        return ctx.cross(a -> mk(ctx, (Integer) a[0], (Byte) a[1]), ctx.varInts(), values);
    }
}
