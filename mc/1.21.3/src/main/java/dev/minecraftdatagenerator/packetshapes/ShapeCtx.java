package dev.minecraftdatagenerator.packetshapes;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Value-construction toolkit handed to every {@link PacketShapes} provider. Bundles the live server
 * context (registries, overworld, a sample entity) with concise corner-case value builders and a
 * cartesian-product helper, so a provider can express "all shapes" declaratively:
 *
 * <pre>{@code
 * // one field:  new ClientboundKeepAlivePacket(id) for each id
 * return ctx.each(ctx.longs(), ClientboundKeepAlivePacket::new);
 *
 * // several fields: full cross-product of corner cases
 * return ctx.cross(a -> new ServerboundInteractPacket.SomeCtor((int) a[0], (boolean) a[1]),
 *                  ctx.ints(), ctx.bools());
 * }</pre>
 *
 * The scalar builders return the standard corner-case spreads; use the {@code *Small} variants where a
 * field is a bounded id/count and the extreme values would be invalid on the wire.
 */
public final class ShapeCtx {
    public final MinecraftServer server;
    public final RegistryAccess registries;
    public final ServerLevel overworld;
    public final Entity sampleEntity;

    public ShapeCtx(MinecraftServer server, RegistryAccess registries, ServerLevel overworld, Entity sampleEntity) {
        this.server = server;
        this.registries = registries;
        this.overworld = overworld;
        this.sampleEntity = sampleEntity;
    }

    // ---- scalar corner-case spreads ------------------------------------------------------------

    public List<Boolean> bools() {
        return List.of(false, true);
    }

    /** {0, 1, -1, MAX, MIN} — full int edge coverage. */
    public List<Integer> ints() {
        return List.of(0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE);
    }

    /** {0, 1, -1} — for fields where extremes would be invalid (ids, small counts). */
    public List<Integer> intsSmall() {
        return List.of(0, 1, -1);
    }

    /** {0, 1, 2} — non-negative counts/indices. */
    public List<Integer> counts() {
        return List.of(0, 1, 2);
    }

    public List<Long> longs() {
        return List.of(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE);
    }

    public List<Long> longsSmall() {
        return List.of(0L, 1L, -1L);
    }

    public List<Byte> bytes() {
        return List.of((byte) 0, (byte) 1, (byte) -1, Byte.MAX_VALUE, Byte.MIN_VALUE);
    }

    public List<Short> shorts() {
        return List.of((short) 0, (short) 1, (short) -1, Short.MAX_VALUE, Short.MIN_VALUE);
    }

    public List<Float> floats() {
        return List.of(0f, 1f, -1f, 3.5f, Float.MAX_VALUE, -Float.MAX_VALUE);
    }

    public List<Double> doubles() {
        return List.of(0d, 1d, -1d, 3.5d, Double.MAX_VALUE, -Double.MAX_VALUE);
    }

    public List<String> strings() {
        return List.of("", "x", "minecraft:stone");
    }

    public List<UUID> uuids() {
        return List.of(new UUID(0L, 0L), new UUID(1L, 2L));
    }

    /**
     * Domain-correct block positions. X/Z get only a few values (0, +-1, the +-30,000,000 world
     * border) because their packed fields are plain 26-bit signed ints; Y sweeps the whole vertical
     * range instead, since that is where the version-specific limits live: the overworld build range
     * is -64..319 in 1.21.3, and the packed field itself is 12-bit signed (-2048..2047) with
     * DimensionType clamping to -2032..2031.
     */
    public List<BlockPos> blockPos() {
        return List.of(
                new BlockPos(0, -64, 0),           // world bottom
                new BlockPos(0, 0, 0),             // origin
                new BlockPos(0, 63, 0),            // sea level
                new BlockPos(0, 319, 0),           // topmost overworld block
                new BlockPos(1, -1, 2),
                new BlockPos(-1, 1, -2),
                new BlockPos(30000000, 319, 30000000),     // +world border
                new BlockPos(-30000000, -64, -30000000),   // -world border
                new BlockPos(30000000, 2031, -30000000),   // DimensionType MAX_Y
                new BlockPos(-30000000, -2032, 30000000)); // DimensionType MIN_Y
    }

    /** Y values a block position can legally carry, from world bottom to the packed-field extremes. */
    public List<Integer> worldYs() {
        return List.of(-2032, -64, -1, 0, 63, 128, 255, 319, 2031);
    }

    /** Overworld build range only (-64 .. 319 in 1.21.3) - for fields clamped to the loaded level. */
    public List<Integer> buildYs() {
        return List.of(-64, -1, 0, 63, 128, 255, 319);
    }

    /** X/Z values: origin, +-1 and the +-30,000,000 world border. */
    public List<Integer> horizontals() {
        return List.of(0, 1, -1, 30000000, -30000000);
    }

    /** Chunk-section Y indices for the overworld (-4 .. 19 for a -64..319 world). */
    public List<Integer> sectionYs() {
        return List.of(-4, -1, 0, 1, 19);
    }

    /** Chunk coordinates, including the border-derived extremes. */
    public List<Integer> chunkCoords() {
        return List.of(0, 1, -1, 1875000, -1875000);
    }

    /**
     * VarInt encoding boundaries: every byte-length transition (1..5 bytes) plus the negative forms,
     * which always encode as the full 5 bytes. Use this for a single-field sweep rather than inside a
     * wide cross-product.
     */
    public List<Integer> varInts() {
        return List.of(0, 1, 127, 128, 16383, 16384, 2097151, 2097152,
                268435455, 268435456, Integer.MAX_VALUE, -1, Integer.MIN_VALUE);
    }

    /** Every vanilla VarLong width, including negative ten-byte values. */
    public List<Long> varLongs() {
        return dev.minecraftdatagenerator.packetgen.ReflectionShapes.longValues();
    }

    /** Rotation as a packed byte (256 steps over a full turn): cardinal points and both extremes. */
    public List<Byte> angles() {
        return List.of((byte) 0, (byte) 64, (byte) 127, (byte) -128, (byte) -64, (byte) -1);
    }

    /** Rotation in degrees: cardinals, wrap-around and both signs. */
    public List<Float> rotations() {
        return List.of(0f, 90f, 180f, 270f, -90f, -180f, 359.9f);
    }

    /** Pitch in degrees, clamped to the legal -90..90 range. */
    public List<Float> pitches() {
        return List.of(-90f, -45f, 0f, 45f, 90f);
    }

    /** Container slot indices as shorts: the -1 "outside" sentinel plus a full inventory span. */
    public List<Short> slots() {
        return List.of((short) -1, (short) 0, (short) 1, (short) 35, (short) 44, (short) 45);
    }

    /** Strings exercising the UTF-8 length prefix: empty, ASCII, 2-byte, 3-byte and 4-byte code points. */
    public List<String> utf8() {
        return List.of("", "x", "minecraft:stone",
                "éèê",
                "你好",
                new String(Character.toChars(0x1F600)));
    }

    /** Every constant of an enum, in declaration order. */
    public <E extends Enum<E>> List<E> enumAll(Class<E> e) {
        return Arrays.asList(e.getEnumConstants());
    }

    // ---- rich leaves ---------------------------------------------------------------------------

    public List<ItemStack> items() {
        List<ItemStack> out = new ArrayList<>();
        out.add(ItemStack.EMPTY);
        out.add(new ItemStack(Items.STONE));
        out.add(new ItemStack(Items.STONE, 64));
        return out;
    }

    public ItemStack emptyItem() {
        return ItemStack.EMPTY;
    }

    public List<Component> components() {
        return List.of(Component.empty(), Component.literal("x"), Component.translatable("x"));
    }

    public ResourceLocation id(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    public GameProfile gameProfile() {
        return new GameProfile(new UUID(0L, 0L), "x");
    }

    public CompoundTag emptyNbt() {
        return new CompoundTag();
    }

    // ---- registry-backed values ----------------------------------------------------------------

    /** A registry-bound {@code Holder.Reference} from the given registry (first entry). */
    public <T> Holder<T> holder(ResourceKey<? extends Registry<T>> registryKey) {
        Registry<T> reg = registries.lookupOrThrow(registryKey);
        return reg.getAny().orElseThrow();
    }

    /** The first value of the given registry. */
    public <T> T anyOf(ResourceKey<? extends Registry<T>> registryKey) {
        return holder(registryKey).value();
    }

    /** A resource key of the first entry of the given registry (its wire form is just the id). */
    public <T> ResourceKey<T> anyKey(ResourceKey<? extends Registry<T>> registryKey) {
        Registry<T> reg = registries.lookupOrThrow(registryKey);
        return reg.getResourceKey(reg.getAny().orElseThrow().value()).orElseThrow();
    }

    // ---- combinators ---------------------------------------------------------------------------

    /** One packet per value of a single dimension. */
    public <T> List<Packet<?>> each(List<T> values, Function<T, Packet<?>> factory) {
        List<Packet<?>> out = new ArrayList<>();
        for (T v : values) {
            Packet<?> p = factory.apply(v);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Full cartesian product over {@code dims}; {@code factory} receives one value per dimension.
     *  Combinations whose factory returns null are dropped. */
    public List<Packet<?>> cross(Function<Object[], Packet<?>> factory, List<?>... dims) {
        List<Packet<?>> out = new ArrayList<>();
        int n = dims.length;
        if (n == 0) return out;
        int[] idx = new int[n];
        long total = 1;
        for (List<?> d : dims) {
            if (d.isEmpty()) return out;
            total *= d.size();
        }
        for (long produced = 0; produced < total; produced++) {
            Object[] args = new Object[n];
            for (int i = 0; i < n; i++) args[i] = dims[i].get(idx[i]);
            Packet<?> p = factory.apply(args);
            if (p != null) out.add(p);
            for (int i = n - 1; i >= 0; i--) {
                if (++idx[i] < dims[i].size()) break;
                idx[i] = 0;
            }
        }
        return out;
    }

    /** Convenience: a mutable list starting with the given shapes (for providers that add ad-hoc cases). */
    @SafeVarargs
    public final List<Packet<?>> list(Packet<?>... shapes) {
        List<Packet<?>> out = new ArrayList<>();
        for (Packet<?> p : shapes) if (p != null) out.add(p);
        return out;
    }
}
