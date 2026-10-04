package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;

import java.util.List;

/**
 * ClientboundTakeItemEntityPacket(int itemId [varint], int playerId [varint], int amount [varint]).
 * Three plain varints with no decoder guards, so the full cartesian product of the VarInt
 * byte-length boundaries is emitted (13^3 = 2197 shapes, under the 3000 budget).
 */
public class ClientboundTakeItemEntityShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundTakeItemEntityPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundTakeItemEntityPacket((int) a[0], (int) a[1], (int) a[2]),
                ctx.varInts(), ctx.varInts(), ctx.varInts());
    }
}
