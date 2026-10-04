package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.world.level.border.WorldBorder;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetBorderLerpSizePacket — wire body is {@code oldSize:f64, newSize:f64, lerpTime:varLong}.
 * The only public constructor takes a {@link WorldBorder}; {@code lerpSizeBetween(from, to, ticks)} drives
 * the three fields (1.21.11 added a 4th {@code gameTime} arg; 1.20.5 has the 3-arg form):
 * <ul>
 *   <li>{@code from == to} selects the static extent: {@code oldSize = newSize = to} and
 *       {@code lerpTime = 0};</li>
 *   <li>{@code from != to} selects the moving extent: {@code oldSize = from}, {@code newSize = to} and
 *       {@code lerpTime = ticks} verbatim.</li>
 * </ul>
 * Coverage: both branches, a full cross of distinct size pairs for the moving branch, and a VarLong
 * byte-length boundary sweep on {@code lerpTime}.
 * <p>
 * NOTE: sizes are kept positive and finite. A negative size makes the border's collision box have
 * {@code min > max}, which throws in {@code Shapes.box} before the packet can be built.
 */
public class ClientboundSetBorderLerpSizeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetBorderLerpSizePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        double[] sizes = {1.0, 3.5, 12345.678, WorldBorder.MAX_SIZE};

        // Static branch: from == to, lerpTime forced to 0.
        for (double size : sizes) {
            WorldBorder b = new WorldBorder();
            b.lerpSizeBetween(size, size, 1000L);
            out.add(new ClientboundSetBorderLerpSizePacket(b));
        }
        // Moving branch: every distinct (from, to) pair, both growing and shrinking.
        for (double from : sizes) {
            for (double to : sizes) {
                if (from == to) continue;
                WorldBorder b = new WorldBorder();
                b.lerpSizeBetween(from, to, 1000L);
                out.add(new ClientboundSetBorderLerpSizePacket(b));
            }
        }
        // VarLong boundary sweep on lerpTime (moving branch, so the duration is reported verbatim).
        for (long ticks : ctx.varLongs()) {
            WorldBorder b = new WorldBorder();
            b.lerpSizeBetween(1.0, 100.0, ticks);
            out.add(new ClientboundSetBorderLerpSizePacket(b));
        }
        return out;
    }
}
