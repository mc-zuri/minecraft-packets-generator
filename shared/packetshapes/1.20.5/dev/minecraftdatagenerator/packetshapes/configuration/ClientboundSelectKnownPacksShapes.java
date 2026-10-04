package dev.minecraftdatagenerator.packetshapes.configuration;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.server.packs.repository.KnownPack;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSelectKnownPacks(List&lt;KnownPack&gt; knownPacks).
 * <p>
 * Wire body: knownPacks:list of KnownPack(namespace, id, version), each a plain
 * {@code ByteBufCodecs.STRING_UTF8} - no identifier validation, so the full UTF-8 spread is legal.
 * <p>
 * Covers list sizes 0/1/2/3 and, for the single-element case, the full cross-product of the UTF-8
 * corner-case spread over all three KnownPack fields (6^3 = 216 shapes), which exercises the
 * one- and two-byte string length prefixes and every UTF-8 sequence length.
 */
public class ClientboundSelectKnownPacksShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSelectKnownPacks.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        // Empty list.
        out.add(new ClientboundSelectKnownPacks(List.of()));
        // Single element crossed over the UTF-8 corner cases for all three fields.
        for (String ns : ctx.utf8()) {
            for (String id : ctx.utf8()) {
                for (String ver : ctx.utf8()) {
                    out.add(new ClientboundSelectKnownPacks(List.of(new KnownPack(ns, id, ver))));
                }
            }
        }
        // A string long enough to push the UTF-8 length prefix to two varint bytes.
        out.add(new ClientboundSelectKnownPacks(List.of(new KnownPack("minecraft", "x".repeat(200), "1"))));
        // Two and three elements.
        out.add(new ClientboundSelectKnownPacks(List.of(
                new KnownPack("minecraft", "core", "1"),
                new KnownPack("x", "", "2"))));
        out.add(new ClientboundSelectKnownPacks(List.of(
                new KnownPack("minecraft", "core", "1"),
                new KnownPack("x", "", "2"),
                new KnownPack("", "éèê", "你好"))));
        return out;
    }
}
