package dev.minecraftdatagenerator.packetgen;

import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Small runtime-backed leaves shared across mapping families. Unknown requirements stay visible. */
final class RuntimeFixtures implements ReflectionShapes.Fixtures {
    final Object server, registries, world, entity;
    private final List<Object> registryList = new ArrayList<>();
    private final Map<String, ShapePlan> registryPlans = new HashMap<>();
    private final Map<Class<?>, List<Class<?>>> subtypes = new HashMap<>();
    private Object player;
    private List<Object> recipes;
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
        if (!dispatched(type)) return List.of();
        return subtypes.computeIfAbsent(type, cls -> {
            List<Class<?>> result = new ArrayList<>();
            for (String name : implementors(cls)) {
                try { Class<?> c = Class.forName(name, false, cls.getClassLoader()); if (cls.isAssignableFrom(c)) result.add(c); }
                catch (ClassNotFoundException | LinkageError ignored) {}
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

    @Override public ShapePlan resolve(Type type, String path, int depth, ReflectionShapes engine) throws Exception {
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
            // Plan both halves with the calling engine, so their gaps and depth limit stay visible.
            return ShapePlan.product("pair", List.of(engine.plan(ReflectionShapes.argument(type, 0), path + ".first", depth + 1),
                    engine.plan(ReflectionShapes.argument(type, 1), path + ".second", depth + 1)), List.of("first", "second"), a -> Reflect.call(raw, "of", a[0], a[1]));
        }
        if (name.equals("WorldBorder")) {
            Object border = Reflect.maybe(world, "getWorldBorder");
            if (raw.isInstance(border)) return ShapePlan.values("world-border", border);
        }
        // Callbacks (e.g. a boss event's dirty listener) are never encoded; a no-op is enough.
        if (raw == Runnable.class) return ShapePlan.values("no-op", (Runnable) () -> {});
        if (raw.getName().startsWith("org.joml.")) {
            ShapePlan joml = joml(raw);
            if (joml != null) return joml;
        }
        if (SERIALIZERS.contains(name)) {
            ShapePlan serializers = serializers(raw, ReflectionShapes.argument(type, 0));
            if (serializers != null) return serializers;
        }
        if (metadataEntry(raw) && unbound(type)) return metadataEntries(raw, path, depth, engine);
        if (name.equals("TagKey")) {
            ShapePlan tags = tagKeys(raw, ReflectionShapes.raw(ReflectionShapes.argument(type, 0)));
            if (tags != null) return tags;
        }
        if (name.equals("HolderSet") || name.equals("RegistryEntryList")) {
            ShapePlan sets = holderSets(raw, type);
            if (sets != null) return sets;
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
                else if (type.getSimpleName().equals("GameProfile")) args[i] = ShapePlan.first(resolve(type, "player-profile", 0, null));
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
    private static ShapePlan leaf(String name, Maker maker) { return ShapePlan.product(name, List.of(), List.of(), a -> maker.make()); }

    // ---- subtype index -------------------------------------------------------------------------

    /** Abstract types whose implementations are enumerated, by simple name or binary-name suffix. */
    private static final Set<String> DISPATCH = Set.of("CustomPacketPayload", "CustomPayload", "LoginQueryRequestPayload", "LoginQueryResponsePayload",
            "NumberFormat", "NumberFormatType", "RecipeDisplay", "SlotDisplay", "ParticleOptions", "ParticleEffect", "PositionSource", "BossEvent", "BossBar");
    private static final List<String> DISPATCH_SUFFIXES = List.of("BossEventPacket$Operation", "BossBarS2CPacket$Action", "WorldBorder$BorderExtent", "WorldBorder$Area");
    private Map<String, List<String>> implementors;

    private boolean dispatched(Class<?> type) {
        return DISPATCH.contains(type.getSimpleName()) || DISPATCH_SUFFIXES.stream().anyMatch(type.getName()::endsWith);
    }

    /** Concrete implementations anywhere under net.minecraft, including private nested classes; scanned once. */
    private List<String> implementors(Class<?> type) {
        if (implementors == null) {
            implementors = new HashMap<>();
            try (var scan = new io.github.classgraph.ClassGraph().acceptPackages("net.minecraft").ignoreClassVisibility()
                    .overrideClassLoaders(type.getClassLoader()).enableClassInfo().scan()) {
                for (var ci : scan.getAllClasses()) {
                    if (ci.isAbstract() || ci.isInterface() || ci.isAnonymousInnerClass()) continue;
                    for (var parent : ci.getSuperclasses()) implementors.computeIfAbsent(parent.getName(), k -> new ArrayList<>()).add(ci.getName());
                    for (var parent : ci.getInterfaces()) implementors.computeIfAbsent(parent.getName(), k -> new ArrayList<>()).add(ci.getName());
                }
            }
        }
        return implementors.getOrDefault(type.getName(), List.of());
    }

    // ---- value fixtures ------------------------------------------------------------------------

    /** JOML vectors and quaternions, with signed, fractional and NaN components. */
    private static ShapePlan joml(Class<?> raw) {
        String name = raw.getSimpleName().replaceFirst("c$", "");
        Class<?> impl = Reflect.cls("org.joml." + name);
        if (impl == null || !raw.isAssignableFrom(impl)) return null;
        ShapePlan f = ShapePlan.values("float", 0f, 1f, -1f, 3.5f, Float.NaN);
        ShapePlan d = ShapePlan.values("double", 0d, 1d, -1d, 3.5d, Double.NaN);
        ShapePlan i = ShapePlan.values("int", 0, 1, -1, 127, Integer.MIN_VALUE);
        return switch (name) {
            case "Vector3f" -> ShapePlan.product(name, List.of(f, f, f), List.of("x", "y", "z"), a -> Reflect.construct(impl, a));
            case "Vector3d" -> ShapePlan.product(name, List.of(d, d, d), List.of("x", "y", "z"), a -> Reflect.construct(impl, a));
            case "Vector3i" -> ShapePlan.product(name, List.of(i, i, i), List.of("x", "y", "z"), a -> Reflect.construct(impl, a));
            case "Vector2f" -> ShapePlan.product(name, List.of(f, f), List.of("x", "y"), a -> Reflect.construct(impl, a));
            case "Quaternionf" -> ShapePlan.product(name, List.of(f, f, f, f), List.of("x", "y", "z", "w"), a -> Reflect.construct(impl, a));
            default -> null;
        };
    }

    private static final Set<String> SERIALIZERS = Set.of("EntityDataSerializer", "TrackedDataHandler");

    /** The registered entity-data serializers, optionally only those for one value type. */
    private ShapePlan serializers(Class<?> raw, Type valueType) {
        Map<String, Object[]> constants = serializerConstants(raw);
        if (constants.isEmpty()) return null;
        List<Object> values = new ArrayList<>(); List<String> labels = new ArrayList<>();
        boolean bound = !(valueType instanceof TypeVariable<?>) && !(valueType instanceof WildcardType) && valueType != Object.class;
        for (Map.Entry<String, Object[]> e : constants.entrySet()) {
            if (bound && !e.getValue()[1].equals(valueType.getTypeName())) continue;
            values.add(e.getValue()[0]); labels.add(e.getKey());
        }
        if (values.isEmpty()) return null;
        List<java.util.function.Supplier<?>> suppliers = new ArrayList<>();
        for (Object value : values) suppliers.add(() -> value);
        return ShapePlan.leaves("serializer", suppliers, labels);
    }

    /** Field name -> {serializer, value type name}, in declaration order. */
    private final Map<Class<?>, Map<String, Object[]>> serializerConstants = new HashMap<>();
    private Map<String, Object[]> serializerConstants(Class<?> serializer) {
        return serializerConstants.computeIfAbsent(serializer, s -> {
            Map<String, Object[]> out = new LinkedHashMap<>();
            Class<?> owner = Reflect.cls(s.getPackageName() + ".EntityDataSerializers", s.getPackageName() + ".TrackedDataHandlerRegistry");
            if (owner == null) return out;
            for (Field f : owner.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || !s.isAssignableFrom(f.getType()) || !f.trySetAccessible()) continue;
                try {
                    Object value = f.get(null);
                    Type argument = ReflectionShapes.argument(f.getGenericType(), 0);
                    if (value != null && argument != Object.class) out.put(f.getName(), new Object[]{value, argument.getTypeName(), argument});
                } catch (IllegalAccessException ignored) {}
            }
            return out;
        });
    }

    /** Entity metadata entries pair a serializer with a value of its type (DataValue, SerializedEntry, legacy Entry). */
    private static boolean metadataEntry(Class<?> raw) {
        Class<?> owner = raw.getEnclosingClass();
        return raw.getTypeParameters().length == 1 && owner != null && Set.of("SynchedEntityData", "DataTracker").contains(owner.getSimpleName())
                && Set.of("DataValue", "SerializedEntry", "Entry", "DataEntry", "DataItem").contains(raw.getSimpleName());
    }

    private static boolean unbound(Type type) {
        if (type instanceof Class<?>) return true;
        if (!(type instanceof ParameterizedType p)) return false;
        Type argument = p.getActualTypeArguments()[0];
        return argument instanceof WildcardType || argument instanceof TypeVariable<?> || argument == Object.class;
    }

    /** One branch per serializer, each planned with the entry's type parameter bound to that serializer's value type. */
    private ShapePlan metadataEntries(Class<?> raw, String path, int depth, ReflectionShapes engine) throws Exception {
        Class<?> serializer = null;
        for (String name : SERIALIZERS) for (String pkg : List.of(raw.getPackageName(), "net.minecraft.network.syncher", "net.minecraft.entity.data")) {
            Class<?> c = Reflect.cls(pkg + "." + name);
            if (c != null && serializer == null) serializer = c;
        }
        if (serializer == null || engine == null) return null;
        List<ShapePlan> branches = new ArrayList<>();
        for (Map.Entry<String, Object[]> e : serializerConstants(serializer).entrySet()) {
            ShapePlan bound = engine.plan(parameterized(raw, (Type) e.getValue()[2]), path + "<" + e.getKey() + ">", depth + 1);
            branches.add(ShapePlan.product(e.getKey(), List.of(bound), List.of("entry"), a -> a[0]));
        }
        return branches.isEmpty() ? null : ShapePlan.union("serializer", branches);
    }

    /** Tag keys of the registry holding {@code element}; up to three real tags, else one synthetic id. */
    private ShapePlan tagKeys(Class<?> tagKey, Class<?> element) {
        Object registry = registryOf(element);
        if (registry == null) return null;
        List<Object> tags = new ArrayList<>();
        for (String method : List.of("getTags", "listTagIds", "getTagNames", "streamTags", "getTagKeys")) {
            Object found = Reflect.maybe(registry, method);
            Iterator<?> it = found instanceof Stream<?> s ? s.iterator() : found instanceof Iterable<?> i ? i.iterator() : null;
            while (it != null && it.hasNext() && tags.size() < 3) {
                Object tag = it.next();
                if (!tagKey.isInstance(tag)) tag = Reflect.maybe(tag, "key", "getFirst", "getTag");
                if (tagKey.isInstance(tag)) tags.add(tag);
            }
            if (!tags.isEmpty()) break;
        }
        if (tags.isEmpty()) {
            Object key = Reflect.maybe(registry, "key", "getKey");
            Class<?> identifier = Reflect.cls("net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation", "net.minecraft.util.Identifier");
            if (key == null || identifier == null) return null;
            for (String method : List.of("create", "of")) try { tags.add(Reflect.call(tagKey, method, key, identifier(identifier, "packetgen_tag"))); break; } catch (Exception ignored) {}
        }
        return tags.isEmpty() ? null : ShapePlan.values("tag", tags.toArray());
    }

    /** Direct holder sets of 0, 1 or 2 registry holders. */
    private ShapePlan holderSets(Class<?> raw, Type type) throws Exception {
        Class<?> element = ReflectionShapes.raw(ReflectionShapes.argument(type, 0));
        ShapePlan holder = registry(type.getTypeName() + ":holderset", element, "holder");
        if (holder == null) return null;
        List<ShapePlan> sizes = new ArrayList<>();
        for (int n = 0; n <= 2; n++) {
            List<String> slots = new ArrayList<>(); for (int i = 0; i < n; i++) slots.add("[" + i + "]");
            sizes.add(ShapePlan.product("direct=" + n, Collections.nCopies(n, holder), slots, a -> {
                for (String method : List.of("direct", "of")) try { return Reflect.call(raw, method, List.of(a)); } catch (NoSuchMethodException ignored) {}
                throw new NoSuchMethodException("HolderSet.direct(List)");
            }));
        }
        return ShapePlan.union("holder-set", sizes);
    }

    private Object registryOf(Class<?> element) {
        if (element == Object.class) return null;
        for (Object registry : registryList) if (registry instanceof Iterable<?> values) {
            Iterator<?> it = values.iterator();
            if (it.hasNext() && element.isInstance(it.next())) return registry;
        }
        return null;
    }

    static ParameterizedType parameterized(Class<?> raw, Type... arguments) {
        return new ParameterizedType() {
            public Type[] getActualTypeArguments() { return arguments.clone(); }
            public Type getRawType() { return raw; }
            public Type getOwnerType() { return raw.getEnclosingClass(); }
            public String getTypeName() { return raw.getTypeName() + Arrays.toString(arguments); }
            public boolean equals(Object o) { return o instanceof ParameterizedType p && p.getRawType().equals(raw) && Arrays.equals(p.getActualTypeArguments(), arguments); }
            public int hashCode() { return raw.hashCode() * 31 + Arrays.hashCode(arguments); }
        };
    }

    private static final Class<?> VALUE_DUMP = Reflect.cls("dev.minecraftdatagenerator.packetshapes.ValueDump");
    JsonElement dump(Object value) {
        if (VALUE_DUMP != null && registries != null) try { return (JsonElement) Reflect.call(VALUE_DUMP, "dump", value, registries); } catch (Exception ignored) {}
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
