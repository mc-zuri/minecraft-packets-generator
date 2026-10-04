package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * ClientboundRecipeBookAddPacket(List&lt;Entry&gt; entries, boolean replace) —
 * wire form {@code entries:varint count + n x Entry}, {@code replace:bool}.
 * <p>
 * {@code Entry} = {@code contents:RecipeDisplayEntry, flags:i8} where flags is a bit field
 * ({@code 1 = notification}, {@code 2 = highlight}).
 * {@code RecipeDisplayEntry} = {@code id:varint, display:RecipeDisplay (registry-dispatched),
 * group:optional varint, category:registry id, craftingRequirements:optional list of Ingredient}.
 * <p>
 * Coverage:
 * <ul>
 *   <li>every {@link SlotDisplay} dispatch branch registered in 1.21.11 (empty, any_fuel, item,
 *       item_stack, tag, with_remainder, composite, smithing_trim) used as the slots of a
 *       stonecutter display;</li>
 *   <li>every {@link RecipeDisplay} dispatch branch (stonecutter, furnace, shaped, shapeless, smithing);</li>
 *   <li>the full {@code flags} x {@code group} present/absent x {@code craftingRequirements}
 *       absent/empty/1/2 cartesian;</li>
 *   <li>a VarInt boundary sweep on the recipe display id;</li>
 *   <li>entry-list sizes 0, 1, 2 and many, each with both {@code replace} values.</li>
 * </ul>
 * In 1.21.11 the {@code tag} slot display holds a plain {@code TagKey<Item>} (wire form: the tag
 * {@code Identifier}); it has no direct holder-list branch, so none is emitted.
 */
public class ClientboundRecipeBookAddShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundRecipeBookAddPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        SlotDisplay empty = SlotDisplay.Empty.INSTANCE;
        SlotDisplay item = new SlotDisplay.ItemSlotDisplay(ctx.holder(Registries.ITEM));
        SlotDisplay stack = new SlotDisplay.ItemStackSlotDisplay(new ItemStack(Items.STONE, 64));

        // Every SlotDisplay dispatch branch.
        List<SlotDisplay> slots = new ArrayList<>();
        slots.add(empty);
        slots.add(SlotDisplay.AnyFuel.INSTANCE);
        slots.add(item);
        slots.add(stack);
        // 1.21.11 tag slot display is a named TagKey<Item> (wire form: just the tag Identifier).
        slots.add(new SlotDisplay.TagSlotDisplay(ItemTags.PLANKS));
        slots.add(new SlotDisplay.TagSlotDisplay(ItemTags.LOGS));
        slots.add(new SlotDisplay.WithRemainder(item, empty));
        slots.add(new SlotDisplay.Composite(List.of()));
        slots.add(new SlotDisplay.Composite(List.of(empty, item)));
        // 1.21.11's SlotDisplay registry has exactly eight branches -- with_any_potion,
        // only_with_component and dyed are later additions and have no wire form here.
        slots.add(new SlotDisplay.SmithingTrimDemoSlotDisplay(item, item, ctx.holder(Registries.TRIM_PATTERN)));

        // Every RecipeDisplay dispatch branch.
        List<RecipeDisplay> displays = new ArrayList<>();
        displays.add(new StonecutterRecipeDisplay(item, item, empty));
        displays.add(new FurnaceRecipeDisplay(item, item, item, empty, 200, 0.7f));
        displays.add(new ShapelessCraftingRecipeDisplay(List.of(), item, empty));
        displays.add(new ShapelessCraftingRecipeDisplay(List.of(item, empty), item, empty));
        // Shaped requires ingredients.size() == width * height.
        displays.add(new ShapedCraftingRecipeDisplay(1, 1, List.of(item), item, empty));
        displays.add(new ShapedCraftingRecipeDisplay(3, 2, List.of(item, empty, item, empty, item, empty), item, stack));
        displays.add(new SmithingRecipeDisplay(item, item, item, item, empty));

        RecipeBookCategory category = ctx.anyOf(Registries.RECIPE_BOOK_CATEGORY);
        Ingredient ingredient = Ingredient.of(Items.STONE);
        List<Optional<List<Ingredient>>> requirements = List.of(
                Optional.empty(),
                Optional.of(List.of()),
                Optional.of(List.of(ingredient)),
                Optional.of(List.of(ingredient, Ingredient.of(Items.DIRT))));
        List<OptionalInt> groups = List.of(OptionalInt.empty(), OptionalInt.of(0), OptionalInt.of(128));
        byte[] flagValues = {0, ClientboundRecipeBookAddPacket.Entry.FLAG_NOTIFICATION,
                ClientboundRecipeBookAddPacket.Entry.FLAG_HIGHLIGHT,
                (byte) (ClientboundRecipeBookAddPacket.Entry.FLAG_NOTIFICATION
                        | ClientboundRecipeBookAddPacket.Entry.FLAG_HIGHLIGHT)};

        List<ClientboundRecipeBookAddPacket.Entry> entries = new ArrayList<>();
        // 1. every SlotDisplay branch.
        for (SlotDisplay slot : slots) {
            entries.add(new ClientboundRecipeBookAddPacket.Entry(
                    new RecipeDisplayEntry(new RecipeDisplayId(0), new StonecutterRecipeDisplay(slot, slot, empty),
                            OptionalInt.empty(), category, Optional.empty()),
                    (byte) 0));
        }
        // 2. every RecipeDisplay branch.
        for (RecipeDisplay display : displays) {
            entries.add(new ClientboundRecipeBookAddPacket.Entry(
                    new RecipeDisplayEntry(new RecipeDisplayId(1), display, OptionalInt.of(3), category,
                            Optional.of(List.of(ingredient))),
                    (byte) 0));
        }
        // 3. flags x group x craftingRequirements cartesian.
        for (byte flags : flagValues) {
            for (OptionalInt group : groups) {
                for (Optional<List<Ingredient>> req : requirements) {
                    entries.add(new ClientboundRecipeBookAddPacket.Entry(
                            new RecipeDisplayEntry(new RecipeDisplayId(2),
                                    new StonecutterRecipeDisplay(item, item, empty), group, category, req),
                            flags));
                }
            }
        }
        // 4. VarInt boundary sweep on the display id.
        for (int id : ctx.varInts()) {
            entries.add(new ClientboundRecipeBookAddPacket.Entry(
                    new RecipeDisplayEntry(new RecipeDisplayId(id),
                            new StonecutterRecipeDisplay(item, item, empty), OptionalInt.empty(), category,
                            Optional.empty()),
                    (byte) 0));
        }

        List<Packet<?>> out = new ArrayList<>();
        for (boolean replace : ctx.bools()) {
            out.add(new ClientboundRecipeBookAddPacket(List.of(), replace));
            for (ClientboundRecipeBookAddPacket.Entry entry : entries) {
                out.add(new ClientboundRecipeBookAddPacket(List.of(entry), replace));
            }
            out.add(new ClientboundRecipeBookAddPacket(List.of(entries.get(0), entries.get(1)), replace));
            out.add(new ClientboundRecipeBookAddPacket(List.copyOf(entries), replace));
        }
        return out;
    }
}
