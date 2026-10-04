package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;
import net.minecraft.util.debugchart.RemoteDebugSampleType;

import java.util.List;

/**
 * ClientboundDebugSamplePacket(long[] sample, RemoteDebugSampleType debugSampleType).
 * <p>
 * {@code sample} is a var-int-length-prefixed long array (writeLongArray); {@code debugSampleType} is
 * an enum (writeEnum). Covers empty/single/multi long arrays with edge-value longs, crossed with every
 * enum constant.
 */
public class ClientboundDebugSampleShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundDebugSamplePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<long[]> samples = List.of(
                new long[]{},
                new long[]{0L},
                new long[]{1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE});
        return ctx.cross(
                a -> new ClientboundDebugSamplePacket((long[]) a[0], (RemoteDebugSampleType) a[1]),
                samples, ctx.enumAll(RemoteDebugSampleType.class));
    }
}
