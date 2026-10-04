package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetDisplayObjectivePacket(DisplaySlot slot, @Nullable Objective objective) — wire body
 * is {@code slot:varInt(DisplaySlot.id), objectiveName:utf8}. A null objective writes the
 * empty-string name.
 * Full cross of every one of the 19 {@link DisplaySlot} constants with the UTF-8 name spread
 * (empty, ASCII, and 2-/3-/4-byte code points, which exercise the byte-length prefix), plus the
 * null-objective form for every slot.
 */
public class ClientboundSetDisplayObjectiveShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetDisplayObjectivePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        Scoreboard sb = new Scoreboard();

        for (DisplaySlot slot : ctx.enumAll(DisplaySlot.class)) {
            // null objective -> empty objectiveName.
            out.add(new ClientboundSetDisplayObjectivePacket(slot, null));
            for (String name : ctx.utf8()) {
                Objective o = new Objective(sb, name, ObjectiveCriteria.DUMMY, Component.literal("x"),
                        ObjectiveCriteria.RenderType.INTEGER, false, null);
                out.add(new ClientboundSetDisplayObjectivePacket(slot, o));
            }
        }
        return out;
    }
}
