package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ServerboundContainerClickPacket(int containerId, int stateId, short slotNum, byte buttonNum,
 * ClickType clickType, Int2ObjectMap&lt;HashedStack&gt; changedSlots, HashedStack carriedItem).
 * <p>
 * Wire body: {@code containerId:CONTAINER_ID(varint), stateId:varint, slotNum:i16, buttonNum:i8,
 * clickType:varint id (ClickType.STREAM_CODEC is an id-mapper),
 * changedSlots:map(varint count, max 128) of {slot:i16 -> HashedStack},
 * carriedItem:HashedStack}. A HashedStack is {@code optional(bool)} of
 * {@code {item:Holder<Item>(registry id), count:varint,
 * components:{added:map(varint count, max 256) of registry-id -> i32, removed:collection(max 256) of
 * registry-id}}} — the absent optional is {@code HashedStack.EMPTY}.
 * <p>
 * Coverage:
 * <ul>
 *   <li>categorical cartesian: every {@link ClickType} constant x the three distinguishable
 *       {@code carriedItem} forms (EMPTY, ActualItem with an empty patch, ActualItem with a non-empty
 *       patch that has both an added and a removed component);</li>
 *   <li>{@code containerId} and {@code stateId} over every VarInt byte-length boundary
 *       ({@code FriendlyByteBuf.writeContainerId} is a plain VarInt);</li>
 *   <li>{@code slotNum} over the container-slot spread plus the raw i16 edge cases;</li>
 *   <li>{@code buttonNum} over the byte edge cases;</li>
 *   <li>{@code changedSlots} at sizes 0/1/2 (including the -1 "outside" slot key) mixing EMPTY and
 *       present HashedStacks;</li>
 *   <li>the ActualItem {@code count} over every VarInt byte-length boundary.</li>
 * </ul>
 * {@code changedSlots} is built with the same sized {@code Int2ObjectOpenHashMap::new} constructor the
 * decoder uses, so iteration order — and therefore the encoded byte sequence — survives the round trip.
 */
public class ServerboundContainerClickShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundContainerClickPacket.class;
    }

    private static Int2ObjectMap<HashedStack> slots(int[] keys, HashedStack[] values) {
        // Mirror the decoder: ByteBufCodecs.map(Int2ObjectOpenHashMap::new, ...) sizes by entry count.
        Int2ObjectMap<HashedStack> m = new Int2ObjectOpenHashMap<>(keys.length);
        for (int i = 0; i < keys.length; i++) m.put(keys[i], values[i]);
        return m;
    }

    private static Int2ObjectMap<HashedStack> noSlots() {
        return slots(new int[0], new HashedStack[0]);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        Holder<Item> item = ctx.holder(Registries.ITEM);
        List<Holder.Reference<DataComponentType<?>>> componentTypes =
                ctx.registries.lookupOrThrow(Registries.DATA_COMPONENT_TYPE).listElements().limit(2).toList();
        DataComponentType<?> added = componentTypes.get(0).value();
        DataComponentType<?> removed = componentTypes.get(componentTypes.size() > 1 ? 1 : 0).value();

        HashedPatchMap emptyPatch = new HashedPatchMap(Map.of(), Set.of());
        // Sized HashMap/HashSet, matching ByteBufCodecs.map(HashMap::new)/collection(HashSet::new);
        // kept to a single entry each so the hash iteration order cannot drift.
        Map<DataComponentType<?>, Integer> addedMap = new HashMap<>(1);
        addedMap.put(added, 0x12345678);
        Set<DataComponentType<?>> removedSet = new HashSet<>(1);
        removedSet.add(removed);
        HashedPatchMap fullPatch = new HashedPatchMap(addedMap, removedSet);

        HashedStack empty = HashedStack.EMPTY;
        HashedStack plainItem = new HashedStack.ActualItem(item, 1, emptyPatch);
        HashedStack patchedItem = new HashedStack.ActualItem(item, 1, fullPatch);

        // --- categorical cartesian: every ClickType x every carriedItem form ---
        for (ClickType clickType : ctx.enumAll(ClickType.class)) {
            for (HashedStack carried : List.of(empty, plainItem, patchedItem)) {
                out.add(new ServerboundContainerClickPacket(0, 0, (short) 0, (byte) 0,
                        clickType, noSlots(), carried));
            }
        }

        // --- containerId (CONTAINER_ID = plain varint) ---
        for (int v : ctx.varInts()) {
            out.add(new ServerboundContainerClickPacket(v, 0, (short) 0, (byte) 0,
                    ClickType.PICKUP, noSlots(), empty));
        }
        // --- stateId (varint) ---
        for (int v : ctx.varInts()) {
            out.add(new ServerboundContainerClickPacket(0, v, (short) 0, (byte) 0,
                    ClickType.PICKUP, noSlots(), empty));
        }
        // --- slotNum: container-slot spread, then the raw i16 edges ---
        for (short v : ctx.slots()) {
            out.add(new ServerboundContainerClickPacket(0, 0, v, (byte) 0,
                    ClickType.PICKUP, noSlots(), empty));
        }
        for (short v : ctx.shorts()) {
            out.add(new ServerboundContainerClickPacket(0, 0, v, (byte) 0,
                    ClickType.PICKUP, noSlots(), empty));
        }
        // --- buttonNum (i8) ---
        for (byte v : ctx.bytes()) {
            out.add(new ServerboundContainerClickPacket(0, 0, (short) 0, v,
                    ClickType.PICKUP, noSlots(), empty));
        }

        // --- changedSlots at sizes 1 and 2 (size 0 covered above), mixing stack forms and slot keys ---
        out.add(new ServerboundContainerClickPacket(0, 0, (short) 0, (byte) 0, ClickType.PICKUP,
                slots(new int[]{0}, new HashedStack[]{empty}), empty));
        out.add(new ServerboundContainerClickPacket(0, 0, (short) 0, (byte) 0, ClickType.PICKUP,
                slots(new int[]{0}, new HashedStack[]{plainItem}), empty));
        out.add(new ServerboundContainerClickPacket(0, 0, (short) 0, (byte) 0, ClickType.PICKUP,
                slots(new int[]{-1}, new HashedStack[]{patchedItem}), empty));
        out.add(new ServerboundContainerClickPacket(0, 0, (short) 0, (byte) 0, ClickType.PICKUP,
                slots(new int[]{0, 45}, new HashedStack[]{plainItem, empty}), plainItem));

        // --- ActualItem count over every VarInt byte-length boundary ---
        for (int count : ctx.varInts()) {
            out.add(new ServerboundContainerClickPacket(0, 0, (short) 0, (byte) 0, ClickType.PICKUP,
                    noSlots(), new HashedStack.ActualItem(item, count, emptyPatch)));
        }

        return out;
    }
}
