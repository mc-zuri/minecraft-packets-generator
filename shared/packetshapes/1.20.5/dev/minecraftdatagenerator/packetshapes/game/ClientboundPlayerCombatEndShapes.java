package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;

import java.util.List;

/** ClientboundPlayerCombatEndPacket(int duration[varint]) — the only field is a VarInt, so it is swept
 *  over every VarInt byte-length boundary (1..5 bytes) plus the negative forms. */
public class ClientboundPlayerCombatEndShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlayerCombatEndPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), ClientboundPlayerCombatEndPacket::new);
    }
}
