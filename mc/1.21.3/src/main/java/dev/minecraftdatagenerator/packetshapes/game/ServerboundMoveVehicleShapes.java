package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundMoveVehiclePacket(double x, double y, double z, float yRot, float xRot).
 * <p>
 * Wire form in 1.21.3 is {@code f64 x, f64 y, f64 z, f32 yRot, f32 xRot} — the fields are written
 * directly. There is NO trailing {@code onGround} bool here (it was added in 1.21.5, verified against
 * this version's {@code write}), and neither the {@code Vec3} field nor the {@code PositionAndRotation}
 * wrapper (26.3) exists yet.
 * <p>
 * 1.21.3 exposes no field-wise public constructor (only {@code (Entity)}), so each shape is produced
 * by writing the exact wire form and decoding it back through the packet's own STREAM_CODEC. That is
 * the only way to reach arbitrary field values, and it is byte-exact by construction.
 * <p>
 * Full cartesian of a domain-correct position spread (build-range and packed-limit Y, world-border
 * X/Z, fractional offsets and the f64 extremes) x every yaw in {@code rotations()} plus the f32
 * extremes x every pitch in {@code pitches()} plus 3.5 and the f32 extremes (8 x 9 x 8 = 576).
 */
public class ServerboundMoveVehicleShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundMoveVehiclePacket.class;
    }

    private static ServerboundMoveVehiclePacket mk(Vec3 pos, float yRot, float xRot) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeFloat(yRot);
        buf.writeFloat(xRot);
        return ServerboundMoveVehiclePacket.STREAM_CODEC.decode(buf);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Vec3> positions = List.of(
                Vec3.ZERO,
                new Vec3(0.5, 64.5, -0.5),
                new Vec3(1, -64, 2),
                new Vec3(30000000, 319, 30000000),
                new Vec3(-30000000, -64, -30000000),
                new Vec3(0, -2032, 0),
                new Vec3(0, 2031, 0),
                new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 3.5));

        List<Float> yRots = new ArrayList<>(ctx.rotations());
        yRots.add(Float.MAX_VALUE);
        yRots.add(-Float.MAX_VALUE);

        List<Float> xRots = new ArrayList<>(ctx.pitches());
        xRots.add(3.5f);
        xRots.add(Float.MAX_VALUE);
        xRots.add(-Float.MAX_VALUE);

        return ctx.cross(
                a -> mk((Vec3) a[0], (float) a[1], (float) a[2]),
                positions, yRots, xRots);
    }
}
