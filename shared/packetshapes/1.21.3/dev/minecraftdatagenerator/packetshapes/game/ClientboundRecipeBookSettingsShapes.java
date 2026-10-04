package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundRecipeBookSettingsPacket(RecipeBookSettings bookSettings). The wire form is four
 * {@code (open:bool, filtering:bool)} pairs, one per {@link RecipeBookType} in declaration order
 * (crafting, furnace, blast_furnace, smoker).
 * <p>
 * Eight independent booleans: the full 2^8 = 256 cartesian is emitted, so every combination of the four
 * per-type {@code (open, filtering)} states is covered.
 */
public class ClientboundRecipeBookSettingsShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundRecipeBookSettingsPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        RecipeBookType[] types = RecipeBookType.values();
        List<Packet<?>> out = new ArrayList<>();
        for (int mask = 0; mask < (1 << (2 * types.length)); mask++) {
            RecipeBookSettings settings = new RecipeBookSettings();
            for (int i = 0; i < types.length; i++) {
                settings.setOpen(types[i], (mask & (1 << (2 * i))) != 0);
                settings.setFiltering(types[i], (mask & (1 << (2 * i + 1))) != 0);
            }
            out.add(new ClientboundRecipeBookSettingsPacket(settings));
        }
        return out;
    }
}
