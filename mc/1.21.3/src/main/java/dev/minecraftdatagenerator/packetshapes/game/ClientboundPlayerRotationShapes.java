package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundPlayerRotationPacket(float yRot, float xRot) — wire fields {@code yRot:f32, xRot:f32}.
 * <p>
 * Full cartesian of the domain-correct rotation spreads (yaw over {@link ShapeCtx#rotations()}, pitch
 * over {@link ShapeCtx#pitches()}), plus a float edge-case sweep on each of the two f32 fields.
 * <p>
 * NOTE: 1.21.3 has no {@code relativeY}/{@code relativeX} booleans (they were added in 26.1), so
 * there are no such dimensions here.
 */
public class ClientboundPlayerRotationShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlayerRotationPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundPlayerRotationPacket((float) a[0], (float) a[1]),
                ctx.rotations(), ctx.pitches()));
        // Raw f32 edge cases on each rotation field.
        for (float f : ctx.floats()) {
            out.add(new ClientboundPlayerRotationPacket(f, 0f));
            out.add(new ClientboundPlayerRotationPacket(0f, f));
        }
        return out;
    }
}
