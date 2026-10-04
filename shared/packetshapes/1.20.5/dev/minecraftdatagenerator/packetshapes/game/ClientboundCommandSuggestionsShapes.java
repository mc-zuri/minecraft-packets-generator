package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket.Entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ClientboundCommandSuggestionsPacket(int id[varint], int start[varint], int length[varint],
 * List&lt;Entry&gt; suggestions[list]). Entry(String text[utf8], Optional&lt;Component&gt; tooltip).
 * <p>
 * Categorical dimensions: the entry list's {@code Optional} tooltip (both the empty and the present
 * branch of the bool prefix) crossed with the UTF-8 text spread and with every corner Component, plus
 * list sizes 0, 1 and 2. The three VarInt header fields are swept over the full VarInt byte-length
 * spread one at a time (crossing them would be 13^3 = 2197 shapes before the list dimension).
 */
public class ClientboundCommandSuggestionsShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundCommandSuggestionsPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<List<Entry>> entryLists = new ArrayList<>();

        // size 0
        entryLists.add(List.of());
        // size 1: every UTF-8 text width x the empty/present tooltip branch
        for (String text : ctx.utf8()) {
            entryLists.add(List.of(new Entry(text, Optional.empty())));
            entryLists.add(List.of(new Entry(text, Optional.of(Component.literal("tip")))));
        }
        // size 1: every corner Component as the present tooltip
        for (Component tooltip : ctx.components()) {
            entryLists.add(List.of(new Entry("x", Optional.of(tooltip))));
        }
        // size 2, mixing both optional branches
        entryLists.add(List.of(new Entry("a", Optional.of(Component.literal("tip"))),
                new Entry("", Optional.empty())));

        List<Packet<?>> out = new ArrayList<>();
        for (List<Entry> entries : entryLists) {
            out.add(new ClientboundCommandSuggestionsPacket(0, 0, 0, entries));
        }

        List<Entry> base = entryLists.get(entryLists.size() - 1);
        for (int id : ctx.varInts()) {
            out.add(new ClientboundCommandSuggestionsPacket(id, 0, 0, base));
        }
        for (int start : ctx.varInts()) {
            out.add(new ClientboundCommandSuggestionsPacket(0, start, 0, base));
        }
        for (int length : ctx.varInts()) {
            out.add(new ClientboundCommandSuggestionsPacket(0, 0, length, base));
        }
        return out;
    }
}
