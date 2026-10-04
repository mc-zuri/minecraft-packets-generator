package dev.minecraftdatagenerator.packetshapes.ping;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;

import java.util.List;

/** ServerboundPingRequestPacket(long time) — single i64 field (writeLong); all long edge cases. */
public class ServerboundPingRequestShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPingRequestPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.longs(), ServerboundPingRequestPacket::new);
    }
}
