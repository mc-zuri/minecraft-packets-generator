package dev.minecraftdatagenerator.packetshapes.cookie;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * ClientboundCookieRequestPacket(ResourceLocation key).
 * <p>
 * Wire body: key:identifier (a UTF-8 string, max 32767, written as {@code namespace:path}).
 * <p>
 * The only field is an identifier, so this sweeps the identifier spread: the shortest legal form,
 * the default namespace, every legal path character class ({@code a-z 0-9 _ . - /}), every legal
 * namespace character class, and a 130-byte path that pushes the string's UTF-8 length prefix from
 * one varint byte to two.
 */
public class ClientboundCookieRequestPacketShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundCookieRequestPacket.class;
    }

    /** Identifiers exercising both segments' character classes and the length-prefix boundary. */
    static List<ResourceLocation> keys(ShapeCtx ctx) {
        return List.of(
                new ResourceLocation("a", "b"),
                ctx.id("x"),
                ctx.id("cookie/session"),
                ctx.id("a/b/c_d.e-f0123456789"),
                new ResourceLocation("mymod", "data"),
                new ResourceLocation("my_mod.x-y0", "data"),
                new ResourceLocation("mymod", "x".repeat(130)));
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(keys(ctx), ClientboundCookieRequestPacket::new);
    }
}
