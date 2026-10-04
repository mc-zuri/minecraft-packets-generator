package dev.minecraftdatagenerator.packetshapes.common;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

import java.util.List;

/** ServerboundResourcePackPacket(UUID id, Action action) — cross-product of UUID corner cases and
 *  every Action enum constant (8 values). */
public class ServerboundResourcePackShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundResourcePackPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundResourcePackPacket(
                        (java.util.UUID) a[0], (ServerboundResourcePackPacket.Action) a[1]),
                ctx.uuids(), ctx.enumAll(ServerboundResourcePackPacket.Action.class));
    }
}
