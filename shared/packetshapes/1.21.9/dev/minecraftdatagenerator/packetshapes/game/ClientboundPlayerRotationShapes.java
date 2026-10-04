package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundPlayerRotationPacket(float yRot, boolean relativeY, float xRot, boolean relativeX) —
 * wire fields {@code yRot:f32, relativeY:bool, xRot:f32, relativeX:bool}.
 * <p>
 * Full cartesian of the two booleans with the domain-correct rotation spreads (yaw over
 * {@link ShapeCtx#rotations()}, pitch over {@link ShapeCtx#pitches()}), plus a float edge-case sweep on
 * each of the two f32 fields.
 */
public class ClientboundPlayerRotationShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlayerRotationPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundPlayerRotationPacket((float) a[0], (boolean) a[1], (float) a[2], (boolean) a[3]),
                ctx.rotations(), ctx.bools(), ctx.pitches(), ctx.bools()));
        // Raw f32 edge cases on each rotation field.
        for (float f : ctx.floats()) {
            out.add(new ClientboundPlayerRotationPacket(f, true, 0f, false));
            out.add(new ClientboundPlayerRotationPacket(0f, false, f, true));
        }
        return out;
    }
}
