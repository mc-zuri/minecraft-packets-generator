package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundPlaceGhostRecipePacket(int containerId, RecipeHolder&lt;?&gt; recipe).
 * <p>
 * Wire: {@code writeByte(containerId)} then {@code writeResourceLocation(recipe.id())} — a plain
 * length-prefixed UTF-8 string. Only the holder's id is serialized, so a synthetic
 * {@link RecipeHolder} with a null value is byte-equivalent to a real one.
 * <p>
 * 1.21 identifies the recipe by its {@link ResourceLocation}; from 1.21.2 the packet carries a
 * {@code RecipeDisplay} tree plus a var-int {@code RecipeDisplayId} instead, so there is no display
 * dispatch to enumerate here.
 * <p>
 * Coverage: the full cartesian of the signed-byte {@code containerId} corner cases x a spread of
 * resource locations exercising the default and an explicit namespace, the shortest legal path, a
 * nested path and the full set of characters a path may contain.
 */
public class ClientboundPlaceGhostRecipeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlaceGhostRecipePacket.class;
    }

    /** A holder carrying only an id; the recipe value is never written by this packet. */
    static <T extends Recipe<?>> RecipeHolder<T> holder(ResourceLocation id) {
        return new RecipeHolder<>(id, null);
    }

    /** Resource locations exercising the string form written by {@code writeResourceLocation}. */
    static List<ResourceLocation> recipeIds(ShapeCtx ctx) {
        return List.of(
                ctx.id("a"),                                        // shortest legal path
                ctx.id("stone"),
                ctx.id("stone_sword"),
                ctx.id("gold/ingot_from_nuggets"),                  // nested path
                ctx.id("abcdefghijklmnopqrstuvwxyz0123456789_.-/"), // every legal path character
                ResourceLocation.fromNamespaceAndPath("x", "y"),    // explicit non-default namespace
                ResourceLocation.fromNamespaceAndPath("some_mod.0-9", "deep/nested/path"));
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        for (int containerId : List.of(0, 1, -1, 127, -128)) {
            for (ResourceLocation id : recipeIds(ctx)) {
                out.add(new ClientboundPlaceGhostRecipePacket(containerId, holder(id)));
            }
        }
        return out;
    }
}
