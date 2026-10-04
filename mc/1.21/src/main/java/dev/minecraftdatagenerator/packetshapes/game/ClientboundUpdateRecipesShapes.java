package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ClientboundUpdateRecipesPacket(Collection&lt;RecipeHolder&lt;?&gt;&gt; recipes) — a var-int-prefixed list
 * of {@code RecipeHolder}, each written as {@code ResourceLocation id} followed by the recipe itself
 * through {@code Recipe.STREAM_CODEC}, which is a registry dispatch on
 * {@code minecraft:recipe_serializer}.
 * <p>
 * 1.21 still sends the full recipe definitions here; 1.21.2 reduced this packet to the
 * {@code RecipePropertySet} tables plus the stonecutter {@code SelectableRecipe} list and moved the
 * recipe-book entries to ClientboundRecipeBookAddPacket, so the dispatch enumerated here is over
 * recipe serializers rather than over slot displays.
 * <p>
 * Coverage: the dispatch has 23 branches in 1.21 (shaped, shapeless, the thirteen
 * {@code crafting_special_*} forms, the four cooking forms, stonecutting, both smithing forms and
 * crafting_decorated_pot). Fabricating a recipe per branch is not possible without inventing
 * ingredient data the codec would reject, so the live recipe manager's recipes are grouped by
 * serializer and emitted as:
 * <ul>
 *   <li>the empty list (size 0);</li>
 *   <li>one single-element packet per distinct serializer found — every dispatch branch;</li>
 *   <li>up to three further single-element packets per serializer, so the per-branch payload varies
 *       (different shaped widths/heights, ingredient counts, cooking times, result stacks);</li>
 *   <li>a two-element packet (size 2) and one packet holding a representative of every serializer at
 *       once, which exercises the list length prefix past a single entry.</li>
 * </ul>
 */
public class ClientboundUpdateRecipesShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundUpdateRecipesPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        out.add(new ClientboundUpdateRecipesPacket(List.of()));   // size 0

        List<RecipeHolder<?>> all = new ArrayList<>();
        try {
            if (ctx.overworld != null) {
                all.addAll(ctx.overworld.getRecipeManager().getRecipes());
            }
        } catch (Throwable ignored) {
        }
        if (all.isEmpty()) return out;

        // Group by serializer so every dispatch branch present in the datapack is represented.
        Map<RecipeSerializer<?>, List<RecipeHolder<?>>> bySerializer = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : all) {
            bySerializer.computeIfAbsent(holder.value().getSerializer(), k -> new ArrayList<>()).add(holder);
        }

        List<RecipeHolder<?>> oneEach = new ArrayList<>();
        for (List<RecipeHolder<?>> group : bySerializer.values()) {
            oneEach.add(group.get(0));
            for (int i = 0; i < Math.min(4, group.size()); i++) {
                out.add(new ClientboundUpdateRecipesPacket(List.of(group.get(i))));
            }
        }

        if (all.size() >= 2) {
            out.add(new ClientboundUpdateRecipesPacket(all.subList(0, 2)));   // size 2
        }
        out.add(new ClientboundUpdateRecipesPacket(oneEach));                 // every branch at once
        return out;
    }
}
