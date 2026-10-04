package dev.minecraftdatagenerator.packetshapes.common;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPostEffectsPacket;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundPostEffectsPacket(List&lt;Identifier&gt; postEffects) —
 * {@code Identifier.STREAM_CODEC.apply(ByteBufCodecs.list())}: a VarInt element count followed by one
 * UTF-8 identifier per entry. Covers list sizes 0, 1, 2, 3 and 128 (the last pushing the count's
 * VarInt prefix to two bytes), and identifier shapes spanning the default namespace, an explicit
 * namespace, a one-character path and a nested path.
 */
public class ClientboundPostEffectsShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPostEffectsPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Identifier> variants = List.of(
                ctx.id("blur"),
                ctx.id("a"),
                Identifier.fromNamespaceAndPath("x", "y"),
                Identifier.fromNamespaceAndPath("some_namespace", "some/nested/path"));

        List<Packet<?>> out = new ArrayList<>();
        out.add(new ClientboundPostEffectsPacket(List.of()));
        for (Identifier id : variants) {
            out.add(new ClientboundPostEffectsPacket(List.of(id)));
        }
        out.add(new ClientboundPostEffectsPacket(List.of(variants.get(0), variants.get(2))));
        out.add(new ClientboundPostEffectsPacket(
                List.of(variants.get(0), variants.get(1), variants.get(3))));

        // 128 entries: the element count no longer fits in a single VarInt byte.
        List<Identifier> many = new ArrayList<>();
        for (int i = 0; i < 128; i++) many.add(ctx.id("effect_" + i));
        out.add(new ClientboundPostEffectsPacket(many));
        return out;
    }
}
