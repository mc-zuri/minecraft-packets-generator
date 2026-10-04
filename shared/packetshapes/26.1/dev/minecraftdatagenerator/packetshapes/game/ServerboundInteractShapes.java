package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * ServerboundInteractPacket(int entityId[VarInt], InteractionHand hand[idMapper VarInt],
 * Vec3 location[LP_STREAM_CODEC], boolean usingSecondaryAction).
 * <p>
 * Full cartesian of every VarInt byte-length boundary x both hands x every distinguishable
 * {@code LpVec3} wire form x both booleans (13 x 2 x 7 x 2 = 364).
 * <p>
 * {@code LpVec3} has three distinct encodings, all of which are covered by the location spread:
 * <ul>
 *   <li>a single {@code 0x00} byte when the chessboard length is below {@code ABS_MIN_VALUE}
 *       (3.05e-5) - both {@code Vec3.ZERO} and a sub-threshold vector;</li>
 *   <li>a flat 6-byte form (2 bytes + i32) when the ceil-ed scale fits the 2 marker bits, i.e.
 *       scale 1..3 - covered at scale 1 and scale 3;</li>
 *   <li>the 6-byte form plus the continuation flag and a trailing VarInt scale when scale &gt; 3 -
 *       covered at scale 4 (1-byte VarInt), 64 (1-byte) and 30,000,000 (4-byte).</li>
 * </ul>
 * Values are chosen so the 15-bit-per-axis quantisation is idempotent and the shapes round-trip byte
 * for byte.
 */
public class ServerboundInteractShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundInteractPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Vec3> locations = List.of(
                Vec3.ZERO,                                    // short form: single 0x00 byte
                new Vec3(1.0E-6, 0.0, 0.0),                   // below ABS_MIN_VALUE -> same short form
                new Vec3(1.0, 0.0, 0.0),                      // scale 1, no continuation
                new Vec3(-3.0, 2.0, 3.0),                     // scale 3, largest non-continuation scale
                new Vec3(4.0, -4.0, 0.0),                     // scale 4, smallest continuation (VarInt 1)
                new Vec3(1.0, -64.0, 2.5),                    // scale 64, 1-byte continuation VarInt
                new Vec3(-30000000.0, 319.0, 30000000.0));    // world border, 4-byte continuation VarInt

        return ctx.cross(
                a -> new ServerboundInteractPacket((int) a[0], (InteractionHand) a[1],
                        (Vec3) a[2], (boolean) a[3]),
                ctx.varInts(), ctx.enumAll(InteractionHand.class), locations, ctx.bools());
    }
}
