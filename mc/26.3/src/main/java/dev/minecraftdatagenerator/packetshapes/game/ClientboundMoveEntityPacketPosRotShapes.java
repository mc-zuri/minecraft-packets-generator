package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.VecDelta;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundMoveEntityPacket.PosRot(int id[varint], VecDelta delta, byte yRot, byte xRot,
 * boolean onGround) — wire is {@code varint entityId, varint properties, <delta>, i8 yRot, i8 xRot},
 * where {@code properties = (onGround ? 1 : 0) | stepCount << 1}.
 * <p>
 * 26.3 replaced the three raw i16 deltas with the sealed {@link VecDelta}, discriminated by the step
 * count packed into {@code properties}: {@code VecDelta.Linear(short xa, short ya, short za)} when the
 * count is 0 (three i16), and {@code VecDelta.Stepped(List<DeltaStep>)} otherwise, each
 * {@code DeltaStep(short xa, short ya, short za, int ticks)} written as {@code varint ticks, 3xi16}.
 * <p>
 * Categorical dimensions (the sealed VecDelta member and {@code onGround}) are fully crossed; the
 * rotation bytes are packed degrees, so they get their own cartesian against a baseline delta, and the
 * entity id sweeps the VarInt boundaries.
 * <p>
 * OMITTED: a zero-length {@code Stepped} encodes {@code stepCount = 0}, which is indistinguishable
 * from {@code Linear} on the wire and leaves the reader short three i16s. It is not emitted.
 */
public class ClientboundMoveEntityPacketPosRotShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundMoveEntityPacket.PosRot.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<VecDelta> deltas = ClientboundMoveEntityPacketPosShapes.deltas(ctx);
        // delta kind x onGround (the categorical dims), rotations held at a baseline.
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundMoveEntityPacket.PosRot(1, (VecDelta) a[0], (byte) 0, (byte) 0, (boolean) a[1]),
                deltas, ctx.bools()));
        // yRot x xRot x onGround, both delta kinds.
        out.addAll(ctx.cross(
                a -> new ClientboundMoveEntityPacket.PosRot(1, VecDelta.ZERO, (byte) a[0], (byte) a[1], (boolean) a[2]),
                ctx.angles(), ctx.angles(), ctx.bools()));
        out.addAll(ctx.cross(
                a -> new ClientboundMoveEntityPacket.PosRot(1,
                        new VecDelta.Stepped(List.of(new VecDelta.Stepped.DeltaStep((short) 1, (short) 2, (short) 3, 4))),
                        (byte) a[0], (byte) a[1], (boolean) a[2]),
                ctx.angles(), ctx.angles(), ctx.bools()));
        // raw i8 extremes in each rotation slot.
        for (byte b : ctx.bytes()) {
            out.add(new ClientboundMoveEntityPacket.PosRot(1, VecDelta.ZERO, b, (byte) 0, false));
            out.add(new ClientboundMoveEntityPacket.PosRot(1, VecDelta.ZERO, (byte) 0, b, false));
        }
        // entity id: every VarInt byte-length boundary.
        for (int id : ctx.varInts()) {
            out.add(new ClientboundMoveEntityPacket.PosRot(id, VecDelta.ZERO, (byte) 0, (byte) 0, false));
        }
        return out;
    }
}
