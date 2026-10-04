package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.world.inventory.RecipeBookType;

import java.util.List;

/** ServerboundRecipeBookChangeSettingsPacket(RecipeBookType bookType[enum], boolean isOpen, boolean
 *  isFiltering) — full cross of all book types × both booleans (4×2×2 = 16). */
public class ServerboundRecipeBookChangeSettingsShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundRecipeBookChangeSettingsPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundRecipeBookChangeSettingsPacket((RecipeBookType) a[0], (boolean) a[1], (boolean) a[2]),
                ctx.enumAll(RecipeBookType.class), ctx.bools(), ctx.bools());
    }
}
