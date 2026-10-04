package dev.minecraftdatagenerator.packetgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

/** The running game's packet table and codecs, behind a version-independent interface. */
public interface PacketAdapter extends AutoCloseable {
    record Entry(String state, String direction, int id, String name, String context, Class<?> packetClass, Object codec) {
        public String key() { return state + "/" + direction + "/" + id + "/" + context; }
        public String path() { return state + "/" + direction + "/" + name + (context.isEmpty() ? "" : "/" + context); }
        /** Registrations sharing a name share one output file. */
        public String file() { return state + "/" + direction + "/" + name; }
    }
    /**
     * @param seeds       handwritten and adapter regression values, enumerated in full (may be null)
     * @param exploration the reflective domain
     * @param unresolved  alternatives that could not be built at all
     * @param terminal    positions below the depth limit, built as one minimal value
     */
    record Plan(ShapePlan seeds, ShapePlan exploration, List<String> unresolved, List<String> terminal) {}
    /** Bytes vanilla wrote and the ordered write ops; {@code complete} when they partition the body. */
    record Traced(byte[] body, List<TraceOp> writes, boolean complete) {}
    /** The decoded packet and the reads vanilla made; {@code restored} names any legacy receive-state repair. */
    record Decoded(Object value, List<TraceOp> reads, boolean consumed, boolean rewound, String restored) {}
    /** Vanilla deliberately suppresses this packet in the selected protocol context. */
    final class EncoderSkipped extends Exception {
        public EncoderSkipped(Throwable cause) { super(cause); }
    }
    String version();
    String fingerprint() throws Exception;
    List<Entry> inventory() throws Exception;
    default JsonObject metadata() { return new JsonObject(); }
    Plan shapes(Entry packet) throws Exception;
    /** Encodes without tracing. */
    byte[] encode(Entry packet, Object instance) throws Exception;
    /** Encodes into the tracing buffer. */
    Traced encodeTraced(Entry packet, Object instance) throws Exception;
    /** Decodes from the tracing buffer. */
    Decoded decode(Entry packet, byte[] body) throws Exception;
    JsonElement dump(Object value);
    default void close() throws Exception {}
}
