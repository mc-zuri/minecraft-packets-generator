package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundDebugSubscriptionRequestPacket;
import net.minecraft.util.debug.DebugSubscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ServerboundDebugSubscriptionRequestPacket(Set&lt;DebugSubscription&lt;?&gt;&gt; subscriptions).
 * <p>
 * Wire form is {@code VarInt count} followed by that many registry ids from
 * {@code Registries.DEBUG_SUBSCRIPTION} (max 32 entries). Collection sizes 0..3 are emitted, drawn from
 * live registry entries.
 * <p>
 * The decoder is {@code ByteBufCodecs.collection(ReferenceOpenHashSet::new, 32)}, i.e. a <em>sized</em>
 * identity-hash set. The shapes therefore use the very same sized constructor with the very same
 * registry instances, so the decoded set has an identical internal layout and re-encodes byte for byte;
 * a {@code LinkedHashSet} here would not round-trip stably.
 */
public class ServerboundDebugSubscriptionRequestShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundDebugSubscriptionRequestPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Registry<DebugSubscription<?>> reg = ctx.registries.lookupOrThrow(Registries.DEBUG_SUBSCRIPTION);
        List<DebugSubscription<?>> entries = new ArrayList<>();
        for (DebugSubscription<?> sub : reg) {
            entries.add(sub);
            if (entries.size() == 3) break;
        }

        List<Packet<?>> out = new ArrayList<>();
        for (int size = 0; size <= entries.size(); size++) {
            // Mirror the decoder exactly: sized ReferenceOpenHashSet, same elements, same insertion order.
            Set<DebugSubscription<?>> set = new ReferenceOpenHashSet<>(size);
            set.addAll(entries.subList(0, size));
            out.add(new ServerboundDebugSubscriptionRequestPacket(set));
        }
        return out;
    }
}
