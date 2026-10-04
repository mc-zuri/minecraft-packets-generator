package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * ServerboundSetCreativeModeSlotPacket(short slotNum, ItemStack itemStack).
 * Wire: i16(slotNum), then {@code ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC} — a count-prefixed
 * optional: count 0 means EMPTY (nothing further), otherwise varInt(count), item id and the data
 * component patch.
 * <p>
 * Full cross of the container-slot domain merged with the short edge spread x the item corner cases
 * (empty / single / full stack), so both branches of the optional item are covered at every slot.
 */
public class ServerboundSetCreativeModeSlotShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSetCreativeModeSlotPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        LinkedHashSet<Short> slots = new LinkedHashSet<>(ctx.slots());
        slots.addAll(ctx.shorts());
        return ctx.cross(
                a -> new ServerboundSetCreativeModeSlotPacket((short) a[0], (ItemStack) a[1]),
                new ArrayList<>(slots), ctx.items());
    }
}
