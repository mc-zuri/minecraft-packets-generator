package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket;

import java.util.List;

/** ClientboundTickingStepPacket(int tickSteps [varint]) — single varint field; swept over every
 *  VarInt byte-length boundary plus the negative forms. */
public class ClientboundTickingStepShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundTickingStepPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), ClientboundTickingStepPacket::new);
    }
}
