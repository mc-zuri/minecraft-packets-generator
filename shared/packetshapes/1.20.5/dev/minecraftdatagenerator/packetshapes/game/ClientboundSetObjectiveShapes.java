package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reference provider for a <b>discriminator/switch</b> packet. The wire body is
 * {@code objectiveName:utf8, method:byte}, and the extra fields
 * {@code displayName:Component, renderType:enum(byte), numberFormat:Optional<NumberFormat>} are
 * written <b>only when</b> {@code method} is {@code METHOD_ADD (0)} or {@code METHOD_CHANGE (2)};
 * for {@code METHOD_REMOVE (1)} the body ends after the method byte.
 * <p>
 * {@code numberFormat} is itself a registry dispatch over {@code Registries.NUMBER_FORMAT_TYPE}, so
 * every one of its three variants is covered on top of the absent case:
 * {@code blank} (no payload), {@code styled} (a Style) and {@code fixed} (a Component).
 * <p>
 * Coverage: the bare REMOVE branch, ADD and CHANGE crossed over every
 * {@link ObjectiveCriteria.RenderType}, all four numberFormat forms and a spread of display names,
 * plus a UTF-8 objective-name sweep on all three methods.
 */
public class ClientboundSetObjectiveShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetObjectivePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Scoreboard sb = new Scoreboard();
        List<Packet<?>> out = new ArrayList<>();

        // Optional.empty() plus every NumberFormatType variant.
        List<NumberFormat> formats = Arrays.<NumberFormat>asList(
                null,
                BlankFormat.INSTANCE,
                StyledFormat.NO_STYLE,
                StyledFormat.SIDEBAR_DEFAULT,
                new FixedFormat(Component.literal("x")));

        // ADD (0) and CHANGE (2): the full extra-field payload.
        int[] withPayload = {ClientboundSetObjectivePacket.METHOD_ADD, ClientboundSetObjectivePacket.METHOD_CHANGE};
        for (int method : withPayload) {
            for (ObjectiveCriteria.RenderType rt : ctx.enumAll(ObjectiveCriteria.RenderType.class)) {
                for (NumberFormat nf : formats) {
                    for (Component displayName : ctx.components()) {
                        Objective o = new Objective(sb, "obj", ObjectiveCriteria.DUMMY, displayName, rt, false, nf);
                        out.add(new ClientboundSetObjectivePacket(o, method));
                    }
                }
            }
        }

        // objectiveName UTF-8 sweep on every method, including the bare REMOVE (1) branch.
        int[] allMethods = {ClientboundSetObjectivePacket.METHOD_ADD,
                ClientboundSetObjectivePacket.METHOD_REMOVE,
                ClientboundSetObjectivePacket.METHOD_CHANGE};
        for (int method : allMethods) {
            for (String name : ctx.utf8()) {
                Objective o = new Objective(sb, name, ObjectiveCriteria.DUMMY, Component.literal("x"),
                        ObjectiveCriteria.RenderType.INTEGER, false, null);
                out.add(new ClientboundSetObjectivePacket(o, method));
            }
        }
        return out;
    }
}
