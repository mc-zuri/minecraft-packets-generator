package dev.minecraftdatagenerator.packetshapes.configuration;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.RegistrySynchronization.PackedRegistryEntry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ClientboundRegistryDataPacket(ResourceKey&lt;? extends Registry&lt;?&gt;&gt; registry,
 * List&lt;PackedRegistryEntry&gt; entries).
 * <p>
 * Wire body: registry:identifier (the bare registry id), entries:list of
 * PackedRegistryEntry(id:identifier, data:optional-nbt-tag).
 * <p>
 * Categorical dimensions crossed in full: the registry key (a spread of static and datapack
 * registries, with short and long ids) x the entry list. The entry list enumerates size 0/1/2/3 and,
 * for the single-entry case, {@code Optional.empty()} plus one entry per concrete NBT tag type, since
 * the tag's type byte is its own discriminator on the wire.
 * <p>
 * EndTag is deliberately omitted: {@code ByteBufCodecs.TAG} throws an EncoderException for it
 * ("Expected non-null compound tag"), so it is not a legal wire value here.
 */
public class ClientboundRegistryDataPacketShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundRegistryDataPacket.class;
    }

    private static List<Tag> tags() {
        CompoundTag filled = new CompoundTag();
        filled.putInt("i", 1);
        filled.putString("s", "x");
        filled.putByte("b", (byte) -1);

        ListTag list = new ListTag();
        list.add(IntTag.valueOf(1));
        list.add(IntTag.valueOf(-1));

        return List.of(
                ByteTag.valueOf((byte) -1),
                ShortTag.valueOf((short) -1),
                IntTag.valueOf(Integer.MIN_VALUE),
                LongTag.valueOf(Long.MAX_VALUE),
                FloatTag.valueOf(3.5f),
                DoubleTag.valueOf(-3.5d),
                new ByteArrayTag(new byte[]{0, 1, -1}),
                StringTag.valueOf(""),
                StringTag.valueOf("你好"),
                new ListTag(),
                list,
                new CompoundTag(),
                filled,
                new IntArrayTag(new int[]{0, Integer.MAX_VALUE}),
                new LongArrayTag(new long[]{0L, Long.MIN_VALUE}));
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        ResourceLocation shortId = new ResourceLocation("a", "b");
        ResourceLocation defaultId = ctx.id("stone");
        ResourceLocation pathyId = ctx.id("a/b/c_d.e-f");
        // 130-byte path: pushes the identifier's UTF-8 length prefix to two varint bytes.
        ResourceLocation longId = new ResourceLocation("mymod", "x".repeat(130));

        List<List<PackedRegistryEntry>> entryLists = new ArrayList<>();
        // size 0
        entryLists.add(List.of());
        // size 1, data absent, over an identifier spread
        for (ResourceLocation id : List.of(shortId, defaultId, pathyId, longId)) {
            entryLists.add(List.of(new PackedRegistryEntry(id, Optional.empty())));
        }
        // size 1, data present, one shape per NBT tag type
        for (Tag tag : tags()) {
            entryLists.add(List.of(new PackedRegistryEntry(defaultId, Optional.of(tag))));
        }
        // size 2: both optional branches in one list
        entryLists.add(List.of(
                new PackedRegistryEntry(defaultId, Optional.empty()),
                new PackedRegistryEntry(ctx.id("dirt"), Optional.of(new CompoundTag()))));
        // size 3
        entryLists.add(List.of(
                new PackedRegistryEntry(defaultId, Optional.of(StringTag.valueOf("x"))),
                new PackedRegistryEntry(ctx.id("dirt"), Optional.empty()),
                new PackedRegistryEntry(longId, Optional.of(IntTag.valueOf(7)))));

        List<ResourceKey<? extends Registry<?>>> registryKeys = List.of(
                Registries.BLOCK,
                Registries.ITEM,
                Registries.BIOME,
                Registries.DIMENSION_TYPE,
                Registries.TRIM_MATERIAL);

        List<Packet<?>> out = new ArrayList<>();
        for (ResourceKey<? extends Registry<?>> key : registryKeys) {
            for (List<PackedRegistryEntry> entries : entryLists) {
                out.add(new ClientboundRegistryDataPacket(key, entries));
            }
        }
        return out;
    }
}
