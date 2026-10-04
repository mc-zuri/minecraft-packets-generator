package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetCursorItemPacket(ItemStack contents) — single item field encoded with
 * {@code ItemStack.OPTIONAL_STREAM_CODEC}, whose wire form is
 * {@code count:varInt} and, when non-zero, {@code item:varInt-registry-id, addedComponents:varInt,
 * removedComponents:varInt, <component entries>}.
 * Covers the empty (count 0, body ends) branch, non-empty stacks with count 1 and 64, and a stack
 * carrying a patched data component so the non-zero added-component-count branch appears too.
 */
public class ClientboundSetCursorItemShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetCursorItemPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<ItemStack> stacks = new ArrayList<>(ctx.items());
        ItemStack named = new ItemStack(Items.STONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("x"));
        stacks.add(named);
        return ctx.each(stacks, ClientboundSetCursorItemPacket::new);
    }
}
