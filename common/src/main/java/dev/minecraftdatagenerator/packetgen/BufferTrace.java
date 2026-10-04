package dev.minecraftdatagenerator.packetgen;

import com.google.gson.*;
import java.util.*;

/** Recorder shared by generated buffer adapters. Values and spans come from actual vanilla writes. */
public final class BufferTrace {
    private record Op(int offset, int size, String kind, Object value) {}
    public record Token(Object buffer, String kind, Object value, int offset, boolean semantic, int depth, boolean reading) {}
    private static final ThreadLocal<BufferTrace> ACTIVE = new ThreadLocal<>();
    private final List<Op> primitives = new ArrayList<>(), semantics = new ArrayList<>();
    private final TreeMap<Integer, String> reads = new TreeMap<>();
    private int writeDepth, readDepth;
    private boolean patched, view, failed;
    public void begin() { ACTIVE.set(this); }
    public static void end() { ACTIVE.remove(); }
    public static Token before(Object buffer, String kind, Object value, boolean semantic, boolean reading) {
        BufferTrace t = ACTIVE.get(); if (t == null) return null;
        try {
            if (kind.equals("patch")) { t.patched = true; return null; }
            if (kind.equals("view")) { t.view = true; return null; }
            int depth = semantic ? -1 : reading ? t.readDepth++ : t.writeDepth++;
            return new Token(buffer, kind, value, ((Number) Reflect.call(buffer, reading ? "readerIndex" : "writerIndex")).intValue(), semantic, depth, reading);
        } catch (Exception e) { t.failed = true; return null; }
    }
    public static void after(Token token) {
        BufferTrace t = ACTIVE.get(); if (t == null || token == null) return;
        try {
            if (!token.semantic()) { if (token.reading()) t.readDepth--; else t.writeDepth--; }
            if (token.depth() > 0) return;
            int end = ((Number) Reflect.call(token.buffer(), token.reading() ? "readerIndex" : "writerIndex")).intValue();
            int size = end - token.offset();
            if (size < 0) { t.patched = true; return; }
            if (token.reading()) { t.reads.put(token.offset(), token.kind()); return; }
            Object value = token.value();
            if (token.kind().equals("bytes")) {
                byte[] bytes = new byte[size]; Reflect.call(token.buffer(), "getBytes", token.offset(), bytes); value = HexFormat.of().formatHex(bytes);
            } else if (token.kind().equals("blockpos")) {
                JsonArray xyz = new JsonArray();
                for (String name : List.of("getX", "getY", "getZ")) xyz.add((Number) Reflect.call(value, name));
                value = xyz;
            } else if (token.kind().equals("f32")) value = Float.floatToRawIntBits(((Number) value).floatValue());
            else if (token.kind().equals("f64")) value = Long.toString(Double.doubleToRawLongBits(((Number) value).doubleValue()));
            else if (Set.of("i64", "li64", "varlong").contains(token.kind())) value = value.toString();
            Op op = new Op(token.offset(), size, token.kind(), value);
            (token.semantic() ? t.semantics : t.primitives).add(op);
        } catch (Exception e) { t.failed = true; }
    }
    private static JsonArray json(Op op) {
        JsonArray a = new JsonArray(); a.add(op.offset()); a.add(op.size()); a.add(op.kind());
        if (op.value() instanceof JsonElement j) a.add(j);
        else if (op.value() instanceof Boolean b) a.add(b);
        else if (op.value() instanceof Number n) a.add(n);
        else a.add(String.valueOf(op.value()));
        return a;
    }
    public JsonArray primitives() { JsonArray out = new JsonArray(); primitives.forEach(p -> out.add(json(p))); return out; }
    public JsonArray reads() { JsonArray out = new JsonArray(); reads.forEach((off, kind) -> { JsonArray a = new JsonArray(); a.add(off); a.add(kind); out.add(a); }); return out; }
    public JsonArray writes() {
        List<Op> ordered = new ArrayList<>(semantics);
        ordered.sort(Comparator.comparingInt(Op::offset).thenComparing(Comparator.comparingInt(Op::size).reversed()));
        List<Op> outer = new ArrayList<>(); int until = -1;
        for (Op op : ordered) if (op.offset() >= until) { outer.add(op); until = op.offset() + op.size(); }
        ordered = new ArrayList<>(outer);
        for (Op p : primitives) if (outer.stream().noneMatch(o -> p.offset() >= o.offset() && p.offset() + p.size() <= o.offset() + o.size())) ordered.add(p);
        ordered.sort(Comparator.comparingInt(Op::offset)); JsonArray out = new JsonArray(); ordered.forEach(p -> out.add(json(p))); return out;
    }
    public JsonObject coverage(int length) {
        int cursor = 0; boolean complete = !failed && !patched && !view;
        for (Op p : primitives) { if (p.offset() != cursor) complete = false; cursor = p.offset() + p.size(); }
        JsonObject out = new JsonObject(); out.addProperty("bodyBytes", length); out.addProperty("coveredBytes", cursor);
        out.addProperty("complete", complete && cursor == length); out.addProperty("patched", patched); out.addProperty("exposedView", view); out.addProperty("recorderFailed", failed); return out;
    }
}
