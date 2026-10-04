package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundMovePlayerPacket.Pos(double x, double y, double z, boolean onGround) — wire body is
 * {@code f64 x, f64 y, f64 z, u8 onGround}.
 * <p>
 * 1.21 writes a single {@code u8} carrying only {@code onGround} (0 or 1); the
 * {@code horizontalCollision} bit was added to that byte in 1.21.2, so there is no second flag bit
 * to enumerate here.
 * <p>
 * Both {@code onGround} values are crossed against the whole position spread: the domain-correct positions (build-range and packed-limit Y,
 * world-border X/Z, fractional offsets) plus a per-axis sweep of the f64 edge values.
 */
public class ServerboundMovePlayerPacketPosShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundMovePlayerPacket.Pos.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<double[]> positions = new ArrayList<>(List.of(
                new double[]{0, 0, 0},
                new double[]{0.5, 64.5, -0.5},
                new double[]{1, -64, 2},
                new double[]{30000000, 319, 30000000},
                new double[]{-30000000, -64, -30000000},
                new double[]{0, -2032, 0},
                new double[]{0, 2031, 0},
                new double[]{Double.MAX_VALUE, -Double.MAX_VALUE, 3.5}));
        // Per-axis sweep of the f64 edge values, so every corner appears on every axis.
        for (double d : ctx.doubles()) {
            positions.add(new double[]{d, 0, 0});
            positions.add(new double[]{0, d, 0});
            positions.add(new double[]{0, 0, d});
        }

        return ctx.cross(a -> {
            double[] p = (double[]) a[0];
            return new ServerboundMovePlayerPacket.Pos(p[0], p[1], p[2], (boolean) a[1]);
        }, positions, ctx.bools());
    }
}
