package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundPlaceGhostRecipePacket(int containerId[varint], RecipeDisplay recipeDisplay).
 * <p>
 * {@code RecipeDisplay} is registry-dispatched (a varint type id selects the branch), and so is every
 * {@code SlotDisplay} nested inside it. Both are closed sets in 1.21.11, so both are enumerated in full:
 * <ul>
 *   <li>RecipeDisplay (5): {@code crafting_shapeless}, {@code crafting_shaped}, {@code furnace},
 *       {@code stonecutter}, {@code smithing}.</li>
 *   <li>SlotDisplay (8): {@code empty}, {@code any_fuel}, {@code item}, {@code item_stack},
 *       {@code tag}, {@code smithing_trim}, {@code with_remainder}, {@code composite}. The
 *       {@code with_any_potion}, {@code only_with_component} and {@code dyed} branches are later
 *       additions and are not registered in 1.21.11.</li>
 * </ul>
 * Every SlotDisplay branch is emitted through a stonecutter display (the cheapest carrier), and each
 * RecipeDisplay branch is emitted with representative slots plus sweeps of its own scalar/collection
 * fields (shaped width/height and ingredient count, shapeless ingredient count, furnace duration
 * VarInt boundaries and f32 experience), all crossed with the containerId VarInt boundaries at the
 * top level.
 * <p>
 * In 1.21.11 the {@code tag} slot display carries a plain {@code TagKey<Item>}, so its wire form is just
 * the tag {@code Identifier}. There is NO direct holder-list branch in this version (the field only
 * becomes a {@code HolderSet} later), so that branch is deliberately absent here; several distinct tag
 * ids are emitted instead, exercising the identifier length prefix and a non-default namespace.
 * <p>
 * OMITTED: a {@code ShapedCraftingRecipeDisplay} whose ingredient count differs from width*height —
 * its record constructor throws, so only consistent shapes are emitted.
 */
public class ClientboundPlaceGhostRecipeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlaceGhostRecipePacket.class;
    }

    /** Every registered SlotDisplay branch, plus the collection sub-branches. */
    private static List<SlotDisplay> slotDisplays(ShapeCtx ctx) {
        SlotDisplay empty = SlotDisplay.Empty.INSTANCE;
        SlotDisplay item = new SlotDisplay.ItemSlotDisplay(ctx.holder(Registries.ITEM));
        SlotDisplay stone = new SlotDisplay.ItemSlotDisplay(Items.STONE.builtInRegistryHolder());

        List<SlotDisplay> out = new ArrayList<>();
        out.add(empty);                                                     // empty
        out.add(SlotDisplay.AnyFuel.INSTANCE);                              // any_fuel
        out.add(item);                                                      // item
        out.add(stone);
        out.add(new SlotDisplay.ItemStackSlotDisplay(new ItemStack(Items.STONE)));       // item_stack
        out.add(new SlotDisplay.ItemStackSlotDisplay(new ItemStack(Items.STONE, 64)));
        // tag: a named TagKey<Item> in 1.21.11 - short, long and non-default-namespace ids.
        out.add(new SlotDisplay.TagSlotDisplay(ItemTags.PLANKS));
        out.add(new SlotDisplay.TagSlotDisplay(ItemTags.LOGS));
        out.add(new SlotDisplay.TagSlotDisplay(ItemTags.WOODEN_PRESSURE_PLATES));
        out.add(new SlotDisplay.TagSlotDisplay(TagKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("example", "some/nested/tag"))));
        // with_any_potion, only_with_component and dyed are later additions: SlotDisplays.bootstrap
        // in 1.21.11 registers only the eight branches emitted here, so there is nothing else to cover.
        out.add(new SlotDisplay.SmithingTrimDemoSlotDisplay(empty, empty, ctx.holder(Registries.TRIM_PATTERN)));
        out.add(new SlotDisplay.SmithingTrimDemoSlotDisplay(stone, item, ctx.holder(Registries.TRIM_PATTERN)));
        out.add(new SlotDisplay.WithRemainder(empty, empty));                                    // with_remainder
        out.add(new SlotDisplay.WithRemainder(stone, item));
        out.add(new SlotDisplay.Composite(List.of()));                                           // composite 0
        out.add(new SlotDisplay.Composite(List.of(stone)));                                      // composite 1
        out.add(new SlotDisplay.Composite(List.of(stone, empty, item)));                         // composite 3, nested
        out.add(new SlotDisplay.Composite(List.of(new SlotDisplay.WithRemainder(stone, item)))); // nested dispatch
        return out;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        SlotDisplay empty = SlotDisplay.Empty.INSTANCE;
        SlotDisplay item = new SlotDisplay.ItemSlotDisplay(Items.STONE.builtInRegistryHolder());

        List<RecipeDisplay> displays = new ArrayList<>();
        // Every SlotDisplay branch, carried by a stonecutter display (input, result, craftingStation).
        for (SlotDisplay slot : slotDisplays(ctx)) {
            displays.add(new StonecutterRecipeDisplay(slot, empty, empty));
        }
        // stonecutter: the other two slots too.
        displays.add(new StonecutterRecipeDisplay(empty, item, empty));
        displays.add(new StonecutterRecipeDisplay(empty, empty, item));
        displays.add(new StonecutterRecipeDisplay(item, item, item));

        // crafting_shapeless: ingredient-list cardinality 0/1/2/3.
        displays.add(new ShapelessCraftingRecipeDisplay(List.of(), empty, empty));
        displays.add(new ShapelessCraftingRecipeDisplay(List.of(item), item, empty));
        displays.add(new ShapelessCraftingRecipeDisplay(List.of(item, empty), item, item));
        displays.add(new ShapelessCraftingRecipeDisplay(List.of(item, empty, item), item, empty));

        // crafting_shaped: ingredient count must equal width*height.
        displays.add(new ShapedCraftingRecipeDisplay(0, 0, List.of(), empty, empty));
        displays.add(new ShapedCraftingRecipeDisplay(1, 1, List.of(item), item, empty));
        displays.add(new ShapedCraftingRecipeDisplay(2, 1, List.of(item, empty), item, empty));
        displays.add(new ShapedCraftingRecipeDisplay(1, 2, List.of(item, empty), item, item));
        displays.add(new ShapedCraftingRecipeDisplay(3, 3, List.of(item, empty, item, empty, item, empty, item, empty, item), item, empty));

        // furnace: duration VarInt boundaries and f32 experience corners.
        for (int duration : ctx.varInts()) {
            displays.add(new FurnaceRecipeDisplay(item, item, item, empty, duration, 0.7f));
        }
        for (float experience : ctx.floats()) {
            displays.add(new FurnaceRecipeDisplay(item, item, item, empty, 200, experience));
        }
        displays.add(new FurnaceRecipeDisplay(empty, empty, empty, empty, 0, 0f));

        // smithing: template, base, addition, result, craftingStation.
        displays.add(new SmithingRecipeDisplay(empty, empty, empty, empty, empty));
        displays.add(new SmithingRecipeDisplay(item, empty, item, empty, item));
        displays.add(new SmithingRecipeDisplay(item, item, item, item, item));

        List<Packet<?>> out = new ArrayList<>();
        for (RecipeDisplay display : displays) {
            out.add(new ClientboundPlaceGhostRecipePacket(0, display));
        }
        // containerId: every VarInt byte-length boundary, against a baseline display.
        for (int containerId : ctx.varInts()) {
            out.add(new ClientboundPlaceGhostRecipePacket(containerId, new StonecutterRecipeDisplay(item, item, empty)));
        }
        return out;
    }
}
