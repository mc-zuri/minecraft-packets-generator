package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLowDiskSpaceWarningPacket;

import java.util.List;

/** ClientboundLowDiskSpaceWarningPacket — empty body (StreamCodec.unit(INSTANCE)); one shape. */
public class ClientboundLowDiskSpaceWarningShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundLowDiskSpaceWarningPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.list(ClientboundLowDiskSpaceWarningPacket.INSTANCE);
    }
}
