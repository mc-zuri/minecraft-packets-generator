package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * ClientboundCooldownPacket(ResourceLocation cooldownGroup, int duration[varint]) — full cross of an identifier
 * spread with the VarInt byte-length spread (4 x 13 = 52 shapes).
 * <p>
 * The identifier is written by {@code ResourceLocation.STREAM_CODEC} as the whole {@code namespace:path}
 * string, so the spread varies both halves' lengths. It cannot use {@code ctx.utf8()}: ResourceLocation only
 * accepts {@code [a-z0-9._-]} in the namespace and additionally {@code /} in the path, so any non-ASCII
 * value would throw on construction.
 */
public class ClientboundCooldownShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundCooldownPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<ResourceLocation> groups = List.of(
                ctx.id("a"),
                ctx.id("stone"),
                ResourceLocation.parse("mymod:custom_group"),
                ResourceLocation.parse("a_very_long_namespace_0123456789:deeply/nested/cooldown/group/path"));
        return ctx.cross(
                a -> new ClientboundCooldownPacket((ResourceLocation) a[0], (int) a[1]),
                groups, ctx.varInts());
    }
}
