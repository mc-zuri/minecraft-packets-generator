package dev.minecraftdatagenerator.packetgen;

import com.google.gson.*;
import io.github.classgraph.ClassGraph;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** Capability-based adapters for packet-table/read-write and registered StreamCodec generations. */
public final class RuntimePacketAdapter implements PacketAdapter {
    private record Legacy(Object protocol, Object direction) {}
    private final String version;
    private final RuntimeFixtures fixtures;
    private final Class<?> bufferClass, registryBuffer, packetBase;
    private final GeneratedBuffers generated;
    private final boolean modern;
    private final Map<Class<?>, Object> providers = new HashMap<>();
    private final Map<Object, Class<?>> packetTypes = new IdentityHashMap<>();
    private final Map<String, Plan> plans = new HashMap<>();
    private final Object shapeContext;
    private String fingerprint;
    private String fixtureFingerprint;
    private final JsonObject contexts = new JsonObject();
    private static final Class<?> UNPOOLED = Reflect.cls("io.netty.buffer.Unpooled");
    private Class<?> writeTrace, readTrace;

    public RuntimePacketAdapter(String version, Path cache) throws Exception {
        // Mojang's launcher id "1.7" is the protocol-3 prerelease. The data
        // library's "1.7" family alias instead selects protocol 5 (1.7.10).
        this.version = version.equals("1.7") ? "1.7-pre" : version;
        Object server = Reflect.call(Class.forName("dev.minecraftdatagenerator.util.DGU"), "getCurrentlyRunningServer");
        fixtures = new RuntimeFixtures(server);
        modern = Reflect.cls("net.minecraft.network.codec.StreamCodec") != null;
        packetBase = Reflect.cls("net.minecraft.network.protocol.Packet", "net.minecraft.network.packet.Packet", "net.minecraft.network.Packet");
        if (packetBase == null) throw new ClassNotFoundException("packet base class for " + version);
        bufferClass = Reflect.cls("net.minecraft.network.FriendlyByteBuf", "net.minecraft.network.PacketByteBuf", "net.minecraft.util.PacketByteBuf");
        registryBuffer = Reflect.cls("net.minecraft.network.RegistryFriendlyByteBuf", "net.minecraft.network.RegistryByteBuf");
        generated = modern ? null : new GeneratedBuffers(bufferClass, cache);
        Object context = null;
        Class<?> ctx = Reflect.cls("dev.minecraftdatagenerator.packetshapes.ShapeCtx");
        if (ctx != null) context = Reflect.construct(ctx, fixtures.server, fixtures.registries, fixtures.world, fixtures.entity);
        shapeContext = context;
        try (var scan = new ClassGraph().acceptPackages("net.minecraft.network.protocol", "dev.minecraftdatagenerator.packetshapes").overrideClassLoaders(packetBase.getClassLoader()).enableClassInfo().scan()) {
            for (var ci : scan.getAllClasses()) {
                if (ci.getName().endsWith("PacketTypes")) {
                    Class<?> cls = ci.loadClass();
                    for (Field f : cls.getDeclaredFields()) if (Modifier.isStatic(f.getModifiers()) && f.getGenericType() instanceof ParameterizedType p && f.trySetAccessible()) {
                        if (f.getType().getSimpleName().equals("PacketType")) packetTypes.put(f.get(null), ReflectionShapes.raw(p.getActualTypeArguments()[0]));
                    }
                }
            }
            Class<?> provider = Reflect.cls("dev.minecraftdatagenerator.packetshapes.PacketShapes");
            if (provider != null) for (var ci : scan.getClassesImplementing(provider)) if (!ci.isAbstract() && !ci.isInterface()) {
                Object p = Reflect.construct(ci.loadClass()); Class<?> cls = (Class<?>) Reflect.call(p, "packetType");
                if (providers.put(cls, p) != null) throw new IllegalStateException("duplicate manual provider " + cls);
            }
        }
    }

    public String version() { return version; }
    public String fingerprint() throws Exception {
        if (fingerprint == null) {
            List<Class<?>> classes = new ArrayList<>(List.of(packetBase, RuntimePacketAdapter.class, Class.forName("io.netty.buffer.ByteBuf")));
            providers.values().forEach(p -> classes.add(p.getClass()));
            fixtureFingerprint = fixtures.fingerprint();
            fingerprint = RuntimeFingerprint.of("packetgen-v4\n" + version + "\n" + packetBase.getName() + "\n" + fixtureFingerprint, classes);
        }
        return fingerprint;
    }

    public List<Entry> inventory() throws Exception { return modern ? modernInventory() : legacyInventory(); }
    public JsonObject metadata() { JsonObject data = new JsonObject(); data.add("protocolContexts", contexts.deepCopy()); data.addProperty("fixtureFingerprint", fixtureFingerprint); data.addProperty("javaRuntime", System.getProperty("java.runtime.version")); return data; }

    private List<Entry> modernInventory() throws Exception {
        List<Entry> entries = new ArrayList<>();
        for (String state : List.of("handshake", "status", "login", "configuration", "game")) {
            String title = Character.toUpperCase(state.charAt(0)) + state.substring(1);
            Class<?> protocols = Reflect.cls("net.minecraft.network.protocol." + state + "." + title + "Protocols");
            if (protocols == null) continue;
            for (String direction : List.of("SERVERBOUND", "CLIENTBOUND")) {
                Object template;
                try { template = Reflect.field(protocols, direction + "_TEMPLATE", direction); } catch (NoSuchFieldException e) { continue; }
                List<Method> binders = Arrays.stream(template.getClass().getMethods()).filter(m -> m.getName().equals("bind")).sorted(Comparator.comparingInt(Method::getParameterCount)).toList();
                Method bind = binders.isEmpty() ? null : binders.get(0);
                int contexts = 1; Method[] flags = new Method[0]; Class<?> contextType = null;
                if (bind != null && bind.getParameterCount() == 2) {
                    // Generic implementation often erases C to Object; obtain the declared protocol context.
                    contextType = Reflect.cls(protocols.getName() + "$Context");
                    if (contextType == null) throw new IllegalStateException("unresolved protocol context " + protocols.getName());
                    flags = Arrays.stream(contextType.getMethods()).filter(m -> Modifier.isAbstract(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType() == boolean.class).sorted(Comparator.comparing(Method::getName)).toArray(Method[]::new);
                    if (flags.length > 8) throw new IllegalStateException("protocol context exceeds 8 boolean dimensions");
                    contexts = 1 << flags.length;
                }
                for (int bits = 0; bits < contexts; bits++) {
                    Object info = template; String contextName = contexts == 1 ? "" : "context-" + bits;
                    if (flags.length > 0) {
                        JsonObject values = new JsonObject();
                        for (int i = 0; i < flags.length; i++) values.addProperty(flags[i].getName(), (bits & (1 << i)) != 0);
                        this.contexts.add(state + "/" + direction + "/" + contextName, values);
                    }
                    if (bind != null) {
                        bind.setAccessible(true);
                        Function<Object, Object> wrapper = b -> { try { return wrap(b); } catch (Exception e) { throw new RuntimeException(e); } };
                        if (bind.getParameterCount() == 1) info = bind.invoke(template, wrapper);
                        else {
                            int selected = bits; Method[] dimensions = flags;
                            Object context = Proxy.newProxyInstance(contextType.getClassLoader(), new Class<?>[]{contextType}, (p, method, args) -> {
                                for (int i = 0; i < dimensions.length; i++) if (method.getName().equals(dimensions[i].getName())) return (selected & (1 << i)) != 0;
                                if (method.getName().equals("toString")) return "packetgen-context-" + selected;
                                throw new UnsupportedOperationException(method.toString());
                            });
                            info = bind.invoke(template, wrapper, context);
                        }
                    }
                    Object codec = Reflect.call(info, "codec");
                    List<?> byId = (List<?>) Reflect.field(codec, "byId");
                    for (int id = 0; id < byId.size(); id++) {
                        Object registration = byId.get(id); Object type = Reflect.call(registration, "type");
                        Object serializer = Reflect.call(registration, "serializer");
                        Object identifier = Reflect.call(type, "id"); String name = String.valueOf(Reflect.call(identifier, "getPath"));
                        Class<?> cls = packetTypes.get(type);
                        if (name.equals("bundle_delimiter")) cls = Reflect.cls("net.minecraft.network.protocol.game.ClientboundBundleDelimiterPacket");
                        entries.add(new Entry(state.equals("game") ? "play" : state.equals("handshake") ? "handshaking" : state,
                                direction.equals("CLIENTBOUND") ? "toClient" : "toServer", id, name, contextName, cls, serializer));
                    }
                }
            }
        }
        if (entries.isEmpty()) throw new IllegalStateException("empty modern inventory");
        return entries;
    }

    private List<Entry> legacyInventory() throws Exception {
        Class<?> protocolClass = Reflect.cls("net.minecraft.network.NetworkState", "net.minecraft.network.ConnectionProtocol");
        if (protocolClass == null || !protocolClass.isEnum()) throw new IllegalStateException("legacy protocol enum missing");
        List<Entry> entries = new ArrayList<>();
        Class<?> flow = Reflect.cls("net.minecraft.network.NetworkSide", "net.minecraft.network.NetworkDirection", "net.minecraft.network.protocol.PacketFlow");
        for (Object protocol : protocolClass.getEnumConstants()) {
            String state = ((Enum<?>) protocol).name().toLowerCase(Locale.ROOT);
            if (state.equals("handshake")) state = "handshaking";
            if (flow != null) {
                for (Object direction : flow.getEnumConstants()) {
                    Object table = null;
                    try { table = Reflect.call(protocol, "getPacketsByIds", direction); } catch (Exception ignored) {}
                    if (table == null) try { table = Reflect.call(protocol, "getPacketIdToPacketMap", direction); } catch (NoSuchMethodException ignored) {}
                    if (table == null) {
                        for (Field f : protocolClass.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers()) && Map.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true); Object candidate = ((Map<?, ?>) f.get(protocol)).get(direction);
                            if (candidate != null) { table = candidate; break; }
                        }
                    }
                    if (table != null && !(table instanceof Map<?, ?>)) {
                        try { table = Reflect.field(table, "packetIds", "classToId", "packetTypes"); } catch (Exception ignored) {}
                    }
                    if (table instanceof Map<?, ?> map) addLegacy(entries, state, direction.toString().toUpperCase(Locale.ROOT).contains("CLIENT") ? "toClient" : "toServer", protocol, direction, map);
                }
            } else {
                for (String direction : List.of("ClientToServer", "ServerToClient")) {
                    Object map = Reflect.call(protocol, "get" + direction);
                    addLegacy(entries, state, direction.equals("ClientToServer") ? "toServer" : "toClient", protocol, direction, (Map<?, ?>) map);
                }
            }
        }
        if (entries.isEmpty()) throw new IllegalStateException("empty legacy inventory; unsupported packet table layout");
        return entries;
    }
    private void addLegacy(List<Entry> out, String state, String direction, Object protocol, Object flow, Map<?, ?> map) {
        for (var e : map.entrySet()) {
            Class<?> cls = e.getValue() instanceof Class<?> c ? c : e.getKey() instanceof Class<?> c ? c : null;
            Number id = e.getKey() instanceof Number n ? n : e.getValue() instanceof Number n ? n : null;
            if (cls == null || id == null || !packetBase.isAssignableFrom(cls)) continue;
            out.add(new Entry(state, direction, id.intValue(), cls.getSimpleName(), "", cls, new Legacy(protocol, flow)));
        }
    }

    public Plan shapes(Entry entry) throws Exception {
        Class<?> cls = entry.packetClass();
        if (cls == null) throw new IllegalStateException("registered packet class could not be resolved: " + entry.key());
        if (plans.containsKey(entry.key())) return plans.get(entry.key());
        if (entry.name().equals("bundle_delimiter")) {
            Object unit = decode(entry, new byte[0]).value();
            return new Plan(null, ShapePlan.values("registered-unit", unit), List.of(), List.of());
        }
        List<ShapePlan> seeds = new ArrayList<>(); List<String> unresolved = new ArrayList<>();
        if (!modern && fixtures.world != null && LegacyReceiveState.hasPackedChunkRecords(cls)) {
            Object chunk = Reflect.call(fixtures.world, "getChunk", 0, 0);
            seeds.add(ShapePlan.product("vanilla-chunk-update-constructor", List.of(
                ShapePlan.values("positions", new short[0], new short[] { 0 }, new short[] { 0x1234, (short) 0xffff })),
                a -> { short[] positions = (short[]) a[0]; return Reflect.construct(cls, positions.length, positions, chunk); }));
        }
        if (entry.name().equals("custom_payload") && entry.direction().equals("toClient")) {
            Class<?> brand = Reflect.cls("net.minecraft.network.protocol.common.custom.BrandPayload");
            if (brand != null) seeds.add(ShapePlan.product("brand-seed", List.of(ShapePlan.values("brand", "", "packetgen")), a -> Reflect.construct(cls, Reflect.construct(brand, a[0]))));
        }
        Object provider = providers.get(cls);
        if (provider != null) try {
            List<?> samples = (List<?>) Reflect.call(provider, "shapes", shapeContext);
            List<SupplierValue> values = new ArrayList<>(); for (Object value : samples) values.add(new SupplierValue(value));
            seeds.add(ShapePlan.leaves("manual:" + provider.getClass().getSimpleName(), values));
        } catch (Exception e) { unresolved.add("manual provider: " + Reflect.error(e)); }
        ReflectionShapes reflection = new ReflectionShapes(fixtures, 6);
        ShapePlan exploration = reflection.plan(cls);
        unresolved.addAll(reflection.unresolved());
        Plan plan = new Plan(seeds.isEmpty() ? null : ShapePlan.union("seeds", seeds), exploration, List.copyOf(unresolved), List.copyOf(reflection.terminal()));
        plans.put(entry.key(), plan); return plan;
    }
    private record SupplierValue(Object value) implements java.util.function.Supplier<Object> { public Object get() { return value; } }

    private Object wrap(Object buffer) throws Exception {
        return registryBuffer != null && fixtures.registries != null ? Reflect.construct(registryBuffer, buffer, fixtures.registries) : Reflect.construct(bufferClass, buffer);
    }
    private Object tracingBuffer(byte[] body) throws Exception {
        if (!modern) return body == null ? generated.output() : generated.input(body);
        if (writeTrace == null) {
            writeTrace = Class.forName("dev.minecraftdatagenerator.packetshapes.WireTrace");
            readTrace = Class.forName("dev.minecraftdatagenerator.packetshapes.ReadTrace");
        }
        return body == null ? Reflect.construct(writeTrace) : Reflect.construct(readTrace, body);
    }
    private void encodeInto(Entry entry, Object instance, Object target) throws Exception {
        try {
            if (modern) Reflect.call(entry.codec(), "encode", wrap(target), instance);
            else Reflect.call(instance, "write", target);
        } catch (Exception e) {
            if (Reflect.cause(e).getClass().getName().equals("net.minecraft.network.SkipPacketEncoderException"))
                throw new PacketAdapter.EncoderSkipped(Reflect.cause(e));
            throw e;
        }
    }

    /** Legacy packet tables differ in how a packet is created from bytes. The first strategy that
     * exists for an entry is remembered, so later attempts do not fail through missing methods. */
    @FunctionalInterface private interface LegacyDecoder { Object decode(Entry entry, Legacy legacy, Object input) throws Exception; }
    private static final List<LegacyDecoder> LEGACY_DECODERS = List.of(
        (e, l, in) -> Reflect.call(Reflect.call(l.protocol(), "getHandler", l.direction()), "createPacket", e.id(), in),
        (e, l, in) -> Reflect.call(l.protocol(), "getPacketHandler", l.direction(), e.id(), in),
        (e, l, in) -> Reflect.call(l.protocol(), "createPacket", l.direction(), e.id(), in),
        (e, l, in) -> Reflect.call(Reflect.call(l.protocol(), "codec", l.direction()), "createPacket", e.id(), in),
        (e, l, in) -> Reflect.construct(e.packetClass(), in),
        (e, l, in) -> { Object value = Reflect.construct(e.packetClass()); Reflect.call(value, "read", in); return value; });
    private final Map<String, LegacyDecoder> legacyDecoders = new HashMap<>();

    private Object decodeFrom(Entry entry, Object input) throws Exception {
        if (modern) return Reflect.call(entry.codec(), "decode", wrap(input));
        Legacy legacy = (Legacy) entry.codec();
        LegacyDecoder known = legacyDecoders.get(entry.key());
        if (known != null) return known.decode(entry, legacy, input);
        NoSuchMethodException missing = null;
        for (LegacyDecoder decoder : LEGACY_DECODERS) {
            try {
                Object value = decoder.decode(entry, legacy, input);
                legacyDecoders.put(entry.key(), decoder);
                return value;
            } catch (NoSuchMethodException e) { missing = e; }
        }
        throw missing;
    }
    private static byte[] bytes(Object buffer) throws Exception {
        int len = ((Number) Reflect.call(buffer, "writerIndex")).intValue(); byte[] bytes = new byte[len]; Reflect.call(buffer, "getBytes", 0, bytes); return bytes;
    }

    public byte[] encode(Entry entry, Object instance) throws Exception {
        if (!modern) LegacyReceiveState.validateSource(instance);
        Object raw = Reflect.call(UNPOOLED, "buffer");
        try {
            encodeInto(entry, instance, modern ? raw : wrap(raw));
            return bytes(raw);
        } finally { Reflect.call(raw, "release"); }
    }

    @SuppressWarnings("unchecked")
    public Traced encodeTraced(Entry entry, Object instance) throws Exception {
        Object out = tracingBuffer(null);
        BufferTrace recording = new BufferTrace();
        try {
            if (modern) Reflect.call(writeTrace, "begin"); else recording.begin();
            try { encodeInto(entry, instance, out); }
            finally { if (modern) Reflect.call(writeTrace, "end"); else BufferTrace.end(); }
            byte[] body = bytes(out);
            if (modern) return new Traced(body, (List<TraceOp>) Reflect.call(out, "ops"), (Boolean) Reflect.call(out, "complete"));
            return new Traced(body, recording.writeOps(), recording.writeComplete(body.length));
        } finally { Reflect.call(out, "release"); }
    }

    @SuppressWarnings("unchecked")
    public Decoded decode(Entry entry, byte[] body) throws Exception {
        Object in = tracingBuffer(body);
        BufferTrace recording = new BufferTrace();
        try {
            Object value;
            if (!modern) recording.begin();
            try { value = decodeFrom(entry, in); } finally { if (!modern) BufferTrace.end(); }
            boolean consumed = ((Number) Reflect.call(in, "readableBytes")).intValue() == 0;
            String restored = modern ? null : LegacyReceiveState.restore(value);
            if (modern) return new Decoded(value, (List<TraceOp>) Reflect.call(in, "ops"), consumed, (Boolean) Reflect.call(in, "rewound"), null);
            return new Decoded(value, recording.readOps(), consumed, recording.rewound(), restored);
        } finally { Reflect.call(in, "release"); }
    }
    public JsonElement dump(Object value) { return fixtures.dump(value); }
    public void close() throws Exception { if (generated != null) generated.close(); }
}
