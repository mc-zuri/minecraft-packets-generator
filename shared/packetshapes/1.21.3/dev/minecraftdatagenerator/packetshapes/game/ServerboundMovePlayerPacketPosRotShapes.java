package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundMovePlayerPacket.PosRot(double x, double y, double z, float yRot, float xRot,
 * boolean onGround, boolean horizontalCollision) — wire body is {@code f64 x, f64 y, f64 z, f32 yRot,
 * f32 xRot, u8 flags}, where the flags byte packs bit 0 = onGround and bit 1 = horizontalCollision.
 * <p>
 * The flags byte is a bit-flag discriminator, so both bits are fully crossed (none, each alone, both).
 * That cross is taken against a domain-correct position spread (build-range and packed-limit Y values,
 * world-border X/Z, fractional offsets and the f64 extremes), every yaw in {@code rotations()} plus the
 * f32 extremes, and every pitch in {@code pitches()} plus the f32 extremes: 8 x 9 x 8 x 2 x 2 = 2304.
 */
public class ServerboundMovePlayerPacketPosRotShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundMovePlayerPacket.PosRot.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<double[]> positions = List.of(
                new double[]{0, 0, 0},
                new double[]{0.5, 64.5, -0.5},
                new double[]{1, -64, 2},
                new double[]{30000000, 319, 30000000},
                new double[]{-30000000, -64, -30000000},
                new double[]{0, -2032, 0},
                new double[]{0, 2031, 0},
                new double[]{Double.MAX_VALUE, -Double.MAX_VALUE, 3.5});

        List<Float> yRots = new ArrayList<>(ctx.rotations());
        yRots.add(Float.MAX_VALUE);
        yRots.add(-Float.MAX_VALUE);

        List<Float> xRots = new ArrayList<>(ctx.pitches());
        xRots.add(3.5f);
        xRots.add(Float.MAX_VALUE);
        xRots.add(-Float.MAX_VALUE);

        return ctx.cross(a -> {
            double[] p = (double[]) a[0];
            return new ServerboundMovePlayerPacket.PosRot(p[0], p[1], p[2],
                    (float) a[1], (float) a[2], (boolean) a[3], (boolean) a[4]);
        }, positions, yRots, xRots, ctx.bools(), ctx.bools());
    }
}
