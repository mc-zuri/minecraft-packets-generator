package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundAcceptTeleportationPacket(int id, double x, double y, double z, float yRot, float xRot).
 * <p>
 * Wire body: {@code id:varint, x:f64, y:f64, z:f64, yRot:f32, xRot:f32} — 26.3 added the acknowledged
 * position and rotation alongside the teleport id.
 * <p>
 * Coverage: id over every VarInt byte-length boundary; x/z over the horizontal world-border extremes
 * plus the double edge cases; y over the legal vertical range plus the double edge cases; yRot over the
 * degree spread ({@code rotations()}) and xRot over the clamped pitch spread ({@code pitches()}), each
 * also over the raw float edge cases since the codec is a plain f32 with no validation.
 */
public class ServerboundAcceptTeleportationShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundAcceptTeleportationPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        // id over every VarInt byte-length boundary.
        for (int id : ctx.varInts()) {
            out.add(new ServerboundAcceptTeleportationPacket(id, 0d, 0d, 0d, 0f, 0f));
        }

        // Domain-correct position sweeps: X/Z over the world border, Y over the full legal range.
        for (int h : ctx.horizontals()) {
            out.add(new ServerboundAcceptTeleportationPacket(1, h, 64d, 0d, 0f, 0f));
            out.add(new ServerboundAcceptTeleportationPacket(1, 0d, 64d, h, 0f, 0f));
        }
        for (int y : ctx.worldYs()) {
            out.add(new ServerboundAcceptTeleportationPacket(1, 0d, y, 0d, 0f, 0f));
        }

        // Raw f64 edge cases in each of the three position slots.
        for (double d : ctx.doubles()) {
            out.add(new ServerboundAcceptTeleportationPacket(1, d, 0d, 0d, 0f, 0f));
            out.add(new ServerboundAcceptTeleportationPacket(1, 0d, d, 0d, 0f, 0f));
            out.add(new ServerboundAcceptTeleportationPacket(1, 0d, 0d, d, 0f, 0f));
        }

        // Rotations: yaw over the degree spread, pitch over the clamped -90..90 spread.
        for (float yRot : ctx.rotations()) {
            out.add(new ServerboundAcceptTeleportationPacket(1, 0d, 0d, 0d, yRot, 0f));
        }
        for (float xRot : ctx.pitches()) {
            out.add(new ServerboundAcceptTeleportationPacket(1, 0d, 0d, 0d, 0f, xRot));
        }

        // Raw f32 edge cases in both rotation slots.
        for (float f : ctx.floats()) {
            out.add(new ServerboundAcceptTeleportationPacket(1, 0d, 0d, 0d, f, 0f));
            out.add(new ServerboundAcceptTeleportationPacket(1, 0d, 0d, 0d, 0f, f));
        }

        return out;
    }
}
