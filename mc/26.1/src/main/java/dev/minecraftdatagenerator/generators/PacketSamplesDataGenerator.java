package dev.minecraftdatagenerator.generators;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import dev.minecraftdatagenerator.util.DGU;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import dev.minecraftdatagenerator.packetshapes.WireTrace;
import dev.minecraftdatagenerator.packetshapes.ReadTrace;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.status.StatusProtocols;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import javax.crypto.KeyGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Generates <b>all bounded packet shapes</b> straight from vanilla, exported as replayable bytes.
 * <p>
 * For every packet class vanilla knows, this reflectively synthesizes a bounded cross-product of
 * instances — every enum branch, every {@code Optional}/{@code OptionalInt}/{@code OptionalLong}
 * present/absent, and small collection/array counts (0/1/2) — then for each shape encodes it with
 * the packet's own {@code STREAM_CODEC} (vanilla's real serializer), prepends the network-id varint
 * to make a full on-wire packet, and round-trips the body to record a stability verdict.
 * <p>
 * Output layout, under {@code <serverDir>/minecraft-data/<version>/packets/}:
 * <ul>
 *   <li>{@code <state>/<direction>/<packetName>.bin} — every shape of that packet concatenated as
 *       Varint21 length-delimited frames: {@code varint(frameLen)} then the frame, where the frame
 *       is {@code varint(networkId)} + body. A node minecraft-protocol length framer reads it as a
 *       stream of packets.</li>
 *   <li>{@code <state>/<direction>/<packetName>.json} — per-shape metadata (order matches frames).</li>
 * </ul>
 * The generator's returned {@code packetSamples.json} is an index/manifest of all packets.
 */
public class PacketSamplesDataGenerator implements IDataGenerator {
    private static final Logger LOGGER = Logger.getLogger(PacketSamplesDataGenerator.class.getSimpleName());
    private static final int MAX_DEPTH = 6;

    private MinecraftServer server;
    private RegistryAccess registries;
    private List<Registry<?>> registryList;
    private ServerLevel overworld;
    private Object levelData;
    private Entity sampleEntity;

    private static int maxShapes() {
        String env = System.getenv("PACKETGEN_MAX_SHAPES");
        if (env != null && !env.isBlank()) {
            try {
                return Math.max(1, Integer.parseInt(env.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return 40960;
    }

    @Override
    public String getDataName() {
        return "packetSamples";
    }

    @Override
    public boolean isEnabled() {
        // In manual mode PacketShapesDataGenerator owns the packets/ output; step aside to avoid clobber.
        return System.getenv("PACKETGEN_MANUAL") == null && "reflective".equals(System.getenv("PACKETGEN_MODE"));
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

    /** Map each PacketType known to vanilla to where it lives (state/direction/network id). */
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
            this.levelData = overworld.getLevelData();
        } catch (Throwable ignored) {
        }
        try {
            this.sampleEntity = EntityType.PIG.create(overworld, EntitySpawnReason.NATURAL);
        } catch (Throwable ignored) {
        }
        Map<PacketType<?>, Loc> locations = buildLocations();
        int cap = maxShapes();

        Path outDir = server.getServerDirectory()
                .resolve("minecraft-data")
                .resolve(SharedConstants.getCurrentVersion().name());
        String packetOutput = System.getenv("PACKETGEN_OUTPUT_DIR");
        if (packetOutput != null && !packetOutput.isBlank()) outDir = Path.of(packetOutput).resolve(SharedConstants.getCurrentVersion().name());
        Path packetsDir = outDir.resolve("packets");
        deleteRecursively(packetsDir);

        JsonObject byState = new JsonObject();
        int packetsTotal = 0, packetsOk = 0, skipped = 0, noLocation = 0;
        int totalShapes = 0, totalRoundTripOk = 0, totalEncodeFailed = 0, totalDropped = 0;

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
                if (codec == null) continue; // bundle/delimiter or codec declared on another type

                packetsTotal++;
                JsonObject entry = new JsonObject();
                entry.addProperty("class", cls.getName());

                // Enumerate bounded shapes (instances) for this packet.
                int[] dropped = {0};
                List<Object> shapes;
                try {
                    shapes = synthesize(cls, cap, dropped);
                } catch (Throwable t) {
                    entry.addProperty("status", "skipped");
                    entry.addProperty("reason", rootMessage(t));
                    skipped++;
                    place(byState, "unresolved", "unresolved", cls.getSimpleName(), entry);
                    continue;
                }
                if (shapes.isEmpty()) {
                    entry.addProperty("status", "skipped");
                    entry.addProperty("reason", "no synthesizable shapes");
                    skipped++;
                    place(byState, "unresolved", "unresolved", cls.getSimpleName(), entry);
                    continue;
                }

                // Locate the packet (state/direction/id) from the first shape's type().
                PacketType<?> type;
                try {
                    type = ((Packet<?>) shapes.get(0)).type();
                } catch (Throwable t) {
                    entry.addProperty("status", "skipped");
                    entry.addProperty("reason", "type() threw: " + rootMessage(t));
                    skipped++;
                    place(byState, "unresolved", "unresolved", cls.getSimpleName(), entry);
                    continue;
                }
                Loc loc = locations.get(type);
                if (loc == null) {
                    entry.addProperty("status", "noLocation");
                    entry.addProperty("packetType", String.valueOf(type));
                    noLocation++;
                    place(byState, "unresolved", "unresolved", type.id().getPath(), entry);
                    continue;
                }

                String state = loc.state();
                String dir = loc.direction();
                String name = type.id().getPath();
                int id = loc.id();

                // Encode + round-trip every shape; assemble the length-delimited .bin and the .json.
                JsonArray shapeArr = new JsonArray();
                ByteArrayOutputStream bin = new ByteArrayOutputStream();
                int okCount = 0, encodeFailed = 0, frameIndex = 0;
                String sampleError = null;
                for (Object instance : shapes) {
                    byte[] body;
                    WireTrace trace = new WireTrace();
                    try {
                        body = encode(codec, registries, instance, trace);
                    } catch (Throwable t) {
                        encodeFailed++;
                        if (sampleError == null) sampleError = rootMessage(t);
                        continue; // no frame for a shape we cannot encode
                    }
                    byte[] frame = frame(id, body);

                    boolean stable = false, consumed = false;
                    ReadTrace reads = new ReadTrace(body);
                    try {
                        RegistryFriendlyByteBuf in = new RegistryFriendlyByteBuf(reads, registries);
                        Object decoded = rawDecode(codec, in);
                        consumed = in.readableBytes() == 0;
                        byte[] second = encode(codec, registries, decoded);
                        stable = Arrays.equals(body, second);
                    } catch (Throwable ignored) {
                        // round-trip is metadata only; the frame is still exported
                    }

                    // Varint21 length framing: varint(frameLen) then the frame.
                    writeVarInt(bin, frame.length);
                    bin.writeBytes(frame);

                    JsonObject shape = new JsonObject();
                    shape.addProperty("i", frameIndex);
                    shape.addProperty("bytes", frame.length);
                    shape.addProperty("hex", toHex(frame));
                    shape.addProperty("roundTrip", stable);
                    shape.addProperty("fullyConsumed", consumed);
                    shape.add("trace", trace.toJson());
                    shape.add("reads", reads.toJson());
                    if (trace.patched()) shape.addProperty("tracePatched", true);
                    shape.addProperty("traceFormatVersion", 2);
                    shape.add("traceCoverage", trace.coverage());
                    shape.add("primitiveTrace", trace.primitives());
                    shapeArr.add(shape);
                    if (stable && consumed) okCount++;
                    frameIndex++;
                }

                byte[] binBytes = bin.toByteArray();

                JsonObject fileJson = new JsonObject();
                fileJson.addProperty("state", state);
                fileJson.addProperty("direction", dir);
                fileJson.addProperty("packet", name);
                fileJson.addProperty("id", id);
                fileJson.addProperty("class", cls.getName());
                fileJson.addProperty("status", "ok");
                fileJson.addProperty("shapeCount", frameIndex);
                fileJson.addProperty("roundTripOk", okCount);
                if (dropped[0] > 0) fileJson.addProperty("droppedShapes", dropped[0]);
                fileJson.add("shapes", shapeArr);

                String rel = "packets/" + state + "/" + dir + "/" + name;
                Path binPath = packetsDir.resolve(state).resolve(dir).resolve(name + ".bin");
                Path jsonPath = packetsDir.resolve(state).resolve(dir).resolve(name + ".json");
                try {
                    Files.createDirectories(binPath.getParent()); // name may contain '/' (e.g. debug/block_value)
                    Files.write(binPath, binBytes);
                    Files.writeString(jsonPath, fileJson.toString());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed writing packet files for " + name, e);
                }

                entry.addProperty("id", id);
                entry.addProperty("status", "ok");
                entry.addProperty("shapeCount", frameIndex);
                entry.addProperty("roundTripOk", okCount);
                if (encodeFailed > 0) entry.addProperty("encodeFailed", encodeFailed);
                if (sampleError != null) entry.addProperty("sampleError", sampleError);
                if (dropped[0] > 0) entry.addProperty("droppedShapes", dropped[0]);
                JsonArray files = new JsonArray();
                files.add(rel + ".bin");
                files.add(rel + ".json");
                entry.add("files", files);
                place(byState, state, dir, name, entry);

                packetsOk++;
                totalShapes += frameIndex;
                totalRoundTripOk += okCount;
                totalEncodeFailed += encodeFailed;
                totalDropped += dropped[0];
            }
        }

        JsonObject root = new JsonObject();
        JsonObject summary = new JsonObject();
        summary.addProperty("maxShapesPerPacket", cap);
        summary.addProperty("packetsTotal", packetsTotal);
        summary.addProperty("packetsOk", packetsOk);
        summary.addProperty("skipped", skipped);
        summary.addProperty("noLocation", noLocation);
        summary.addProperty("totalShapes", totalShapes);
        summary.addProperty("roundTripOk", totalRoundTripOk);
        summary.addProperty("encodeFailed", totalEncodeFailed);
        summary.addProperty("droppedShapes", totalDropped);
        root.add("summary", summary);
        root.add("byState", byState);
        return root;
    }

    // ---- output placement ----------------------------------------------------------------------

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

    // ---- vanilla codec access ------------------------------------------------------------------

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
    private static byte[] encode(StreamCodec<?, ?> codec, RegistryAccess registries, Object value) {
        return encode(codec, registries, value, Unpooled.buffer());
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

    /** LEB128 unsigned varint, matching Minecraft's FriendlyByteBuf#writeVarInt. */
    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value & 0x7F);
    }

    /** Full on-wire packet: varint(networkId) + body. */
    private static byte[] frame(int networkId, byte[] body) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(body.length + 5);
        writeVarInt(b, networkId);
        b.writeBytes(body);
        return b.toByteArray();
    }

    // ---- bounded shape enumeration -------------------------------------------------------------

    private Map<String, Supplier<List<Object>>> packetBuilders;

    /** Minimal hand-built shapes for the handful of packets generic synthesis cannot cover. */
    private Map<String, Supplier<List<Object>>> packetBuilders() {
        if (packetBuilders == null) {
            packetBuilders = new HashMap<>();

            // Interdependent int-tag: build only the (method, fields) combinations vanilla accepts.
            packetBuilders.put("ClientboundSetPlayerTeamPacket", () -> {
                net.minecraft.world.scores.Scoreboard sb = new net.minecraft.world.scores.Scoreboard();
                net.minecraft.world.scores.PlayerTeam team = new net.minecraft.world.scores.PlayerTeam(sb, "team");
                List<Object> out = new ArrayList<>();
                out.add(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
                out.add(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, false));
                out.add(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.createRemovePacket(team));
                out.add(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.createPlayerPacket(team, "p",
                        net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Action.ADD));
                out.add(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.createPlayerPacket(team, "p",
                        net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Action.REMOVE));
                return out;
            });

            // Real command tree + vanilla's own node inspector — exactly how Commands.java builds it.
            packetBuilders.put("ClientboundCommandsPacket", () -> {
                try {
                    net.minecraft.server.MinecraftServer srv = DGU.getCurrentlyRunningServer();
                    com.mojang.brigadier.tree.RootCommandNode<net.minecraft.commands.CommandSourceStack> root =
                            srv.getCommands().getDispatcher().getRoot();
                    java.lang.reflect.Field f = net.minecraft.commands.Commands.class.getDeclaredField("COMMAND_NODE_INSPECTOR");
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    net.minecraft.network.protocol.game.ClientboundCommandsPacket.NodeInspector<net.minecraft.commands.CommandSourceStack> inspector =
                            (net.minecraft.network.protocol.game.ClientboundCommandsPacket.NodeInspector<net.minecraft.commands.CommandSourceStack>) f.get(null);
                    return List.of(new net.minecraft.network.protocol.game.ClientboundCommandsPacket(root, inspector));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // Ghost recipe: a stonecutter display of empty slots (the simplest concrete RecipeDisplay).
            packetBuilders.put("ClientboundPlaceGhostRecipePacket", () -> {
                net.minecraft.world.item.crafting.display.SlotDisplay empty =
                        net.minecraft.world.item.crafting.display.SlotDisplay.Empty.INSTANCE;
                net.minecraft.world.item.crafting.display.RecipeDisplay disp =
                        new net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay(empty, empty, empty);
                return List.of(new net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket(0, disp));
            });

            // Debug event: a value-carrying subscription vanilla can actually serialize (Unit value).
            packetBuilders.put("ClientboundDebugEventPacket", () -> {
                net.minecraft.util.debug.DebugSubscription.Event<net.minecraft.util.Unit> ev =
                        net.minecraft.util.debug.DebugSubscriptions.VILLAGE_SECTIONS.packEvent(net.minecraft.util.Unit.INSTANCE);
                return List.of(new net.minecraft.network.protocol.game.ClientboundDebugEventPacket(ev));
            });

            // Recipe book add: empty list, plus one entry with an empty display and no ingredients
            // (a synthesized ingredient references items by an id vanilla cannot resolve here).
            packetBuilders.put("ClientboundRecipeBookAddPacket", () -> {
                net.minecraft.world.item.crafting.display.SlotDisplay empty =
                        net.minecraft.world.item.crafting.display.SlotDisplay.Empty.INSTANCE;
                net.minecraft.world.item.crafting.display.RecipeDisplay disp =
                        new net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay(empty, empty, empty);
                net.minecraft.world.item.crafting.RecipeBookCategory cat = this.registries
                        .lookupOrThrow(net.minecraft.core.registries.Registries.RECIPE_BOOK_CATEGORY)
                        .getAny().orElseThrow().value();
                net.minecraft.world.item.crafting.display.RecipeDisplayEntry rde =
                        new net.minecraft.world.item.crafting.display.RecipeDisplayEntry(
                                new net.minecraft.world.item.crafting.display.RecipeDisplayId(0),
                                disp, java.util.OptionalInt.empty(), cat, java.util.Optional.empty());
                net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket.Entry entry =
                        new net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket.Entry(rde, (byte) 0);
                return List.of(
                        new net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket(List.of(), false),
                        new net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket(List.of(entry), true));
            });

            // Equipment slots are decoded with a do-while loop, so the list must have at least one entry
            // (an empty list encodes to nothing and cannot be read back). Emit 1- and 2-entry shapes.
            packetBuilders.put("ClientboundSetEquipmentPacket", () -> {
                net.minecraft.world.entity.EquipmentSlot s0 = net.minecraft.world.entity.EquipmentSlot.VALUES.get(0);
                net.minecraft.world.entity.EquipmentSlot s1 = net.minecraft.world.entity.EquipmentSlot.VALUES.get(1);
                List<Object> out = new ArrayList<>();
                out.add(new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(
                        0, List.of(Pair.of(s0, ItemStack.EMPTY))));
                out.add(new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(
                        0, List.of(Pair.of(s0, ItemStack.EMPTY), Pair.of(s1, ItemStack.EMPTY))));
                return out;
            });

            // A real chunk + light snapshot from the running server. The heightmaps map is built with
            // Collectors.toMap (a HashMap in hash-iteration order) but decode rebuilds it as an EnumMap
            // (enum-ordinal order), so a straight re-encode reorders and is not byte-stable. Copy the map
            // into an EnumMap up front so the emitted bytes already match what decode reproduces.
            packetBuilders.put("ClientboundLevelChunkWithLightPacket", () -> {
                try {
                    net.minecraft.world.level.chunk.LevelChunk chunk = overworld.getChunk(0, 0);
                    net.minecraft.world.level.lighting.LevelLightEngine light = overworld.getLightEngine();
                    net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket pkt =
                            new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(chunk, light, null, null);
                    Field cdF = net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket.class
                            .getDeclaredField("chunkData");
                    cdF.setAccessible(true);
                    Object cd = cdF.get(pkt);
                    Field hmF = cd.getClass().getDeclaredField("heightmaps");
                    hmF.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<net.minecraft.world.level.levelgen.Heightmap.Types, long[]> hm =
                            (Map<net.minecraft.world.level.levelgen.Heightmap.Types, long[]>) hmF.get(cd);
                    if (hm != null && !hm.isEmpty() && !(hm instanceof java.util.EnumMap)) {
                        hmF.set(cd, new java.util.EnumMap<>(hm));
                    }
                    return List.of(pkt);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return packetBuilders;
    }

    private List<Object> synthesize(Class<?> cls, int cap, int[] dropped) throws Exception {
        Supplier<List<Object>> hand = packetBuilders().get(cls.getSimpleName());
        if (hand != null) {
            try {
                List<Object> v = hand.get();
                if (v != null && !v.isEmpty()) return v;
            } catch (Throwable ignored) {
            }
        }
        return variants(cls, cls, 0, cap, dropped);
    }

    /**
     * Returns a bounded list of synthesized values for {@code raw} (using {@code generic} for element
     * types). Enums, Optionals and collections branch; scalars and complex leaf types contribute one
     * value. Records/constructors are the cross-product of their components, capped at {@code cap}
     * (overflow accumulated into {@code dropped}). Throws if the type cannot be synthesized at all.
     */
    private List<Object> variants(Class<?> raw, Type generic, int depth, int cap, int[] dropped) throws Exception {
        return variants(raw, generic, null, depth, cap, dropped);
    }

    private List<Object> variants(Class<?> raw, Type generic, String name, int depth, int cap, int[] dropped) throws Exception {
        if (depth > MAX_DEPTH) return List.of(leaf(raw, generic, depth));

        // World/entity/opaque value providers (Level, Entity, MessageSignature, fastutil maps, ...).
        Object sp = special(raw, generic);
        if (sp != SENTINEL && sp != null) return List.of(sp);

        // Booleans exercise both states.
        if (raw == boolean.class || raw == Boolean.class) return List.of(false, true);

        // Scalar leaves whose field name implies a size/count/index get a small non-zero spread.
        List<Object> named = namedScalarVariants(raw, name);
        if (named != null) return named;

        // Rich leaves that used to collapse to a single empty value now branch to a few representatives.
        if (raw == ItemStack.class) return itemStackVariants();
        if (raw == Component.class) return List.of(Component.empty(), Component.literal("x"), Component.translatable("x"));

        // Non-branching leaf values (primitives, String, UUID, Identifier, Instant, Component, ...).
        Object simple = simpleLeaf(raw);
        if (simple != SENTINEL) return List.of(simple);

        // Branching leaf types.
        if (raw.isEnum()) {
            Object[] cs = raw.getEnumConstants();
            if (cs == null || cs.length == 0) throw new UnsupportedOperationException("empty enum " + raw.getSimpleName());
            List<Object> out = new ArrayList<>();
            out.addAll(Arrays.asList(cs));
            return out;
        }
        if (raw == Optional.class) {
            List<Object> out = new ArrayList<>();
            out.add(Optional.empty());
            Class<?> inner = typeArg(generic, 0);
            if (inner != null) {
                Object iv = firstOrNull(inner, typeArgType(generic, 0), depth + 1, cap, dropped);
                if (iv != null) out.add(Optional.of(iv));
            }
            return out;
        }
        if (raw == OptionalInt.class) return List.of(OptionalInt.empty(), OptionalInt.of(0));
        if (raw == OptionalLong.class) return List.of(OptionalLong.empty(), OptionalLong.of(0L));
        if (Collection.class.isAssignableFrom(raw)) {
            boolean set = java.util.Set.class.isAssignableFrom(raw);
            return collectionShapes(raw, generic, depth, cap, dropped, set);
        }
        if (raw.isArray()) {
            Class<?> comp = raw.getComponentType();
            Object el = firstOrNull(comp, comp, depth + 1, cap, dropped);
            List<Object> out = new ArrayList<>();
            out.add(Array.newInstance(comp, 0));
            if (el != null && !comp.isPrimitive()) {
                Object one = Array.newInstance(comp, 1);
                Array.set(one, 0, el);
                out.add(one);
            }
            return out;
        }

        // Registry-typed references.
        if (raw == Holder.class) {
            Class<?> elem = typeArg(generic, 0);
            Holder<?> h = holderFor(elem);
            if (h != null) return List.of(h);
            throw new UnsupportedOperationException("no registry-bound Holder for " + typeName(generic));
        }
        if (raw == ResourceKey.class) {
            Class<?> elem = typeArg(generic, 0);
            ResourceKey<?> rk = resourceKeyFor(elem);
            if (rk != null) return List.of(rk);
            throw new UnsupportedOperationException("no ResourceKey for " + typeName(generic));
        }
        // Pair<A,B>: erased ctor params, so build A and B from the parameterized type.
        if (raw == Pair.class) {
            Type aT = typeArgType(generic, 0), bT = typeArgType(generic, 1);
            Class<?> aC = rawClass(aT), bC = rawClass(bT);
            if (aC != null && bC != null) {
                List<Object> as = variants(aC, aT, depth + 1, cap, dropped);
                List<Object> bs = variants(bC, bT, depth + 1, cap, dropped);
                List<Object> out = new ArrayList<>();
                for (Object a : as) {
                    for (Object b : bs) {
                        out.add(Pair.of(a, b));
                        if (out.size() >= cap) return out;
                    }
                }
                return out;
            }
            throw new UnsupportedOperationException("cannot resolve Pair type args from " + typeName(generic));
        }

        // Records: cross-product of component variant lists.
        if (raw.isRecord()) {
            RecordComponent[] comps = raw.getRecordComponents();
            Class<?>[] paramTypes = new Class<?>[comps.length];
            List<List<Object>> perComp = new ArrayList<>();
            for (int i = 0; i < comps.length; i++) {
                paramTypes[i] = comps[i].getType();
                perComp.add(variants(comps[i].getType(), comps[i].getGenericType(), comps[i].getName(), depth + 1, cap, dropped));
            }
            Constructor<?> ctor = raw.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return instantiateProduct(ctor, perComp, cap, dropped);
        }

        // Vanilla marker singleton (private ctor + public static final INSTANCE, unit codec).
        Object marker = markerSingleton(raw);
        if (marker != null) return List.of(marker);

        // Non-record: try candidate constructors and static factory methods, widest->narrowest.
        try {
            return constructorShapes(raw, depth, cap, dropped);
        } catch (Exception e) {
            // Last resort: a value straight out of a registry (SoundEvent, Item, ParticleOptions...).
            Object regVal = fromRegistry(raw);
            if (regVal != null) return List.of(regVal);
            throw e;
        }
    }

    /** A public static final field of the type itself (marker packets carry an INSTANCE singleton),
     *  returned only when the class exposes no data constructor so we do not shadow richer shapes. */
    private static Object markerSingleton(Class<?> raw) {
        boolean hasDataCtor = false;
        for (Constructor<?> c : raw.getDeclaredConstructors()) {
            if (c.getParameterCount() > 0 && !isBufferReader(c)) {
                hasDataCtor = true;
                break;
            }
        }
        if (hasDataCtor) return null;
        for (Field f : raw.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (!raw.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(null);
                if (v != null) return v;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static final int FACTORY_MAX_DEPTH = 1;

    /** Try every declared constructor (any access) that is not the wire reader, widest first. Only if
     *  none work — and only near the top of the graph — fall back to static factory methods returning
     *  this type. Gating factories keeps synthesis from fanning out combinatorially. */
    private List<Object> constructorShapes(Class<?> raw, int depth, int cap, int[] dropped) throws Exception {
        List<Executable> ctors = new ArrayList<>();
        for (Constructor<?> c : raw.getDeclaredConstructors()) {
            if (isBufferReader(c)) continue;
            ctors.add(c);
        }
        ctors.sort((a, b) -> Integer.compare(b.getParameterCount(), a.getParameterCount()));
        Exception[] last = {null};
        List<Object> built = tryCandidates(ctors, depth, cap, dropped, last);
        if (built != null) return built;

        if (depth <= FACTORY_MAX_DEPTH) {
            List<Executable> factories = new ArrayList<>();
            for (Method m : raw.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (m.isSynthetic() || m.getParameterCount() > 5) continue;
                if (!raw.isAssignableFrom(m.getReturnType())) continue;
                boolean selfRef = false;
                for (Class<?> p : m.getParameterTypes()) {
                    if (p.isAssignableFrom(raw) || raw.isAssignableFrom(p) || ByteBuf.class.isAssignableFrom(p)) {
                        selfRef = true;
                        break;
                    }
                }
                if (selfRef) continue;
                factories.add(m);
            }
            factories.sort((a, b) -> Integer.compare(b.getParameterCount(), a.getParameterCount()));
            built = tryCandidates(factories, depth, cap, dropped, last);
            if (built != null) return built;
        }
        if (last[0] != null) throw last[0];
        throw new UnsupportedOperationException("no data constructor for " + raw.getSimpleName());
    }

    /** First candidate whose parameters all synthesize and that produces at least one instance. */
    private List<Object> tryCandidates(List<Executable> candidates, int depth, int cap, int[] dropped, Exception[] last) {
        for (Executable exec : candidates) {
            try {
                Class<?>[] pts = exec.getParameterTypes();
                Type[] gts = exec.getGenericParameterTypes();
                java.lang.reflect.Parameter[] params = exec.getParameters();
                List<List<Object>> perParam = new ArrayList<>();
                for (int i = 0; i < pts.length; i++) {
                    String pName = params[i].isNamePresent() ? params[i].getName() : null;
                    perParam.add(variants(pts[i], gts[i], pName, depth + 1, cap, dropped));
                }
                exec.setAccessible(true);
                List<Object> built = instantiateProduct(exec, perParam, cap, dropped);
                if (!built.isEmpty()) return built;
            } catch (Exception e) {
                last[0] = e;
            }
        }
        return null;
    }

    /** [empty], plus [oneElement] (and [oneElement,oneElement]) when an element can be synthesized,
     *  each materialized into the concrete collection type ({@code raw}) so subclass fields fit. */
    private List<Object> collectionShapes(Class<?> raw, Type generic, int depth, int cap, int[] dropped, boolean set) throws Exception {
        List<Object> out = new ArrayList<>();
        Class<?> inner = typeArg(generic, 0);
        Object el = inner == null ? null : firstOrNull(inner, typeArgType(generic, 0), depth + 1, cap, dropped);
        Class<?> elemClass = el != null ? el.getClass() : inner;

        out.add(newCollection(raw, set, elemClass));
        if (el != null) {
            out.add(fillCollection(newCollection(raw, set, elemClass), el));
            if (!set) out.add(fillCollection(newCollection(raw, set, elemClass), el, el));
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Collection<Object> newCollection(Class<?> raw, boolean set, Class<?> elemClass) throws Exception {
        if (EnumSet.class.isAssignableFrom(raw) && elemClass != null && elemClass.isEnum()) {
            return (Collection) EnumSet.noneOf((Class) elemClass);
        }
        if (!raw.isInterface() && !Modifier.isAbstract(raw.getModifiers())) {
            try {
                Constructor<?> c = raw.getDeclaredConstructor();
                c.setAccessible(true);
                Object inst = c.newInstance();
                if (inst instanceof Collection<?>) return (Collection<Object>) inst;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return set ? new LinkedHashSet<>() : new ArrayList<>();
    }

    private static Collection<Object> fillCollection(Collection<Object> c, Object... els) {
        for (Object e : els) c.add(e);
        return c;
    }

    /** Build instances from the cartesian product of per-param variant lists, capped. */
    private static List<Object> instantiateProduct(Executable exec, List<List<Object>> perParam, int cap, int[] dropped) throws Exception {
        int n = perParam.size();
        long total = 1;
        for (List<Object> l : perParam) {
            if (l.isEmpty()) throw new UnsupportedOperationException("no value for a parameter of " + exec.getDeclaringClass().getSimpleName());
            total *= l.size();
        }
        int take = (int) Math.min(total, cap);
        if (total > cap) dropped[0] += (int) Math.min(Integer.MAX_VALUE, total - cap);

        List<Object> out = new ArrayList<>();
        int[] idx = new int[n];
        Exception last = null;
        for (int produced = 0; produced < take; produced++) {
            Object[] args = new Object[n];
            for (int i = 0; i < n; i++) args[i] = perParam.get(i).get(idx[i]);
            try {
                Object built = exec instanceof Constructor<?> c ? c.newInstance(args) : ((Method) exec).invoke(null, args);
                if (built != null) out.add(built);
            } catch (Throwable t) {
                last = new RuntimeException(rootMessage(t));
            }
            // advance mixed-radix counter
            for (int i = n - 1; i >= 0; i--) {
                if (++idx[i] < perParam.get(i).size()) break;
                idx[i] = 0;
            }
        }
        if (out.isEmpty() && last != null) throw last;
        return out;
    }

    private final Map<Class<?>, Object> firstCache = new HashMap<>();
    private static final Object NO_VALUE = new Object();

    /** One representative value for a type; memoized to keep nested synthesis from re-exploring the
     *  same type graphs. Generic-dependent kinds (containers, Optional, Pair, Holder...) are not cached. */
    private Object firstOrNull(Class<?> raw, Type generic, int depth, int cap, int[] dropped) {
        boolean cacheable = raw != null && !raw.isArray() && !raw.isEnum()
                && raw != Optional.class && raw != OptionalInt.class && raw != OptionalLong.class
                && raw != Pair.class && raw != Holder.class && raw != ResourceKey.class
                && !Collection.class.isAssignableFrom(raw) && !Map.class.isAssignableFrom(raw);
        if (cacheable) {
            Object cached = firstCache.get(raw);
            if (cached != null) return cached == NO_VALUE ? null : cached;
        }
        Object result;
        try {
            List<Object> v = variants(raw, generic, depth, cap, dropped);
            result = v.isEmpty() ? null : v.get(0);
        } catch (Throwable t) {
            result = null;
        }
        if (cacheable) firstCache.put(raw, result == null ? NO_VALUE : result);
        return result;
    }

    /** A single value for a leaf type when depth is exhausted; throws for unsupported types. */
    private Object leaf(Class<?> raw, Type generic, int depth) {
        Object simple = simpleLeaf(raw);
        if (simple != SENTINEL) return simple;
        if (raw.isEnum()) {
            Object[] cs = raw.getEnumConstants();
            if (cs != null && cs.length > 0) return cs[0];
        }
        if (raw == Optional.class) return Optional.empty();
        if (raw == OptionalInt.class) return OptionalInt.empty();
        if (raw == OptionalLong.class) return OptionalLong.empty();
        if (List.class.isAssignableFrom(raw) || Collection.class == raw) return List.of();
        if (java.util.Set.class.isAssignableFrom(raw)) return java.util.Set.of();
        if (Map.class.isAssignableFrom(raw)) return Map.of();
        if (raw.isArray()) return Array.newInstance(raw.getComponentType(), 0);
        if (raw == Holder.class) {
            Holder<?> h = holderFor(null);
            if (h != null) return h;
        }
        Object regVal = fromRegistry(raw);
        if (regVal != null) return regVal;
        throw new UnsupportedOperationException("depth limit at " + raw.getSimpleName());
    }

    private static final Object SENTINEL = new Object();

    /** Non-branching leaf value, or SENTINEL if this type needs constructor recursion. */
    private Object simpleLeaf(Class<?> t) {
        if (t == int.class || t == Integer.class) return 0;
        if (t == long.class || t == Long.class) return 0L;
        if (t == short.class || t == Short.class) return (short) 0;
        if (t == byte.class || t == Byte.class) return (byte) 0;
        if (t == boolean.class || t == Boolean.class) return false;
        if (t == float.class || t == Float.class) return 0f;
        if (t == double.class || t == Double.class) return 0d;
        if (t == char.class || t == Character.class) return 'a';
        if (t == String.class) return "x";
        if (t == UUID.class) return new UUID(0L, 0L);
        if (Map.class.isAssignableFrom(t)) return Map.of();
        if (t == Component.class) return Component.empty();
        if (t == ItemStack.class) return ItemStack.EMPTY;
        if (t == BlockPos.class) return new BlockPos(0, 0, 0);
        if (t == Identifier.class) return Identifier.withDefaultNamespace("x");
        if (t == Instant.class) return Instant.ofEpochMilli(0L);
        return SENTINEL;
    }

    /** Integer-typed fields whose name implies a size/count/index/slot get a small non-zero spread
     *  ({0,1}) so the empty-vs-non-empty and multi-byte varint forms are exercised; returns null for
     *  every other type/name so the caller falls back to a single representative value. */
    private static List<Object> namedScalarVariants(Class<?> raw, String name) {
        if (name == null) return null;
        boolean intLike = raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class
                || raw == short.class || raw == Short.class;
        if (!intLike) return null;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        boolean sizey = n.contains("count") || n.contains("size") || n.contains("length") || n.contains("len")
                || n.contains("amount") || n.contains("num") || n.contains("index") || n.contains("slot");
        if (!sizey) return null;
        if (raw == long.class || raw == Long.class) return List.of(0L, 1L);
        if (raw == short.class || raw == Short.class) return List.of((short) 0, (short) 1);
        return List.of(0, 1);
    }

    /** ItemStack leaves: the empty stack plus real non-empty stacks (single and full) so the encoded
     *  item id / count / data-component forms are exercised, not just the empty marker. */
    private static List<Object> itemStackVariants() {
        List<Object> out = new ArrayList<>();
        out.add(ItemStack.EMPTY);
        try {
            out.add(new ItemStack(net.minecraft.world.item.Items.STONE));
            out.add(new ItemStack(net.minecraft.world.item.Items.STONE, 64));
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** A constructor that takes a single ByteBuf/FriendlyByteBuf is the wire deserializer — skip it. */
    private static boolean isBufferReader(Constructor<?> c) {
        if (c.getParameterCount() != 1) return false;
        return ByteBuf.class.isAssignableFrom(c.getParameterTypes()[0]);
    }

    // ---- world/entity/opaque value providers ---------------------------------------------------

    private java.security.PublicKey publicKeyCache;
    private javax.crypto.SecretKey secretKeyCache;

    /** Values sourced from the running server or purpose-built for opaque wire types; SENTINEL if none. */
    private Object special(Class<?> raw, Type generic) {
        if (raw == Object.class) return SENTINEL;

        // Live world/entity graph from the running dedicated server.
        if (overworld != null && raw.isInstance(overworld)) return overworld;
        if (levelData != null && raw.isInstance(levelData)) return levelData;
        if (sampleEntity != null && Entity.class.isAssignableFrom(raw) && raw.isInstance(sampleEntity)) return sampleEntity;

        // Purpose-built opaque leaves.
        if (raw == MessageSignature.class) return new MessageSignature(new byte[MessageSignature.BYTES]);
        // MessageSignature.Packed couples an int id with a @Nullable full signature: the canonical wire
        // forms are (id >= 0, no signature) OR (id == -1, full signature). The generic record cross-product
        // would pick the illegal (id == 0, full signature) combo, which decodes as a bare reference and so
        // is not byte-stable. Emit the canonical reference form instead.
        if (raw == MessageSignature.Packed.class) return new MessageSignature.Packed(0);
        // MapItemSavedData.MapPatch's reader treats width == 0 as "no patch" (Optional.empty), so a present
        // patch with the default width 0 decodes back to absent. Emit a minimal valid patch (width/height 1).
        if (raw == net.minecraft.world.level.saveddata.maps.MapItemSavedData.MapPatch.class) {
            return new net.minecraft.world.level.saveddata.maps.MapItemSavedData.MapPatch(0, 0, 1, 1, new byte[1]);
        }
        if (raw == GameProfile.class) return new GameProfile(new UUID(0L, 0L), "x");
        if (raw == CompoundTag.class) return new CompoundTag();
        if (net.minecraft.network.chat.numbers.NumberFormat.class.isAssignableFrom(raw)) return BlankFormat.INSTANCE;
        if (net.minecraft.network.protocol.login.custom.CustomQueryPayload.class.isAssignableFrom(raw)) {
            return new net.minecraft.network.protocol.login.custom.DiscardedQueryPayload(Identifier.withDefaultNamespace("x"));
        }
        if (net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload.class.isAssignableFrom(raw)) {
            return new net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload();
        }
        if (net.minecraft.network.protocol.common.custom.CustomPacketPayload.class.isAssignableFrom(raw)) return new BrandPayload("vanilla");
        if (raw == net.minecraft.world.level.block.state.BlockState.class) {
            return net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        }
        if (raw == net.minecraft.world.level.chunk.LevelChunkSection.class && overworld != null) {
            try {
                return overworld.getChunk(0, 0).getSection(0);
            } catch (Throwable ignored) {
            }
        }
        if (it.unimi.dsi.fastutil.shorts.ShortSet.class.isAssignableFrom(raw)) {
            return new it.unimi.dsi.fastutil.shorts.ShortOpenHashSet();
        }
        if (net.minecraft.world.item.crafting.display.SlotDisplay.class.isAssignableFrom(raw)) {
            return net.minecraft.world.item.crafting.display.SlotDisplay.Empty.INSTANCE;
        }
        if (net.minecraft.world.item.crafting.display.RecipeDisplay.class.isAssignableFrom(raw)) {
            net.minecraft.world.item.crafting.display.SlotDisplay e = net.minecraft.world.item.crafting.display.SlotDisplay.Empty.INSTANCE;
            return new net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay(e, e, e);
        }
        if (raw == net.minecraft.util.debug.DebugSubscription.Update.class) {
            net.minecraft.util.debug.DebugSubscription<?> sub = anyDebugSubscription();
            if (sub != null) return sub.emptyUpdate();
        }
        if (raw == javax.crypto.SecretKey.class) return secretKey();
        if (raw == java.security.PublicKey.class) return publicKey();

        // Concrete collection/map types whose no-arg java containers would not fit the field.
        Object m = concreteMap(raw);
        if (m != SENTINEL) return m;

        return SENTINEL;
    }

    private net.minecraft.util.debug.DebugSubscription<?> debugSub;
    private boolean debugSubResolved;

    /** A registered debug subscription that carries a value codec (so its Update can be encoded). */
    private net.minecraft.util.debug.DebugSubscription<?> anyDebugSubscription() {
        if (!debugSubResolved) {
            debugSubResolved = true;
            try {
                Registry<net.minecraft.util.debug.DebugSubscription<?>> reg =
                        registries.lookupOrThrow(net.minecraft.core.registries.Registries.DEBUG_SUBSCRIPTION);
                for (net.minecraft.util.debug.DebugSubscription<?> s : reg) {
                    if (s.valueStreamCodec() != null) {
                        debugSub = s;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return debugSub;
    }

    private Object concreteMap(Class<?> raw) {
        if (!Map.class.isAssignableFrom(raw) && !Multimap.class.isAssignableFrom(raw)) return SENTINEL;
        try {
            if (Object2IntMap.class.isAssignableFrom(raw)) {
                return raw.isInterface() ? new Object2IntOpenHashMap<>() : raw.getDeclaredConstructor().newInstance();
            }
            if (Int2ObjectMap.class.isAssignableFrom(raw)) {
                return raw.isInterface() ? new Int2ObjectOpenHashMap<>() : raw.getDeclaredConstructor().newInstance();
            }
            if (Multimap.class.isAssignableFrom(raw)) return ImmutableMultimap.of();
            if (!raw.isInterface() && !Modifier.isAbstract(raw.getModifiers())) {
                Constructor<?> c = raw.getDeclaredConstructor();
                c.setAccessible(true);
                return c.newInstance();
            }
        } catch (Throwable ignored) {
        }
        return SENTINEL;
    }

    private javax.crypto.SecretKey secretKey() {
        if (secretKeyCache == null) {
            try {
                secretKeyCache = KeyGenerator.getInstance("AES").generateKey();
            } catch (Throwable t) {
                return null;
            }
        }
        return secretKeyCache;
    }

    private java.security.PublicKey publicKey() {
        if (publicKeyCache == null) {
            try {
                KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
                g.initialize(1024);
                publicKeyCache = g.generateKeyPair().getPublic();
            } catch (Throwable t) {
                return null;
            }
        }
        return publicKeyCache;
    }

    // ---- registry-backed synthesis -------------------------------------------------------------

    private List<Registry<?>> registryList() {
        if (registryList == null) {
            registryList = registries.registries()
                    .map(RegistryAccess.RegistryEntry::value)
                    .collect(Collectors.toList());
        }
        return registryList;
    }

    /** A registry-bound Holder.Reference whose value is an instance of {@code elem} (or any if null). */
    private Holder<?> holderFor(Class<?> elem) {
        for (Registry<?> reg : registryList()) {
            Optional<? extends Holder.Reference<?>> any = reg.getAny();
            if (any.isEmpty()) continue;
            Holder.Reference<?> ref = any.get();
            Object v;
            try {
                v = ref.value();
            } catch (Throwable t) {
                continue;
            }
            if (elem == null || elem.isInstance(v)) return ref;
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResourceKey<?> resourceKeyFor(Class<?> elem) {
        if (elem == net.minecraft.world.level.Level.class) return net.minecraft.world.level.Level.OVERWORLD;
        for (Registry<?> reg : registryList()) {
            Optional<? extends Holder.Reference<?>> any = reg.getAny();
            if (any.isEmpty()) continue;
            Object v;
            try {
                v = any.get().value();
            } catch (Throwable t) {
                continue;
            }
            if (elem == null || elem.isInstance(v)) {
                Optional<? extends ResourceKey<?>> rk = ((Registry) reg).getResourceKey(v);
                if (rk.isPresent()) return rk.get();
            }
        }
        // Synthetic key: the wire form of a ResourceKey is only its location Identifier.
        ResourceKey rootKey = ResourceKey.createRegistryKey(Identifier.withDefaultNamespace("root"));
        return ResourceKey.create(rootKey, Identifier.withDefaultNamespace("x"));
    }

    /** A bare value out of whichever registry holds instances of {@code wanted}. */
    private Object fromRegistry(Class<?> wanted) {
        if (wanted == null || wanted == Object.class) return null;
        for (Registry<?> reg : registryList()) {
            Optional<? extends Holder.Reference<?>> any = reg.getAny();
            if (any.isEmpty()) continue;
            Object v;
            try {
                v = any.get().value();
            } catch (Throwable t) {
                continue;
            }
            if (wanted.isInstance(v)) return v;
        }
        return null;
    }

    // ---- generic-type helpers ------------------------------------------------------------------

    private static Class<?> typeArg(Type generic, int i) {
        return rawClass(typeArgType(generic, i));
    }

    private static Type typeArgType(Type generic, int i) {
        if (generic instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (i < args.length) return args[i];
        }
        return null;
    }

    private static Class<?> rawClass(Type t) {
        if (t instanceof Class<?> c) return c;
        if (t instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) return c;
        return null;
    }

    private static String typeName(Type t) {
        return t == null ? "?" : t.getTypeName();
    }

    // ---- small helpers -------------------------------------------------------------------------

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
