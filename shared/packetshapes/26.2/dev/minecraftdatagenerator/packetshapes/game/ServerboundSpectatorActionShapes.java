package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSpectatorActionPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * ServerboundSpectatorActionPacket(OptionalInt spectateEntityId) — single field encoded by
 * {@code ByteBufCodecs.OPTIONAL_VAR_INT}: absent -&gt; varint 0, present -&gt; varint (value + 1).
 * <p>
 * Both branches of the optional are emitted, and the present branch sweeps the VarInt byte-length
 * boundaries so every 1..5 byte encoding of the entity id is represented.
 * <p>
 * OMITTED: {@code OptionalInt.of(-1)} — its wire form is varint 0, which the decoder reads back as
 * {@code OptionalInt.empty()}, so it is indistinguishable from the absent case and does not
 * round-trip. Every other {@link ShapeCtx#varInts()} value (including MIN_VALUE, whose +1 form is
 * still non-zero) round-trips exactly.
 */
public class ServerboundSpectatorActionShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSpectatorActionPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        out.add(new ServerboundSpectatorActionPacket(OptionalInt.empty()));
        for (int id : ctx.varInts()) {
            if (id == -1) continue; // encodes as varint 0 == the "absent" form
            out.add(new ServerboundSpectatorActionPacket(OptionalInt.of(id)));
        }
        return out;
    }
}
