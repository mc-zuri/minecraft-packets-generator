package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundRecipePacket(State state, Collection&lt;ResourceLocation&gt; recipes,
 * Collection&lt;ResourceLocation&gt; toHighlight, RecipeBookSettings bookSettings) — the pre-1.21.2
 * recipe-book packet.
 * <p>
 * Wire order: {@code state} as a var-int enum id, then {@code RecipeBookSettings} = two booleans
 * ({@code open}, {@code filtering}) per {@link RecipeBookType} in declaration order, then the
 * {@code recipes} id list (var-int count + one identifier each), and — only when
 * {@code state == INIT} — the {@code toHighlight} id list. That conditional write is the packet's
 * discriminator.
 * <p>
 * 1.21.2 split this packet into ClientboundRecipeBookAddPacket, ClientboundRecipeBookRemovePacket and
 * ClientboundRecipeBookSettingsPacket, so the state discriminator and the settings block only exist
 * together here.
 * <p>
 * Coverage: every {@link ClientboundRecipePacket.State} constant (so the conditional
 * {@code toHighlight} write is exercised both on and off) crossed with recipe-list and
 * highlight-list cardinalities 0/1/2/3 and both extremes of the settings block; then, per state, a
 * sweep of the settings bit field — all flags clear, each of the eight flags set on its own, and all
 * flags set — and a sweep of the identifier spread through both id lists.
 */
public class ClientboundRecipeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundRecipePacket.class;
    }

    private static RecipeBookSettings settings(boolean value) {
        RecipeBookSettings s = new RecipeBookSettings();
        if (value) {
            for (RecipeBookType type : RecipeBookType.values()) {
                s.setOpen(type, true);
                s.setFiltering(type, true);
            }
        }
        return s;
    }

    /** All flags clear, each of the two flags per book type on its own, then all flags set. */
    private static List<RecipeBookSettings> settingsBits() {
        List<RecipeBookSettings> out = new ArrayList<>();
        out.add(settings(false));
        for (RecipeBookType type : RecipeBookType.values()) {
            RecipeBookSettings open = settings(false);
            open.setOpen(type, true);
            out.add(open);
            RecipeBookSettings filtering = settings(false);
            filtering.setFiltering(type, true);
            out.add(filtering);
        }
        out.add(settings(true));
        return out;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<ResourceLocation> ids = ClientboundPlaceGhostRecipeShapes.recipeIds(ctx);
        List<List<ResourceLocation>> idLists = List.of(
                List.of(),
                List.of(ids.get(0)),
                List.of(ids.get(0), ids.get(1)),
                List.of(ids.get(0), ids.get(1), ids.get(2)));

        List<Packet<?>> out = new ArrayList<>();
        // 3 states x 4 recipe lists x 4 highlight lists x 2 settings extremes = 96
        for (ClientboundRecipePacket.State state : ClientboundRecipePacket.State.values()) {
            for (List<ResourceLocation> recipes : idLists) {
                for (List<ResourceLocation> highlight : idLists) {
                    for (boolean allSet : ctx.bools()) {
                        out.add(new ClientboundRecipePacket(state, recipes, highlight, settings(allSet)));
                    }
                }
            }
        }
        // settings bit field, per state
        for (ClientboundRecipePacket.State state : ClientboundRecipePacket.State.values()) {
            for (RecipeBookSettings s : settingsBits()) {
                out.add(new ClientboundRecipePacket(state, List.of(ids.get(0)), List.of(ids.get(0)), s));
            }
        }
        // identifier spread through both id lists
        for (ResourceLocation id : ids) {
            out.add(new ClientboundRecipePacket(ClientboundRecipePacket.State.INIT,
                    List.of(id), List.of(), settings(false)));
            out.add(new ClientboundRecipePacket(ClientboundRecipePacket.State.INIT,
                    List.of(), List.of(id), settings(false)));
        }
        return out;
    }
}
