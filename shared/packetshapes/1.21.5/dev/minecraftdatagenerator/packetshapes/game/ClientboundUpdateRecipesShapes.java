package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ClientboundUpdateRecipesPacket(Map&lt;ResourceKey&lt;RecipePropertySet&gt;, RecipePropertySet&gt; itemSets,
 * SelectableRecipe.SingleInputSet&lt;StonecutterRecipe&gt; stonecutterRecipes).
 * <p>
 * Wire body:
 * <ul>
 *   <li>{@code itemSets:map(varint count)} of {@code key:ResourceLocation -> RecipePropertySet}, where a
 *       RecipePropertySet is {@code items:list of Holder<Item>(registry id)};</li>
 *   <li>{@code stonecutterRecipes:list} of SingleInputEntry, each
 *       {@code input:Ingredient(HolderSet<Item>), recipe:SelectableRecipe -> optionDisplay:SlotDisplay}
 *       (the {@code noRecipeCodec} path never writes the server-side RecipeHolder).</li>
 * </ul>
 * <p>
 * Coverage: the item-set map at sizes 0/1/2 over the real vanilla property-set keys, with property-set
 * values holding 0, 1 and 2 items; the stonecutter list at sizes 0/1/2; the entry {@code input}
 * ingredient at 1 and 2 items; and the entry {@code optionDisplay} over the {@code Empty} and
 * {@code ItemSlotDisplay} SlotDisplay variants (the full SlotDisplay dispatch is the province of
 * ClientboundPlaceGhostRecipe/ClientboundRecipeBookAdd, not of this packet).
 * <p>
 * The map is built with the same sized {@code HashMap::new} constructor the decoder uses, so the
 * encode/decode/encode round trip is byte-stable.
 */
public class ClientboundUpdateRecipesShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundUpdateRecipesPacket.class;
    }

    @SafeVarargs
    private static Map<ResourceKey<RecipePropertySet>, RecipePropertySet> map(
            Map.Entry<ResourceKey<RecipePropertySet>, RecipePropertySet>... entries) {
        // Mirror the decoder: ByteBufCodecs.map(HashMap::new, ...) sizes the map by the entry count.
        Map<ResourceKey<RecipePropertySet>, RecipePropertySet> m = new HashMap<>(entries.length);
        for (Map.Entry<ResourceKey<RecipePropertySet>, RecipePropertySet> e : entries) m.put(e.getKey(), e.getValue());
        return m;
    }

    private static SelectableRecipe.SingleInputSet<StonecutterRecipe> stonecutter(
            List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> entries) {
        return new SelectableRecipe.SingleInputSet<>(entries);
    }

    private static SelectableRecipe.SingleInputEntry<StonecutterRecipe> entry(Ingredient input, SlotDisplay display) {
        return new SelectableRecipe.SingleInputEntry<>(input,
                new SelectableRecipe<StonecutterRecipe>(display, Optional.empty()));
    }

    /**
     * {@code RecipePropertySet.create} is package-private in 26.1, so the set is built through the
     * class's own public STREAM_CODEC, whose wire form is just a list of item holders.
     */
    private static RecipePropertySet propertySet(ShapeCtx ctx, Item... items) {
        List<Holder<Item>> holders = new ArrayList<>(items.length);
        for (Item i : items) holders.add(i.builtInRegistryHolder());
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), ctx.registries);
        Item.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, holders);
        return RecipePropertySet.STREAM_CODEC.decode(buf);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        Holder<Item> item = ctx.holder(Registries.ITEM);
        SelectableRecipe.SingleInputSet<StonecutterRecipe> noStonecutter = stonecutter(List.of());

        RecipePropertySet emptySet = RecipePropertySet.EMPTY;
        RecipePropertySet oneItem = propertySet(ctx, Items.STONE);
        RecipePropertySet twoItems = propertySet(ctx, Items.STONE, Items.DIRT);

        // --- itemSets map: sizes 0 / 1 / 2, and property-set values of 0 / 1 / 2 items ---
        out.add(new ClientboundUpdateRecipesPacket(map(), noStonecutter));
        for (RecipePropertySet value : List.of(emptySet, oneItem, twoItems)) {
            out.add(new ClientboundUpdateRecipesPacket(
                    map(Map.entry(RecipePropertySet.FURNACE_INPUT, value)), noStonecutter));
        }
        out.add(new ClientboundUpdateRecipesPacket(map(
                Map.entry(RecipePropertySet.FURNACE_INPUT, emptySet),
                Map.entry(RecipePropertySet.SMITHING_BASE, oneItem)), noStonecutter));
        // Every vanilla property-set key appears at least once. 26.2 declares exactly seven
        // (BREWING_INPUTS / BREWING_REAGENTS only exist in a later version).
        for (ResourceKey<RecipePropertySet> key : List.of(
                RecipePropertySet.SMITHING_BASE,
                RecipePropertySet.SMITHING_TEMPLATE,
                RecipePropertySet.SMITHING_ADDITION,
                RecipePropertySet.FURNACE_INPUT,
                RecipePropertySet.BLAST_FURNACE_INPUT,
                RecipePropertySet.SMOKER_INPUT,
                RecipePropertySet.CAMPFIRE_INPUT)) {
            out.add(new ClientboundUpdateRecipesPacket(map(Map.entry(key, oneItem)), noStonecutter));
        }

        // --- stonecutterRecipes: list sizes 0 / 1 / 2 ---
        Ingredient oneItemIngredient = Ingredient.of(Items.STONE);
        Ingredient twoItemIngredient = Ingredient.of(Items.STONE, Items.DIRT);
        out.add(new ClientboundUpdateRecipesPacket(map(), stonecutter(List.of(
                entry(oneItemIngredient, SlotDisplay.Empty.INSTANCE)))));
        out.add(new ClientboundUpdateRecipesPacket(map(), stonecutter(List.of(
                entry(oneItemIngredient, SlotDisplay.Empty.INSTANCE),
                entry(twoItemIngredient, new SlotDisplay.ItemSlotDisplay(item))))));

        // --- stonecutter entry: ingredient width and SlotDisplay variant ---
        for (Ingredient input : List.of(oneItemIngredient, twoItemIngredient)) {
            for (SlotDisplay display : List.of(SlotDisplay.Empty.INSTANCE, new SlotDisplay.ItemSlotDisplay(item))) {
                out.add(new ClientboundUpdateRecipesPacket(map(), stonecutter(List.of(entry(input, display)))));
            }
        }

        // --- both fields populated at once ---
        out.add(new ClientboundUpdateRecipesPacket(
                map(Map.entry(RecipePropertySet.FURNACE_INPUT, oneItem)),
                stonecutter(List.of(entry(oneItemIngredient, new SlotDisplay.ItemSlotDisplay(item))))));

        return out;
    }
}
