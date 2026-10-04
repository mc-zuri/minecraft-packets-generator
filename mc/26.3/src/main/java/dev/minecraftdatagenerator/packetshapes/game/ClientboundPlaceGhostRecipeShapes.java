package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
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
 * {@code SlotDisplay} nested inside it. Both are closed sets in 26.3, so both are enumerated in full:
 * <ul>
 *   <li>RecipeDisplay (5): {@code crafting_shapeless}, {@code crafting_shaped}, {@code furnace},
 *       {@code stonecutter}, {@code smithing}.</li>
 *   <li>SlotDisplay (11): {@code empty}, {@code any_fuel}, {@code with_any_potion},
 *       {@code only_with_component}, {@code item}, {@code item_stack}, {@code tag}, {@code dyed},
 *       {@code smithing_trim}, {@code with_remainder}, {@code composite}.</li>
 * </ul>
 * Every SlotDisplay branch is emitted through a stonecutter display (the cheapest carrier), and each
 * RecipeDisplay branch is emitted with representative slots plus sweeps of its own scalar/collection
 * fields (shaped width/height and ingredient count, shapeless ingredient count, furnace duration
 * VarInt boundaries and f32 experience), all crossed with the containerId VarInt boundaries at the
 * top level.
 * <p>
 * The {@code tag} slot display covers both wire branches of {@code ByteBufCodecs.holderSet}: a named
 * tag (varint 0 then the tag id) and a direct holder list of size 0/1/2.
 * <p>
 * OMITTED: a {@code ShapedCraftingRecipeDisplay} whose ingredient count differs from width*height —
 * its record constructor throws, so only consistent shapes are emitted.
 */
public class ClientboundPlaceGhostRecipeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlaceGhostRecipePacket.class;
    }

    /** Every registered SlotDisplay branch, plus the collection/holder-set sub-branches. */
    private static List<SlotDisplay> slotDisplays(ShapeCtx ctx) {
        SlotDisplay empty = SlotDisplay.Empty.INSTANCE;
        SlotDisplay item = new SlotDisplay.ItemSlotDisplay(ctx.holder(Registries.ITEM));
        SlotDisplay stone = new SlotDisplay.ItemSlotDisplay(Items.STONE.builtInRegistryHolder());
        DataComponentType<?> componentType = ctx.anyOf(Registries.DATA_COMPONENT_TYPE);
        HolderSet<Item> namedTag = ctx.registries.lookupOrThrow(Registries.ITEM)
                .getTags().findFirst().orElse(null);

        List<SlotDisplay> out = new ArrayList<>();
        out.add(empty);                                                     // empty
        out.add(SlotDisplay.AnyFuel.INSTANCE);                              // any_fuel
        out.add(new SlotDisplay.WithAnyPotion(empty));                      // with_any_potion
        out.add(new SlotDisplay.WithAnyPotion(stone));
        out.add(new SlotDisplay.OnlyWithComponent(empty, componentType));   // only_with_component
        out.add(new SlotDisplay.OnlyWithComponent(stone, componentType));
        out.add(item);                                                      // item
        out.add(stone);
        out.add(new SlotDisplay.ItemStackSlotDisplay(new ItemStackTemplate(Items.STONE)));       // item_stack
        out.add(new SlotDisplay.ItemStackSlotDisplay(new ItemStackTemplate(Items.STONE, 64)));
        out.add(new SlotDisplay.TagSlotDisplay(HolderSet.direct(List.<Holder<Item>>of())));                             // tag, direct 0
        out.add(new SlotDisplay.TagSlotDisplay(HolderSet.direct(List.<Holder<Item>>of(Items.STONE.builtInRegistryHolder()))));
        out.add(new SlotDisplay.TagSlotDisplay(HolderSet.direct(List.<Holder<Item>>of(
                Items.STONE.builtInRegistryHolder(), Items.DIRT.builtInRegistryHolder()))));
        if (namedTag != null) out.add(new SlotDisplay.TagSlotDisplay(namedTag));                 // tag, named
        out.add(new SlotDisplay.DyedSlotDemo(empty, empty));                                     // dyed
        out.add(new SlotDisplay.DyedSlotDemo(stone, item));
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
