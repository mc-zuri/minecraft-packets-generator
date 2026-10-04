package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket;
import net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket.Action;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundCustomChatCompletionsPacket(Action action[varint id], List&lt;String&gt; entries[utf8 list]).
 * Full cross of every Action constant with a list spread covering sizes 0, 1 and 2, plus a list carrying
 * the whole UTF-8 spread (empty / ASCII / 2-byte / 3-byte / 4-byte code points) so the per-string UTF-8
 * length prefix is exercised in every width.
 */
public class ClientboundCustomChatCompletionsShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundCustomChatCompletionsPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<List<String>> entryLists = new ArrayList<>();
        entryLists.add(List.of());
        entryLists.add(List.of("x"));
        entryLists.add(List.of("", "minecraft:stone"));
        entryLists.add(ctx.utf8());
        // one entry per UTF-8 corner value, so every string width also appears as a single-element list
        for (String s : ctx.utf8()) entryLists.add(List.of(s));

        return ctx.cross(
                a -> {
                    @SuppressWarnings("unchecked")
                    List<String> entries = (List<String>) a[1];
                    return new ClientboundCustomChatCompletionsPacket((Action) a[0], entries);
                },
                ctx.enumAll(Action.class), entryLists);
    }
}
