package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.ArrayList;
import java.util.List;

/** ClientboundRecipeBookRemovePacket(List&lt;RecipeDisplayId&gt; recipes) — {@code count:varint} followed
 *  by one {@code index:varint} per entry. Collection sizes 0/1/2/many, plus a VarInt byte-length boundary
 *  sweep on the single-entry form. */
public class ClientboundRecipeBookRemoveShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundRecipeBookRemovePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        out.add(new ClientboundRecipeBookRemovePacket(List.of()));
        for (int index : ctx.varInts()) {
            out.add(new ClientboundRecipeBookRemovePacket(List.of(new RecipeDisplayId(index))));
        }
        out.add(new ClientboundRecipeBookRemovePacket(List.of(new RecipeDisplayId(0), new RecipeDisplayId(1))));
        List<RecipeDisplayId> many = new ArrayList<>();
        for (int index : ctx.varInts()) many.add(new RecipeDisplayId(index));
        out.add(new ClientboundRecipeBookRemovePacket(many));
        return out;
    }
}
