package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;

import java.util.List;

/**
 * ServerboundSelectBundleItemPacket(int slotId[varint], int selectedItemIndex[varint]) — full cross of
 * the VarInt boundary spread for {@code slotId} (13) x the legal {@code selectedItemIndex} values (12)
 * = 156 shapes.
 * NOTE: the decoder throws for any {@code selectedItemIndex} that is negative and not exactly -1, so
 * that dimension omits {@code Integer.MIN_VALUE} and keeps only -1 plus the non-negative VarInt
 * boundaries (0, 127, 128, ..., Integer.MAX_VALUE).
 */
public class ServerboundSelectBundleItemShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSelectBundleItemPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Integer> selected = List.of(-1, 0, 1, 127, 128, 16383, 16384, 2097151, 2097152,
                268435455, 268435456, Integer.MAX_VALUE);
        return ctx.cross(
                a -> new ServerboundSelectBundleItemPacket((int) a[0], (int) a[1]),
                ctx.varInts(), selected);
    }
}
