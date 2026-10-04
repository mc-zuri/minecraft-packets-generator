package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * ClientboundBlockEntityDataPacket(BlockPos pos, BlockEntityType&lt;?&gt; type, CompoundTag tag) —
 * {@code BlockPos.STREAM_CODEC} (packed i64), {@code ByteBufCodecs.registry(BLOCK_ENTITY_TYPE)} (VarInt
 * registry id) and {@code ByteBufCodecs.TRUSTED_COMPOUND_TAG} (an unlimited-heap NBT compound).
 * <p>
 * Full cross of the domain-correct block-position spread, a block-entity-type spread taken from the live
 * registry at ids straddling the VarInt byte-length boundaries, and an NBT spread covering the empty
 * compound, a flat compound and a compound exercising every NBT tag id (byte, short, int, long, float,
 * double, byte[], string, list, compound, int[], long[]) including nesting.
 * <p>
 * NOTE: the (pos, type, tag) constructor is private (only the static {@code create(BlockEntity...)}
 * factories are public, which would need a live BlockEntity), so it is reached via reflection. The wire
 * encoding does not care whether the tag actually matches the block-entity type.
 */
public class ClientboundBlockEntityDataPacketShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundBlockEntityDataPacket.class;
    }

    private static CompoundTag flatTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:sign");
        tag.putInt("x", 1);
        return tag;
    }

    /** A compound touching every NBT tag id the writer can emit, plus one level of nesting. */
    private static CompoundTag everyTagType() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("b", (byte) -1);
        tag.putShort("s", Short.MIN_VALUE);
        tag.putInt("i", Integer.MAX_VALUE);
        tag.putLong("l", Long.MIN_VALUE);
        tag.putFloat("f", 3.5f);
        tag.putDouble("d", -3.5d);
        tag.putByteArray("ba", new byte[]{0, 1, -1});
        tag.putIntArray("ia", new int[]{0, Integer.MIN_VALUE});
        tag.putLongArray("la", new long[]{0L, Long.MAX_VALUE});
        tag.putString("str", "minecraft:stone");
        tag.putBoolean("bool", true);

        ListTag list = new ListTag();
        list.add(StringTag.valueOf(""));
        list.add(StringTag.valueOf("x"));
        tag.put("list", list);

        CompoundTag nested = new CompoundTag();
        nested.putInt("n", 0);
        nested.put("empty", new CompoundTag());
        tag.put("nested", nested);
        return tag;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Registry<BlockEntityType<?>> registry = ctx.registries.lookupOrThrow(Registries.BLOCK_ENTITY_TYPE);
        int size = registry.size();
        LinkedHashSet<BlockEntityType<?>> types = new LinkedHashSet<>();
        for (int id : new int[]{0, 1, 127, 128, size - 1}) {
            if (id >= 0 && id < size) {
                BlockEntityType<?> type = registry.byId(id);
                if (type != null) types.add(type);
            }
        }
        if (types.isEmpty()) types.add(ctx.anyOf(Registries.BLOCK_ENTITY_TYPE));

        List<CompoundTag> tags = List.of(new CompoundTag(), flatTag(), everyTagType());

        try {
            Constructor<ClientboundBlockEntityDataPacket> ctor =
                    ClientboundBlockEntityDataPacket.class.getDeclaredConstructor(
                            BlockPos.class, BlockEntityType.class, CompoundTag.class);
            ctor.setAccessible(true);

            List<Packet<?>> out = new ArrayList<>();
            for (BlockPos pos : ctx.blockPos()) {
                for (BlockEntityType<?> type : types) {
                    for (CompoundTag tag : tags) {
                        out.add(ctor.newInstance(pos, type, tag));
                    }
                }
            }
            return out;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to construct ClientboundBlockEntityDataPacket", e);
        }
    }
}
