package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundProjectilePowerPacket(int id[varint], double xPower[f64], double yPower[f64],
 * double zPower[f64]) — 1.20.5 sends the acceleration as three separate axes; 1.20.5 collapsed them into
 * a single {@code accelerationPower} double.
 * <p>
 * Coverage: the full cross of the VarInt byte-length boundaries for the entity id with the double edge
 * cases applied to all three axes at once, plus a per-axis double sweep so each f64 field is exercised
 * on its own.
 */
public class ClientboundProjectilePowerShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundProjectilePowerPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> {
                    double d = (double) a[1];
                    return new ClientboundProjectilePowerPacket((int) a[0], d, d, d);
                },
                ctx.varInts(), ctx.doubles()));
        // per-axis sweep
        for (double d : ctx.doubles()) {
            out.add(new ClientboundProjectilePowerPacket(0, d, 0, 0));
            out.add(new ClientboundProjectilePowerPacket(0, 0, d, 0));
            out.add(new ClientboundProjectilePowerPacket(0, 0, 0, d));
        }
        return out;
    }
}
