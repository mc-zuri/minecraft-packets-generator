package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ClientboundSetScorePacket(String owner, String objectiveName, int score,
 * Optional&lt;Component&gt; display, Optional&lt;NumberFormat&gt; numberFormat) — wire body is
 * {@code owner:utf8, objectiveName:utf8, score:varInt, display:Optional<Component>,
 * numberFormat:Optional<NumberFormat>}.
 * <p>
 * {@code numberFormat} is a registry dispatch over {@code Registries.NUMBER_FORMAT_TYPE}; all three
 * variants are covered ({@code blank} with no payload, {@code styled} carrying a Style, {@code fixed}
 * carrying a Component) alongside the absent case.
 * <p>
 * Cartesian: owner x objectiveName x score-varInt-boundaries x display(absent + 3 components) x
 * numberFormat(absent + 4 concrete formats) = 3 * 3 * 13 * 4 * 5 = 2340 shapes, plus a UTF-8 sweep
 * over each of the two string fields (the string codec's byte-length prefix).
 */
public class ClientboundSetScoreShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetScorePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Optional<Component>> displays = new ArrayList<>();
        displays.add(Optional.empty());
        for (Component c : ctx.components()) displays.add(Optional.of(c));

        List<Optional<NumberFormat>> formats = List.of(
                Optional.empty(),
                Optional.of(BlankFormat.INSTANCE),
                Optional.of(StyledFormat.NO_STYLE),
                Optional.of(StyledFormat.SIDEBAR_DEFAULT),
                Optional.of(new FixedFormat(Component.literal("x"))));

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundSetScorePacket(
                        (String) a[0], (String) a[1], (int) a[2],
                        castDisplay(a[3]), castFormat(a[4])),
                ctx.strings(), ctx.strings(), ctx.varInts(), displays, formats));

        // UTF-8 sweeps on the two string fields.
        for (String s : ctx.utf8()) {
            out.add(new ClientboundSetScorePacket(s, "obj", 0, Optional.empty(), Optional.empty()));
            out.add(new ClientboundSetScorePacket("owner", s, 0, Optional.empty(), Optional.empty()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Optional<Component> castDisplay(Object o) {
        return (Optional<Component>) o;
    }

    @SuppressWarnings("unchecked")
    private static Optional<NumberFormat> castFormat(Object o) {
        return (Optional<NumberFormat>) o;
    }
}
