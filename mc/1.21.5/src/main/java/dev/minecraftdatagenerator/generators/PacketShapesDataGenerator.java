package dev.minecraftdatagenerator.generators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ReadTrace;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import dev.minecraftdatagenerator.packetshapes.ValueDump;
import dev.minecraftdatagenerator.packetshapes.WireTrace;
import dev.minecraftdatagenerator.util.DGU;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.status.StatusProtocols;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The <b>manual, exhaustive</b> packet-shape corpus generator. Unlike {@link PacketSamplesDataGenerator}
 * (which reflectively synthesizes bounded shapes), this one requires a hand-written
 * {@link PacketShapes} provider for <b>every</b> packet that carries a {@code STREAM_CODEC}. Each
 * provider is authored from the packet's exact source and enumerates all corner cases (every enum
 * value, both boolean states, number edge cases, all switch branches). There is no generic fallback:
 * a packet with no provider is reported loudly under {@code missingProviders} — nothing is silently
 * skipped.
 * <p>
 * Gated behind the {@code PACKETGEN_MANUAL} env var so ordinary data-gen runs keep using the reflective
 * generator. When {@code PACKETGEN_MANUAL} is set this generator runs and {@link PacketSamplesDataGenerator}
 * steps aside (they share the {@code packets/} output dir). Set {@code PACKETGEN_STRICT} additionally to
 * make missing providers a hard failure.
 * <p>
 * Output is identical in shape to the reflective generator: per-packet {@code .bin} (Varint21
 * length-delimited frames of {@code varint(id)+body}) and {@code .json}, plus a {@code packetShapes.json}
 * manifest.
 */
public class PacketShapesDataGenerator implements IDataGenerator {
    private static final Logger LOGGER = Logger.getLogger(PacketShapesDataGenerator.class.getSimpleName());

    private MinecraftServer server;
    private RegistryAccess registries;
    private ServerLevel overworld;
    private Entity sampleEntity;

    @Override
    public String getDataName() {
        return "packetShapes";
    }

    @Override
    public boolean isEnabled() {
        return System.getenv("PACKETGEN_MANUAL") != null;
    }

    private record Loc(String state, String direction, int id) {
    }

    private record Slot(String state, String direction, ProtocolInfo.DetailsProvider provider) {
    }

    private static List<Slot> slots() {
        List<Slot> s = new ArrayList<>();
        s.add(new Slot("handshaking", "toServer", HandshakeProtocols.SERVERBOUND_TEMPLATE));
        s.add(new Slot("status", "toServer", StatusProtocols.SERVERBOUND_TEMPLATE));
        s.add(new Slot("status", "toClient", StatusProtocols.CLIENTBOUND_TEMPLATE));
        s.add(new Slot("login", "toServer", LoginProtocols.SERVERBOUND_TEMPLATE));
        s.add(new Slot("login", "toClient", LoginProtocols.CLIENTBOUND_TEMPLATE));
        s.add(new Slot("configuration", "toServer", ConfigurationProtocols.SERVERBOUND_TEMPLATE));
        s.add(new Slot("configuration", "toClient", ConfigurationProtocols.CLIENTBOUND_TEMPLATE));
        s.add(new Slot("play", "toServer", GameProtocols.SERVERBOUND_TEMPLATE));
        s.add(new Slot("play", "toClient", GameProtocols.CLIENTBOUND_TEMPLATE));
        return s;
    }

    private static Map<PacketType<?>, Loc> buildLocations() {
        Map<PacketType<?>, Loc> map = new HashMap<>();
        for (Slot slot : slots()) {
            slot.provider().details().listPackets((PacketType<?> type, int id) ->
                    map.put(type, new Loc(slot.state(), slot.direction(), id)));
        }
        return map;
    }

    @Override
    public JsonElement generateDataJson() {
        this.server = DGU.getCurrentlyRunningServer();
        this.registries = server.registryAccess();
        try {
            this.overworld = server.overworld();
        } catch (Throwable ignored) {
        }
        try {
            for (EntityType<?> et : BuiltInRegistries.ENTITY_TYPE) {
                Entity e = et.create(overworld, EntitySpawnReason.NATURAL);
                if (e != null) {
                    this.sampleEntity = e;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        ShapeCtx ctx = new ShapeCtx(server, registries, overworld, sampleEntity);

        Map<Class<?>, PacketShapes> providers = discoverProviders();
        Map<PacketType<?>, Loc> locations = buildLocations();

        Path outDir = server.getServerDirectory()
                .resolve("minecraft-data")
                .resolve(SharedConstants.getCurrentVersion().getName());
        String packetOutput = System.getenv("PACKETGEN_OUTPUT_DIR");
        if (packetOutput != null && !packetOutput.isBlank()) outDir = Path.of(packetOutput).resolve(SharedConstants.getCurrentVersion().getName());
        Path packetsDir = outDir.resolve("packets");
        deleteRecursively(packetsDir);

        JsonObject byState = new JsonObject();
        JsonArray missingProviders = new JsonArray();
        JsonArray noCodecPackets = new JsonArray();
        int packetsTotal = 0, packetsOk = 0, totalShapes = 0, totalRoundTripOk = 0, totalEncodeFailed = 0;

        try (ScanResult scan = new ClassGraph()
                .acceptPackages("net.minecraft.network.protocol")
                .overrideClassLoaders(Packet.class.getClassLoader())
                .enableClassInfo().scan()) {
            for (ClassInfo ci : scan.getClassesImplementing(Packet.class)) {
                if (ci.isAbstract() || ci.isInterface()) continue;
                Class<?> cls;
                try {
                    cls = ci.loadClass();
                } catch (Throwable t) {
                    continue;
                }
                StreamCodec<?, ?> codec = readStreamCodec(cls);
                if (codec == null) {
                    // No body to serialize (bundle / bundle_delimiter meta-packets). Recorded, not skipped silently.
                    noCodecPackets.add(cls.getName());
                    continue;
                }

                packetsTotal++;
                PacketShapes provider = providers.get(cls);
                if (provider == null) {
                    JsonObject miss = new JsonObject();
                    miss.addProperty("class", cls.getName());
                    missingProviders.add(miss);
                    continue;
                }

                List<Packet<?>> shapes;
                try {
                    shapes = provider.shapes(ctx);
                } catch (Throwable t) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("class", cls.getName());
                    entry.addProperty("status", "providerThrew");
                    entry.addProperty("reason", rootMessage(t));
                    place(byState, "unresolved", "unresolved", cls.getSimpleName(), entry);
                    LOGGER.log(Level.SEVERE, "provider threw for " + cls.getName(), t);
                    continue;
                }
                if (shapes == null || shapes.isEmpty()) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("class", cls.getName());
                    entry.addProperty("status", "noShapes");
                    place(byState, "unresolved", "unresolved", cls.getSimpleName(), entry);
                    continue;
                }

                PacketType<?> type;
                try {
                    type = shapes.get(0).type();
                } catch (Throwable t) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("class", cls.getName());
                    entry.addProperty("status", "typeThrew");
                    entry.addProperty("reason", rootMessage(t));
                    place(byState, "unresolved", "unresolved", cls.getSimpleName(), entry);
                    continue;
                }
                Loc loc = locations.get(type);
                if (loc == null) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("class", cls.getName());
                    entry.addProperty("status", "noLocation");
                    place(byState, "unresolved", "unresolved", type.id().getPath(), entry);
                    continue;
                }

                String state = loc.state(), dir = loc.direction(), name = type.id().getPath();
                int id = loc.id();

                JsonArray shapeArr = new JsonArray();
                ByteArrayOutputStream bin = new ByteArrayOutputStream();
                int okCount = 0, encodeFailed = 0, frameIndex = 0;
                String sampleError = null;
                for (Packet<?> instance : shapes) {
                    byte[] body;
                    WireTrace trace = new WireTrace();
                    try {
                        body = encode(codec, registries, instance, trace);
                    } catch (Throwable t) {
                        encodeFailed++;
                        if (sampleError == null) sampleError = rootMessage(t);
                        continue;
                    }
                    byte[] frame = frame(id, body);

                    boolean stable = false, consumed = false;
                    ReadTrace reads = new ReadTrace(body);
                    try {
                        RegistryFriendlyByteBuf in = new RegistryFriendlyByteBuf(reads, registries);
                        Object decoded = rawDecode(codec, in);
                        consumed = in.readableBytes() == 0;
                        byte[] second = encode(codec, registries, decoded, Unpooled.buffer());
                        stable = Arrays.equals(body, second);
                    } catch (Throwable ignored) {
                    }

                    writeVarInt(bin, frame.length);
                    bin.writeBytes(frame);

                    JsonObject shape = new JsonObject();
                    shape.addProperty("i", frameIndex);
                    shape.addProperty("bytes", frame.length);
                    shape.addProperty("hex", toHex(frame));
                    shape.addProperty("roundTrip", stable);
                    shape.addProperty("fullyConsumed", consumed);
                    // vanilla's own record of what it wrote where, for the validator's type check
                    shape.add("trace", trace.toJson());
                    // vanilla's decoder says which fixed-width fields are signed; a write alone does not
                    shape.add("reads", reads.toJson());
                    // the packet object itself, so the validator can check decoded values mean the same thing
                    shape.add("java", ValueDump.dump(instance, registries));
                    if (trace.patched()) shape.addProperty("tracePatched", true);
                    shape.addProperty("traceFormatVersion", 2);
                    shape.add("traceCoverage", trace.coverage());
                    shape.add("primitiveTrace", trace.primitives());
                    shapeArr.add(shape);
                    if (stable && consumed) okCount++;
                    frameIndex++;
                }

                JsonObject fileJson = new JsonObject();
                fileJson.addProperty("state", state);
                fileJson.addProperty("direction", dir);
                fileJson.addProperty("packet", name);
                fileJson.addProperty("id", id);
                fileJson.addProperty("class", cls.getName());
                fileJson.addProperty("status", "ok");
                fileJson.addProperty("shapeCount", frameIndex);
                fileJson.addProperty("roundTripOk", okCount);
                fileJson.add("shapes", shapeArr);

                String rel = "packets/" + state + "/" + dir + "/" + name;
                Path binPath = packetsDir.resolve(state).resolve(dir).resolve(name + ".bin");
                Path jsonPath = packetsDir.resolve(state).resolve(dir).resolve(name + ".json");
                try {
                    Files.createDirectories(binPath.getParent());
                    Files.write(binPath, bin.toByteArray());
                    Files.writeString(jsonPath, fileJson.toString());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed writing packet files for " + name, e);
                }

                JsonObject entry = new JsonObject();
                entry.addProperty("class", cls.getName());
                entry.addProperty("id", id);
                entry.addProperty("status", "ok");
                entry.addProperty("shapeCount", frameIndex);
                entry.addProperty("roundTripOk", okCount);
                if (encodeFailed > 0) entry.addProperty("encodeFailed", encodeFailed);
                if (sampleError != null) entry.addProperty("sampleError", sampleError);
                JsonArray files = new JsonArray();
                files.add(rel + ".bin");
                files.add(rel + ".json");
                entry.add("files", files);
                place(byState, state, dir, name, entry);

                packetsOk++;
                totalShapes += frameIndex;
                totalRoundTripOk += okCount;
                totalEncodeFailed += encodeFailed;
            }
        }

        int missing = missingProviders.size();
        if (missing > 0) {
            List<String> names = new ArrayList<>();
            missingProviders.forEach(e -> names.add(e.getAsJsonObject().get("class").getAsString()));
            names.sort(null);
            LOGGER.severe("MISSING PacketShapes providers for " + missing + " packet(s):\n  " + String.join("\n  ", names));
            if (System.getenv("PACKETGEN_STRICT") != null) {
                throw new IllegalStateException("PACKETGEN_STRICT: " + missing + " packets have no PacketShapes provider");
            }
        }

        JsonObject root = new JsonObject();
        JsonObject summary = new JsonObject();
        summary.addProperty("packetsTotal", packetsTotal);
        summary.addProperty("packetsOk", packetsOk);
        summary.addProperty("providersFound", providers.size());
        summary.addProperty("missingProviders", missing);
        summary.addProperty("noCodecPackets", noCodecPackets.size());
        summary.addProperty("totalShapes", totalShapes);
        summary.addProperty("roundTripOk", totalRoundTripOk);
        summary.addProperty("encodeFailed", totalEncodeFailed);
        root.add("summary", summary);
        root.add("missingProviderList", missingProviders);
        root.add("noCodecPacketList", noCodecPackets);
        root.add("byState", byState);
        return root;
    }

    // ---- provider discovery --------------------------------------------------------------------

    private Map<Class<?>, PacketShapes> discoverProviders() {
        Map<Class<?>, PacketShapes> map = new HashMap<>();
        try (ScanResult scan = new ClassGraph()
                .acceptPackages("dev.minecraftdatagenerator.packetshapes")
                .overrideClassLoaders(PacketShapes.class.getClassLoader())
                .enableClassInfo().scan()) {
            for (ClassInfo ci : scan.getClassesImplementing(PacketShapes.class)) {
                if (ci.isAbstract() || ci.isInterface()) continue;
                try {
                    PacketShapes p = (PacketShapes) ci.loadClass().getDeclaredConstructor().newInstance();
                    Class<?> pt = p.packetType();
                    PacketShapes prev = map.put(pt, p);
                    if (prev != null) {
                        LOGGER.severe("duplicate PacketShapes for " + pt.getName() + ": "
                                + prev.getClass().getName() + " and " + p.getClass().getName());
                    }
                } catch (Throwable t) {
                    LOGGER.log(Level.SEVERE, "could not instantiate provider " + ci.getName(), t);
                }
            }
        }
        return map;
    }

    // ---- shared low-level helpers (mirror PacketSamplesDataGenerator) ---------------------------

    private static StreamCodec<?, ?> readStreamCodec(Class<?> cls) {
        Field field;
        try {
            field = cls.getField("STREAM_CODEC");
        } catch (NoSuchFieldException e) {
            return null;
        }
        if (!Modifier.isStatic(field.getModifiers()) || !StreamCodec.class.isAssignableFrom(field.getType())) {
            return null;
        }
        try {
            return (StreamCodec<?, ?>) field.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static byte[] encode(StreamCodec<?, ?> codec, RegistryAccess registries, Object value, ByteBuf target) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(target, registries);
        boolean tracing = target instanceof WireTrace;
        if (tracing) WireTrace.begin();
        try {
            ((StreamCodec) codec).encode(buf, value);
        } finally {
            if (tracing) WireTrace.end();
        }
        int len = buf.readableBytes();
        byte[] out = new byte[len];
        buf.getBytes(buf.readerIndex(), out);
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object rawDecode(StreamCodec<?, ?> codec, ByteBuf buf) {
        return ((StreamCodec) codec).decode(buf);
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value & 0x7F);
    }

    private static byte[] frame(int networkId, byte[] body) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(body.length + 5);
        writeVarInt(b, networkId);
        b.writeBytes(body);
        return b.toByteArray();
    }

    private static void place(JsonObject byState, String state, String dir, String name, JsonObject entry) {
        JsonObject stateObj = byState.has(state) ? byState.getAsJsonObject(state) : new JsonObject();
        JsonObject dirObj = stateObj.has(dir) ? stateObj.getAsJsonObject(dir) : new JsonObject();
        dirObj.add(name, entry);
        stateObj.add(dir, dirObj);
        byState.add(state, stateObj);
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to clear " + dir, e);
        }
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static String toHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a) {
            sb.append(HEX[(b >> 4) & 0xf]).append(HEX[b & 0xf]);
        }
        return sb.toString();
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        String m = r.getClass().getSimpleName();
        if (r.getMessage() != null) m += ": " + r.getMessage();
        return m;
    }
}
