package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundEntityTagQueryPacket;

import java.util.List;

/**
 * ServerboundEntityTagQueryPacket(int transactionId[VarInt], int entityId[VarInt]).
 * <p>
 * Both fields are VarInts, so the full cartesian of the VarInt byte-length boundaries is emitted
 * (13x13 = 169).
 */
public class ServerboundEntityTagQueryShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundEntityTagQueryPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundEntityTagQueryPacket((int) a[0], (int) a[1]),
                ctx.varInts(), ctx.varInts());
    }
}
