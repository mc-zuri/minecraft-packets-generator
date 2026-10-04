package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundContainerClickPacket(int containerId, int stateId, int slotNum, int buttonNum,
 * ClickType clickType, ItemStack carriedItem, Int2ObjectMap&lt;ItemStack&gt; changedSlots).
 * <p>
 * Wire body: {@code containerId:CONTAINER_ID(varint), stateId:varint, slotNum:i16, buttonNum:i8,
 * clickType:varint enum ordinal (written with writeEnum),
 * changedSlots:map(varint count, max 128) of {slot:i16 -> ItemStack},
 * carriedItem:ItemStack}. {@code ItemStack.OPTIONAL_STREAM_CODEC} leads with the count as a var-int
 * and encodes the empty stack as a bare {@code 0}; a non-empty stack continues with
 * {@code item:Holder<Item>(registry id)} and a {@code DataComponentPatch}.
 * <p>
 * NOTE: 1.21.3 puts real {@code ItemStack}s on this packet. The hashed forms
 * ({@code HashedStack}/{@code HashedPatchMap}, where the client sends only component hashes) arrived
 * in 1.21.5, so the component coverage here is expressed as {@code DataComponentPatch} add/remove
 * branches on a real stack instead.
 * <p>
 * Coverage:
 * <ul>
 *   <li>categorical cartesian: every {@link ClickType} constant x the four distinguishable
 *       {@code carriedItem} forms (EMPTY, a plain stack with an empty patch, a stack whose patch
 *       <i>adds</i> a component, and a stack whose patch <i>removes</i> one);</li>
 *   <li>{@code containerId} and {@code stateId} over every VarInt byte-length boundary
 *       ({@code FriendlyByteBuf.writeContainerId} is a plain VarInt);</li>
 *   <li>{@code slotNum} over the container-slot spread plus the raw i16 edge cases;</li>
 *   <li>{@code buttonNum} over the byte edge cases;</li>
 *   <li>{@code changedSlots} at sizes 0/1/2 (including the -1 "outside" slot key) mixing EMPTY and
 *       present stacks;</li>
 *   <li>the stack {@code count} over every positive VarInt byte-length boundary — a count of 0 or
 *       less is the empty-stack encoding, already covered categorically.</li>
 * </ul>
 * {@code changedSlots} is built with the same sized {@code Int2ObjectOpenHashMap::new} constructor the
 * decoder uses, so iteration order — and therefore the encoded byte sequence — survives the round trip.
 */
public class ServerboundContainerClickShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundContainerClickPacket.class;
    }

    private static Int2ObjectMap<ItemStack> slots(int[] keys, ItemStack[] values) {
        // Mirror the decoder: ByteBufCodecs.map(Int2ObjectOpenHashMap::new, ...) sizes by entry count.
        Int2ObjectMap<ItemStack> m = new Int2ObjectOpenHashMap<>(keys.length);
        for (int i = 0; i < keys.length; i++) m.put(keys[i], values[i]);
        return m;
    }

    private static Int2ObjectMap<ItemStack> noSlots() {
        return slots(new int[0], new ItemStack[0]);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        Holder<Item> item = ctx.holder(net.minecraft.core.registries.Registries.ITEM);

        ItemStack empty = ItemStack.EMPTY;
        ItemStack plainItem = new ItemStack(item, 1, DataComponentPatch.EMPTY);
        ItemStack patchedItem = new ItemStack(item, 1,
                DataComponentPatch.builder().set(DataComponents.ITEM_NAME, Component.literal("x")).build());
        ItemStack removalItem = new ItemStack(item, 1,
                DataComponentPatch.builder().remove(DataComponents.ITEM_NAME).build());

        // --- categorical cartesian: every ClickType x every carriedItem form ---
        for (ClickType clickType : ctx.enumAll(ClickType.class)) {
            for (ItemStack carried : List.of(empty, plainItem, patchedItem, removalItem)) {
                out.add(new ServerboundContainerClickPacket(0, 0, 0, 0,
                        clickType, carried, noSlots()));
            }
        }

        // --- containerId (CONTAINER_ID = plain varint) ---
        for (int v : ctx.varInts()) {
            out.add(new ServerboundContainerClickPacket(v, 0, 0, 0,
                    ClickType.PICKUP, empty, noSlots()));
        }
        // --- stateId (varint) ---
        for (int v : ctx.varInts()) {
            out.add(new ServerboundContainerClickPacket(0, v, 0, 0,
                    ClickType.PICKUP, empty, noSlots()));
        }
        // --- slotNum: container-slot spread, then the raw i16 edges ---
        for (short v : ctx.slots()) {
            out.add(new ServerboundContainerClickPacket(0, 0, v, 0,
                    ClickType.PICKUP, empty, noSlots()));
        }
        for (short v : ctx.shorts()) {
            out.add(new ServerboundContainerClickPacket(0, 0, v, 0,
                    ClickType.PICKUP, empty, noSlots()));
        }
        // --- buttonNum (i8) ---
        for (byte v : ctx.bytes()) {
            out.add(new ServerboundContainerClickPacket(0, 0, 0, v,
                    ClickType.PICKUP, empty, noSlots()));
        }

        // --- changedSlots at sizes 1 and 2 (size 0 covered above), mixing stack forms and slot keys ---
        out.add(new ServerboundContainerClickPacket(0, 0, 0, 0, ClickType.PICKUP, empty,
                slots(new int[]{0}, new ItemStack[]{empty})));
        out.add(new ServerboundContainerClickPacket(0, 0, 0, 0, ClickType.PICKUP, empty,
                slots(new int[]{0}, new ItemStack[]{plainItem})));
        out.add(new ServerboundContainerClickPacket(0, 0, 0, 0, ClickType.PICKUP, empty,
                slots(new int[]{-1}, new ItemStack[]{patchedItem})));
        out.add(new ServerboundContainerClickPacket(0, 0, 0, 0, ClickType.PICKUP, plainItem,
                slots(new int[]{0, 45}, new ItemStack[]{plainItem, empty})));

        // --- stack count over every positive VarInt byte-length boundary ---
        for (int count : ctx.varInts()) {
            if (count <= 0) continue; // count <= 0 is the empty-stack encoding
            out.add(new ServerboundContainerClickPacket(0, 0, 0, 0, ClickType.PICKUP,
                    new ItemStack(item, count, DataComponentPatch.EMPTY), noSlots()));
        }

        return out;
    }
}
