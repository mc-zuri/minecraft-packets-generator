package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundAddExperienceOrbPacket — wire body {@code id:varint, x:f64, y:f64, z:f64, value:i16}.
 * <p>
 * This packet exists only in 1.20.5 and older; 1.21.5 dropped it in favour of a generic
 * {@code ClientboundAddEntityPacket} plus entity data.
 * <p>
 * The only public constructor is {@code (ExperienceOrb, ServerEntity)}, which derives every field from
 * a live entity and cannot reach arbitrary ids or out-of-range values. Each shape is therefore produced
 * by writing the exact wire form and decoding it back through the packet's own STREAM_CODEC — byte-exact
 * by construction, and the only way to cover the field space.
 * <p>
 * Coverage:
 * <ul>
 *   <li>{@code id} over every VarInt byte-length boundary;</li>
 *   <li>a domain-correct position spread (origin, fractional offsets, world-border X/Z, the full legal
 *       Y range) crossed with a representative value spread;</li>
 *   <li>each position axis over the f64 corner cases, and X/Z over {@code horizontals()} / Y over
 *       {@code worldYs()};</li>
 *   <li>{@code value} over the raw i16 corner cases — it is written with {@code writeShort} and read
 *       with {@code readShort}, so the whole signed-short range is round-trip stable.</li>
 * </ul>
 */
public class ClientboundAddExperienceOrbShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundAddExperienceOrbPacket.class;
    }

    private static ClientboundAddExperienceOrbPacket mk(int id, Vec3 pos, short value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(id);
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeShort(value);
        return ClientboundAddExperienceOrbPacket.STREAM_CODEC.decode(buf);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Vec3> positions = List.of(
                Vec3.ZERO,
                new Vec3(0.5, 63.5, -0.5),
                new Vec3(1, -64, 2),
                new Vec3(30000000, 319, 30000000),
                new Vec3(-30000000, -64, -30000000),
                new Vec3(30000000, 2031, -30000000),
                new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 3.5));
        List<Short> values = List.of((short) 0, (short) 1, (short) 2, (short) 100,
                Short.MAX_VALUE, Short.MIN_VALUE, (short) -1);

        // position x value cartesian
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> mk(1, (Vec3) a[0], (short) a[1]), positions, values));

        // id over every VarInt byte-length boundary
        for (int id : ctx.varInts()) {
            out.add(mk(id, new Vec3(0, 64, 0), (short) 1));
        }
        // per-axis f64 corner sweep
        for (double d : ctx.doubles()) {
            out.add(mk(1, new Vec3(d, 0, 0), (short) 1));
            out.add(mk(1, new Vec3(0, d, 0), (short) 1));
            out.add(mk(1, new Vec3(0, 0, d), (short) 1));
        }
        // X/Z over the world border, Y over the full legal vertical range
        for (int h : ctx.horizontals()) {
            out.add(mk(1, new Vec3(h, 64, 0), (short) 1));
            out.add(mk(1, new Vec3(0, 64, h), (short) 1));
        }
        for (int y : ctx.worldYs()) {
            out.add(mk(1, new Vec3(0, y, 0), (short) 1));
        }
        // value over the raw i16 corner cases
        for (short v : ctx.shorts()) {
            out.add(mk(1, new Vec3(0, 64, 0), v));
        }
        return out;
    }
}
