package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundTagQueryPacket(int transactionId [varint], @Nullable CompoundTag tag).
 * <p>
 * {@code writeNbt} encodes null as a bare TAG_END byte, so the nullable tag is a real wire
 * discriminator. Coverage: full cross of the VarInt boundary transaction ids with a tag spread of
 * null, empty, flat-scalar, nested-compound, list and array payloads (the array/list tags exercise
 * the remaining NBT tag-type ids).
 */
public class ClientboundTagQueryShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundTagQueryPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<CompoundTag> tags = new ArrayList<>();
        tags.add(null);
        tags.add(new CompoundTag());

        CompoundTag scalars = new CompoundTag();
        scalars.putByte("b", (byte) -1);
        scalars.putShort("sh", Short.MIN_VALUE);
        scalars.putInt("i", Integer.MAX_VALUE);
        scalars.putLong("l", Long.MIN_VALUE);
        scalars.putFloat("f", 3.5f);
        scalars.putDouble("d", -1.0);
        scalars.put("s", StringTag.valueOf("x"));
        tags.add(scalars);

        CompoundTag nested = new CompoundTag();
        CompoundTag inner = new CompoundTag();
        inner.putInt("a", 1);
        nested.put("inner", inner);
        ListTag list = new ListTag();
        list.add(StringTag.valueOf(""));
        list.add(StringTag.valueOf("你好"));
        nested.put("list", list);
        nested.put("empty_list", new ListTag());
        tags.add(nested);

        CompoundTag arrays = new CompoundTag();
        arrays.put("ba", new ByteArrayTag(new byte[]{0, 1, -1}));
        arrays.put("ia", new IntArrayTag(new int[]{0, Integer.MAX_VALUE, Integer.MIN_VALUE}));
        arrays.put("la", new LongArrayTag(new long[]{0L, Long.MAX_VALUE}));
        tags.add(arrays);

        return ctx.cross(
                a -> new ClientboundTagQueryPacket((int) a[0], (CompoundTag) a[1]),
                ctx.varInts(), tags);
    }
}
