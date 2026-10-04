package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;

import java.lang.reflect.Field;
import java.util.List;

/**
 * ClientboundSetCameraPacket — wire body is {@code cameraId:varInt}.
 * <p>
 * The only public constructor derives the id from a live {@link net.minecraft.world.entity.Entity},
 * so the packet is built from {@link ShapeCtx#sampleEntity} and the private final {@code cameraId}
 * field is then overwritten reflectively — the same technique
 * {@code ClientboundPlayerInfoUpdateShapes} uses — to sweep the VarInt byte-length boundaries.
 */
public class ClientboundSetCameraShapes implements PacketShapes {
    private static final Field CAMERA_ID;

    static {
        try {
            Field f = ClientboundSetCameraPacket.class.getDeclaredField("cameraId");
            f.setAccessible(true);
            CAMERA_ID = f;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to reflect ClientboundSetCameraPacket.cameraId", e);
        }
    }

    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetCameraPacket.class;
    }

    private static ClientboundSetCameraPacket withId(ShapeCtx ctx, int cameraId) {
        ClientboundSetCameraPacket packet = new ClientboundSetCameraPacket(ctx.sampleEntity);
        try {
            CAMERA_ID.setInt(packet, cameraId);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set ClientboundSetCameraPacket.cameraId", e);
        }
        return packet;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), id -> withId(ctx, id));
    }
}
