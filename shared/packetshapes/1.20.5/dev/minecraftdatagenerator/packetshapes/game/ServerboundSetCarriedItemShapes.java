package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * ServerboundSetCarriedItemPacket(int slot) — the wire field is a signed i16
 * ({@code writeShort}/{@code readShort}). Swept over the hotbar/container slot domain plus the full
 * short edge spread, de-duplicated and cast to int for the constructor.
 */
public class ServerboundSetCarriedItemShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSetCarriedItemPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        LinkedHashSet<Short> values = new LinkedHashSet<>(ctx.slots());
        values.addAll(ctx.shorts());
        values.add((short) 8); // last hotbar index
        return ctx.each(new ArrayList<>(values), s -> new ServerboundSetCarriedItemPacket((int) (short) s));
    }
}
