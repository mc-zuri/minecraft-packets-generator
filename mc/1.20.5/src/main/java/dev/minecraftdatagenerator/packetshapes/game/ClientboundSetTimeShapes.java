package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

import java.util.List;

/**
 * ClientboundSetTimePacket(long gameTime [i64], long dayTime [i64], boolean tickDayTime [bool]).
 * <p>
 * 1.20.5 predates the {@code WORLD_CLOCK} registry, so there is no {@code clockUpdates} map here —
 * the body is three fixed-width scalars and a full cross-product is cheap enough to be exhaustive.
 * <p>
 * Coverage: every long edge case on gameTime crossed with every long edge case on dayTime crossed
 * with both booleans (5 * 5 * 2 = 50 shapes). Both longs are plain i64 (not VarLong), so the int
 * edge spread is the complete corner-case set for them.
 */
public class ClientboundSetTimeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetTimePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundSetTimePacket((long) a[0], (long) a[1], (boolean) a[2]),
                ctx.longs(), ctx.longs(), ctx.bools());
    }
}
