package dev.minecraftdatagenerator.packetgen;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** A finite, random-access domain. Access creates a fresh value, not a shared mutable fixture. */
public interface ShapePlan {
    BigInteger size();
    Object create(BigInteger ordinal) throws Exception;
    String description();
    /** Regression seeds are visited before the bounded exploratory domain. */
    default BigInteger requiredPrefix() { return BigInteger.ZERO; }

    @FunctionalInterface
    interface Factory { Object create(Object[] arguments) throws Exception; }

    static ShapePlan leaves(String name, List<? extends Supplier<?>> values) {
        List<? extends Supplier<?>> copy = List.copyOf(values);
        return new ShapePlan() {
            public BigInteger size() { return BigInteger.valueOf(copy.size()); }
            public String description() { return name + "[" + copy.size() + "]"; }
            public Object create(BigInteger ordinal) { return copy.get(ordinal.intValueExact()).get(); }
        };
    }

    static ShapePlan values(String name, Object... values) {
        List<Supplier<?>> suppliers = new ArrayList<>();
        for (Object value : values) suppliers.add(() -> value);
        return leaves(name, suppliers);
    }

    static ShapePlan product(String name, List<ShapePlan> dimensions, Factory factory) {
        List<ShapePlan> dims = List.copyOf(dimensions);
        BigInteger total = BigInteger.ONE;
        for (ShapePlan dimension : dims) total = total.multiply(dimension.size());
        BigInteger count = total;
        return new ShapePlan() {
            public BigInteger size() { return count; }
            public String description() { return "spread-v1:" + name + dims.stream().map(ShapePlan::description).toList(); }
            public Object create(BigInteger ordinal) throws Exception {
                if (ordinal.signum() < 0 || ordinal.compareTo(count) >= 0) throw new IndexOutOfBoundsException(ordinal.toString());
                Object[] args = new Object[dims.size()];
                BigInteger original = ordinal, stride = BigInteger.ONE;
                for (int i = dims.size() - 1; i >= 0; i--) {
                    BigInteger size = dims.get(i).size();
                    BigInteger suffix = original.subtract(ordinal.multiply(stride));
                    BigInteger[] qr = ordinal.divideAndRemainder(size);
                    // A triangular permutation: faster digits rotate slower dimensions. It
                    // is bijective, but the prefix varies every field instead of just the last.
                    args[i] = dims.get(i).create(qr[1].add(suffix).mod(size));
                    ordinal = qr[0];
                    stride = stride.multiply(size);
                }
                return factory.create(args);
            }
        };
    }

    static ShapePlan union(String name, List<ShapePlan> alternatives) {
        List<ShapePlan> parts = List.copyOf(alternatives);
        BigInteger count = parts.stream().map(ShapePlan::size).reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger largest = parts.stream().map(ShapePlan::size).max(BigInteger::compareTo).orElse(BigInteger.ZERO);
        return new ShapePlan() {
            public BigInteger size() { return count; }
            public String description() { return "interleaved-v1:" + name + parts.stream().map(ShapePlan::description).toList(); }
            public Object create(BigInteger ordinal) throws Exception {
                if (ordinal.signum() < 0 || ordinal.compareTo(count) >= 0) throw new IndexOutOfBoundsException(ordinal.toString());
                // Seek to the round directly, even for huge and uneven branch domains.
                BigInteger lo = BigInteger.ZERO, hi = largest;
                while (lo.add(BigInteger.ONE).compareTo(hi) < 0) {
                    BigInteger mid = lo.add(hi).shiftRight(1), before = BigInteger.ZERO;
                    for (ShapePlan part : parts) before = before.add(part.size().min(mid));
                    if (before.compareTo(ordinal) <= 0) lo = mid; else hi = mid;
                }
                BigInteger within = ordinal;
                for (ShapePlan part : parts) within = within.subtract(part.size().min(lo));
                for (ShapePlan part : parts) if (part.size().compareTo(lo) > 0) {
                    if (within.signum() == 0) return part.create(lo);
                    within = within.subtract(BigInteger.ONE);
                }
                throw new AssertionError("union ordinal not found");
            }
        };
    }

    static ShapePlan seeded(String name, List<ShapePlan> seeds, ShapePlan exploration) {
        List<ShapePlan> parts = new ArrayList<>(seeds);
        BigInteger required = seeds.stream().map(ShapePlan::size).reduce(BigInteger.ZERO, BigInteger::add);
        parts.add(exploration);
        ShapePlan combined = concat(name, parts);
        return new ShapePlan() {
            public BigInteger size() { return combined.size(); }
            public BigInteger requiredPrefix() { return required; }
            public String description() { return "seeds-v1:" + combined.description(); }
            public Object create(BigInteger ordinal) throws Exception { return combined.create(ordinal); }
        };
    }

    static ShapePlan concat(String name, List<ShapePlan> alternatives) {
        List<ShapePlan> parts = List.copyOf(alternatives);
        BigInteger count = parts.stream().map(ShapePlan::size).reduce(BigInteger.ZERO, BigInteger::add);
        return new ShapePlan() {
            public BigInteger size() { return count; }
            public String description() { return name + parts.stream().map(ShapePlan::description).toList(); }
            public Object create(BigInteger ordinal) throws Exception {
                if (ordinal.signum() < 0) throw new IndexOutOfBoundsException(ordinal.toString());
                for (ShapePlan part : parts) {
                    if (ordinal.compareTo(part.size()) < 0) return part.create(ordinal);
                    ordinal = ordinal.subtract(part.size());
                }
                throw new IndexOutOfBoundsException(ordinal.toString());
            }
        };
    }
}
