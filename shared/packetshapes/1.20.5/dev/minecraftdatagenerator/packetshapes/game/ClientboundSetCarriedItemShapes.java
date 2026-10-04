package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetCarriedItemPacket(int slot) — the whole body is {@code writeByte(slot)}, read back with
 * {@code readByte()}, so the wire field is a single signed byte.
 * <p>
 * 1.21.2 renamed this to ClientboundSetHeldSlotPacket and widened {@code slot} to a varint, which is
 * why this sweeps the signed-byte range instead of the VarInt byte-length boundaries.
 * <p>
 * The constructor performs no validation, so every legal wire value is emitted: the whole hotbar range
 * 0..8, plus both ends of the signed byte (-128 and 127) and -1.
 */
public class ClientboundSetCarriedItemShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetCarriedItemPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot <= 8; slot++) slots.add(slot);   // every hotbar slot
        slots.add(-1);
        slots.add(9);
        slots.add((int) Byte.MAX_VALUE);
        slots.add((int) Byte.MIN_VALUE);
        return ctx.each(slots, ClientboundSetCarriedItemPacket::new);
    }
}
