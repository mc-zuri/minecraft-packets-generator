package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.List;

/**
 * ServerboundPlaceRecipePacket(int containerId[CONTAINER_ID = VarInt],
 * RecipeDisplayId recipe[VarInt index], boolean useMaxItems).
 * <p>
 * Both numeric fields are plain VarInts, so the full cartesian of the VarInt byte-length boundaries on
 * both x both booleans is emitted (13x13x2 = 338).
 */
public class ServerboundPlaceRecipeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPlaceRecipePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundPlaceRecipePacket((int) a[0], new RecipeDisplayId((int) a[1]), (boolean) a[2]),
                ctx.varInts(), ctx.varInts(), ctx.bools());
    }
}
