package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundContainerSetSlotPacket(int containerId[varint], int stateId[varint], int slot[i16],
 * ItemStack itemStack[optional-item]).
 * <p>
 * The item is written with {@code ItemStack.OPTIONAL_STREAM_CODEC}: an empty stack is a bare {@code 0}
 * count, a non-empty stack is count + item id + the added/removed data-component lists, so the item
 * dimension covers empty, plain, stacked and component-bearing stacks.
 * <p>
 * Categorical cartesian: container id x state id x slot x item. The two VarInt fields are additionally
 * swept over the full VarInt byte-length spread (crossing all four dimensions at full width would be
 * 13 x 13 x 6 x 4 = 4056 shapes, over the budget cap).
 */
public class ClientboundContainerSetSlotShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundContainerSetSlotPacket.class;
    }

    private static List<ItemStack> itemSpread() {
        List<ItemStack> items = new ArrayList<>();
        items.add(ItemStack.EMPTY);
        items.add(new ItemStack(Items.STONE));
        items.add(new ItemStack(Items.STONE, 64));
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("x"));
        items.add(named);
        return items;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<ItemStack> items = itemSpread();
        List<Integer> containerIds = List.of(0, 1, -1);
        List<Integer> stateIds = List.of(0, 1, 127, 128);

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundContainerSetSlotPacket((int) a[0], (int) a[1], (short) a[2], (ItemStack) a[3]),
                containerIds, stateIds, ctx.slots(), items));

        // per-field sweeps for the two VarInt fields and the raw short range of the slot
        ItemStack baseItem = items.get(1);
        for (int containerId : ctx.varInts()) {
            out.add(new ClientboundContainerSetSlotPacket(containerId, 0, 0, baseItem));
        }
        for (int stateId : ctx.varInts()) {
            out.add(new ClientboundContainerSetSlotPacket(0, stateId, 0, baseItem));
        }
        for (short slot : ctx.shorts()) {
            out.add(new ClientboundContainerSetSlotPacket(0, 0, slot, baseItem));
        }
        return out;
    }
}
