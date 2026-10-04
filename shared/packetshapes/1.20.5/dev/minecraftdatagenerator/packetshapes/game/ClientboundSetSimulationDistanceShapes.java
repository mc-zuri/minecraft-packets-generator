package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;

import java.util.List;

/** ClientboundSetSimulationDistancePacket(int simulationDistance [varint]) — single varint field;
 *  swept over every VarInt byte-length boundary plus the negative forms. */
public class ClientboundSetSimulationDistanceShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetSimulationDistancePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), ClientboundSetSimulationDistancePacket::new);
    }
}
