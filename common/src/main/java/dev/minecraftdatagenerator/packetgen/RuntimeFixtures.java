package dev.minecraftdatagenerator.packetgen;

import com.google.gson.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Small runtime-backed leaves shared across mapping families. Unknown requirements stay visible. */
final class RuntimeFixtures implements ReflectionShapes.Fixtures {
    final Object server, registries, world, entity;
    private final List<Object> registryList = new ArrayList<>();
    private final Map<String, ShapePlan> registryPlans = new HashMap<>();
    private final SortedSet<String> limitations = new TreeSet<>();
    private final Map<Class<?>, List<Class<?>>> subtypes = new HashMap<>();
    private Object player;
    private List<Object> recipes;
    public Collection<String> limitations() { return limitations; }
    String fingerprint() throws Exception {
        List<String> entries = new ArrayList<>();
        for (Object registry : registryList) if (registry instanceof Iterable<?> values) {
            Object registryKey = Reflect.maybe(registry, "key", "getKey"); int index = 0;
            List<String> getters = Arrays.stream(registry.getClass().getMethods()).filter(m -> m.getParameterCount() == 1 && Set.of("getKey", "getId").contains(m.getName())).map(Method::getName).distinct().sorted().toList();
            for (Object value : values) {
                Object key = null;
                for (String method : getters) try { key = Reflect.call(registry, method, value); if (!(key instanceof Number)) break; } catch (Exception ignored) {}
                String className = value.getClass().getName(); int lambda = className.indexOf("$$Lambda");
                // Hidden lambda class names contain process addresses/counters. Registry IDs and
                // artifact hashes identify their implementation without making every resume stale.
                if (lambda >= 0) className = className.substring(0, lambda) + "$$Lambda";
                entries.add(registryKey + ":" + index++ + ":" + className + ":" + key);
            }
        }
        Collections.sort(entries);
        Object seed = Reflect.maybe(world, "getSeed");
        entries.add("world-seed:" + (seed instanceof Number ? seed : "unavailable"));
        return PacketCorpus.hash(String.join("\n", entries).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public List<Class<?>> subtypes(Class<?> type) {
        if (!Set.of("CustomPacketPayload", "CustomPayload", "LoginQueryRequestPayload", "LoginQueryResponsePayload", "NumberFormat", "NumberFormatType", "RecipeDisplay", "SlotDisplay", "ParticleOptions", "ParticleEffect", "PositionSource").contains(type.getSimpleName())) return List.of();
        return subtypes.computeIfAbsent(type, cls -> {
            List<Class<?>> result = new ArrayList<>();
            try (var scan = new io.github.classgraph.ClassGraph().acceptPackages(cls.getPackageName()).overrideClassLoaders(cls.getClassLoader()).enableClassInfo().scan()) {
                var classes = cls.isInterface() ? scan.getClassesImplementing(cls) : scan.getSubclasses(cls);
                for (var ci : classes) if (!ci.isAbstract() && !ci.isInterface()) result.add(ci.loadClass());
            }
            result.sort(Comparator.<Class<?>>comparingInt(c -> c.getSimpleName().contains("Brand") ? 0 : 1).thenComparing(Class::getName)); return List.copyOf(result);
        });
    }
    RuntimeFixtures(Object server) throws Exception {
        this.server = server;
        Object registryAccess = Reflect.maybe(server, "registryAccess", "getRegistryManager", "getDynamicRegistryManager");
        if (registryAccess == null) try { registryAccess = Reflect.field(server, "dimensionTracker", "registryManager"); } catch (NoSuchFieldException ignored) {}
        registries = registryAccess;
        Object level = Reflect.maybe(server, "overworld", "getOverworld", "getWorld");
        if (level == null) try { level = Reflect.call(Class.forName("dev.minecraftdatagenerator.util.DGU"), "getWorld"); } catch (Exception ignored) {}
        world = level;
        Object sample = null;
        Class<?> entities = Reflect.cls("net.minecraft.world.entity.EntityTypes", "net.minecraft.world.entity.EntityType", "net.minecraft.entity.EntityType");
        if (entities != null && world != null) try {
            Object pig = Reflect.field(entities, "PIG");
            Class<?> reason = Reflect.cls("net.minecraft.world.entity.EntitySpawnReason", "net.minecraft.world.entity.MobSpawnType", "net.minecraft.entity.SpawnReason");
            if (reason != null) try { sample = Reflect.call(pig, "create", world, Reflect.field(reason, "NATURAL")); } catch (NoSuchMethodException ignored) {}
            if (sample == null) sample = Reflect.call(pig, "create", world);
        } catch (Exception ignored) {}
        if (sample == null && world != null) {
            Class<?> pig = Reflect.cls("net.minecraft.entity.passive.PigEntity", "net.minecraft.world.entity.animal.Pig");
            if (pig != null) try { sample = Reflect.construct(pig, world); } catch (Exception ignored) {}
        }
        if (sample != null) for (String method : List.of("setUUID", "setUuid")) try { Reflect.call(sample, method, new UUID(1, 2)); break; } catch (Exception ignored) {}
        if (sample != null) try { Reflect.call(sample, "setId", 42); } catch (Exception ignored) {}
        entity = sample;
        if (registries != null) {
            Object stream = Reflect.maybe(registries, "registries", "streamAllRegistries");
            if (stream instanceof Stream<?> s) try (s) { s.forEach(e -> { Object r = Reflect.maybe(e, "value"); if (r != null) registryList.add(r); }); }
        }
        for (String className : List.of("net.minecraft.core.registries.BuiltInRegistries", "net.minecraft.util.registry.Registry", "net.minecraft.registry.Registries", "dev.minecraftdatagenerator.util.Registries", "net.minecraft.block.Block", "net.minecraft.world.level.block.Block", "net.minecraft.sound.Sound", "net.minecraft.sound.SoundEvent")) {
            Class<?> cls = Reflect.cls(className); if (cls == null) continue;
            Field[] fields = cls.getFields(); Arrays.sort(fields, Comparator.comparing(Field::getName));
            for (Field f : fields) if (Modifier.isStatic(f.getModifiers())) try {
                Object r = f.get(null); if (r instanceof Iterable<?> && !registryList.contains(r)) registryList.add(r);
            } catch (ReflectiveOperationException ignored) {}
        }
    }

    @Override public ShapePlan resolve(Type type, String path, int depth) throws Exception {
        Class<?> raw = ReflectionShapes.raw(type); String name = raw.getSimpleName();
        if (name.endsWith("ByteBuf") && (raw.getName().startsWith("net.minecraft.") || raw.getName().startsWith("io.netty."))) {
            return ShapePlan.product("payload-buffer", List.of(ShapePlan.values("bytes", new byte[0], new byte[]{0}, new byte[]{1, -1})), a -> {
                Object buffer = Reflect.call(Class.forName("io.netty.buffer.Unpooled"), "wrappedBuffer", ((byte[]) a[0]).clone());
                return raw.isInstance(buffer) ? buffer : Reflect.construct(raw, buffer);
            });
        }
        if (name.equals("DataTracker") || name.equals("SynchedEntityData")) {
            Object tracker = entity == null ? null : Reflect.maybe(entity, "getDataTracker", "getEntityData");
            if (tracker != null && raw.isInstance(tracker)) return ShapePlan.values("entity-tracker", tracker);
        }
        if (raw != Object.class) {
            if (raw.isInstance(server)) return ShapePlan.values("server-fixture", server);
            if (world != null && raw.isInstance(world)) return ShapePlan.values("world-fixture", world);
            if (entity != null && raw.isInstance(entity)) return ShapePlan.values("entity-fixture", entity);
            if (registries != null && raw.isInstance(registries)) return ShapePlan.values("registry-access", registries);
        }
        if (Set.of("PlayerEntity", "Player", "ServerPlayerEntity", "ServerPlayer").contains(name)) {
            Object value = player();
            if (raw.isInstance(value)) return ShapePlan.values("player-fixture", value);
        }
        if (name.equals("Recipe") || name.equals("RecipeEntry")) {
            if (recipes == null) {
                recipes = new ArrayList<>();
                Object manager = Reflect.maybe(server, "getRecipeManager");
                flattenRecipes(Reflect.maybe(manager, "getRecipes", "values"), recipes, 0);
                recipes.sort(Comparator.comparing(v -> v.getClass().getName() + ":" + Reflect.maybe(v, "getId", "id")));
            }
            Map<Class<?>, Object> branches = new LinkedHashMap<>();
            for (Object recipe : recipes) if (raw.isInstance(recipe)) {
                Object value = Reflect.maybe(recipe, "value"); branches.putIfAbsent(value == null ? recipe.getClass() : value.getClass(), recipe);
            }
            if (!branches.isEmpty()) return ShapePlan.values("recipe-serializer-branches", branches.values().toArray());
        }
        if (world != null && name.equals("DimensionType")) {
            Object dimension = Reflect.maybe(world, "dimensionType", "getDimension", "getDimensionType");
            if (raw.isInstance(dimension)) return ShapePlan.values("world-dimension", dimension);
        }
        if (world != null && Set.of("ChunkProvider", "ChunkSource", "LightingProvider", "LevelLightEngine", "WorldChunk", "LevelChunk", "Chunk", "ChunkSection", "LevelChunkSection").contains(name)) {
            Object chunkSource = Reflect.maybe(world, "getChunkSource", "getChunkManager");
            if (raw.isInstance(chunkSource)) return ShapePlan.values("world-chunk-source", chunkSource);
            Object light = Reflect.maybe(chunkSource, "getLightingProvider", "getLightEngine");
            if (raw.isInstance(light)) return ShapePlan.values("world-light", light);
            try {
                Object chunk = Reflect.call(world, "getChunk", 0, 0);
                if (raw.isInstance(chunk)) return ShapePlan.values("world-chunk", chunk);
                Object sections = Reflect.maybe(chunk, "getSections", "getSectionArray");
                if (sections != null && sections.getClass().isArray()) for (int i = 0; i < Array.getLength(sections); i++) {
                    Object section = Array.get(sections, i); if (raw.isInstance(section)) return ShapePlan.values("world-section", section);
                }
            } catch (NoSuchMethodException ignored) {}
        }
        if ((path.contains("SignUpdate") || path.contains("UpdateSign")) && (raw.isArray() || List.class.isAssignableFrom(raw))) {
            Class<?> element = raw.isArray() ? raw.componentType() : ReflectionShapes.raw(ReflectionShapes.argument(type, 0));
            if (element == String.class || Set.of("Text", "Component").contains(element.getSimpleName())) {
                ShapePlan texts = ShapePlan.values("sign-line", "", "x", "\u00e9", "\u4f60\u597d", "\ud83d\ude00");
                ShapePlan lines = element == String.class ? texts : ShapePlan.product("sign-text", List.of(texts), a -> text(element, (String) a[0]));
                return ShapePlan.product("four-sign-lines", Collections.nCopies(4, lines), a -> {
                    if (!raw.isArray()) return List.of(a);
                    Object array = Array.newInstance(element, 4); for (int i = 0; i < 4; i++) Array.set(array, i, a[i]); return array;
                });
            }
        }
        if (name.equals("Identifier") || name.equals("ResourceLocation")) return leaf("identifier", () -> identifier(raw, "stone"));
        if (name.equals("BlockPos")) return ShapePlan.product("positions", List.of(ShapePlan.values("coordinates", -1, 0, 1, 127, 128)), a -> Reflect.construct(raw, a[0], a[0], a[0]));
        if (name.equals("Component") || name.equals("Text")) {
            return ShapePlan.union("text", List.of(leaf("empty", () -> text(raw, "")), leaf("literal", () -> text(raw, "x"))));
        }
        if (name.equals("ItemStack")) {
            List<ShapePlan> items = new ArrayList<>();
            try { Object empty = Reflect.field(raw, "EMPTY"); items.add(leaf("empty-item", () -> copy(empty))); } catch (Exception ignored) {}
            Class<?> owner = Reflect.cls("net.minecraft.world.item.Items", "net.minecraft.item.Items");
            if (owner != null) try {
                Object stone = Reflect.field(owner, "STONE");
                for (int count : List.of(1, 64)) items.add(leaf("stone:" + count, () -> Reflect.construct(raw, stone, count)));
            } catch (Exception ignored) {}
            if (!items.isEmpty()) return ShapePlan.union("items", items);
        }
        if (name.equals("GameProfile")) return leaf("profile", () -> {
            try { return Reflect.construct(raw, new UUID(1, 2), "packetgen"); }
            catch (NoSuchMethodException e) { return Reflect.construct(raw, new UUID(1, 2).toString(), "packetgen"); }
        });
        if (name.equals("CompoundTag") || name.equals("NbtCompound") || name.equals("CompoundNBT")) return leaf("empty-nbt", () -> Reflect.construct(raw));
        if (name.equals("MessageSignature") || name.equals("MessageSignatureData")) {
            // 1.19 Yarn uses this name for a compound UUID/timestamp/signature record instead.
            try { raw.getDeclaredConstructor(byte[].class); return leaf("signature", () -> Reflect.construct(raw, new byte[256])); }
            catch (NoSuchMethodException ignored) {}
        }
        if (raw.getName().endsWith("MessageSignature$Packed")) {
            Class<?> signature = raw.getEnclosingClass();
            return ShapePlan.union("packed-signature", List.of(
                    ShapePlan.product("reference", List.of(ShapePlan.values("id", 0, 1, 127, 128)), a -> Reflect.construct(raw, a[0])),
                    leaf("full", () -> Reflect.construct(raw, -1, Reflect.construct(signature, new byte[256])))));
        }
        if (name.equals("MapPatch")) return ShapePlan.product("map-patch", List.of(ShapePlan.values("dimension", 1, 2)), a -> {
            int n = (int) a[0]; return Reflect.construct(raw, 0, 0, n, n, new byte[n * n]);
        });
        if (raw == javax.crypto.SecretKey.class) return leaf("aes-fixture", () -> new javax.crypto.spec.SecretKeySpec(new byte[16], "AES"));
        if (raw == java.security.PublicKey.class) return leaf("rsa-fixture", () -> {
            // A deterministic public fixture. No private key or host randomness is needed.
            return StaticFixtures.rsaPublicKey(1024);
        });
        if (name.equals("ServerboundKeyPacket")) {
            return ShapePlan.product("opaque-encryption-bytes", List.of(ShapePlan.values("length", 0, 1, 128)), a -> {
                Object bytes = Reflect.call(Class.forName("io.netty.buffer.Unpooled"), "buffer");
                Object buf = Reflect.construct(Class.forName("net.minecraft.network.FriendlyByteBuf"), bytes);
                try {
                    Reflect.call(buf, "writeByteArray", new byte[(int) a[0]]);
                    Reflect.call(buf, "writeByteArray", new byte[(int) a[0]]);
                    return Reflect.call(Reflect.field(raw, "STREAM_CODEC"), "decode", buf);
                } finally { Reflect.call(bytes, "release"); }
            });
        }
        if (name.equals("BitSet")) return ShapePlan.product("bits", List.of(ShapePlan.values("bit", -1, 0, 1, 63, 64)), a -> { BitSet b = new BitSet(); if ((int) a[0] >= 0) b.set((int) a[0]); return b; });
        if (name.equals("Holder") || name.equals("RegistryEntry") || name.equals("ResourceKey") || name.equals("RegistryKey")) {
            Type element = ReflectionShapes.argument(type, 0); Class<?> wanted = ReflectionShapes.raw(element);
            ShapePlan p = registry(type.getTypeName(), wanted, name.equals("Holder") || name.equals("RegistryEntry") ? "holder" : "key");
            if (p != null) return p;
        }
        if (raw.getName().startsWith("it.unimi.dsi.fastutil.") && (name.equals("IntList") || name.equals("IntCollection"))) {
            Class<?> implementation = Class.forName("it.unimi.dsi.fastutil.ints.IntArrayList");
            List<ShapePlan> lengths = new ArrayList<>(); ShapePlan element = ShapePlan.values("entity-id", 0, 1, 127, 128, Integer.MAX_VALUE);
            for (int n = 0; n <= 2; n++) lengths.add(ShapePlan.product("size:" + n, Collections.nCopies(n, element), a -> { Object list = Reflect.construct(implementation); for (Object v : a) Reflect.call(list, "add", v); return list; }));
            return ShapePlan.union(name, lengths);
        }
        if (name.equals("Pair")) {
            ReflectionShapes nested = new ReflectionShapes(this, Math.max(1, 6 - depth));
            return ShapePlan.product("pair", List.of(nested.plan(ReflectionShapes.argument(type, 0)), nested.plan(ReflectionShapes.argument(type, 1))), a -> Reflect.call(raw, "of", a[0], a[1]));
        }
        if (name.equals("Style")) try { return ShapePlan.values("empty-style", Reflect.field(raw, "EMPTY")); } catch (Exception ignored) {}
        if (raw.getName().startsWith("net.minecraft.")) {
            ShapePlan p = registry(type.getTypeName(), raw, "value"); if (p != null) return p;
        }
        return null;
    }

    private ShapePlan registry(String key, Class<?> wanted, String mode) {
        if (wanted == Object.class) return null;
        if (registryPlans.containsKey(key + mode)) return registryPlans.get(key + mode);
        List<Supplier<?>> values = new ArrayList<>(); Set<String> branches = new HashSet<>();
        for (Object registry : registryList) if (registry instanceof Iterable<?> iterable) {
            int i = 0;
            for (Object value : iterable) {
                int id = i++;
                if (!wanted.isInstance(value)) continue;
                boolean boundary = id <= 1 || id == 127 || id == 128 || id == 16383 || id == 16384;
                if (!boundary && !branches.add(value.getClass().getName())) continue;
                branches.add(value.getClass().getName());
                try {
                    Object fixture = value;
                    if (mode.equals("holder")) {
                        Object h = Reflect.maybe(value, "builtInRegistryHolder", "getRegistryEntry");
                        if (h == null) try { h = Reflect.call(registry, "wrapAsHolder", value); } catch (Exception ignored) {}
                        if (h == null) try { h = Reflect.call(registry, "getEntry", value); } catch (Exception ignored) {}
                        if (h == null) try { h = Reflect.call(registry, "getHolder", id); } catch (Exception ignored) {}
                        if (h == null) try { h = Reflect.call(registry, "getEntry", id); } catch (Exception ignored) {}
                        if (h instanceof Optional<?> o) h = o.orElse(null);
                        if (h == null) continue; fixture = h;
                    } else if (mode.equals("key")) {
                        Object k;
                        try { k = Reflect.call(registry, "getResourceKey", value); } catch (Exception e) { k = Reflect.call(registry, "getKey", value); }
                        if (k instanceof Optional<?> o) k = o.orElse(null);
                        if (k == null) continue; fixture = k;
                    }
                    Object f = fixture; values.add(() -> f);
                } catch (Exception ignored) {}
            }
        }
        ShapePlan result = values.isEmpty() ? null : ShapePlan.leaves("registry:" + key + ":" + mode, values);
        registryPlans.put(key + mode, result); return result;
    }
    private static Object copy(Object v) { Object c = Reflect.maybe(v, "copy"); return c == null ? v : c; }
    private Object player() throws Exception {
        if (player != null) return player;
        Class<?> cls = Reflect.cls("net.minecraft.server.level.ServerPlayer", "net.minecraft.server.network.ServerPlayerEntity", "net.minecraft.entity.player.ServerPlayerEntity");
        if (cls == null || world == null) return null;
        for (Constructor<?> constructor : cls.getDeclaredConstructors()) {
            Object[] args = new Object[constructor.getParameterCount()]; boolean supported = true;
            for (int i = 0; i < args.length; i++) {
                Class<?> type = constructor.getParameterTypes()[i];
                if (type.isInstance(server)) args[i] = server;
                else if (type.isInstance(world)) args[i] = world;
                else if (type.getSimpleName().equals("GameProfile")) args[i] = resolve(type, "player-profile", 0).create(BigInteger.ZERO);
                else if (type.getSimpleName().equals("ServerPlayerInteractionManager")) args[i] = Reflect.construct(type, world);
                else if (Set.of("PlayerPublicKey", "ProfilePublicKey").contains(type.getSimpleName())) { args[i] = null; continue; } // Optional authentication key on 1.19 players.
                else args[i] = Reflect.maybe(type, "createDefault", "getDefault");
                if (args[i] == null) supported = false;
            }
            if (supported && constructor.trySetAccessible()) {
                player = constructor.newInstance(args);
                try { Reflect.call(player, "setId", 43); } catch (NoSuchMethodException ignored) {}
                return player;
            }
        }
        return null;
    }
    private static void flattenRecipes(Object value, List<Object> out, int depth) {
        if (value == null || depth > 4) return;
        if (value instanceof Map<?, ?> map) { map.values().forEach(v -> flattenRecipes(v, out, depth + 1)); return; }
        if (value instanceof Iterable<?> list) { list.forEach(v -> flattenRecipes(v, out, depth + 1)); return; }
        if (value instanceof Stream<?> stream) { try (stream) { stream.forEach(v -> flattenRecipes(v, out, depth + 1)); } return; }
        out.add(value); Object unwrapped = Reflect.maybe(value, "value"); if (unwrapped != null) out.add(unwrapped);
    }
    private static Object identifier(Class<?> cls, String name) throws Exception {
        for (String method : List.of("withDefaultNamespace", "ofVanilla")) try { return Reflect.call(cls, method, name); } catch (NoSuchMethodException ignored) {}
        for (String method : List.of("parse", "of", "tryParse")) try { return Reflect.call(cls, method, "minecraft:" + name); } catch (NoSuchMethodException ignored) {}
        try { return Reflect.construct(cls, "minecraft", name); } catch (NoSuchMethodException e) { return Reflect.construct(cls, "minecraft:" + name); }
    }
    private static Object text(Class<?> cls, String value) throws Exception {
        for (String method : List.of("literal", "of")) try { return Reflect.call(cls, method, value); } catch (NoSuchMethodException ignored) {}
        return Reflect.construct(Reflect.cls("net.minecraft.text.LiteralText", "net.minecraft.text.StringTextComponent", "net.minecraft.text.TextComponent", "net.minecraft.network.chat.TextComponent"), value);
    }
    @FunctionalInterface interface Maker { Object make() throws Exception; }
    private static ShapePlan leaf(String name, Maker maker) { return ShapePlan.product(name, List.of(), a -> maker.make()); }

    JsonElement dump(Object value) {
        Class<?> dump = Reflect.cls("dev.minecraftdatagenerator.packetshapes.ValueDump");
        if (dump != null && registries != null) try { return (JsonElement) Reflect.call(dump, "dump", value, registries); } catch (Exception ignored) {}
        return dump(value, new IdentityHashMap<>(), 0, new int[]{2048});
    }
    private JsonElement dump(Object v, IdentityHashMap<Object, Boolean> seen, int depth, int[] remaining) {
        if (v == null) return JsonNull.INSTANCE;
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof Number || v instanceof CharSequence || v instanceof UUID || v instanceof Enum<?>) return new JsonPrimitive(v.toString());
        if (depth >= 8 || --remaining[0] < 0) return new JsonPrimitive("$truncated");
        if (seen.put(v, true) != null) return new JsonPrimitive("$cycle");
        try {
            if (v.getClass().isArray()) { JsonArray a = new JsonArray(); for (int i = 0; i < Math.min(Array.getLength(v), 128); i++) a.add(dump(Array.get(v, i), seen, depth + 1, remaining)); return a; }
            if (v instanceof Collection<?> c) { JsonArray a = new JsonArray(); int n = 0; for (Object x : c) { if (n++ == 128) break; a.add(dump(x, seen, depth + 1, remaining)); } return a; }
            if (v instanceof Optional<?> o) return dump(o.orElse(null), seen, depth + 1, remaining);
            JsonObject o = new JsonObject(); o.addProperty("$class", v.getClass().getName());
            if (v == server || v == world || v == registries) { o.addProperty("$fixture", true); return o; }
            for (Class<?> cls = v.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                Field[] fields = cls.getDeclaredFields(); Arrays.sort(fields, Comparator.comparing(Field::getName));
                for (Field f : fields) if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) try { if (f.trySetAccessible()) o.add(f.getName(), dump(f.get(v), seen, depth + 1, remaining)); } catch (Exception ignored) {}
            }
            return o;
        } finally { seen.remove(v); }
    }
}
