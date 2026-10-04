package dev.minecraftdatagenerator.packetshapes.common;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;

import java.util.List;

/** ServerboundKeepAlivePacket(long id) — single i64 field; all long edge cases. */
public class ServerboundKeepAliveShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundKeepAlivePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.longs(), ServerboundKeepAlivePacket::new);
    }
}
