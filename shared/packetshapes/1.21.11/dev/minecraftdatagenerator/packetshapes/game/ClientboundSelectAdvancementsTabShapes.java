package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSelectAdvancementsTabPacket(@Nullable Identifier tab) — a {@code present:bool} prefixed
 * nullable {@code tab:identifier} (written as its {@code namespace:path} string).
 * <p>
 * Covers the absent (null) branch plus present identifiers spanning the vanilla advancement roots, a
 * minimal one-character path, a deep nested path and a non-{@code minecraft} namespace.
 * <p>
 * NOTE: the identifier charset is restricted ({@code [a-z0-9_.-/]} for the path,
 * {@code [a-z0-9_.-]} for the namespace), so the multi-byte {@link ShapeCtx#utf8()} strings are not used
 * here — {@code Identifier} would reject them.
 */
public class ClientboundSelectAdvancementsTabShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSelectAdvancementsTabPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Identifier> tabs = new ArrayList<>();
        tabs.add(null); // absent
        tabs.add(ctx.id("a"));
        tabs.add(ctx.id("story/root"));
        tabs.add(ctx.id("nether/root"));
        tabs.add(ctx.id("end/root"));
        tabs.add(ctx.id("adventure/root"));
        tabs.add(ctx.id("husbandry/root"));
        tabs.add(ctx.id("a/deeply/nested/advancement/tab.path-1"));
        tabs.add(Identifier.fromNamespaceAndPath("custom_namespace", "story/root"));
        return ctx.each(tabs, ClientboundSelectAdvancementsTabPacket::new);
    }
}
