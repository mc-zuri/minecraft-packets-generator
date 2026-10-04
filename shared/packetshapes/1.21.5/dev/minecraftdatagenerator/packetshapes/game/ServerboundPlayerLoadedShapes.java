package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;

import java.util.List;

/** ServerboundPlayerLoadedPacket() — empty body (StreamCodec.unit); one shape. */
public class ServerboundPlayerLoadedShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPlayerLoadedPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.list(new ServerboundPlayerLoadedPacket());
    }
}
