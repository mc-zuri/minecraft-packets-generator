package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetPassengersPacket — wire body is {@code vehicleId:varInt} then
 * {@code passengers:varIntArray} (a VarInt count followed by that many VarInt entity ids).
 * <p>
 * The only public constructor reads the vehicle id and its live passenger list, so the packet is built
 * from {@link ShapeCtx#sampleEntity} and the private final {@code vehicle} / {@code passengers} fields
 * are overwritten reflectively — the same technique {@code ClientboundPlayerInfoUpdateShapes} uses.
 * <p>
 * Coverage: the VarInt byte-length boundary sweep on {@code vehicle}, the array cardinalities 0/1/2,
 * and the same VarInt boundary sweep on a single array element (which also exercises the count prefix).
 */
public class ClientboundSetPassengersShapes implements PacketShapes {
    private static final Field VEHICLE;
    private static final Field PASSENGERS;

    static {
        try {
            Field v = ClientboundSetPassengersPacket.class.getDeclaredField("vehicle");
            v.setAccessible(true);
            VEHICLE = v;
            Field p = ClientboundSetPassengersPacket.class.getDeclaredField("passengers");
            p.setAccessible(true);
            PASSENGERS = p;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to reflect ClientboundSetPassengersPacket fields", e);
        }
    }

    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetPassengersPacket.class;
    }

    private static ClientboundSetPassengersPacket mk(ShapeCtx ctx, int vehicle, int[] passengers) {
        ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(ctx.sampleEntity);
        try {
            VEHICLE.setInt(packet, vehicle);
            PASSENGERS.set(packet, passengers);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set ClientboundSetPassengersPacket fields", e);
        }
        return packet;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        // vehicle id: every VarInt byte-length boundary, with an empty passenger array.
        for (int vehicle : ctx.varInts()) {
            out.add(mk(ctx, vehicle, new int[0]));
        }
        // array cardinality 0/1/2 against a fixed vehicle id.
        out.add(mk(ctx, 1, new int[0]));
        out.add(mk(ctx, 1, new int[]{0}));
        out.add(mk(ctx, 1, new int[]{0, 1}));
        out.add(mk(ctx, 1, new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE}));
        // a single passenger id over the VarInt byte-length boundaries.
        for (int passenger : ctx.varInts()) {
            out.add(mk(ctx, 1, new int[]{passenger}));
        }
        return out;
    }
}
