package dev.minecraftdatagenerator.packetshapes;

import net.minecraft.network.protocol.Packet;

import java.util.List;

/**
 * A hand-written shape generator for exactly one packet class.
 * <p>
 * Every packet that carries a {@code STREAM_CODEC} must have a {@code PacketShapes} implementation in
 * this package. {@link dev.minecraftdatagenerator.generators.PacketSamplesDataGenerator} discovers
 * all implementations, and <b>fails loudly</b> (listing the offenders) if any packet lacks one — there
 * is no generic fallback and nothing is silently skipped.
 * <p>
 * The returned list should exercise <b>all corner cases</b> of the packet: every enum value, both
 * boolean states, number edge cases ({@code 0}, positive, negative, and — where the field context
 * warrants — min/max), {@code Optional} present/absent, empty vs non-empty collections and item stacks,
 * and every {@code switch}/branch the wire format has. Use {@link ShapeCtx} for concise, context-aware
 * value construction (live registries, world/entity graph, and cartesian-product helpers).
 * <p>
 * Implementations must have a public no-argument constructor.
 */
public interface PacketShapes {

    /** The exact packet class this provider generates shapes for. */
    Class<? extends Packet<?>> packetType();

    /**
     * All corner-case shapes of {@link #packetType()}. Each element is a ready-to-encode packet
     * instance; the generator encodes it with the packet's own {@code STREAM_CODEC}, frames it, and
     * round-trips it. Return at least one shape.
     */
    List<Packet<?>> shapes(ShapeCtx ctx);
}
