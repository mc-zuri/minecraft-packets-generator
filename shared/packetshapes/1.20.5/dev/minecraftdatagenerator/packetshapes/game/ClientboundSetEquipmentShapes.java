package dev.minecraftdatagenerator.packetshapes.game;

import com.mojang.datafixers.util.Pair;
import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetEquipmentPacket(int entity, List&lt;Pair&lt;EquipmentSlot, ItemStack&gt;&gt; slots) —
 * wire body is {@code entity:varInt} then one-or-more slot entries, each
 * {@code slotId:byte (bit 0x80 = "another entry follows"), item:OPTIONAL_ItemStack}. The last entry
 * clears the continue bit.
 * NOTE: the slot list must be non-empty — an empty list writes zero entries and the decoder's
 * do/while would read past the buffer, so that case is deliberately omitted.
 * Covers the full cross of every {@link EquipmentSlot} with the item spread (empty / count 1 /
 * count 64 / component-bearing), a VarInt byte-length sweep on the entity id, and multi-entry lists
 * (2 entries and all 8 slots) that exercise the continue bit.
 */
public class ClientboundSetEquipmentShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetEquipmentPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<ItemStack> stacks = new ArrayList<>(ctx.items());
        ItemStack named = new ItemStack(Items.STONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("x"));
        stacks.add(named);
        ItemStack stone = new ItemStack(Items.STONE);

        List<Packet<?>> out = new ArrayList<>();

        // Every equipment slot x every item form, as a single-entry list (continue bit clear).
        for (EquipmentSlot slot : ctx.enumAll(EquipmentSlot.class)) {
            for (ItemStack stack : stacks) {
                out.add(new ClientboundSetEquipmentPacket(1, List.of(Pair.of(slot, stack))));
            }
        }

        // Two-entry list: the first entry carries the 0x80 continue bit.
        out.add(new ClientboundSetEquipmentPacket(1, List.of(
                Pair.of(EquipmentSlot.HEAD, stone),
                Pair.of(EquipmentSlot.CHEST, stone))));

        // All slots in one packet (longest continue-bit chain).
        List<Pair<EquipmentSlot, ItemStack>> allSlots = new ArrayList<>();
        for (EquipmentSlot slot : ctx.enumAll(EquipmentSlot.class)) {
            allSlots.add(Pair.of(slot, stone));
        }
        out.add(new ClientboundSetEquipmentPacket(1, allSlots));

        // Entity-id sweep across every VarInt byte-length boundary, with a fixed single slot.
        for (int id : ctx.varInts()) {
            out.add(new ClientboundSetEquipmentPacket(id, List.of(Pair.of(EquipmentSlot.MAINHAND, stone))));
        }
        return out;
    }
}
