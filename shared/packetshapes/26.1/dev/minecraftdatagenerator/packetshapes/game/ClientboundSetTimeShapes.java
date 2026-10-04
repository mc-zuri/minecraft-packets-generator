package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ClientboundSetTimePacket(long gameTime [i64], Map&lt;Holder&lt;WorldClock&gt;, ClockNetworkState&gt;
 * clockUpdates [varint count + entries]).
 * <p>
 * The map key is {@code ByteBufCodecs.holderRegistry(Registries.WORLD_CLOCK)} — a registry reference
 * only (no inline/direct holder form exists for WorldClock), so keys must come from the live registry.
 * The value is {@code ClockNetworkState(varlong totalTicks, f32 partialTick, f32 rate)}.
 * <p>
 * Coverage: gameTime over the long edge cases crossed with map sizes 0/1/2; then per-field sweeps of
 * the ClockNetworkState fields (totalTicks over the VarLong byte-length boundaries, partialTick and
 * rate over the float edge cases) on a single-entry map.
 * <p>
 * Every registry-backed shape is guarded on the registry actually holding enough entries, so this
 * never fabricates a holder that the encoder would reject.
 */
public class ClientboundSetTimeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetTimePacket.class;
    }

    private static Map<Holder<WorldClock>, ClockNetworkState> map(List<Holder<WorldClock>> keys,
                                                                  int size, ClockNetworkState state) {
        Map<Holder<WorldClock>, ClockNetworkState> m = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) m.put(keys.get(i), state);
        return m;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        List<Holder<WorldClock>> keys = new ArrayList<>();
        try {
            ctx.registries.lookupOrThrow(Registries.WORLD_CLOCK).listElements()
                    .limit(2).forEach(keys::add);
        } catch (RuntimeException ignored) {
            // WORLD_CLOCK registry missing/empty in this datapack; only empty-map shapes are emitted.
        }

        ClockNetworkState baseline = new ClockNetworkState(1L, 0.5f, 1f);

        // gameTime edges x map sizes 0/1/2 (only the sizes the registry can actually supply).
        for (long t : ctx.longs()) {
            for (int size = 0; size <= keys.size(); size++) {
                out.add(new ClientboundSetTimePacket(t, map(keys, size, baseline)));
            }
        }

        if (!keys.isEmpty()) {
            // totalTicks: VarLong byte-length boundaries.
            for (long ticks : ctx.varLongs()) {
                out.add(new ClientboundSetTimePacket(0L, map(keys, 1, new ClockNetworkState(ticks, 0.5f, 1f))));
            }
            // partialTick / rate: float edge cases.
            for (float f : ctx.floats()) {
                out.add(new ClientboundSetTimePacket(0L, map(keys, 1, new ClockNetworkState(1L, f, 1f))));
                out.add(new ClientboundSetTimePacket(0L, map(keys, 1, new ClockNetworkState(1L, 0.5f, f))));
            }
        }

        return out;
    }
}
