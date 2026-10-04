package dev.minecraftdatagenerator.packetshapes.common;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundCustomPayloadPacket(CustomPacketPayload payload) — an Identifier discriminator followed
 * by the payload body. The serverbound codec registers exactly one known type, "minecraft:brand"
 * (BrandPayload: a single writeUtf string); every other identifier falls through to
 * {@code DiscardedPayload.codec(id, 32767)}, whose encoder writes an empty body. Both branches are
 * covered exhaustively: the brand string is swept over the UTF-8 spread plus a value long enough to
 * need a two-byte length prefix, and the fallback branch is exercised with several identifier shapes.
 */
public class ServerboundCustomPayloadShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundCustomPayloadPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        for (String brand : ctx.utf8()) {
            out.add(new ServerboundCustomPayloadPacket(new BrandPayload(brand)));
        }
        out.add(new ServerboundCustomPayloadPacket(new BrandPayload("a".repeat(200))));

        for (Identifier id : List.of(
                ctx.id("custom_test"),
                ctx.id("a"),
                Identifier.fromNamespaceAndPath("x", "y"),
                Identifier.fromNamespaceAndPath("some_namespace", "some/nested/path"))) {
            out.add(new ServerboundCustomPayloadPacket(new DiscardedPayload(id)));
        }
        return out;
    }
}
