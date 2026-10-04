package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundMoveEntityPacket.PosRot(int id[varint], short xa, short ya, short za, byte yRot,
 * byte xRot, boolean onGround).
 * <p>
 * Wire form in 26.2 is exactly {@code varint entityId, i16 xa, i16 ya, i16 za, i8 yRot, i8 xRot,
 * bool onGround} — three raw i16 deltas and two packed-degree rotation bytes, with no step count or
 * packed properties byte (the sealed {@code VecDelta} dispatch is a 26.3 addition).
 * <p>
 * Covered: every i16 corner in every one of the three delta slots x both {@code onGround} values with
 * the rotations at a baseline (5 x 5 x 5 x 2 = 250); the packed-angle cartesian
 * {@code angles() x angles() x onGround} against both a zero and a non-zero delta (6 x 6 x 2 x 2 =
 * 144); raw i8 extremes in each rotation slot (2 x 5 = 10); and a var-int byte-length sweep on the
 * entity id (13). Total 417.
 */
public class ClientboundMoveEntityPacketPosRotShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundMoveEntityPacket.PosRot.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        // Every i16 corner in every delta slot, crossed with onGround; rotations at a baseline.
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundMoveEntityPacket.PosRot(
                        1, (short) a[0], (short) a[1], (short) a[2], (byte) 0, (byte) 0, (boolean) a[3]),
                ctx.shorts(), ctx.shorts(), ctx.shorts(), ctx.bools()));
        // yRot x xRot x onGround, against a zero delta and a non-zero delta.
        out.addAll(ctx.cross(
                a -> new ClientboundMoveEntityPacket.PosRot(
                        1, (short) 0, (short) 0, (short) 0, (byte) a[0], (byte) a[1], (boolean) a[2]),
                ctx.angles(), ctx.angles(), ctx.bools()));
        out.addAll(ctx.cross(
                a -> new ClientboundMoveEntityPacket.PosRot(
                        1, (short) 1, (short) 2, (short) 3, (byte) a[0], (byte) a[1], (boolean) a[2]),
                ctx.angles(), ctx.angles(), ctx.bools()));
        // raw i8 extremes in each rotation slot.
        for (byte b : ctx.bytes()) {
            out.add(new ClientboundMoveEntityPacket.PosRot(1, (short) 0, (short) 0, (short) 0, b, (byte) 0, false));
            out.add(new ClientboundMoveEntityPacket.PosRot(1, (short) 0, (short) 0, (short) 0, (byte) 0, b, false));
        }
        // entity id: every VarInt byte-length boundary.
        for (int id : ctx.varInts()) {
            out.add(new ClientboundMoveEntityPacket.PosRot(id, (short) 0, (short) 0, (short) 0, (byte) 0, (byte) 0, false));
        }
        return out;
    }
}
