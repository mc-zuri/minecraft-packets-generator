package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Rotations;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * ClientboundSetEntityDataPacket(int id, List&lt;DataValue&gt; packedItems) — wire body is
 * {@code id:varInt} followed by a stream of packed data values, terminated by the {@code 0xFF}
 * EOF marker. Each entry is {@code index:unsignedByte, serializerId:varInt, value:<per-serializer>}.
 * <p>
 * The {@code serializerId} is the packet's real discriminator: it selects one of the 31 entries of
 * {@code EntityDataSerializers}, each with its own payload encoding. Every registered serializer is
 * emitted here, with both branches of each {@code Optional}-shaped one and every constant of each
 * enum-shaped one, so the whole dispatch table appears in the corpus. On top of that:
 * <ul>
 *   <li>list sizes 0 (bare EOF), 1, 2 and 3;</li>
 *   <li>an index sweep (0, 1, 127, 128, 254) — 255 is the EOF marker and is deliberately omitted,
 *       since it would terminate the stream rather than encode an index;</li>
 *   <li>an entity-id sweep across every VarInt byte-length boundary.</li>
 * </ul>
 * Registry-backed serializers take a live {@code Holder.Reference} from the server registries; if a
 * registry happens to be absent that serializer is skipped rather than fabricating an id.
 */
public class ClientboundSetEntityDataShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetEntityDataPacket.class;
    }

    private static <T> SynchedEntityData.DataValue<T> dv(int index, EntityDataSerializer<T> ser, T value) {
        return new SynchedEntityData.DataValue<>(index, ser, value);
    }

    /** A holder from the given registry, or null when that registry is not present/populated. */
    private static <T> Holder<T> h(ShapeCtx ctx, ResourceKey<? extends Registry<T>> key) {
        try {
            return ctx.holder(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static <T> void addHolder(List<SynchedEntityData.DataValue<?>> out,
                                      EntityDataSerializer<Holder<T>> ser, Holder<T> holder) {
        if (holder != null) out.add(dv(0, ser, holder));
    }

    /** One DataValue per distinguishable serializer payload. */
    private List<SynchedEntityData.DataValue<?>> allValues(ShapeCtx ctx) {
        List<SynchedEntityData.DataValue<?>> v = new ArrayList<>();
        BlockState stone = Blocks.STONE.defaultBlockState();
        ItemStack stoneStack = new ItemStack(net.minecraft.world.item.Items.STONE);

        v.add(dv(0, EntityDataSerializers.BYTE, (byte) 0));
        v.add(dv(0, EntityDataSerializers.BYTE, (byte) -1));
        v.add(dv(0, EntityDataSerializers.INT, 0));
        v.add(dv(0, EntityDataSerializers.INT, Integer.MIN_VALUE));
        v.add(dv(0, EntityDataSerializers.LONG, 0L));
        // Full vanilla VarLong domain; downstream limitations are reported separately.
        for (long value : ctx.varLongs()) v.add(dv(0, EntityDataSerializers.LONG, value));
        v.add(dv(0, EntityDataSerializers.FLOAT, 3.5f));
        v.add(dv(0, EntityDataSerializers.STRING, ""));
        v.add(dv(0, EntityDataSerializers.STRING, new String(Character.toChars(0x1F600))));
        v.add(dv(0, EntityDataSerializers.COMPONENT, Component.literal("x")));
        // Optional<Component>: both branches
        v.add(dv(0, EntityDataSerializers.OPTIONAL_COMPONENT, Optional.<Component>empty()));
        v.add(dv(0, EntityDataSerializers.OPTIONAL_COMPONENT, Optional.of(Component.literal("x"))));
        // ItemStack uses the OPTIONAL codec: empty and non-empty
        v.add(dv(0, EntityDataSerializers.ITEM_STACK, ItemStack.EMPTY));
        v.add(dv(0, EntityDataSerializers.ITEM_STACK, stoneStack));
        v.add(dv(0, EntityDataSerializers.BOOLEAN, false));
        v.add(dv(0, EntityDataSerializers.BOOLEAN, true));
        v.add(dv(0, EntityDataSerializers.ROTATIONS, new Rotations(0f, 0f, 0f)));
        v.add(dv(0, EntityDataSerializers.ROTATIONS, new Rotations(90f, -180f, 359.9f)));
        v.add(dv(0, EntityDataSerializers.BLOCK_POS, BlockPos.ZERO));
        v.add(dv(0, EntityDataSerializers.BLOCK_POS, new BlockPos(30000000, 2031, -30000000)));
        // Optional<BlockPos>: both branches
        v.add(dv(0, EntityDataSerializers.OPTIONAL_BLOCK_POS, Optional.<BlockPos>empty()));
        v.add(dv(0, EntityDataSerializers.OPTIONAL_BLOCK_POS, Optional.of(new BlockPos(1, 63, -1))));
        // Direction: every constant
        for (Direction d : ctx.enumAll(Direction.class)) v.add(dv(0, EntityDataSerializers.DIRECTION, d));
        // Optional<UUID>: both branches, plus both UUID long-pair extremes. 1.21.3 syncs a bare
        // UUID here; 1.21.5 replaced this serializer with OPTIONAL_LIVING_ENTITY_REFERENCE, which
        // wraps the same UUID wire form in an EntityReference.
        v.add(dv(0, EntityDataSerializers.OPTIONAL_UUID, Optional.<UUID>empty()));
        v.add(dv(0, EntityDataSerializers.OPTIONAL_UUID, Optional.of(new UUID(0L, 0L))));
        v.add(dv(0, EntityDataSerializers.OPTIONAL_UUID, Optional.of(new UUID(-1L, -1L))));
        v.add(dv(0, EntityDataSerializers.BLOCK_STATE, stone));
        // Optional<BlockState> encodes "absent" as block-state id 0, so the present case must be a
        // non-air state or it would decode back as absent.
        v.add(dv(0, EntityDataSerializers.OPTIONAL_BLOCK_STATE, Optional.<BlockState>empty()));
        v.add(dv(0, EntityDataSerializers.OPTIONAL_BLOCK_STATE, Optional.of(stone)));
        v.add(dv(0, EntityDataSerializers.PARTICLE, ParticleTypes.FLAME));
        // List<ParticleOptions>: sizes 0, 1, 2
        v.add(dv(0, EntityDataSerializers.PARTICLES, List.<ParticleOptions>of()));
        v.add(dv(0, EntityDataSerializers.PARTICLES, List.<ParticleOptions>of(ParticleTypes.FLAME)));
        v.add(dv(0, EntityDataSerializers.PARTICLES,
                List.<ParticleOptions>of(ParticleTypes.FLAME, ParticleTypes.SMOKE)));
        // CompoundTag: the empty tag and a populated one
        v.add(dv(0, EntityDataSerializers.COMPOUND_TAG, ctx.emptyNbt()));
        CompoundTag tag = new CompoundTag();
        tag.putInt("i", 1);
        tag.putString("s", "x");
        v.add(dv(0, EntityDataSerializers.COMPOUND_TAG, tag));
        // VillagerData: two registry-id var-ints + a level var-int. 1.21.3 holds the type and
        // profession directly (they became Holders in a later version), so the constants are used.
        v.add(dv(0, EntityDataSerializers.VILLAGER_DATA,
                new VillagerData(VillagerType.PLAINS, VillagerProfession.NONE, 1)));
        v.add(dv(0, EntityDataSerializers.VILLAGER_DATA,
                new VillagerData(VillagerType.SWAMP, VillagerProfession.FARMER, 5)));
        // OptionalInt is encoded as value+1, so "absent" is 0 and the present case must be >= 0.
        v.add(dv(0, EntityDataSerializers.OPTIONAL_UNSIGNED_INT, OptionalInt.empty()));
        v.add(dv(0, EntityDataSerializers.OPTIONAL_UNSIGNED_INT, OptionalInt.of(0)));
        v.add(dv(0, EntityDataSerializers.OPTIONAL_UNSIGNED_INT, OptionalInt.of(16383)));
        // Pose: every constant
        for (Pose p : ctx.enumAll(Pose.class)) v.add(dv(0, EntityDataSerializers.POSE, p));
        // Registry-backed variant holders. 1.21.3 only has cat/wolf/frog/painting variants; the
        // chicken/cow/pig variant registries and WOLF_SOUND_VARIANT arrived in 1.21.5, and zombie
        // nautilus is a later-version mob, so none of those serializers exist to cover here.
        addHolder(v, EntityDataSerializers.CAT_VARIANT, h(ctx, Registries.CAT_VARIANT));
        addHolder(v, EntityDataSerializers.WOLF_VARIANT, h(ctx, Registries.WOLF_VARIANT));
        addHolder(v, EntityDataSerializers.FROG_VARIANT, h(ctx, Registries.FROG_VARIANT));
        addHolder(v, EntityDataSerializers.PAINTING_VARIANT, h(ctx, Registries.PAINTING_VARIANT));
        // Optional<GlobalPos>: both branches
        v.add(dv(0, EntityDataSerializers.OPTIONAL_GLOBAL_POS, Optional.<GlobalPos>empty()));
        v.add(dv(0, EntityDataSerializers.OPTIONAL_GLOBAL_POS,
                Optional.of(GlobalPos.of(Level.NETHER, new BlockPos(1, 63, -1)))));
        // Small state enums: every constant
        for (Sniffer.State s : ctx.enumAll(Sniffer.State.class)) v.add(dv(0, EntityDataSerializers.SNIFFER_STATE, s));
        for (Armadillo.ArmadilloState s : ctx.enumAll(Armadillo.ArmadilloState.class))
            v.add(dv(0, EntityDataSerializers.ARMADILLO_STATE, s));
        // NOTE: COPPER_GOLEM_STATE and WEATHERING_COPPER_STATE are 26.1 additions (the copper golem
        // mob), so 1.21.3 has no such serializers to cover here.
        v.add(dv(0, EntityDataSerializers.VECTOR3, new Vector3f(0f, 0f, 0f)));
        v.add(dv(0, EntityDataSerializers.VECTOR3, new Vector3f(1f, -1f, 3.5f)));
        v.add(dv(0, EntityDataSerializers.QUATERNION, new Quaternionf(0f, 0f, 0f, 1f)));
        v.add(dv(0, EntityDataSerializers.QUATERNION, new Quaternionf(0.5f, -0.5f, 0.5f, 0.5f)));
        // NOTE: 1.21.3's serializer table ends at QUATERNION; RESOLVABLE_PROFILE, HUMANOID_ARM and
        // DYE_COLOR are added in later versions, so they are intentionally absent here.
        return v;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<SynchedEntityData.DataValue<?>> values = allValues(ctx);
        List<Packet<?>> out = new ArrayList<>();

        // Empty list: the body is just the entity id followed by the bare EOF marker.
        out.add(new ClientboundSetEntityDataPacket(1, List.of()));

        // One packet per distinguishable serializer payload (single-entry list).
        for (SynchedEntityData.DataValue<?> value : values) {
            out.add(new ClientboundSetEntityDataPacket(1, List.of(value)));
        }

        // Multi-entry lists (sizes 2 and 3), with distinct indices.
        out.add(new ClientboundSetEntityDataPacket(1, List.of(
                dv(0, EntityDataSerializers.BYTE, (byte) 1),
                dv(1, EntityDataSerializers.INT, 42))));
        out.add(new ClientboundSetEntityDataPacket(1, List.of(
                dv(0, EntityDataSerializers.BYTE, (byte) 1),
                dv(1, EntityDataSerializers.INT, 42),
                dv(2, EntityDataSerializers.STRING, "x"))));

        // Index sweep. The index is an unsigned byte; 255 is the EOF marker so it is omitted.
        for (int index : new int[]{0, 1, 127, 128, 254}) {
            out.add(new ClientboundSetEntityDataPacket(1,
                    List.of(dv(index, EntityDataSerializers.BYTE, (byte) 1))));
        }

        // Entity-id sweep across every VarInt byte-length boundary.
        for (int id : ctx.varInts()) {
            out.add(new ClientboundSetEntityDataPacket(id,
                    List.of(dv(0, EntityDataSerializers.BYTE, (byte) 1))));
        }
        return out;
    }
}
