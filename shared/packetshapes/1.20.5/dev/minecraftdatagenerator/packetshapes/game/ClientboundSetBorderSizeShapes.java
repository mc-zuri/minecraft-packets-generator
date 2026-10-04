package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.world.level.border.WorldBorder;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetBorderSizePacket — wire body is {@code size:f64} (the border's lerp target).
 * The only public constructor takes a {@link WorldBorder}; {@code setSize(v)} installs a static extent
 * whose lerp target is {@code v}, which is written verbatim.
 * <p>
 * NOTE: negative sizes are omitted — {@code setSize} builds the border collision box eagerly and a
 * negative size gives {@code min > max}, which throws in {@code Shapes.box}. The spread therefore runs
 * over the non-negative doubles, from 0 and the smallest subnormal up through
 * {@code WorldBorder.MAX_SIZE} and {@code Double.MAX_VALUE}.
 */
public class ClientboundSetBorderSizeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetBorderSizePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        double[] sizes = {0.0, Double.MIN_VALUE, 1.0, 3.5, 100.0, 12345.678, 1000000.0,
                WorldBorder.MAX_SIZE, Double.MAX_VALUE};
        for (double size : sizes) {
            WorldBorder b = new WorldBorder();
            b.setSize(size);
            out.add(new ClientboundSetBorderSizePacket(b));
        }
        return out;
    }
}
