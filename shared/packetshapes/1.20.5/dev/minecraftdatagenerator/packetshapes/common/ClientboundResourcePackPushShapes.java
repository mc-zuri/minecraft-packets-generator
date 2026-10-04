package dev.minecraftdatagenerator.packetshapes.common;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ClientboundResourcePackPushPacket(UUID id, String url [STRING_UTF8], String hash [stringUtf8(40)],
 * boolean required [BOOL], Optional&lt;Component&gt; prompt).
 * Full cartesian over every dimension: 2 uuids x 6 UTF-8 urls x 4 hashes x 2 booleans x
 * 4 prompt states (absent + each component) = 384 shapes, so both categorical fields (the boolean
 * and the Optional's presence bit) are crossed with every string/uuid corner case.
 * Hashes longer than 40 characters are omitted: the record's constructor throws on them.
 */
public class ClientboundResourcePackPushShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundResourcePackPushPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Optional<Component>> prompts = new ArrayList<>();
        prompts.add(Optional.empty());
        for (Component c : ctx.components()) prompts.add(Optional.of(c));

        // MAX_HASH_LENGTH = 40; the 40-char entry is the maximum the constructor accepts.
        List<String> hashes = List.of("", "x", "0123456789abcdef", "f".repeat(40));

        return ctx.cross(
                a -> new ClientboundResourcePackPushPacket(
                        (UUID) a[0], (String) a[1], (String) a[2], (boolean) a[3],
                        castPrompt(a[4])),
                ctx.uuids(), ctx.utf8(), hashes, ctx.bools(), prompts);
    }

    @SuppressWarnings("unchecked")
    private static Optional<Component> castPrompt(Object o) {
        return (Optional<Component>) o;
    }
}
