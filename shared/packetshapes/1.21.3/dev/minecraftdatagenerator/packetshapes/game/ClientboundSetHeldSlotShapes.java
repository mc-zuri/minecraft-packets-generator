package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;

import java.util.List;

/**
 * ClientboundSetHeldSlotPacket(int slot) — single {@code slot:varInt} field
 * ({@code ByteBufCodecs.VAR_INT}). Public record constructor with no validation, so the full VarInt
 * byte-length boundary sweep is used.
 */
public class ClientboundSetHeldSlotShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetHeldSlotPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), ClientboundSetHeldSlotPacket::new);
    }
}
