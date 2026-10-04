package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * ServerboundRecipeBookSeenRecipePacket(RecipeHolder&lt;?&gt; recipe) — the whole body is
 * {@code writeResourceLocation(recipe.id())}, so only the holder's id matters and the synthetic
 * holder from {@link ClientboundPlaceGhostRecipeShapes#holder} is byte-equivalent to a real one.
 * <p>
 * 1.20.5 sends the recipe's {@link ResourceLocation}; 1.21.2 replaced it with a var-int
 * {@code RecipeDisplayId}, which is why this is a string sweep rather than a var-int sweep.
 * <p>
 * Swept over the same resource-location spread as the other recipe-id packets.
 */
public class ServerboundRecipeBookSeenRecipeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundRecipeBookSeenRecipePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ClientboundPlaceGhostRecipeShapes.recipeIds(ctx),
                id -> new ServerboundRecipeBookSeenRecipePacket(
                        ClientboundPlaceGhostRecipeShapes.holder(id)));
    }
}
