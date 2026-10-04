package dev.minecraftdatagenerator.packetshapes.common;

import com.mojang.datafixers.util.Either;
import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundServerLinksPacket;
import net.minecraft.server.ServerLinks;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundServerLinksPacket(List&lt;ServerLinks.UntrustedEntry&gt; links) — a VarInt-prefixed list of
 * entries, each being {@code Either&lt;KnownLinkType, Component&gt;} (a bool discriminator, then either a
 * VarInt type id or a trusted component) plus a STRING_UTF8 link.
 * Coverage: the empty list; a one-entry list for EVERY one of the 10 KnownLinkType constants
 * (left branch) and for every component corner case (right branch); a sweep of the UTF-8 link
 * spread; and two-entry lists mixing the branches in both orders.
 */
public class ClientboundServerLinksShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundServerLinksPacket.class;
    }

    private static ServerLinks.UntrustedEntry known(ServerLinks.KnownLinkType t, String link) {
        return new ServerLinks.UntrustedEntry(Either.left(t), link);
    }

    private static ServerLinks.UntrustedEntry custom(Component c, String link) {
        return new ServerLinks.UntrustedEntry(Either.right(c), link);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        String baseLink = "http://a";

        out.add(new ClientboundServerLinksPacket(List.of()));

        // left branch: every KnownLinkType constant
        for (ServerLinks.KnownLinkType t : ctx.enumAll(ServerLinks.KnownLinkType.class)) {
            out.add(new ClientboundServerLinksPacket(List.of(known(t, baseLink))));
        }
        // right branch: every component corner case
        for (Component c : ctx.components()) {
            out.add(new ClientboundServerLinksPacket(List.of(custom(c, baseLink))));
        }
        // link string sweep, on both branches
        for (String link : ctx.utf8()) {
            out.add(new ClientboundServerLinksPacket(List.of(known(ServerLinks.KnownLinkType.BUG_REPORT, link))));
            out.add(new ClientboundServerLinksPacket(List.of(custom(Component.literal("x"), link))));
        }
        // two-entry lists, both branch orderings
        ServerLinks.UntrustedEntry k = known(ServerLinks.KnownLinkType.WEBSITE, baseLink);
        ServerLinks.UntrustedEntry c = custom(Component.literal("x"), "");
        out.add(new ClientboundServerLinksPacket(List.of(k, c)));
        out.add(new ClientboundServerLinksPacket(List.of(c, k)));
        out.add(new ClientboundServerLinksPacket(List.of(k, k)));
        out.add(new ClientboundServerLinksPacket(List.of(c, c)));
        return out;
    }
}
