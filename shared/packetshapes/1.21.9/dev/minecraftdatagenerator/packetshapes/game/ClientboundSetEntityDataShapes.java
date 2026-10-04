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
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.coppergolem.CopperGolemState;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
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
 * The {@code serializerId} is the packet's real discriminator: it selects one of the 39 entries of
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
        // Optional<EntityReference<LivingEntity>>: both branches. The wire form is a bare UUID
        // (EntityReference.streamCodec), so only the Optional presence bit and the UUID vary.
        v.add(dv(0, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE,
                Optional.<EntityReference<LivingEntity>>empty()));
        v.add(dv(0, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE,
                Optional.of(EntityReference.<LivingEntity>of(new UUID(0L, 0L)))));
        v.add(dv(0, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE,
                Optional.of(EntityReference.<LivingEntity>of(new UUID(-1L, -1L)))));
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
        Holder<VillagerType> vType = h(ctx, Registries.VILLAGER_TYPE);
        Holder<VillagerProfession> vProf = h(ctx, Registries.VILLAGER_PROFESSION);
        if (vType != null && vProf != null) {
            v.add(dv(0, EntityDataSerializers.VILLAGER_DATA, new VillagerData(vType, vProf, 1)));
            v.add(dv(0, EntityDataSerializers.VILLAGER_DATA, new VillagerData(vType, vProf, 5)));
        }
        // OptionalInt is encoded as value+1, so "absent" is 0 and the present case must be >= 0.
        v.add(dv(0, EntityDataSerializers.OPTIONAL_UNSIGNED_INT, OptionalInt.empty()));
        v.add(dv(0, EntityDataSerializers.OPTIONAL_UNSIGNED_INT, OptionalInt.of(0)));
        v.add(dv(0, EntityDataSerializers.OPTIONAL_UNSIGNED_INT, OptionalInt.of(16383)));
        // Pose: every constant
        for (Pose p : ctx.enumAll(Pose.class)) v.add(dv(0, EntityDataSerializers.POSE, p));
        // Registry-backed variant holders. 1.21.11 has no cat/chicken/cow/pig *sound* variant
        // registries (only wolf has one), so those serializers do not exist to cover here.
        addHolder(v, EntityDataSerializers.CAT_VARIANT, h(ctx, Registries.CAT_VARIANT));
        addHolder(v, EntityDataSerializers.CHICKEN_VARIANT, h(ctx, Registries.CHICKEN_VARIANT));
        addHolder(v, EntityDataSerializers.COW_VARIANT, h(ctx, Registries.COW_VARIANT));
        addHolder(v, EntityDataSerializers.WOLF_VARIANT, h(ctx, Registries.WOLF_VARIANT));
        addHolder(v, EntityDataSerializers.WOLF_SOUND_VARIANT, h(ctx, Registries.WOLF_SOUND_VARIANT));
        addHolder(v, EntityDataSerializers.FROG_VARIANT, h(ctx, Registries.FROG_VARIANT));
        addHolder(v, EntityDataSerializers.PIG_VARIANT, h(ctx, Registries.PIG_VARIANT));
        // NOTE: zombie nautilus is a later-version mob; 1.21.10 has no ZOMBIE_NAUTILUS_VARIANT.
        addHolder(v, EntityDataSerializers.PAINTING_VARIANT, h(ctx, Registries.PAINTING_VARIANT));
        // Optional<GlobalPos>: both branches
        v.add(dv(0, EntityDataSerializers.OPTIONAL_GLOBAL_POS, Optional.<GlobalPos>empty()));
        v.add(dv(0, EntityDataSerializers.OPTIONAL_GLOBAL_POS,
                Optional.of(GlobalPos.of(Level.NETHER, new BlockPos(1, 63, -1)))));
        // Small state enums: every constant
        for (Sniffer.State s : ctx.enumAll(Sniffer.State.class)) v.add(dv(0, EntityDataSerializers.SNIFFER_STATE, s));
        for (Armadillo.ArmadilloState s : ctx.enumAll(Armadillo.ArmadilloState.class))
            v.add(dv(0, EntityDataSerializers.ARMADILLO_STATE, s));
        for (CopperGolemState s : ctx.enumAll(CopperGolemState.class))
            v.add(dv(0, EntityDataSerializers.COPPER_GOLEM_STATE, s));
        for (WeatheringCopper.WeatherState s : ctx.enumAll(WeatheringCopper.WeatherState.class))
            v.add(dv(0, EntityDataSerializers.WEATHERING_COPPER_STATE, s));
        v.add(dv(0, EntityDataSerializers.VECTOR3, new Vector3f(0f, 0f, 0f)));
        v.add(dv(0, EntityDataSerializers.VECTOR3, new Vector3f(1f, -1f, 3.5f)));
        v.add(dv(0, EntityDataSerializers.QUATERNION, new Quaternionf(0f, 0f, 0f, 1f)));
        v.add(dv(0, EntityDataSerializers.QUATERNION, new Quaternionf(0.5f, -0.5f, 0.5f, 0.5f)));
        // ResolvableProfile: the resolved (full GameProfile) and unresolved (partial) wire branches
        v.add(dv(0, EntityDataSerializers.RESOLVABLE_PROFILE,
                net.minecraft.world.item.component.ResolvableProfile.createResolved(ctx.gameProfile())));
        v.add(dv(0, EntityDataSerializers.RESOLVABLE_PROFILE,
                net.minecraft.world.item.component.ResolvableProfile.createUnresolved("x")));
        v.add(dv(0, EntityDataSerializers.RESOLVABLE_PROFILE,
                net.minecraft.world.item.component.ResolvableProfile.createUnresolved(new UUID(1L, 2L))));
        // NOTE: 1.21.10's serializer table ends at RESOLVABLE_PROFILE; HUMANOID_ARM and DYE_COLOR
        // are added in later versions, so they are intentionally absent here.
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
