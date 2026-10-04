package dev.minecraftdatagenerator.packetshapes.login;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;

import java.util.List;

/**
 * ClientboundLoginDisconnectPacket(Component reason) — single JSON-encoded Component field; all
 * Component corner cases.
 */
public class ClientboundLoginDisconnectShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundLoginDisconnectPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.components(), ClientboundLoginDisconnectPacket::new);
    }
}
