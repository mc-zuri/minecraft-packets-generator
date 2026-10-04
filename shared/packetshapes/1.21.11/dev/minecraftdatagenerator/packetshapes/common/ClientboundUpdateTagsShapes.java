package dev.minecraftdatagenerator.packetshapes.common;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ClientboundUpdateTagsPacket(Map&lt;ResourceKey&lt;? extends Registry&lt;?&gt;&gt;, NetworkPayload&gt; tags) —
 * an unbounded VarInt-prefixed map of registry-id -&gt; NetworkPayload, where NetworkPayload is itself
 * a VarInt-prefixed map of tag-identifier -&gt; VarInt-prefixed list of VarInt numeric ids.
 * Coverage: outer map sizes 0/1/2/3 over several registry keys; inner tag-map sizes 0/1/2; id-list
 * sizes 0/1/2; and a sweep of the VarInt byte-length boundaries through the numeric ids (negative
 * ids included — the decoder just calls {@code registry.get(id)} and drops misses).
 */
public class ClientboundUpdateTagsShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundUpdateTagsPacket.class;
    }

    private static IntList ids(int... values) {
        IntList l = new IntArrayList(values.length);
        for (int v : values) l.add(v);
        return l;
    }

    private static TagNetworkSerialization.NetworkPayload payload(Map<Identifier, IntList> tags) {
        // The NetworkPayload constructor is package-private in 26.1, so the only way to build one
        // from a map is to encode the map in the payload's own wire form and read it back.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeMap(tags, FriendlyByteBuf::writeIdentifier, FriendlyByteBuf::writeIntIdList);
        return TagNetworkSerialization.NetworkPayload.read(buf);
    }

    private static Map<Identifier, IntList> tagMap(Object... kv) {
        Map<Identifier, IntList> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((Identifier) kv[i], (IntList) kv[i + 1]);
        return m;
    }

    private static Packet<?> pkt(Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> m) {
        return new ClientboundUpdateTagsPacket(m);
    }

    private static Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> single(
            ResourceKey<? extends Registry<?>> key, TagNetworkSerialization.NetworkPayload p) {
        Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> m = new LinkedHashMap<>();
        m.put(key, p);
        return m;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        Identifier tagA = ctx.id("a");
        Identifier tagB = Identifier.fromNamespaceAndPath("x", "some/nested/tag");

        TagNetworkSerialization.NetworkPayload empty = TagNetworkSerialization.NetworkPayload.EMPTY;
        TagNetworkSerialization.NetworkPayload oneEmptyList = payload(tagMap(tagA, ids()));
        TagNetworkSerialization.NetworkPayload oneOneId = payload(tagMap(tagA, ids(0)));
        TagNetworkSerialization.NetworkPayload oneTwoIds = payload(tagMap(tagA, ids(0, 1)));
        TagNetworkSerialization.NetworkPayload twoTags = payload(tagMap(tagA, ids(0), tagB, ids(1, 2)));

        List<TagNetworkSerialization.NetworkPayload> payloads =
                List.of(empty, oneEmptyList, oneOneId, oneTwoIds, twoTags);

        // outer map size 0
        out.add(pkt(Map.of()));

        // outer map size 1, over several registry keys x every inner payload shape
        List<ResourceKey<? extends Registry<?>>> keys =
                List.of(Registries.ITEM, Registries.BLOCK, Registries.ENTITY_TYPE, Registries.FLUID, Registries.GAME_EVENT);
        for (ResourceKey<? extends Registry<?>> k : keys) {
            for (TagNetworkSerialization.NetworkPayload p : payloads) {
                out.add(pkt(single(k, p)));
            }
        }

        // outer map sizes 2 and 3
        Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> m2 = new LinkedHashMap<>();
        m2.put(Registries.ITEM, oneOneId);
        m2.put(Registries.BLOCK, empty);
        out.add(pkt(m2));

        Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> m3 = new LinkedHashMap<>();
        m3.put(Registries.ITEM, oneOneId);
        m3.put(Registries.BLOCK, twoTags);
        m3.put(Registries.ENTITY_TYPE, oneEmptyList);
        out.add(pkt(m3));

        // VarInt boundary sweep through the numeric ids
        for (int v : ctx.varInts()) {
            out.add(pkt(single(Registries.ITEM, payload(tagMap(tagA, ids(v))))));
        }
        return out;
    }
}
