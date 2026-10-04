package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

import java.lang.reflect.Field;
import java.util.List;

/**
 * ServerboundPlayerCommandPacket(int id[varint], Action action[enum byte], int data[varint]).
 * <p>
 * Full cartesian of every {@code Action} constant (7) x the VarInt byte-length boundary spread for
 * {@code id} (13) x the same spread for {@code data} (13) = 1183 shapes.
 * <p>
 * The only public constructor derives {@code id} from an {@link net.minecraft.world.entity.Entity}, so
 * the packet is built from {@link ShapeCtx#sampleEntity} and the private final {@code id} field is then
 * overwritten reflectively — the same technique {@code ClientboundPlayerInfoUpdateShapes} uses.
 */
public class ServerboundPlayerCommandShapes implements PacketShapes {
    private static final Field ID;

    static {
        try {
            Field f = ServerboundPlayerCommandPacket.class.getDeclaredField("id");
            f.setAccessible(true);
            ID = f;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to reflect ServerboundPlayerCommandPacket.id", e);
        }
    }

    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPlayerCommandPacket.class;
    }

    private static ServerboundPlayerCommandPacket mk(ShapeCtx ctx, int id,
                                                     ServerboundPlayerCommandPacket.Action action, int data) {
        ServerboundPlayerCommandPacket packet =
                new ServerboundPlayerCommandPacket(ctx.sampleEntity, action, data);
        try {
            ID.setInt(packet, id);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set ServerboundPlayerCommandPacket.id", e);
        }
        return packet;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> mk(ctx, (int) a[0], (ServerboundPlayerCommandPacket.Action) a[1], (int) a[2]),
                ctx.varInts(), ctx.enumAll(ServerboundPlayerCommandPacket.Action.class), ctx.varInts());
    }
}
