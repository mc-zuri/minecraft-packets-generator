package dev.minecraftdatagenerator.generators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.minecraftdatagenerator.util.DGU;
import net.minecraft.SharedConstants;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.status.StatusProtocols;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * Enumerates every packet the running vanilla server knows about — for every connection state and
 * direction — straight from the vanilla protocol templates ({@code *Protocols.*_TEMPLATE}). This is
 * the authoritative packet id ↔ name table, generated from Java (vanilla), independent of any
 * hand-maintained spec.
 * <p>
 * It then reads minecraft-data's {@code protocol.json} for the same version (if it can be located)
 * and diffs the two, aligned by <b>network id</b> — because minecraft-data uses its own packet
 * names (e.g. {@code block_change}) that differ from vanilla's ({@code block_update}), so a
 * name-based comparison would be meaningless. An id present on only one side is a real coverage
 * discrepancy in the spec.
 * <p>
 * Output: {@code packets.json} = {@code { byState, protocolDiff }}.
 */
public class PacketsDataGenerator implements IDataGenerator {
    private static final Logger LOGGER = Logger.getLogger(PacketsDataGenerator.class.getSimpleName());

    // Direction naming follows minecraft-data: toClient = clientbound, toServer = serverbound.
    private static final String TO_CLIENT = "toClient";
    private static final String TO_SERVER = "toServer";

    @Override
    public String getDataName() {
        return "packets";
    }

    /** One (state, direction) enumeration slot backed by a vanilla details provider. */
    private record Slot(String state, String direction, ProtocolInfo.DetailsProvider provider) {
    }

    private static List<Slot> slots() {
        List<Slot> slots = new ArrayList<>();
        slots.add(new Slot("handshaking", TO_SERVER, HandshakeProtocols.SERVERBOUND_TEMPLATE));
        slots.add(new Slot("status", TO_SERVER, StatusProtocols.SERVERBOUND_TEMPLATE));
        slots.add(new Slot("status", TO_CLIENT, StatusProtocols.CLIENTBOUND_TEMPLATE));
        slots.add(new Slot("login", TO_SERVER, LoginProtocols.SERVERBOUND_TEMPLATE));
        slots.add(new Slot("login", TO_CLIENT, LoginProtocols.CLIENTBOUND_TEMPLATE));
        slots.add(new Slot("configuration", TO_SERVER, ConfigurationProtocols.SERVERBOUND_TEMPLATE));
        slots.add(new Slot("configuration", TO_CLIENT, ConfigurationProtocols.CLIENTBOUND_TEMPLATE));
        slots.add(new Slot("play", TO_SERVER, GameProtocols.SERVERBOUND_TEMPLATE));
        slots.add(new Slot("play", TO_CLIENT, GameProtocols.CLIENTBOUND_TEMPLATE));
        return slots;
    }

    /** id -> vanilla packet name (Identifier path), preserving registration order. */
    private static Map<Integer, String> enumerate(ProtocolInfo.DetailsProvider provider) {
        Map<Integer, String> byId = new LinkedHashMap<>();
        provider.details().listPackets((PacketType<?> type, int networkId) -> byId.put(networkId, type.id().getPath()));
        return byId;
    }

    @Override
    public JsonElement generateDataJson() {
        JsonObject root = new JsonObject();

        // 1. Vanilla enumeration, grouped by state then direction.
        JsonObject byState = new JsonObject();
        Map<String, Map<String, Map<Integer, String>>> vanilla = new LinkedHashMap<>();
        for (Slot slot : slots()) {
            Map<Integer, String> byId = enumerate(slot.provider());
            vanilla.computeIfAbsent(slot.state(), k -> new LinkedHashMap<>()).put(slot.direction(), byId);

            JsonObject dirObj = byState.has(slot.state()) ? byState.getAsJsonObject(slot.state()) : new JsonObject();
            JsonObject packetsObj = new JsonObject();
            byId.forEach((id, name) -> packetsObj.addProperty(Integer.toString(id), name));
            dirObj.add(slot.direction(), packetsObj);
            byState.add(slot.state(), dirObj);
        }
        root.add("byState", byState);

        // 2. Diff against minecraft-data protocol.json, aligned by network id.
        JsonObject diff = buildProtocolDiff(vanilla);
        root.add("protocolDiff", diff);
        return root;
    }

    // ---- protocol.json diff --------------------------------------------------------------------

    private JsonObject buildProtocolDiff(Map<String, Map<String, Map<Integer, String>>> vanilla) {
        JsonObject diff = new JsonObject();
        String version = SharedConstants.getCurrentVersion().name();

        Path protocolJson = locateProtocolJson(version);
        if (protocolJson == null) {
            diff.addProperty("status", "skipped");
            diff.addProperty("reason", "Could not locate minecraft-data protocol.json for " + version
                    + " (set PACKETGEN_PROTOCOL_JSON to its path)");
            return diff;
        }
        diff.addProperty("status", "ok");
        diff.addProperty("protocolJson", protocolJson.toString());

        JsonObject mcData;
        try {
            mcData = JsonParser.parseString(Files.readString(protocolJson)).getAsJsonObject();
        } catch (Exception e) {
            diff.addProperty("status", "error");
            diff.addProperty("reason", "Failed to read/parse protocol.json: " + e);
            return diff;
        }

        int totalIdOnlyVanilla = 0;
        int totalIdOnlyMcData = 0;
        JsonObject byState = new JsonObject();

        for (Map.Entry<String, Map<String, Map<Integer, String>>> stateEntry : vanilla.entrySet()) {
            String state = stateEntry.getKey();
            JsonObject stateObj = new JsonObject();
            for (Map.Entry<String, Map<Integer, String>> dirEntry : stateEntry.getValue().entrySet()) {
                String direction = dirEntry.getKey();
                Map<Integer, String> vanillaById = dirEntry.getValue();
                Map<Integer, String> mcById = parseMcDataMapper(mcData, state, direction);

                JsonArray table = new JsonArray();
                TreeSet<Integer> ids = new TreeSet<>();
                ids.addAll(vanillaById.keySet());
                ids.addAll(mcById.keySet());
                int idOnlyVanilla = 0;
                int idOnlyMcData = 0;
                for (Integer id : ids) {
                    String vName = vanillaById.get(id);
                    String mName = mcById.get(id);
                    JsonObject row = new JsonObject();
                    row.addProperty("id", id);
                    row.add("vanilla", vName == null ? JsonNull.INSTANCE : new JsonPrimitive(vName));
                    row.add("minecraftData", mName == null ? JsonNull.INSTANCE : new JsonPrimitive(mName));
                    if (vName == null) {
                        row.addProperty("issue", "idOnlyInMinecraftData");
                        idOnlyMcData++;
                    } else if (mName == null) {
                        row.addProperty("issue", "idOnlyInVanilla");
                        idOnlyVanilla++;
                    } else if (!vName.equals(mName)) {
                        // Expected: the two projects use different naming conventions. Informational.
                        row.addProperty("note", "nameDiffers");
                    }
                    table.add(row);
                }
                JsonObject dirObj = new JsonObject();
                dirObj.addProperty("vanillaCount", vanillaById.size());
                dirObj.addProperty("minecraftDataCount", mcById.size());
                dirObj.addProperty("idOnlyInVanilla", idOnlyVanilla);
                dirObj.addProperty("idOnlyInMinecraftData", idOnlyMcData);
                dirObj.add("table", table);
                stateObj.add(direction, dirObj);
                totalIdOnlyVanilla += idOnlyVanilla;
                totalIdOnlyMcData += idOnlyMcData;
            }
            byState.add(state, stateObj);
        }

        JsonObject summary = new JsonObject();
        summary.addProperty("idOnlyInVanilla", totalIdOnlyVanilla);
        summary.addProperty("idOnlyInMinecraftData", totalIdOnlyMcData);
        diff.add("summary", summary);
        diff.add("byState", byState);
        return diff;
    }

    /** Parse minecraft-data's {@code protocol[state][direction].types.packet} name mapper into id -> name. */
    private static Map<Integer, String> parseMcDataMapper(JsonObject mcData, String state, String direction) {
        Map<Integer, String> byId = new LinkedHashMap<>();
        try {
            JsonObject dir = mcData.getAsJsonObject(state).getAsJsonObject(direction);
            JsonArray packet = dir.getAsJsonObject("types").getAsJsonArray("packet"); // ["container", [fields...]]
            JsonArray fields = packet.get(1).getAsJsonArray();
            for (JsonElement fe : fields) {
                JsonObject field = fe.getAsJsonObject();
                if (!"name".equals(field.get("name").getAsString())) continue;
                // type = ["mapper", { "type": "varint", "mappings": { "0x00": "name", ... } }]
                JsonArray mapper = field.getAsJsonArray("type");
                JsonObject mappings = mapper.get(1).getAsJsonObject().getAsJsonObject("mappings");
                for (Map.Entry<String, JsonElement> m : mappings.entrySet()) {
                    int id = Integer.decode(m.getKey()); // handles "0x08"
                    byId.put(id, m.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            // state/direction absent in this version's protocol.json — leave map empty.
        }
        return byId;
    }

    /** Optional compatibility diff; the standalone runner resolves schemas separately. */
    private static Path locateProtocolJson(String version) {
        String env = System.getenv("PACKETGEN_PROTOCOL_JSON");
        if (env != null && !env.isBlank()) {
            Path path = Path.of(env).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) return path;
            LOGGER.warning("PACKETGEN_PROTOCOL_JSON is not a file: " + path);
        }
        return null;
    }
}
