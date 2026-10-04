package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;

import java.util.List;

/** ClientboundPlayerCombatEnterPacket — empty body (StreamCodec.unit(INSTANCE)); one shape. */
public class ClientboundPlayerCombatEnterShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlayerCombatEnterPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.list(ClientboundPlayerCombatEnterPacket.INSTANCE);
    }
}
