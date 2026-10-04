package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.world.level.border.WorldBorder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ClientboundInitializeBorderPacket(WorldBorder border) — wire body is {@code newCenterX:f64,
 * newCenterZ:f64, oldSize:f64, newSize:f64, lerpTime:varLong, newAbsoluteMaxSize:varInt,
 * warningBlocks:varInt, warningTime:varInt}.
 * <p>
 * The packet has no field-level constructor: all eight wire fields are read off a {@link WorldBorder},
 * so each shape configures a fresh border to isolate one field. The border's setters do no clamping, so
 * the full corner-case spreads are reachable.
 * <p>
 * Covered: the static (oldSize == newSize, lerpTime 0) and the actively-lerping (oldSize != newSize,
 * lerpTime &gt; 0) forms; center coordinates including the +-29,999,984 limits and the double edges;
 * size 0 / 1 / MAX_SIZE; {@code lerpTime} over the var-long byte-length boundaries; and
 * {@code newAbsoluteMaxSize} / {@code warningBlocks} / {@code warningTime} over the var-int boundaries.
 * <p>
 * NOTE: {@code lerpSizeBetween} collapses to the static extent when {@code from == to}, so the lerping
 * shapes always use distinct sizes; and because the extent stores the duration verbatim as
 * {@code lerpProgress}, the emitted {@code lerpTime} equals the requested tick count.
 */
public class ClientboundInitializeBorderShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundInitializeBorderPacket.class;
    }

    private static Packet<?> border(Consumer<WorldBorder> configure) {
        WorldBorder b = new WorldBorder();
        configure.accept(b);
        return new ClientboundInitializeBorderPacket(b);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        // default border (static extent, lerpTime 0, default absoluteMaxSize/warnings)
        out.add(border(b -> {
        }));

        // center: origin, mixed signs, the +-29,999,984 limits and the double edges
        for (double[] c : List.<double[]>of(
                new double[]{0, 0},
                new double[]{1, -1},
                new double[]{123.5, -456.5},
                new double[]{2.9999984E7, -2.9999984E7},
                new double[]{-2.9999984E7, 2.9999984E7},
                new double[]{Double.MAX_VALUE, -Double.MAX_VALUE})) {
            out.add(border(b -> b.setCenter(c[0], c[1])));
        }

        // static size (oldSize == newSize)
        for (double size : List.of(0.0, 1.0, 3.5, 5.9999968E7, Double.MAX_VALUE)) {
            out.add(border(b -> b.setSize(size)));
        }

        // lerping: oldSize != newSize, lerpTime over the var-long byte-length boundaries,
        // Full signed 64-bit wire domain, including values unsupported by Node VarLong.
        for (long ticks : ctx.varLongs()) {
            out.add(border(b -> b.lerpSizeBetween(500.0, 8000.0, ticks, 0L)));
        }
        // shrinking as well as growing
        out.add(border(b -> b.lerpSizeBetween(8000.0, 500.0, 20L, 0L)));
        out.add(border(b -> b.lerpSizeBetween(0.0, 5.9999968E7, 1L, 0L)));

        // newAbsoluteMaxSize (varint)
        for (int v : List.of(0, 1, 127, 128, 16383, 16384, 2097152, 29999984, Integer.MAX_VALUE)) {
            out.add(border(b -> b.setAbsoluteMaxSize(v)));
        }
        // warningBlocks (varint)
        for (int v : List.of(0, 1, 5, 127, 128, 16384, Integer.MAX_VALUE)) {
            out.add(border(b -> b.setWarningBlocks(v)));
        }
        // warningTime (varint)
        for (int v : List.of(0, 1, 15, 127, 128, 16384, Integer.MAX_VALUE)) {
            out.add(border(b -> b.setWarningTime(v)));
        }

        // a fully-customised border touching every field at once
        out.add(border(b -> {
            b.setCenter(-1.0, 1.0);
            b.lerpSizeBetween(500.0, 8000.0, 20000L, 0L);
            b.setAbsoluteMaxSize(29999984);
            b.setWarningBlocks(10);
            b.setWarningTime(15);
        }));
        return out;
    }
}
