package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.VecDelta;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundMoveEntityPacket.Pos(int id[varint], VecDelta delta, boolean onGround) — wire is
 * {@code varint entityId, varint properties, <delta>}, where
 * {@code properties = (onGround ? 1 : 0) | stepCount << 1}.
 * <p>
 * 26.3 replaced the three raw i16 deltas with the sealed {@link VecDelta}, discriminated by the step
 * count packed into {@code properties}: {@code VecDelta.Linear(short xa, short ya, short za)} when the
 * count is 0 (three i16), and {@code VecDelta.Stepped(List<DeltaStep>)} otherwise, each
 * {@code DeltaStep(short xa, short ya, short za, int ticks)} written as {@code varint ticks, 3xi16}.
 * <p>
 * Both VecDelta implementations (the sealed hierarchy's only members) are crossed with both
 * {@code onGround} values, since those are the two categorical dimensions; the entity id and the
 * i16/varint numeric fields are swept against a baseline.
 * <p>
 * OMITTED: a zero-length {@code Stepped} would encode {@code stepCount = 0}, making the wire form
 * identical to a {@code Linear} header with no delta bytes following it — vanilla's reader would then
 * consume three i16s past the end of the packet. It is not emitted.
 */
public class ClientboundMoveEntityPacketPosShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundMoveEntityPacket.Pos.class;
    }

    /** Every distinguishable VecDelta wire form: both sealed members, i16 corners in every slot,
     *  1/2/3-step paths and the varint tick-offset boundaries. */
    static List<VecDelta> deltas(ShapeCtx ctx) {
        List<VecDelta> out = new ArrayList<>();
        for (short s : ctx.shorts()) {
            out.add(new VecDelta.Linear(s, (short) 0, (short) 0));
            out.add(new VecDelta.Linear((short) 0, s, (short) 0));
            out.add(new VecDelta.Linear((short) 0, (short) 0, s));
        }
        for (short s : ctx.shorts()) {
            out.add(new VecDelta.Stepped(List.of(new VecDelta.Stepped.DeltaStep(s, s, s, 0))));
        }
        for (int ticks : ctx.varInts()) {
            out.add(new VecDelta.Stepped(List.of(new VecDelta.Stepped.DeltaStep((short) 1, (short) 2, (short) 3, ticks))));
        }
        out.add(new VecDelta.Stepped(List.of(
                new VecDelta.Stepped.DeltaStep((short) 1, (short) 2, (short) 3, 1),
                new VecDelta.Stepped.DeltaStep((short) -1, (short) -2, (short) -3, 2))));
        out.add(new VecDelta.Stepped(List.of(
                new VecDelta.Stepped.DeltaStep(Short.MAX_VALUE, Short.MIN_VALUE, (short) 0, 0),
                new VecDelta.Stepped.DeltaStep((short) 0, (short) 0, (short) 0, 127),
                new VecDelta.Stepped.DeltaStep((short) 1, (short) 1, (short) 1, 128))));
        // stepCount << 1 crosses the 1 -> 2 byte VarInt boundary of properties between 63 and 64 steps.
        for (int stepCount : List.of(63, 64)) {
            List<VecDelta.Stepped.DeltaStep> steps = new ArrayList<>();
            for (int i = 0; i < stepCount; i++) {
                steps.add(new VecDelta.Stepped.DeltaStep((short) i, (short) -i, (short) 1, i));
            }
            out.add(new VecDelta.Stepped(steps));
        }
        return out;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundMoveEntityPacket.Pos(1, (VecDelta) a[0], (boolean) a[1]),
                deltas(ctx), ctx.bools()));
        // entity id: every VarInt byte-length boundary, against both delta kinds.
        for (int id : ctx.varInts()) {
            out.add(new ClientboundMoveEntityPacket.Pos(id, VecDelta.ZERO, false));
            out.add(new ClientboundMoveEntityPacket.Pos(id,
                    new VecDelta.Stepped(List.of(new VecDelta.Stepped.DeltaStep((short) 1, (short) 2, (short) 3, 4))), true));
        }
        return out;
    }
}
