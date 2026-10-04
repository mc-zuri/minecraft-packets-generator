package dev.minecraftdatagenerator.packetshapes.status;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.players.NameAndId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ClientboundStatusResponsePacket(ServerStatus status) — the whole status is a single JSON string on
 * the wire ({@code lenientJson(32767)}).
 * <p>
 * ServerStatus(Component description, Optional&lt;Players&gt; players, Optional&lt;Version&gt; version,
 * Optional&lt;Favicon&gt; favicon, boolean enforcesSecureChat). Every field is a lenient optional with
 * a default, so an absent optional (and {@code description == empty} / {@code enforcesSecureChat ==
 * false}) omits the JSON key entirely — a distinguishable wire form that the spreads below cover.
 * <p>
 * Full cartesian over the categorical dimensions: description (3 component forms, the first of which
 * is the omitted default) x players absent/present with a 0-, 1- and 2-entry sample list (4) x
 * version absent/present (3) x favicon absent/present with empty and non-empty icon bytes (3) x
 * enforcesSecureChat (2) = 216 shapes. Followed by per-field sweeps of the Players counts and the
 * Version name/protocol.
 */
public class ClientboundStatusResponseShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundStatusResponsePacket.class;
    }

    private static Packet<?> pkt(ServerStatus s) {
        return new ClientboundStatusResponsePacket(s);
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<T> cast(Object o) {
        return (Optional<T>) o;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Component desc = Component.literal("hi");
        NameAndId n1 = new NameAndId(ctx.uuids().get(0), "a");
        NameAndId n2 = new NameAndId(ctx.uuids().get(1), "你好");

        List<Optional<ServerStatus.Players>> players = List.of(
                Optional.empty(),
                Optional.of(new ServerStatus.Players(0, 0, List.of())),
                Optional.of(new ServerStatus.Players(20, 1, List.of(n1))),
                Optional.of(new ServerStatus.Players(-1, -1, List.of(n1, n2))));
        List<Optional<ServerStatus.Version>> versions = List.of(
                Optional.empty(),
                Optional.of(new ServerStatus.Version("1.21", 0)),
                Optional.of(new ServerStatus.Version("", -1)));
        List<Optional<ServerStatus.Favicon>> favicons = List.of(
                Optional.empty(),
                Optional.of(new ServerStatus.Favicon(new byte[]{})),
                Optional.of(new ServerStatus.Favicon(new byte[]{1, 2, 3, 4})));

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> pkt(new ServerStatus((Component) a[0], cast(a[1]), cast(a[2]), cast(a[3]), (boolean) a[4])),
                ctx.components(), players, versions, favicons, ctx.bools()));

        // Players max/online sweep
        for (int v : ctx.ints()) {
            out.add(pkt(new ServerStatus(desc, Optional.of(new ServerStatus.Players(v, 0, List.of())),
                    Optional.empty(), Optional.empty(), false)));
            out.add(pkt(new ServerStatus(desc, Optional.of(new ServerStatus.Players(0, v, List.of())),
                    Optional.empty(), Optional.empty(), false)));
        }
        // Version name/protocol sweep
        for (String name : ctx.utf8()) {
            out.add(pkt(new ServerStatus(desc, Optional.empty(),
                    Optional.of(new ServerStatus.Version(name, 0)), Optional.empty(), false)));
        }
        for (int protocol : ctx.ints()) {
            out.add(pkt(new ServerStatus(desc, Optional.empty(),
                    Optional.of(new ServerStatus.Version("1.21", protocol)), Optional.empty(), false)));
        }
        return out;
    }
}
