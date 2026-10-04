package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetEntityMotionPacket(int id, Vec3 movement) — wire body is {@code id:varInt} then the
 * velocity packed by {@code Vec3.LP_STREAM_CODEC} (LpVec3).
 * <p>
 * LpVec3 has three distinguishable wire forms, all of which are covered here:
 * <ul>
 *   <li><b>zero</b>: when the chessboard length is below {@code ABS_MIN_VALUE} (3.0519e-5) a single
 *       {@code 0x00} byte is written and nothing else;</li>
 *   <li><b>inline scale</b>: {@code ceil(chessboardLength) <= 3} — 6 bytes (byte, byte, int) with the
 *       scale in the low 2 bits and the continuation flag (0x04) clear;</li>
 *   <li><b>continued scale</b>: {@code ceil(chessboardLength) > 3} — the same 6 bytes with the 0x04
 *       continuation flag set, followed by a VarInt carrying {@code scale >> 2}.</li>
 * </ul>
 * The continued-scale form is swept across VarInt byte-length boundaries of {@code scale >> 2} up to
 * {@code ABS_MAX_VALUE} (1.7179869183e10); anything larger is clamped by {@code LpVec3.sanitize} and
 * would not round-trip, so it is omitted.
 */
public class ClientboundSetEntityMotionShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetEntityMotionPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Vec3> vecs = new ArrayList<>(List.of(
                // below ABS_MIN_VALUE -> the single 0x00 byte form
                Vec3.ZERO,
                new Vec3(1.0e-6, -1.0e-6, 0.0),
                // inline scale (ceil(len) <= 3), all sign combinations
                new Vec3(1.0, 1.0, 1.0),
                new Vec3(-1.0, -1.0, -1.0),
                new Vec3(0.5, -0.5, 1.0),
                new Vec3(1.0, 0.0, -3.0),
                new Vec3(3.0, 3.0, 3.0),
                // continuation flag set (ceil(len) > 3), sweeping the trailing VarInt's width
                new Vec3(4.0, 0.0, 0.0),
                new Vec3(-4.0, 2.0, -1.0),
                new Vec3(512.0, -512.0, 0.0),
                new Vec3(70000.0, 1.0, -1.0),
                new Vec3(1.0e8, 0.0, 0.0),
                new Vec3(1.7179869183e10, -1.7179869183e10, 0.0)));
        // scale >> 2 on both sides of every VarInt byte-length boundary, then on both sides of
        // 2^31, where the VarInt turns negative as an int although the scale is unsigned.
        for (double scale : new double[]{511, 65535, 65536, 8388607, 8388608, 1073741823, 1073741824,
                8589934591.0, 8589934592.0}) {
            vecs.add(new Vec3(scale, -scale, 0.0));
        }

        List<Packet<?>> out = new ArrayList<>();
        // Full cross of the entity-id VarInt boundary sweep with every velocity form.
        for (int id : ctx.varInts()) {
            for (Vec3 v : vecs) {
                out.add(new ClientboundSetEntityMotionPacket(id, v));
            }
        }
        return out;
    }
}
