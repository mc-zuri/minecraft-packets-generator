package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;

import java.util.List;

/** ClientboundProjectilePowerPacket(int id[varint], double accelerationPower[f64]) — full cross of the
 *  VarInt byte-length boundaries for the entity id and the double edge cases for the power. */
public class ClientboundProjectilePowerShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundProjectilePowerPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundProjectilePowerPacket((int) a[0], (double) a[1]),
                ctx.varInts(), ctx.doubles());
    }
}
