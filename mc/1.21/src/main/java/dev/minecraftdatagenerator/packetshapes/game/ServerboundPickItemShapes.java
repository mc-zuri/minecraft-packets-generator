package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ServerboundPickItemPacket(int slot). Wire body: {@code slot:varint} — a single field, so the whole
 * shape space is that one var-int.
 * <p>
 * 1.21 is the last family to carry this packet: 1.21.5 split it into
 * {@code ServerboundPickItemFromBlockPacket} / {@code ServerboundPickItemFromEntityPacket}, which pair
 * a position/entity id with an {@code includeData} bool. Here there is nothing to cross, so the
 * coverage is the union of
 * <ul>
 *   <li>every VarInt byte-length boundary ({@code varInts()}) — the only thing the encoding can
 *       distinguish;</li>
 *   <li>the raw i32 corner cases ({@code ints()}), including the negatives that encode as the
 *       maximal 5-byte var-int;</li>
 *   <li>the real inventory hot-bar / container slot spread ({@code slots()}), which is the domain this
 *       field actually takes.</li>
 * </ul>
 * Duplicates are collapsed so each distinct wire value appears exactly once.
 */
public class ServerboundPickItemShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPickItemPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Set<Integer> slots = new LinkedHashSet<>(ctx.varInts());
        slots.addAll(ctx.ints());
        for (short s : ctx.slots()) slots.add((int) s);

        List<Packet<?>> out = new ArrayList<>(slots.size());
        for (int slot : slots) out.add(new ServerboundPickItemPacket(slot));
        return out;
    }
}
