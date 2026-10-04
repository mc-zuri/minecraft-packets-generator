package dev.minecraftdatagenerator.packetshapes.common;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;

import java.util.List;

/**
 * ClientboundTransferPacket(String host [writeUtf], int port [writeVarInt]).
 * Full cartesian of the UTF-8 string spread against every VarInt byte-length boundary
 * (6 x 13 = 78 shapes), so both the string length prefix and the VarInt widths are exercised
 * in every combination.
 */
public class ClientboundTransferShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundTransferPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundTransferPacket((String) a[0], (int) a[1]),
                ctx.utf8(), ctx.varInts());
    }
}
