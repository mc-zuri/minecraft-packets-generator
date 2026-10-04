package dev.minecraftdatagenerator.packetshapes.configuration;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ClientboundUpdateEnabledFeaturesPacket(Set&lt;ResourceLocation&gt; features).
 * <p>
 * Wire body: features:collection of identifiers (decoded into a HashSet).
 * <p>
 * Covers set sizes 0/1/2/3. The single-element case sweeps the identifier spread: the shortest legal
 * id, the default namespace, a path using every legal path character class, an explicit namespace, and
 * a 130-byte path that pushes the UTF-8 length prefix to two varint bytes.
 * <p>
 * HashSet is used for construction so the encode order matches the order of the HashSet the decoder
 * builds, keeping the round-trip byte-stable. Sizes stay small for the same reason.
 */
public class ClientboundUpdateEnabledFeaturesPacketShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundUpdateEnabledFeaturesPacket.class;
    }

    private static ClientboundUpdateEnabledFeaturesPacket of(ResourceLocation... ids) {
        // Same constructor the decoder uses (ByteBufCodecs.collection(HashSet::new) passes the
        // element count), so the HashSet's iteration order - and therefore the encode order - matches.
        Set<ResourceLocation> set = new HashSet<>(ids.length);
        for (ResourceLocation id : ids) set.add(id);
        return new ClientboundUpdateEnabledFeaturesPacket(set);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<ResourceLocation> ids = List.of(
                ResourceLocation.fromNamespaceAndPath("a", "b"),
                ctx.id("vanilla"),
                ctx.id("a/b/c_d.e-f"),
                ResourceLocation.fromNamespaceAndPath("mymod", "data"),
                ResourceLocation.fromNamespaceAndPath("mymod", "x".repeat(130)));

        List<Packet<?>> out = new ArrayList<>();
        // size 0
        out.add(of());
        // size 1, over the identifier spread
        for (ResourceLocation id : ids) out.add(of(id));
        // size 2 and 3
        out.add(of(ctx.id("vanilla"), ctx.id("bundle")));
        out.add(of(ctx.id("vanilla"), ctx.id("bundle"), ResourceLocation.fromNamespaceAndPath("mymod", "data")));
        return out;
    }
}
