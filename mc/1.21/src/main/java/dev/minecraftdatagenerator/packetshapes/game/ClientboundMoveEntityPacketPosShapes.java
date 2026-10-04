package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundMoveEntityPacket.Pos(int id[varint], short xa, short ya, short za, boolean onGround).
 * <p>
 * Wire form in 1.21 is exactly {@code varint entityId, i16 xa, i16 ya, i16 za, bool onGround} — three
 * raw i16 deltas, with no step count or packed properties byte (the sealed {@code VecDelta} dispatch
 * is a 26.3 addition).
 * <p>
 * Covered: the full cartesian of every i16 corner ({0, 1, -1, MAX, MIN}) in every one of the three
 * delta slots x both {@code onGround} values (5 x 5 x 5 x 2 = 250), plus a var-int byte-length sweep
 * on the entity id against both {@code onGround} values (13 x 2 = 26). Total 276.
 */
public class ClientboundMoveEntityPacketPosShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundMoveEntityPacket.Pos.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        // Every i16 corner in every delta slot, crossed with onGround.
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundMoveEntityPacket.Pos(
                        1, (short) a[0], (short) a[1], (short) a[2], (boolean) a[3]),
                ctx.shorts(), ctx.shorts(), ctx.shorts(), ctx.bools()));
        // entity id: every VarInt byte-length boundary, against both onGround values.
        for (int id : ctx.varInts()) {
            out.add(new ClientboundMoveEntityPacket.Pos(id, (short) 0, (short) 0, (short) 0, false));
            out.add(new ClientboundMoveEntityPacket.Pos(id, (short) 1, (short) 2, (short) 3, true));
        }
        return out;
    }
}
