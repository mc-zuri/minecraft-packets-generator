package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;

import java.util.List;

/**
 * ClientboundSetHealthPacket — wire body is {@code health:f32, food:varInt, saturation:f32}
 * (note the write order differs from the constructor's parameter order only in that food is a
 * varInt). Full cross of the float edge cases on health/saturation and the VarInt byte-length
 * boundary sweep on food: 6 * 13 * 6 = 468 shapes.
 */
public class ClientboundSetHealthShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetHealthPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundSetHealthPacket((float) a[0], (int) a[1], (float) a[2]),
                ctx.floats(), ctx.varInts(), ctx.floats());
    }
}
