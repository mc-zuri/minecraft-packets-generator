package dev.minecraftdatagenerator.packetshapes.login;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;

import java.util.List;

/**
 * ClientboundLoginCompressionPacket(int compressionThreshold) — a single {@code varInt} field, swept
 * over every VarInt byte-length boundary (1..5 bytes) plus the negative "compression disabled" forms.
 */
public class ClientboundLoginCompressionShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundLoginCompressionPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), ClientboundLoginCompressionPacket::new);
    }
}
