package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;

import java.util.List;

/** ClientboundPlayerCombatKillPacket(int playerId[varint], Component message[TRUSTED_STREAM_CODEC]) —
 *  full cross of every VarInt byte-length boundary (and the negative 5-byte forms) with the component
 *  spread (13 x 3 = 39). */
public class ClientboundPlayerCombatKillShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlayerCombatKillPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundPlayerCombatKillPacket((int) a[0], (Component) a[1]),
                ctx.varInts(), ctx.components());
    }
}
