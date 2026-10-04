package dev.minecraftdatagenerator.packetgen;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Restores send-side caches from values produced by legacy vanilla decoders.
 * No field is copied from the original packet or its encoded input. */
final class LegacyReceiveState {
    private LegacyReceiveState() {}

    static boolean hasPackedChunkRecords(Class<?> type) {
        Field data = field(type, "field_8598"), count = field(type, "field_8599");
        return type.getSimpleName().equals("ChunkDeltaUpdateS2CPacket") && data != null && count != null
            && data.getType() == byte[].class && count.getType() == int.class;
    }

    static void validateSource(Object packet) throws Exception {
        Class<?> type = packet.getClass();
        if (!hasPackedChunkRecords(type)) return;
        Field data = field(type, "field_8598"), count = field(type, "field_8599");
        if (data == null || count == null) return;
        byte[] records = (byte[]) data.get(packet);
        int size = records == null ? 0 : records.length;
        if (count.getInt(packet) < 0 || count.getInt(packet) > 32767 || (long) count.getInt(packet) * 4 != size)
            throw new IllegalArgumentException("invalid source: chunk update count does not match four-byte records");
    }

    @SuppressWarnings("unchecked")
    static String restore(Object decoded) throws Exception {
        Class<?> type = decoded.getClass();
        String name = type.getSimpleName();
        if (name.equals("MobSpawnS2CPacket") || name.equals("PlayerSpawnS2CPacket")) {
            Field trackerField = field(type, "tracker", "dataTracker");
            if (trackerField == null || trackerField.get(decoded) != null) return null;
            Field entriesField = field(type, "entries", "dataTrackerEntries", "trackedValues");
            if (entriesField == null) return null;
            Object tracker = Reflect.construct(trackerField.getType(), (Object) null);
            // Some intermediary mappings leave this name unmapped. The legacy
            // tracker has exactly one instance map, containing entries by id.
            Field mapField = null;
            for (Field candidate : tracker.getClass().getDeclaredFields()) {
                if (!Modifier.isStatic(candidate.getModifiers()) && Map.class.isAssignableFrom(candidate.getType())) {
                    if (mapField != null) throw new IllegalStateException("ambiguous legacy tracker entry map");
                    candidate.setAccessible(true); mapField = candidate;
                }
            }
            if (mapField == null) throw new IllegalStateException("missing legacy tracker entry map");
            Map<Object, Object> entries = (Map<Object, Object>) mapField.get(tracker);
            List<?> received = (List<?>) entriesField.get(decoded);
            if (received != null) for (Object entry : received) {
                Object id = Reflect.maybe(entry, "getId", "getDataId", "method_2707");
                if (!(id instanceof Number)) {
                    Object data = Reflect.maybe(entry, "getData", "method_12758");
                    id = Reflect.maybe(data, "getId", "method_12713");
                }
                if (!(id instanceof Number)) throw new IllegalStateException("unmapped legacy tracker entry id");
                entries.put(((Number) id).intValue(), entry);
            }
            trackerField.set(decoded, tracker);
            return "tracker rebuilt from decoded metadata entries";
        }
        // 1.7 decoders retain the received compressed bytes in a static scratch
        // array while leaving the instance's send-only compressed array null.
        if (name.equals("ChunkDataS2CPacket"))
            return compressedCache(decoded, "field_8637", "field_8641", "field_8640");
        if (name.equals("ChunkMapS2CPacket"))
            return compressedCache(decoded, "field_8646", "field_8650", "field_8648");
        return null;
    }

    private static String compressedCache(Object decoded, String output, String scratch, String length) throws Exception {
        Class<?> type = decoded.getClass();
        Field target = field(type, output), source = field(type, scratch), size = field(type, length);
        if (target == null || source == null || size == null || target.get(decoded) != null) return null;
        int count = size.getInt(decoded);
        byte[] received = (byte[]) source.get(null);
        if (count < 0 || count > received.length) throw new IllegalStateException("invalid decoded compressed length");
        target.set(decoded, Arrays.copyOf(received, count));
        return "compressed cache retained from vanilla decoder scratch buffer";
    }

    private static Field field(Class<?> type, String... names) {
        for (String name : names) try {
            Field field = type.getDeclaredField(name);
            if (field.trySetAccessible()) return field;
        } catch (NoSuchFieldException ignored) {}
        return null;
    }
}
