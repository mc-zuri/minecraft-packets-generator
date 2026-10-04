package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundDebugSampleSubscriptionPacket;
import net.minecraft.util.debugchart.RemoteDebugSampleType;

import java.util.List;

/** ServerboundDebugSampleSubscriptionPacket(RemoteDebugSampleType sampleType) — single field encoded
 *  as a var-int enum ordinal on the wire ({@code writeEnum}). Exhaustively covers every
 *  RemoteDebugSampleType value -- in 1.21 that enum has the single constant {@code TICK_TIME}, and
 *  the packet has no other field, so this is the complete shape set. */
public class ServerboundDebugSampleSubscriptionShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundDebugSampleSubscriptionPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.enumAll(RemoteDebugSampleType.class),
                ServerboundDebugSampleSubscriptionPacket::new);
    }
}
