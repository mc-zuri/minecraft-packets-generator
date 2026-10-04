package dev.minecraftdatagenerator.packetshapes.common;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ServerboundCustomClickActionPacket(ResourceLocation id, Optional&lt;Tag&gt; payload).
 * The payload is {@code optionalTagCodec(new NbtAccounter(32768, 16))} wrapped in
 * {@code lengthPrefixed(65536)}: a VarInt byte length, then either TAG_End (the empty Optional) or a
 * type-tagged, nameless NBT value. Full cartesian of the identifier variants against a payload
 * spread covering the absent case and every primitive/collection NBT tag type, so each NBT type id
 * appears on the wire. Nesting stays far under the accounter's depth limit of 16 and its 32 KiB quota.
 */
public class ServerboundCustomClickActionShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundCustomClickActionPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        CompoundTag filled = new CompoundTag();
        filled.putString("s", "v");
        filled.putInt("i", 1);
        CompoundTag nested = new CompoundTag();
        nested.putBoolean("b", true);
        filled.put("c", nested);

        ListTag emptyList = new ListTag();
        ListTag stringList = new ListTag();
        stringList.add(StringTag.valueOf("a"));
        stringList.add(StringTag.valueOf("b"));

        List<Optional<Tag>> payloads = new ArrayList<>();
        payloads.add(Optional.empty());                                   // TAG_End on the wire
        payloads.add(Optional.of(new CompoundTag()));
        payloads.add(Optional.of(filled));
        payloads.add(Optional.of(ByteTag.valueOf((byte) -1)));
        payloads.add(Optional.of(ShortTag.valueOf((short) 1)));
        payloads.add(Optional.of(IntTag.valueOf(Integer.MIN_VALUE)));
        payloads.add(Optional.of(LongTag.valueOf(Long.MAX_VALUE)));
        payloads.add(Optional.of(FloatTag.valueOf(3.5f)));
        payloads.add(Optional.of(DoubleTag.valueOf(-3.5d)));
        payloads.add(Optional.of(StringTag.valueOf("")));
        payloads.add(Optional.of(StringTag.valueOf("x")));
        payloads.add(Optional.of(emptyList));
        payloads.add(Optional.of(stringList));
        payloads.add(Optional.of(new ByteArrayTag(new byte[]{0, 1, -1})));
        payloads.add(Optional.of(new IntArrayTag(new int[]{0, -1})));
        payloads.add(Optional.of(new LongArrayTag(new long[]{0L, -1L})));

        List<ResourceLocation> ids = List.of(
                ctx.id("click"),
                ctx.id("a"),
                ResourceLocation.fromNamespaceAndPath("x", "y"),
                ResourceLocation.fromNamespaceAndPath("some_namespace", "some/nested/path"));

        List<Packet<?>> out = new ArrayList<>();
        for (ResourceLocation id : ids) {
            for (Optional<Tag> pl : payloads) {
                out.add(new ServerboundCustomClickActionPacket(id, pl));
            }
        }
        return out;
    }
}
