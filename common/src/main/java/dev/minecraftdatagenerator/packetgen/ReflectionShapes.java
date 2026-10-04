package dev.minecraftdatagenerator.packetgen;

import java.lang.reflect.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

/** Minecraft-independent synthesis. Version adapters supply only constrained or runtime-backed leaves. */
public final class ReflectionShapes {
    @FunctionalInterface
    public interface Fixtures {
        ShapePlan resolve(Type type, String path, int depth) throws Exception;
        default Collection<String> limitations() { return List.of(); }
        default List<Class<?>> subtypes(Class<?> type) { return List.of(); }
    }
    private record Key(Type type, String path, int depth) {}
    private final Fixtures fixtures;
    private final int maxDepth;
    private final Map<Key, ShapePlan> plans = new HashMap<>();
    private final SortedSet<String> gaps = new TreeSet<>();
    private static final ClassValue<List<Executable>> CONSTRUCTORS = new ClassValue<>() {
        protected List<Executable> computeValue(Class<?> cls) {
            List<Executable> out = new ArrayList<>();
            for (Constructor<?> c : cls.getDeclaredConstructors()) {
                if (!cls.isRecord() && wireConstructor(c.getParameterTypes())) continue;
                if (c.trySetAccessible()) out.add(c);
            }
            for (Method m : cls.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || m.isSynthetic() || !cls.isAssignableFrom(m.getReturnType())) continue;
                if (wireConstructor(m.getParameterTypes()) || Arrays.stream(m.getParameterTypes()).anyMatch(t -> cls.isAssignableFrom(t) || t == Object.class)) continue;
                if (m.trySetAccessible()) out.add(m);
            }
            out.sort(Comparator.comparing(Executable::toGenericString));
            return List.copyOf(out);
        }
    };

    public ReflectionShapes(Fixtures fixtures, int maxDepth) { this.fixtures = fixtures; this.maxDepth = maxDepth; }
    public SortedSet<String> gaps() { TreeSet<String> all = new TreeSet<>(gaps); all.addAll(fixtures.limitations()); return Collections.unmodifiableSortedSet(all); }
    public ShapePlan plan(Type type) throws Exception { return plan(type, raw(type).getName(), 0); }

    private ShapePlan plan(Type type, String path, int depth) throws Exception {
        Key key = new Key(type, path, depth);
        ShapePlan cached = plans.get(key);
        if (cached != null) return cached;
        ShapePlan result = build(type, path, depth);
        plans.put(key, result);
        return result;
    }

    private ShapePlan build(Type type, String path, int depth) throws Exception {
        Class<?> cls = raw(type);
        ShapePlan special = fixtures.resolve(type, path, depth);
        if (special != null) return special;
        if (cls.isEnum()) {
            Object[] constants = cls.getEnumConstants();
            // Some legacy mappings rename values(), which Class.getEnumConstants relies on.
            if (constants == null) {
                List<Enum<?>> values = new ArrayList<>();
                for (Field f : cls.getDeclaredFields()) if (f.isEnumConstant() && f.trySetAccessible()) values.add((Enum<?>) f.get(null));
                values.sort(Comparator.comparingInt(Enum::ordinal)); constants = values.toArray();
            }
            if (constants.length == 0) gaps.add(path + ": enum constants unavailable");
            return ShapePlan.values(type.getTypeName(), constants);
        }
        if (cls == boolean.class || cls == Boolean.class) return ShapePlan.values("boolean", false, true);
        if (cls == int.class || cls == Integer.class) return ShapePlan.values("int", 0, 1, -1, 127, 128, 16383, 16384, 2097151, 2097152, 268435455, 268435456, Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (cls == long.class || cls == Long.class) return ShapePlan.values("long", longValues().toArray());
        if (cls == byte.class || cls == Byte.class) return ShapePlan.values("byte", (byte) 0, (byte) 1, (byte) -1, Byte.MIN_VALUE, Byte.MAX_VALUE);
        if (cls == short.class || cls == Short.class) return ShapePlan.values("short", (short) 0, (short) 1, (short) -1, Short.MIN_VALUE, Short.MAX_VALUE);
        if (cls == float.class || cls == Float.class) return ShapePlan.values("float", 0f, -0f, 1f, -1f, 3.5f, Float.MIN_VALUE, Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
        if (cls == double.class || cls == Double.class) return ShapePlan.values("double", 0d, -0d, 1d, -1d, 3.5d, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        if (cls == char.class || cls == Character.class) return ShapePlan.values("char", '\0', 'a', '\u00e9', '\u4f60', Character.MAX_VALUE);
        if (cls == String.class || cls == CharSequence.class) return ShapePlan.values("utf8", "", "x", "minecraft:stone", "\u00e9", "\u4f60\u597d", "\ud83d\ude00", "a".repeat(127), "a".repeat(128));
        if (cls == UUID.class) return ShapePlan.values("uuid", new UUID(0, 0), new UUID(1, 2), new UUID(-1, Long.MIN_VALUE));
        if (cls == Instant.class) return ShapePlan.values("instant", Instant.EPOCH, Instant.ofEpochMilli(1));
        if (!cls.isRecord() && Arrays.stream(cls.getDeclaredConstructors()).noneMatch(c -> c.getParameterCount() > 0 && !wireConstructor(c.getParameterTypes()))) {
            for (Field f : cls.getDeclaredFields()) if (Modifier.isStatic(f.getModifiers()) && cls.isAssignableFrom(f.getType()) && f.trySetAccessible()) {
                Object singleton = f.get(null); if (singleton != null) return ShapePlan.values("singleton:" + cls.getName() + "." + f.getName(), singleton);
            }
        }
        if (depth >= maxDepth) {
            gaps.add(path + ": depth limit " + maxDepth);
            if (cls == Optional.class) return ShapePlan.values("terminal-optional", Optional.empty());
            if (cls.isArray()) return ShapePlan.leaves("terminal-array", List.of(() -> Array.newInstance(cls.componentType(), 0)));
            return ShapePlan.values("unresolved-depth:" + type.getTypeName());
        }
        if (cls == Optional.class) {
            ShapePlan inner = plan(argument(type, 0), path + ".value", depth + 1);
            return ShapePlan.union("optional", List.of(ShapePlan.values("absent", Optional.empty()), ShapePlan.product("present", List.of(inner), a -> Optional.of(a[0]))));
        }
        if (cls == OptionalInt.class) return ShapePlan.union("optionalInt", List.of(ShapePlan.values("absent", OptionalInt.empty()), ShapePlan.product("present", List.of(plan(int.class, path, depth + 1)), a -> OptionalInt.of((int) a[0]))));
        if (cls == OptionalLong.class) return ShapePlan.union("optionalLong", List.of(ShapePlan.values("absent", OptionalLong.empty()), ShapePlan.product("present", List.of(plan(long.class, path, depth + 1)), a -> OptionalLong.of((long) a[0]))));
        if (cls.isArray() || Collection.class.isAssignableFrom(cls)) {
            Type element = cls.isArray() ? cls.componentType() : argument(type, 0);
            ShapePlan item = plan(element, path + "[]", depth + 1);
            List<ShapePlan> sizes = new ArrayList<>();
            for (int length = 0; length <= 2; length++) sizes.add(ShapePlan.product("length=" + length, Collections.nCopies(length, item), a -> {
                if (cls.isArray()) {
                    Object arr = Array.newInstance(cls.componentType(), a.length);
                    for (int i = 0; i < a.length; i++) Array.set(arr, i, a[i]);
                    return arr;
                }
                Collection<Object> out = collection(cls, raw(element));
                for (Object value : a) if (!out.add(value) && Set.class.isAssignableFrom(cls)) throw new IllegalArgumentException("duplicate set element");
                return out;
            }));
            return ShapePlan.union(type.getTypeName(), sizes);
        }
        if (Map.class.isAssignableFrom(cls)) {
            Type keyType = argument(type, 0), valueType = argument(type, 1);
            if (cls.getName().startsWith("it.unimi.dsi.fastutil.")) {
                String[] parts = cls.getSimpleName().replaceFirst("(Sorted)?Map$", "").split("2", 2);
                if (parts.length == 2) {
                    keyType = primitiveOr(parts[0], argument(type, 0));
                    valueType = primitiveOr(parts[1], argument(type, keyType instanceof Class<?> c && c.isPrimitive() ? 0 : 1));
                }
            }
            Type mapKeyType = keyType;
            ShapePlan k = plan(keyType, path + ".key", depth + 1);
            ShapePlan v = plan(valueType, path + ".value", depth + 1);
            List<ShapePlan> sizes = new ArrayList<>();
            for (int length = 0; length <= 2; length++) {
                List<ShapePlan> dims = new ArrayList<>();
                for (int i = 0; i < length; i++) { dims.add(k); dims.add(v); }
                sizes.add(ShapePlan.product("entries=" + length, dims, a -> {
                    Map<Object, Object> out = map(cls, raw(mapKeyType));
                    for (int i = 0; i < a.length; i += 2) { if (out.containsKey(a[i])) throw new IllegalArgumentException("duplicate map key"); out.put(a[i], a[i + 1]); }
                    return out;
                }));
            }
            return ShapePlan.union(type.getTypeName(), sizes);
        }
        if (cls.isSealed()) {
            List<ShapePlan> variants = new ArrayList<>();
            Class<?>[] permitted = cls.getPermittedSubclasses();
            Arrays.sort(permitted, Comparator.comparing(Class::getName));
            for (Class<?> subtype : permitted) variants.add(plan(subtype, path + "/" + subtype.getSimpleName(), depth + 1));
            return ShapePlan.union(type.getTypeName(), variants);
        }
        if (cls.isInterface() || Modifier.isAbstract(cls.getModifiers()) || cls == Object.class) {
            List<ShapePlan> branches = new ArrayList<>();
            for (Class<?> subtype : fixtures.subtypes(cls)) branches.add(plan(subtype, path + "/" + subtype.getSimpleName(), depth + 1));
            if (!branches.isEmpty()) return ShapePlan.union("dispatch:" + cls.getName(), branches);
            gaps.add(path + ": no concrete fixture for " + type.getTypeName());
            return ShapePlan.values("unresolved:" + type.getTypeName());
        }
        Map<TypeVariable<?>, Type> bindings = new HashMap<>();
        if (type instanceof ParameterizedType p) for (int i = 0; i < cls.getTypeParameters().length; i++) bindings.put(cls.getTypeParameters()[i], p.getActualTypeArguments()[i]);
        List<ShapePlan> candidates = new ArrayList<>();
        for (Executable exec : CONSTRUCTORS.get(cls)) {
            if (cls.isRecord() && (!(exec instanceof Constructor<?>) || exec.getParameterCount() != cls.getRecordComponents().length)) continue;
            List<ShapePlan> dims = new ArrayList<>();
            Type[] types = exec.getGenericParameterTypes();
            for (int i = 0; i < types.length; i++) {
                String name = cls.isRecord() ? cls.getRecordComponents()[i].getName() : exec.getParameters()[i].getName();
                dims.add(plan(substitute(types[i], bindings), path + "." + name, depth + 1));
            }
            candidates.add(ShapePlan.product(exec.toGenericString(), dims, a -> exec instanceof Constructor<?> c ? c.newInstance(a) : ((Method) exec).invoke(null, a)));
        }
        // Older packet classes are mutable beans with a no-arg wire constructor. Explore their fields
        // in addition to data constructors; never allocate uninitialized objects or modify final fields.
        if (!cls.isRecord() && cls.getName().startsWith("net.minecraft.")) {
            Constructor<?> empty = null;
            try { empty = cls.getDeclaredConstructor(); } catch (NoSuchMethodException ignored) {}
            if (empty != null && empty.trySetAccessible()) {
                List<Field> fields = new ArrayList<>();
                for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) for (Field f : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()) && !f.isSynthetic() && f.trySetAccessible()) fields.add(f);
                }
                fields.sort(Comparator.comparing(f -> f.getDeclaringClass().getName() + "." + f.getName()));
                if (!fields.isEmpty()) {
                    List<ShapePlan> dims = new ArrayList<>();
                    for (Field f : fields) dims.add(plan(substitute(f.getGenericType(), bindings), path + "." + f.getName(), depth + 1));
                    Constructor<?> constructor = empty;
                    candidates.add(0, ShapePlan.product("mutable-fields:" + cls.getName(), dims, a -> {
                        Object value = constructor.newInstance(); for (int i = 0; i < a.length; i++) fields.get(i).set(value, a[i]); return value;
                    }));
                }
            }
        }
        if (candidates.isEmpty()) gaps.add(path + ": no accessible constructor/factory");
        return ShapePlan.union(type.getTypeName(), candidates);
    }

    private static Type primitiveOr(String name, Type fallback) {
        return switch (name) {
            case "Int" -> int.class; case "Long" -> long.class; case "Byte" -> byte.class;
            case "Short" -> short.class; case "Char" -> char.class; case "Boolean" -> boolean.class;
            case "Float" -> float.class; case "Double" -> double.class; default -> fallback;
        };
    }
    private static boolean wireConstructor(Class<?>[] parameters) { return parameters.length == 1 && parameters[0].getSimpleName().endsWith("ByteBuf"); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<Object, Object> map(Class<?> cls, Class<?> key) throws Exception {
        if (cls == EnumMap.class) return new EnumMap(key);
        if (!cls.isInterface() && !Modifier.isAbstract(cls.getModifiers())) return (Map<Object, Object>) cls.getDeclaredConstructor().newInstance();
        if (cls.getName().startsWith("it.unimi.dsi.fastutil.")) {
            String implementation = cls.getName().replaceFirst("SortedMap$", "RBTreeMap").replaceFirst("(?<!Tree)Map$", "OpenHashMap");
            return (Map<Object, Object>) Class.forName(implementation).getDeclaredConstructor().newInstance();
        }
        return SortedMap.class.isAssignableFrom(cls) ? new TreeMap<>() : new LinkedHashMap<>();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Collection<Object> collection(Class<?> cls, Class<?> element) throws Exception {
        if (EnumSet.class.isAssignableFrom(cls)) return (Collection) EnumSet.noneOf((Class) element);
        if (Set.of("DefaultedList", "NonNullList").contains(cls.getSimpleName())) {
            for (String factory : List.of("of", "create")) try { return (Collection<Object>) Reflect.call(cls, factory); } catch (NoSuchMethodException ignored) {}
        }
        if (!cls.isInterface() && !Modifier.isAbstract(cls.getModifiers())) return (Collection<Object>) cls.getDeclaredConstructor().newInstance();
        if (cls.getName().startsWith("it.unimi.dsi.fastutil.")) {
            String suffix = Set.class.isAssignableFrom(cls) ? "OpenHashSet" : "ArrayList";
            String name = cls.getName().replaceFirst("(List|Collection|Set)$", suffix);
            return (Collection<Object>) Class.forName(name).getDeclaredConstructor().newInstance();
        }
        return Set.class.isAssignableFrom(cls) ? new LinkedHashSet<>() : new ArrayList<>();
    }

    public static List<Long> longValues() {
        LinkedHashSet<Long> values = new LinkedHashSet<>(List.of(0L, 1L, -1L));
        for (int bit = 7; bit < 64; bit += 7) { values.add((1L << bit) - 1); values.add(1L << bit); }
        values.add(Long.MIN_VALUE); values.add(Long.MAX_VALUE);
        return List.copyOf(values);
    }
    public static Class<?> raw(Type t) {
        if (t instanceof Class<?> c) return c;
        if (t instanceof ParameterizedType p) return raw(p.getRawType());
        if (t instanceof GenericArrayType a) return Array.newInstance(raw(a.getGenericComponentType()), 0).getClass();
        if (t instanceof WildcardType w) return raw(w.getUpperBounds()[0]);
        if (t instanceof TypeVariable<?> v) return raw(v.getBounds()[0]);
        return Object.class;
    }
    public static Type argument(Type t, int i) {
        return t instanceof ParameterizedType p && i < p.getActualTypeArguments().length ? p.getActualTypeArguments()[i] : Object.class;
    }
    private static Type substitute(Type t, Map<TypeVariable<?>, Type> bindings) {
        if (t instanceof TypeVariable<?> v) return bindings.getOrDefault(v, v);
        if (t instanceof ParameterizedType p) {
            Type[] args = Arrays.stream(p.getActualTypeArguments()).map(a -> substitute(a, bindings)).toArray(Type[]::new);
            return new ParameterizedType() {
                public Type[] getActualTypeArguments() { return args.clone(); }
                public Type getRawType() { return p.getRawType(); }
                public Type getOwnerType() { return p.getOwnerType(); }
                public String getTypeName() { return p.getRawType().getTypeName() + Arrays.toString(args); }
            };
        }
        return t;
    }
}
