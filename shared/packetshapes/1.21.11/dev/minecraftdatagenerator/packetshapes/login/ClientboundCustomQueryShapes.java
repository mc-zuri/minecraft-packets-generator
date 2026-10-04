package dev.minecraftdatagenerator.packetshapes.login;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * ClientboundCustomQueryPacket(int transactionId, CustomQueryPayload payload).
 * <p>
 * Wire: {@code varInt(transactionId)}, {@code identifier(payload.id())}, then the payload body, which
 * is "the rest of the packet" (the reader consumes all remaining readable bytes, capped at 1 MiB).
 * <p>
 * The read side always produces an opaque {@link DiscardedQueryPayload} (identifier only, and its
 * {@code write} emits no bytes), so the body is always empty and the only distinguishable dimensions
 * are the transaction id and the payload identifier. Full cartesian: transactionId over the VarInt
 * byte-length boundaries (13) x a spread of identifier forms (default namespace, short and nested
 * paths, and a custom namespace).
 */
public class ClientboundCustomQueryShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundCustomQueryPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Identifier> ids = List.of(
                ctx.id("x"),
                ctx.id("brand"),
                ctx.id("foo/bar"),
                Identifier.fromNamespaceAndPath("mynamespace", "a_b.c-d/e"));
        return ctx.cross(
                a -> new ClientboundCustomQueryPacket((int) a[0],
                        (CustomQueryPayload) new DiscardedQueryPayload((Identifier) a[1])),
                ctx.varInts(), ids);
    }
}
