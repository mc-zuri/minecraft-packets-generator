package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.List;

/**
 * ServerboundRecipeBookSeenRecipePacket(RecipeDisplayId recipe) — a single field, encoded by
 * {@code RecipeDisplayId.STREAM_CODEC} as {@code ByteBufCodecs.VAR_INT} over its {@code index}.
 * Swept over every VarInt byte-length boundary (including the negative 5-byte forms).
 */
public class ServerboundRecipeBookSeenRecipeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundRecipeBookSeenRecipePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), i -> new ServerboundRecipeBookSeenRecipePacket(new RecipeDisplayId(i)));
    }
}
