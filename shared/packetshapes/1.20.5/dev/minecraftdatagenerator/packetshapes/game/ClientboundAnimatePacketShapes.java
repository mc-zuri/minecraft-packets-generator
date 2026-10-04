package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundAnimatePacket(Entity entity, int action).
 * <p>
 * Wire body: id:varint, action:unsigned byte.
 * <p>
 * {@code action} is an explicit discriminator, so every named constant is emitted (WAKE_UP,
 * CRITICAL_HIT, MAGIC_CRITICAL_HIT - in 26.3 the swing actions live in
 * {@code ClientboundSwingAnimationPacket}), together with the unused values around them and the
 * unsigned-byte extremes 0/1/2/3/127/128/255.
 * <p>
 * The only public constructor derives {@code id} from a live {@link net.minecraft.world.entity.Entity},
 * so the packet is built from {@link ShapeCtx#sampleEntity} and the private final {@code id} field is
 * overwritten reflectively to sweep the VarInt byte-length boundaries; the full action x id cartesian
 * is emitted. Action values above 255 are omitted because {@code writeByte} truncates them, which is
 * not a distinguishable wire form.
 */
public class ClientboundAnimatePacketShapes implements PacketShapes {
    private static final Field ID;

    static {
        try {
            Field f = ClientboundAnimatePacket.class.getDeclaredField("id");
            f.setAccessible(true);
            ID = f;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to reflect ClientboundAnimatePacket.id", e);
        }
    }

    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundAnimatePacket.class;
    }

    private static ClientboundAnimatePacket mk(ShapeCtx ctx, int id, int action) {
        ClientboundAnimatePacket packet = new ClientboundAnimatePacket(ctx.sampleEntity, action);
        try {
            ID.setInt(packet, id);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set ClientboundAnimatePacket.id", e);
        }
        return packet;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Integer> actions = new ArrayList<>(List.of(
                ClientboundAnimatePacket.WAKE_UP,
                ClientboundAnimatePacket.CRITICAL_HIT,
                ClientboundAnimatePacket.MAGIC_CRITICAL_HIT));
        for (int v : List.of(0, 1, 2, 3, 4, 127, 128, 254, 255)) {
            if (!actions.contains(v)) actions.add(v);
        }
        // full cartesian: ~11 actions x 13 var-int id boundaries.
        return ctx.cross(a -> mk(ctx, (int) a[0], (int) a[1]), ctx.varInts(), actions);
    }
}
