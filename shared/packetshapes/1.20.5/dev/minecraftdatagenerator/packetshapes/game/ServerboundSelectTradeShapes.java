package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;

import java.util.List;

/** ServerboundSelectTradePacket(int item[varint]) — one field; swept over every VarInt byte-length
 *  boundary plus the negative 5-byte forms. */
public class ServerboundSelectTradeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSelectTradePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), ServerboundSelectTradePacket::new);
    }
}
