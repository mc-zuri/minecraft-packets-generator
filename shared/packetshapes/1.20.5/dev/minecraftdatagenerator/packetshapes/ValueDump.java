package dev.minecraftdatagenerator.packetshapes;

import com.google.common.collect.Multimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.security.Key;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Dumps a packet object - the value vanilla encoded - as a JSON tree, so the validator can check that
 * what protocol.json decodes from the bytes means the same thing as what the game had.
 * <p>
 * The byte and type checks prove each field is read with the right width and kind. They cannot tell
 * that a field is the RIGHT field (two floats swapped), that mapper id 3 is really named what
 * protocol.json calls it, or that a var-int is an id-plus-one. This dump is the ground truth for
 * those: records by component, other classes by field, and the values the wire only carries in coded
 * form spelled out alongside the code - enum constants with their name, ordinal and id, registry
 * objects and holder references with their key and numeric id, block states with their state id.
 * <p>
 * Version differences in the registry API (registryOrThrow vs lookupOrThrow, ResourceLocation vs
 * Identifier) are bridged reflectively so the same class works in every version module. Anything
 * that cannot be dumped becomes a {@code "$..."} marker rather than an exception: a dump is evidence,
 * and a shape must never fail to export because of it.
 */
public final class ValueDump {
    private static final int MAX_DEPTH = 14;
    private static final int MAX_ELEMENTS = 4096;
    /**
     * Upper bound on JSON nodes per dump. A packet can reference objects whose fields fan out into
     * far more than its wire data (whole registries, caches); without a budget one such dump runs
     * long enough to trip the server watchdog. Hitting it leaves a visible "$truncated" marker.
     */
    private static final int MAX_NODES = 20000;

    private final RegistryAccess registries;
    private final IdentityHashMap<Object, Boolean> onPath = new IdentityHashMap<>();
    private int nodes;

    private ValueDump(RegistryAccess registries) {
        this.registries = registries;
    }

    public static JsonElement dump(Object value, RegistryAccess registries) {
        try {
            return new ValueDump(registries).value(value, 0);
        } catch (Throwable t) {
            return marker("$error", String.valueOf(t));
        }
    }

    private static JsonObject marker(String key, String value) {
        JsonObject o = new JsonObject();
        o.addProperty(key, value);
        return o;
    }

    private JsonElement value(Object v, int depth) {
        if (v == null) return JsonNull.INSTANCE;
        if (++nodes > MAX_NODES) return marker("$truncated", v.getClass().getName());
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof String s) return new JsonPrimitive(s);
        if (v instanceof Character c) return new JsonPrimitive(String.valueOf(c));
        if (v instanceof Number n) return number(n);
        if (depth > MAX_DEPTH) return marker("$depth", v.getClass().getName());
        if (v instanceof Enum<?> e) return enumValue(e);
        if (v instanceof Optional<?> o) return o.isPresent() ? value(o.get(), depth + 1) : JsonNull.INSTANCE;
        if (v instanceof OptionalInt o) return o.isPresent() ? new JsonPrimitive(o.getAsInt()) : JsonNull.INSTANCE;
        if (v instanceof OptionalLong o) return o.isPresent() ? number(o.getAsLong()) : JsonNull.INSTANCE;
        if (v instanceof OptionalDouble o) return o.isPresent() ? number(o.getAsDouble()) : JsonNull.INSTANCE;
        if (v instanceof UUID u) return new JsonPrimitive(u.toString());
        if (v instanceof Instant i) return marker("$instant", Long.toString(i.toEpochMilli()));
        if (v instanceof Key k) return marker("$bytes", k.getEncoded() == null ? "" : hex(k.getEncoded()));
        if (v instanceof ResourceKey<?> k) return resourceKey(k);
        if (v instanceof EntityDataSerializer<?> ser) return serializer(ser);
        if (v instanceof ParticleOptions p) return particle(p, depth);
        // Identifier entries of a registry (custom stats) go on the wire as their numeric id
        if (isIdentifier(v)) {
            Optional<Registry<Object>> reg = registryOf(v);
            return reg.isPresent() ? registered(reg.get(), v) : new JsonPrimitive(v.toString());
        }
        if (v instanceof Holder<?> h) return holder(h, depth);
        if (v instanceof BlockState s) return blockState(s);
        if (v instanceof ItemStack s) return itemStack(s, depth);
        if (v instanceof Component c) return marker("$component", c.getString());
        if (v instanceof Tag t) return marker("$nbt", t.toString());
        if (v instanceof Entity e) {
            JsonObject o = new JsonObject();
            o.addProperty("$entity", e.getId());
            return o;
        }
        if (v instanceof Level || v instanceof MinecraftServer || v instanceof RegistryAccess || v instanceof Registry<?>) {
            return marker("$skip", v.getClass().getSimpleName()); // a registry is context, not packet data
        }
        if (v instanceof HolderSet<?> hs) return holderSet(hs, depth);
        if (v instanceof Either<?, ?> e) return either(e, depth);
        if (v instanceof BitSet b) return bitSet(b);
        if (v instanceof byte[] b) return marker("$bytes", hex(b));
        Optional<Registry<Object>> reg = registryOf(v);
        if (reg.isPresent()) return registered(reg.get(), v);
        if (onPath.containsKey(v)) return marker("$cycle", v.getClass().getSimpleName());
        onPath.put(v, Boolean.TRUE);
        try {
            if (v.getClass().isArray()) return array(v, depth);
            if (v instanceof Multimap<?, ?> m) return entries(m.entries(), depth);
            if (v instanceof Map<?, ?> m) return entries(m.entrySet(), depth);
            if (v instanceof Collection<?> c) return collection(c, depth);
            if (v.getClass().isRecord()) return record(v, depth);
            if (isOpaque(v.getClass())) return marker("$value", v.toString());
            return fields(v, depth);
        } finally {
            onPath.remove(v);
        }
    }

    // ---- scalars -------------------------------------------------------------------------------

    private static JsonElement number(Number n) {
        if (n instanceof Long l) {
            // JSON numbers lose precision past 2^53; the validator compares these as strings
            return l < -(1L << 53) || l > (1L << 53) ? new JsonPrimitive(Long.toString(l)) : new JsonPrimitive(l);
        }
        if (n instanceof Float f && (f.isNaN() || f.isInfinite())) return new JsonPrimitive(f.toString());
        if (n instanceof Double d && (d.isNaN() || d.isInfinite())) return new JsonPrimitive(d.toString());
        return new JsonPrimitive(n);
    }

    private static JsonElement enumValue(Enum<?> e) {
        JsonObject o = new JsonObject();
        o.addProperty("$enum", e.name());
        o.addProperty("$ord", e.ordinal());
        // Many enums go on the wire through ByteBufCodecs.idMapper(..., X::getId) or a serialized
        // name rather than their ordinal; record whichever of those the enum has.
        Integer id = intAccessor(e, "getId", "id", "get3DDataValue");
        if (id != null && id != e.ordinal()) o.addProperty("$id", id);
        Object name = accessor(e, "getSerializedName");
        if (name instanceof String s) o.addProperty("$name", s);
        return o;
    }

    // ---- registry-backed values ----------------------------------------------------------------

    private JsonElement resourceKey(ResourceKey<?> k) {
        String[] parts = keyParts(k);
        JsonObject o = new JsonObject();
        o.addProperty("$key", parts[1]);
        o.addProperty("$registry", parts[0]);
        return o;
    }

    private JsonElement holder(Holder<?> h, int depth) {
        Optional<? extends ResourceKey<?>> key = h.unwrapKey();
        if (key.isEmpty()) {
            JsonObject o = new JsonObject();
            o.add("$direct", value(h.value(), depth + 1));
            return o;
        }
        String[] parts = keyParts(key.get());
        JsonObject o = new JsonObject();
        o.addProperty("$holder", parts[1]);
        o.addProperty("$registry", parts[0]);
        Integer raw = rawId(key.get(), h.value());
        if (raw != null) o.addProperty("$rawId", raw);
        return o;
    }

    /**
     * ParticleOptions are sometimes their own registry entry (SimpleParticleType) and sometimes an
     * object carrying one; dump both the same way - the type, then any payload - so the particle type
     * lines up at one path whatever the payload is.
     */
    private JsonElement particle(ParticleOptions p, int depth) {
        JsonObject o = new JsonObject();
        // a SimpleParticleType is its own type, so go straight to the registry to avoid recursing
        Object type = p.getType();
        Optional<Registry<Object>> reg = registryOf(type);
        o.add("type", reg.isPresent() ? registered(reg.get(), type) : marker("$value", String.valueOf(type)));
        if (type == p) return o;
        JsonElement fields = fields(p, depth);
        if (fields instanceof JsonObject f) for (Map.Entry<String, JsonElement> e : f.entrySet()) {
            if (!e.getKey().equals("type")) o.add(e.getKey(), e.getValue());
        }
        return o;
    }

    /**
     * An entity-data serializer is identified on the wire by EntityDataSerializers.getSerializedId;
     * name it by the EntityDataSerializers constant that holds it, like an enum.
     */
    private static JsonElement serializer(EntityDataSerializer<?> ser) {
        JsonObject o = new JsonObject();
        String name = "?";
        for (Field f : EntityDataSerializers.class.getFields()) {
            try {
                if (Modifier.isStatic(f.getModifiers()) && f.get(null) == ser) name = f.getName();
            } catch (Throwable ignored) {
            }
        }
        o.addProperty("$enum", name);
        o.addProperty("$ord", EntityDataSerializers.getSerializedId(ser));
        return o;
    }

    /** A named holder set is a tag and goes on the wire as its id; a direct one as its holders. */
    private JsonElement holderSet(HolderSet<?> hs, int depth) {
        Optional<?> tag = hs.unwrapKey();
        if (tag.isPresent()) {
            String t = tag.get().toString(); // TagKey[registry / location]
            int slash = t.indexOf(" / "), close = t.lastIndexOf(']');
            JsonObject o = new JsonObject();
            o.addProperty("$key", slash >= 0 && close > slash ? t.substring(slash + 3, close) : t);
            return o;
        }
        JsonArray out = new JsonArray();
        for (Holder<?> h : hs) out.add(value(h, depth + 1));
        return out;
    }

    private static JsonElement blockState(BlockState s) {
        JsonObject o = new JsonObject();
        o.addProperty("$state", s.toString());
        o.addProperty("$rawId", Block.getId(s));
        return o;
    }

    private JsonElement itemStack(ItemStack s, int depth) {
        JsonObject o = new JsonObject();
        o.add("item", value(s.getItem(), depth + 1));
        o.addProperty("count", s.getCount());
        Object patch = accessor(s, "getComponentsPatch");
        if (patch != null) o.add("components", value(patch, depth + 1));
        return o;
    }

    private static JsonElement registered(Registry<Object> reg, Object v) {
        JsonObject o = new JsonObject();
        o.addProperty("$reg", String.valueOf(reg.key()).replaceAll("^ResourceKey\\[[^/]+/ (.*)]$", "$1"));
        o.addProperty("$key", String.valueOf(reg.getKey(v)));
        o.addProperty("$rawId", reg.getId(v));
        return o;
    }

    /**
     * Every entry of every built-in registry, by identity, built once. Identity rather than a lookup
     * per registry because (a) defaulted registries (game_event, item, block, ...) answer getKey with
     * their default key for objects they do not contain, and (b) membership is per object, not per
     * class: most Identifiers are plain strings, but a custom stat's Identifier is a registry entry
     * and goes on the wire as its numeric id.
     */
    private static IdentityHashMap<Object, Registry<Object>> entries;

    @SuppressWarnings("unchecked")
    private static synchronized Optional<Registry<Object>> registryOf(Object v) {
        if (entries == null) {
            entries = new IdentityHashMap<>();
            for (Registry<?> r : BuiltInRegistries.REGISTRY) {
                for (Object e : r) entries.putIfAbsent(e, (Registry<Object>) r);
            }
        }
        return Optional.ofNullable(entries.get(v));
    }

    /** Numeric id of {@code value} in the registry {@code key} belongs to, via the live registries. */
    private Integer rawId(ResourceKey<?> key, Object value) {
        try {
            Object registryName = ResourceKey.class.getMethod("registry").invoke(key);
            Method create = null;
            for (Method m : ResourceKey.class.getMethods()) {
                if (m.getName().equals("createRegistryKey") && m.getParameterCount() == 1) create = m;
            }
            if (create == null) return null;
            Object registryKey = create.invoke(null, registryName);
            // Look the method up on the RegistryAccess interface, not on the object's class: the
            // implementation classes are often non-public, and invoking their Method fails.
            // Up to 1.21 lookupOrThrow is HolderLookup.Provider's and returns a RegistryLookup, not a
            // Registry; the Registry is registryOrThrow there. Keep going until one IS a Registry.
            for (String name : new String[]{"lookupOrThrow", "registryOrThrow"}) {
                try {
                    Method m = RegistryAccess.class.getMethod(name, ResourceKey.class);
                    if (m.invoke(registries, registryKey) instanceof Registry<?> r) return ((Registry<Object>) r).getId(value);
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** {registry, location} from "ResourceKey[registry / location]", stable across versions. */
    private static String[] keyParts(ResourceKey<?> k) {
        String s = k.toString();
        int open = s.indexOf('['), slash = s.indexOf(" / "), close = s.lastIndexOf(']');
        if (open < 0 || slash < 0 || close < 0) return new String[]{"?", s};
        return new String[]{s.substring(open + 1, slash), s.substring(slash + 3, close)};
    }

    private static boolean isIdentifier(Object v) {
        String n = v.getClass().getName();
        return n.equals("net.minecraft.resources.ResourceLocation") || n.equals("net.minecraft.resources.Identifier");
    }

    // ---- containers ----------------------------------------------------------------------------

    private JsonElement either(Either<?, ?> e, int depth) {
        JsonObject o = new JsonObject();
        if (e.left().isPresent()) o.add("$left", value(e.left().get(), depth + 1));
        else o.add("$right", value(e.right().orElse(null), depth + 1));
        return o;
    }

    private static JsonElement bitSet(BitSet b) {
        JsonObject o = new JsonObject();
        JsonArray longs = new JsonArray();
        for (long l : b.toLongArray()) longs.add(Long.toString(l));
        o.add("$bitset", longs);
        return o;
    }

    private JsonElement array(Object a, int depth) {
        JsonArray out = new JsonArray();
        int n = Math.min(Array.getLength(a), MAX_ELEMENTS);
        for (int i = 0; i < n; i++) out.add(value(Array.get(a, i), depth + 1));
        return out;
    }

    private JsonElement collection(Collection<?> c, int depth) {
        JsonArray out = new JsonArray();
        int i = 0;
        for (Object e : c) {
            if (i++ >= MAX_ELEMENTS) break;
            out.add(value(e, depth + 1));
        }
        return out;
    }

    private JsonElement entries(Collection<? extends Map.Entry<?, ?>> entries, int depth) {
        JsonArray out = new JsonArray();
        int i = 0;
        for (Map.Entry<?, ?> e : entries) {
            if (i++ >= MAX_ELEMENTS) break;
            JsonObject kv = new JsonObject();
            kv.add("key", value(e.getKey(), depth + 1));
            kv.add("value", value(e.getValue(), depth + 1));
            out.add(kv);
        }
        return out;
    }

    /**
     * The discriminator a polymorphic value is written with. Unions go on the wire as a type id
     * followed by the payload - a boss-event operation, a recipe or slot display, a position source -
     * and the id comes from getType()/type(), which is a method, not a field, so it would otherwise
     * be missing from the dump.
     */
    private void addType(JsonObject o, Object v, int depth) {
        // ResolvableProfile writes unpack()'s Either (full profile or partial) whether it is a Static
        // or a Dynamic profile, and the two keep it in differently named fields
        try {
            Method u = declared(v.getClass(), "unpack"); // protected, so getMethod cannot see it
            if (u != null && Either.class.isAssignableFrom(u.getReturnType())) {
                u.setAccessible(true);
                o.add("$unpack", value(u.invoke(v), depth + 1));
            }
        } catch (Throwable ignored) {
        }
        try {
            Method ser = v.getClass().getMethod("getSerializer");
            if (ser.getParameterCount() == 0) {
                Object t = ser.invoke(v);
                if (t != null && registryOf(t).isPresent()) o.add("$serializer", value(t, depth + 1));
            }
        } catch (Throwable ignored) {
        }
        for (String name : new String[]{"getType", "type"}) {
            try {
                Method m = v.getClass().getMethod(name);
                if (m.getParameterCount() != 0 || m.getReturnType() == void.class) continue;
                m.setAccessible(true);
                Object t = m.invoke(v);
                // a custom payload's type() is a record holding its channel id
                if (t instanceof Enum<?> || (t != null && (registryOf(t).isPresent() || t.getClass().isRecord()))) {
                    o.add("$type", value(t, depth + 1));
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private JsonElement record(Object r, int depth) {
        JsonObject o = new JsonObject();
        o.addProperty("$class", r.getClass().getSimpleName());
        addType(o, r, depth);
        for (RecordComponent rc : r.getClass().getRecordComponents()) {
            try {
                Method acc = rc.getAccessor();
                acc.setAccessible(true);
                o.add(rc.getName(), value(acc.invoke(r), depth + 1));
            } catch (Throwable t) {
                o.add(rc.getName(), marker("$error", t.getClass().getSimpleName()));
            }
        }
        return o;
    }

    private JsonElement fields(Object v, int depth) {
        JsonObject o = new JsonObject();
        o.addProperty("$class", v.getClass().getSimpleName());
        addType(o, v, depth);
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = v.getClass(); c != null && c != Object.class; c = c.getSuperclass()) chain.add(0, c);
        for (Class<?> c : chain) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.isSynthetic() && !f.getName().equals("this$0")) continue;
                try {
                    f.setAccessible(true);
                    o.add(f.getName(), value(f.get(v), depth + 1));
                } catch (Throwable t) {
                    o.add(f.getName(), marker("$error", t.getClass().getSimpleName()));
                }
            }
        }
        return o;
    }

    /** Classes whose internals are not packet data (JDK, netty, lambdas): represented by toString. */
    private static boolean isOpaque(Class<?> c) {
        String n = c.getName();
        return n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("jdk.") || n.startsWith("sun.")
                || n.startsWith("io.netty.") || n.contains("$$Lambda") || c.isSynthetic();
    }

    // ---- reflection helpers --------------------------------------------------------------------

    /** A no-arg method declared anywhere up the class chain, whatever its visibility. */
    private static Method declared(Class<?> c, String name) {
        for (; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Object accessor(Object target, String name) {
        try {
            Method m = target.getClass().getMethod(name);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Integer intAccessor(Object target, String... names) {
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name);
                if (m.getParameterCount() == 0 && (m.getReturnType() == int.class)) {
                    m.setAccessible(true);
                    return (Integer) m.invoke(target);
                }
            } catch (Throwable ignored) {
            }
            try {
                Field f = target.getClass().getDeclaredField(name);
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    return f.getInt(target);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
