package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;

import java.util.ArrayList;
import java.util.List;

/** ClientboundContainerSetDataPacket(int containerId[varint], int id[i16], int value[i16]) — containerId
 *  goes through {@code writeContainerId} (a plain VarInt in 1.21.3), id and value through
 *  {@code writeShort}. Full cross of the VarInt byte-length spread with the two short spreads
 *  (13 x 5 x 5 = 325 shapes). */
public class ClientboundContainerSetDataShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundContainerSetDataPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Integer> shortVals = new ArrayList<>();
        for (short s : ctx.shorts()) shortVals.add((int) s);
        return ctx.cross(
                a -> new ClientboundContainerSetDataPacket((int) a[0], (int) a[1], (int) a[2]),
                ctx.varInts(), shortVals, shortVals);
    }
}
