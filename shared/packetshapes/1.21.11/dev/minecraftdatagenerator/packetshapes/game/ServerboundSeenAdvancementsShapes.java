package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundSeenAdvancementsPacket(Action action[enum VarInt], @Nullable Identifier tab).
 * <p>
 * A discriminator packet: {@code tab} is written <b>only when</b> {@code action == OPENED_TAB}; for
 * {@code CLOSED_SCREEN} the body ends after the enum. Both branches are covered — the bare
 * CLOSED_SCREEN shape, plus OPENED_TAB over a spread of identifier forms (default namespace, custom
 * namespace, short and nested paths) that exercises the identifier length prefix.
 * NOTE: {@code tab} cannot be emitted for CLOSED_SCREEN — the writer would skip it and the decoded
 * packet would not round-trip.
 */
public class ServerboundSeenAdvancementsShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSeenAdvancementsPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        // CLOSED_SCREEN branch: no tab written.
        out.add(new ServerboundSeenAdvancementsPacket(ServerboundSeenAdvancementsPacket.Action.CLOSED_SCREEN, null));
        // OPENED_TAB branch: tab identifier written.
        List<Identifier> tabs = List.of(
                ctx.id("x"),
                ctx.id("story/root"),
                ctx.id("recipes/decorations/crafting_table"),
                Identifier.fromNamespaceAndPath("a", "b"),
                Identifier.fromNamespaceAndPath("my_namespace", "some/deeply/nested/advancement.tab-1"));
        for (Identifier tab : tabs) {
            out.add(new ServerboundSeenAdvancementsPacket(ServerboundSeenAdvancementsPacket.Action.OPENED_TAB, tab));
        }
        return out;
    }
}
