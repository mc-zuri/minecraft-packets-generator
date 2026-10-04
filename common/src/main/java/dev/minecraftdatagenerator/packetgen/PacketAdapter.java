package dev.minecraftdatagenerator.packetgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

public interface PacketAdapter extends AutoCloseable {
    record Entry(String state, String direction, int id, String name, String context, Class<?> packetClass, Object codec) {
        public String key() { return state + "/" + direction + "/" + id + "/" + context; }
        public String path() { return state + "/" + direction + "/" + name + (context.isEmpty() ? "" : "/" + context); }
    }
    record Plan(ShapePlan shapes, List<String> gaps) {}
    record Encoded(byte[] body, JsonObject evidence) {}
    /** Vanilla deliberately suppresses this packet in the selected protocol context. */
    final class EncoderSkipped extends Exception {
        public EncoderSkipped(Throwable cause) { super(cause); }
    }
    String version();
    String fingerprint() throws Exception;
    List<Entry> inventory() throws Exception;
    default JsonObject metadata() { return new JsonObject(); }
    Plan shapes(Entry packet) throws Exception;
    Encoded encode(Entry packet, Object instance) throws Exception;
    JsonElement dump(Object value);
    default void close() throws Exception {}
}
