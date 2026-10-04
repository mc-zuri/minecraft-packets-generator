package dev.minecraftdatagenerator.packetgen;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A finite domain of values built from decisions. Every value pool and every structural
 * alternative (optional presence, collection length, constructor, sealed/dispatch subtype)
 * asks the {@link Chooser}, so a value is identified by its decision vector rather than by an
 * ordinal. Access creates a fresh value, not a shared mutable fixture.
 */
public interface ShapePlan {
    enum Kind { LEAF, UNION, PRODUCT }

    BigInteger size();
    /** Short label of this domain, used for node names. */
    String name();
    /** Full recursive description; part of the generator fingerprint. */
    String description();
    /** Builds one value at {@code node}, recording each decision through {@code chooser}. */
    Object build(Chooser chooser, int node) throws Exception;

    /** Decision source for one build. Node 0 is the root; children are interned by (parent, slot). */
    interface Chooser {
        int child(int parent, int slot);
        /** A structural node whose children are named {@code dims}; it makes no decision. */
        default void product(int node, String label, List<String> dims) {}
        /** Returns the selected alternative in {@code [0, alternatives.size())}. */
        int choose(int node, Kind kind, String label, List<String> alternatives);
    }

    @FunctionalInterface
    interface Factory { Object create(Object[] arguments) throws Exception; }

    /** Always selects the first alternative; used for fixtures that need one representative value. */
    Chooser FIRST = new Chooser() {
        public int child(int parent, int slot) { return 0; }
        public int choose(int node, Kind kind, String label, List<String> alternatives) { return 0; }
    };

    static Object first(ShapePlan plan) throws Exception { return plan.build(FIRST, 0); }

    static ShapePlan leaves(String name, List<? extends Supplier<?>> values) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) labels.add("#" + i);
        return leaves(name, values, labels);
    }

    static ShapePlan leaves(String name, List<? extends Supplier<?>> values, List<String> labels) {
        List<? extends Supplier<?>> copy = List.copyOf(values);
        List<String> names = List.copyOf(labels);
        if (names.size() != copy.size()) throw new IllegalArgumentException("one label per value");
        return new ShapePlan() {
            public BigInteger size() { return BigInteger.valueOf(copy.size()); }
            public String name() { return name; }
            public String description() { return name + "[" + copy.size() + "]"; }
            public Object build(Chooser chooser, int node) {
                if (copy.isEmpty()) throw new IllegalStateException("empty domain: " + name);
                return copy.get(chooser.choose(node, Kind.LEAF, name, names)).get();
            }
        };
    }

    static ShapePlan values(String name, Object... values) {
        List<Supplier<?>> suppliers = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Object value : values) { suppliers.add(() -> value); labels.add(label(value)); }
        return leaves(name, suppliers, labels);
    }

    static ShapePlan product(String name, List<ShapePlan> dimensions, Factory factory) {
        return product(name, dimensions, dimensions.stream().map(ShapePlan::name).toList(), factory);
    }

    static ShapePlan product(String name, List<ShapePlan> dimensions, List<String> fields, Factory factory) {
        List<ShapePlan> dims = List.copyOf(dimensions);
        List<String> names = List.copyOf(fields);
        if (names.size() != dims.size()) throw new IllegalArgumentException("one field name per dimension");
        BigInteger total = BigInteger.ONE;
        for (ShapePlan dimension : dims) total = total.multiply(dimension.size());
        BigInteger count = total;
        return new ShapePlan() {
            public BigInteger size() { return count; }
            public String name() { return name; }
            public String description() { return "product:" + name + dims.stream().map(ShapePlan::description).toList(); }
            public Object build(Chooser chooser, int node) throws Exception {
                chooser.product(node, name, names);
                Object[] args = new Object[dims.size()];
                for (int i = 0; i < dims.size(); i++) args[i] = dims.get(i).build(chooser, chooser.child(node, i));
                return factory.create(args);
            }
        };
    }

    /**
     * Alternatives with an empty domain are pruned here so they cannot erase a whole enclosing
     * product. Callers that know why a branch is empty record that as a gap.
     */
    static ShapePlan union(String name, List<ShapePlan> alternatives) {
        List<ShapePlan> parts = alternatives.stream().filter(p -> p.size().signum() > 0).toList();
        List<String> names = parts.stream().map(ShapePlan::name).toList();
        BigInteger count = parts.stream().map(ShapePlan::size).reduce(BigInteger.ZERO, BigInteger::add);
        return new ShapePlan() {
            public BigInteger size() { return count; }
            public String name() { return name; }
            public String description() { return "union:" + name + parts.stream().map(ShapePlan::description).toList(); }
            public Object build(Chooser chooser, int node) throws Exception {
                if (parts.isEmpty()) throw new IllegalStateException("empty domain: " + name);
                int branch = chooser.choose(node, Kind.UNION, name, names);
                return parts.get(branch).build(chooser, chooser.child(node, branch));
            }
        };
    }

    /** A bounded, readable label for a pool value. */
    static String label(Object value) {
        String text;
        if (value instanceof String s) text = '"' + s + '"';
        else if (value instanceof Enum<?> e) text = e.name();
        else if (value instanceof byte[] b) text = "bytes[" + b.length + "]";
        else if (value instanceof short[] s) text = "shorts[" + s.length + "]";
        else text = String.valueOf(value);
        return text.length() > 48 ? text.substring(0, 45) + "..." : text;
    }
}
