package dev.minecraftdatagenerator.packetgen;

import com.google.gson.*;
import java.util.*;

/**
 * Decision nodes of one registration, interned by (parent, slot) in first-visit order. Plans are
 * cached per type, so the same plan object can sit at several positions (list elements, map
 * entries); the dynamic position, not the plan, identifies a node.
 */
final class NodeTable {
    static final int ROOT = 0;
    private final Map<Long, Integer> index = new HashMap<>();
    private final List<Integer> parents = new ArrayList<>(), slots = new ArrayList<>();
    private final List<ShapePlan.Kind> kinds = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final List<List<String>> names = new ArrayList<>();
    private final Map<Integer, Map<Integer, Integer>> branchPaths = new HashMap<>();

    NodeTable() { add(-1, -1); }

    private int add(int parent, int slot) {
        parents.add(parent); slots.add(slot); kinds.add(null); labels.add(null); names.add(null);
        return parents.size() - 1;
    }

    int child(int parent, int slot) {
        return index.computeIfAbsent(((long) parent << 32) | (slot & 0xffffffffL), k -> add(parent, slot));
    }

    void describe(int node, ShapePlan.Kind kind, String label, List<String> alternatives) {
        if (kinds.get(node) != null) return;
        kinds.set(node, kind); labels.set(node, label); names.set(node, alternatives);
    }

    int size() { return parents.size(); }
    ShapePlan.Kind kind(int node) { return kinds.get(node); }
    int parent(int node) { return parents.get(node); }
    int slot(int node) { return slots.get(node); }
    /** Number of alternatives of a decision node, or 0 for structure and unvisited nodes. */
    int arity(int node) {
        ShapePlan.Kind kind = kinds.get(node);
        return kind == ShapePlan.Kind.LEAF || kind == ShapePlan.Kind.UNION ? names.get(node).size() : 0;
    }

    /** Union ancestors of {@code node} and the branch that leads to it. */
    Map<Integer, Integer> branchPath(int node) {
        Map<Integer, Integer> cached = branchPaths.get(node);
        if (cached != null) return cached;
        Map<Integer, Integer> path = new HashMap<>();
        for (int child = node, parent = parent(node); parent >= 0; child = parent, parent = parent(parent)) {
            if (kinds.get(parent) == ShapePlan.Kind.UNION) path.put(parent, slot(child));
        }
        Map<Integer, Integer> result = Map.copyOf(path);
        branchPaths.put(node, result);
        return result;
    }

    /** Readable position: field names below products, branch names below unions, list slots as [i]. */
    String path(int node) {
        Deque<String> steps = new ArrayDeque<>();
        for (int child = node, parent = parent(node); parent >= 0; child = parent, parent = parent(parent)) {
            List<String> named = names.get(parent);
            int slot = slot(child);
            String name = named != null && slot < named.size() ? named.get(slot) : "#" + slot;
            steps.push(kinds.get(parent) == ShapePlan.Kind.UNION ? "<" + name + ">" : "." + name);
        }
        return String.join("", steps);
    }

    JsonObject toJson() {
        JsonObject out = new JsonObject();
        Map<List<String>, Integer> poolIndex = new HashMap<>();
        JsonArray pools = new JsonArray(), nodes = new JsonArray();
        for (int n = 0; n < size(); n++) {
            JsonObject node = new JsonObject();
            node.addProperty("n", n); node.addProperty("parent", parent(n)); node.addProperty("slot", slot(n));
            ShapePlan.Kind kind = kinds.get(n);
            node.addProperty("kind", kind == null ? "unvisited" : kind.name().toLowerCase(Locale.ROOT));
            if (labels.get(n) != null) node.addProperty("label", labels.get(n));
            List<String> named = names.get(n);
            if (named != null) {
                Integer pool = poolIndex.get(named);
                if (pool == null) {
                    pool = pools.size(); poolIndex.put(named, pool);
                    JsonArray values = new JsonArray(); named.forEach(values::add); pools.add(values);
                }
                node.addProperty(kind == ShapePlan.Kind.PRODUCT ? "fields" : "alternatives", pool);
            }
            node.addProperty("path", path(n));
            nodes.add(node);
        }
        out.add("nodes", nodes); out.add("pools", pools);
        return out;
    }
}
