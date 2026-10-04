package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;

import java.util.ArrayList;
import java.util.List;

/** ClientboundResetScorePacket(String owner, @Nullable String objectiveName) — {@code owner:utf} then a
 *  {@code present:bool} prefixed nullable {@code objectiveName:utf}. Both strings are swept over the
 *  UTF-8 spread (empty, ASCII, 2-byte, 3-byte and 4-byte code points) and crossed with each other, with
 *  the null (absent) form included for the objective name. */
public class ClientboundResetScoreShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundResetScorePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<String> objectiveNames = new ArrayList<>();
        objectiveNames.add(null); // nullable absent
        objectiveNames.addAll(ctx.utf8());
        List<Packet<?>> out = new ArrayList<>();
        for (String owner : ctx.utf8()) {
            for (String objective : objectiveNames) {
                out.add(new ClientboundResetScorePacket(owner, objective));
            }
        }
        return out;
    }
}
