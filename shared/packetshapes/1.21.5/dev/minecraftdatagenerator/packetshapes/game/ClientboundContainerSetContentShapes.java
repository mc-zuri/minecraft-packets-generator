package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundContainerSetContentPacket(int containerId[varint], int stateId[varint],
 * List&lt;ItemStack&gt; items[optional-item list], ItemStack carriedItem[optional-item]).
 * <p>
 * Both item fields use {@code ItemStack.OPTIONAL_*_STREAM_CODEC}: an empty stack is a bare {@code 0}
 * count, a non-empty stack is count + item id + added/removed data-component lists. The item spread
 * therefore covers empty, plain, stacked and component-bearing stacks, and the list dimension covers
 * sizes 0, 1 and 3 (including an embedded empty slot).
 * <p>
 * Categorical cartesian: container id x state id x item list x carried item, with the two VarInt fields
 * additionally swept over the full VarInt byte-length spread.
 */
public class ClientboundContainerSetContentShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundContainerSetContentPacket.class;
    }

    private static ItemStack named() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("x"));
        return stack;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<ItemStack> carried = List.of(
                ItemStack.EMPTY, new ItemStack(Items.STONE), new ItemStack(Items.STONE, 64), named());

        List<List<ItemStack>> itemLists = List.of(
                List.of(),
                List.of(new ItemStack(Items.STONE)),
                List.of(ItemStack.EMPTY, new ItemStack(Items.STONE)),
                List.of(ItemStack.EMPTY, new ItemStack(Items.STONE, 64), named()));

        List<Integer> containerIds = List.of(0, 1, -1);
        List<Integer> stateIds = List.of(0, 1, 127, 128);

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> {
                    @SuppressWarnings("unchecked")
                    List<ItemStack> items = (List<ItemStack>) a[2];
                    return new ClientboundContainerSetContentPacket((int) a[0], (int) a[1], items, (ItemStack) a[3]);
                },
                containerIds, stateIds, itemLists, carried));

        List<ItemStack> baseItems = itemLists.get(1);
        ItemStack baseCarried = carried.get(1);
        for (int containerId : ctx.varInts()) {
            out.add(new ClientboundContainerSetContentPacket(containerId, 0, baseItems, baseCarried));
        }
        for (int stateId : ctx.varInts()) {
            out.add(new ClientboundContainerSetContentPacket(0, stateId, baseItems, baseCarried));
        }
        return out;
    }
}
