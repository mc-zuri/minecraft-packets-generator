package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundPlayerInputPacket(float xxa, float zza, boolean isJumping, boolean isShiftKeyDown).
 * <p>
 * Wire: {@code xxa:f32}, {@code zza:f32}, then a flags byte with bit0 = {@code isJumping} and
 * bit1 = {@code isShiftKeyDown}. 1.21.2 replaced this with a single {@code Input} record packing
 * seven booleans into one byte and dropped the two floats, so here the two movement axes are real
 * f32 fields and only two flag bits exist.
 * <p>
 * Coverage: the flags byte is a bit field, so both bits are enumerated in every combination
 * (2x2 = all four byte values the encoder can produce), crossed with the full float corner-case
 * spread on both axes; then each axis is swept on its own against a zeroed baseline.
 */
public class ServerboundPlayerInputShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPlayerInputPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        // 6 x 6 x 2 x 2 = 144 shapes: every float edge on both axes x every flags-byte value.
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ServerboundPlayerInputPacket(
                        (float) a[0], (float) a[1], (boolean) a[2], (boolean) a[3]),
                ctx.floats(), ctx.floats(), ctx.bools(), ctx.bools()));

        // The in-game values are the -1..1 impulse range; sweep each axis alone over them too.
        for (float v : List.of(-1f, -0.98f, -0.5f, 0f, 0.5f, 0.98f, 1f)) {
            out.add(new ServerboundPlayerInputPacket(v, 0f, false, false));
            out.add(new ServerboundPlayerInputPacket(0f, v, false, false));
        }
        return out;
    }
}
