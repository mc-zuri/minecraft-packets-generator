package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundMoveEntityPacket.Rot(int id[varint], byte yRot, byte xRot, boolean onGround) — unlike the
 * Pos/PosRot variants the Rot wire form carries no i16 deltas and no packed properties varint:
 * {@code varint entityId, bool onGround, i8 yRot, i8 xRot}
 * (no packed properties VarInt, no VecDelta).
 * <p>
 * Full cross of entity id (VarInt byte-length boundaries) x yRot x xRot (packed-degree rotation bytes)
 * x onGround (13 x 6 x 6 x 2 = 936), plus a sweep of the raw i8 extremes in each rotation slot.
 */
public class ClientboundMoveEntityPacketRotShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundMoveEntityPacket.Rot.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundMoveEntityPacket.Rot((int) a[0], (byte) a[1], (byte) a[2], (boolean) a[3]),
                ctx.varInts(), ctx.angles(), ctx.angles(), ctx.bools()));
        for (byte b : ctx.bytes()) {
            out.add(new ClientboundMoveEntityPacket.Rot(1, b, (byte) 0, false));
            out.add(new ClientboundMoveEntityPacket.Rot(1, (byte) 0, b, false));
        }
        return out;
    }
}
