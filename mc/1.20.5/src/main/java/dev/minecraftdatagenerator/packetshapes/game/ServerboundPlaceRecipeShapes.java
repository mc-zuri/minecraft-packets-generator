package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundPlaceRecipePacket(int containerId, RecipeHolder&lt;?&gt; recipe, boolean shiftDown).
 * <p>
 * Wire: {@code writeByte(containerId)}, {@code writeResourceLocation(recipe.id())},
 * {@code writeBoolean(shiftDown)}. Only the holder's id is serialized, so the synthetic holder from
 * {@link ClientboundPlaceGhostRecipeShapes#holder} is byte-equivalent to a real one.
 * <p>
 * 1.20.5 identifies the recipe by {@link ResourceLocation} and the trailing boolean is
 * {@code shiftDown}; 1.21.2 replaced the id with a var-int {@code RecipeDisplayId} and renamed the
 * boolean to {@code useMaxItems}.
 * <p>
 * Coverage: the full cartesian of the signed-byte {@code containerId} corner cases x the resource
 * location spread x both booleans.
 */
public class ServerboundPlaceRecipeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPlaceRecipePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        for (int containerId : List.of(0, 1, -1, 127, -128)) {
            for (ResourceLocation id : ClientboundPlaceGhostRecipeShapes.recipeIds(ctx)) {
                for (boolean shiftDown : ctx.bools()) {
                    out.add(new ServerboundPlaceRecipePacket(containerId,
                            ClientboundPlaceGhostRecipeShapes.holder(id), shiftDown));
                }
            }
        }
        return out;
    }
}
