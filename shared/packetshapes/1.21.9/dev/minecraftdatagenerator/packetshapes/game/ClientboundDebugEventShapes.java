package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDebugEventPacket;
import net.minecraft.util.debug.DebugStructureInfo;
import net.minecraft.util.debug.DebugSubscription;
import net.minecraft.util.debug.DebugSubscriptions;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundDebugEventPacket(DebugSubscription.Event&lt;?&gt; event).
 * <p>
 * {@code event} is registry-dispatched on {@code minecraft:debug_subscription}: it writes the
 * subscription's registry id as a var-int then the value with that subscription's own codec — unlike
 * {@code Update}, with no {@code Optional} wrapper. The discriminator therefore has one branch per
 * registered subscription.
 * <p>
 * Covered: every value-carrying subscription, with the same value corner cases as the entity-value
 * provider (optionals, list sizes 0/1/2, string UTF-8 classes, var-int and BlockPos edges, every
 * {@code DebugEntityBlockIntersection} constant, several {@code Orientation} ids, and the
 * {@code entity_paths} {@code Path} built by {@link ClientboundDebugBlockValueShapes#debugPath()},
 * which attaches the pathfinder debug data {@code Path.DEBUG_STREAM_CODEC} requires).
 * <p>
 * OMITTED (vanilla cannot encode it): {@code dedicated_server_tick_time} (null value codec).
 */
public class ClientboundDebugEventShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundDebugEventPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        for (Object value : ClientboundDebugEntityValueShapes.values(ctx)) {
            DebugSubscription.Event<?> event = ClientboundDebugEntityValueShapes.packEvent(value);
            out.add(new ClientboundDebugEventPacket(event));
        }
        // the empty list is routed to RAIDS by the shared dispatcher; add the STRUCTURES branch explicitly
        out.add(new ClientboundDebugEventPacket(DebugSubscriptions.STRUCTURES.packEvent(List.<DebugStructureInfo>of())));
        return out;
    }
}
