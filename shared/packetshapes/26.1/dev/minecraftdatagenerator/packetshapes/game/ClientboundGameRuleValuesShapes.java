package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundGameRuleValuesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.gamerules.GameRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ClientboundGameRuleValuesPacket(Map&lt;ResourceKey&lt;GameRule&lt;?&gt;&gt;, String&gt; values) — a
 * var-int-count-prefixed map whose keys are game-rule resource keys (written as their Identifier) and
 * whose values are UTF-8 strings.
 * <p>
 * Covered: map sizes 0, 1, 2, 3 and the whole registry at once (which also pushes the count past the
 * one-byte var-int boundary when there are enough rules); the value string swept over the UTF-8
 * length-prefix classes (empty, ASCII, 2-, 3- and 4-byte code points) plus the boolean/int literals
 * vanilla actually sends; and several distinct keys so the key-id encoding is exercised.
 */
public class ClientboundGameRuleValuesShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundGameRuleValuesPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Registry<GameRule<?>> reg = ctx.registries.lookupOrThrow(Registries.GAME_RULE);
        List<ResourceKey<GameRule<?>>> keys = new ArrayList<>(reg.registryKeySet());

        List<Packet<?>> out = new ArrayList<>();

        // empty map
        out.add(new ClientboundGameRuleValuesPacket(new LinkedHashMap<>()));

        if (!keys.isEmpty()) {
            // single entry, value swept over the UTF-8 classes and the literals vanilla sends
            List<String> values = new ArrayList<>(ctx.utf8());
            values.add("true");
            values.add("false");
            values.add("0");
            values.add("2147483647");
            values.add("-2147483648");
            for (String v : values) {
                Map<ResourceKey<GameRule<?>>, String> m = new LinkedHashMap<>();
                m.put(keys.get(0), v);
                out.add(new ClientboundGameRuleValuesPacket(m));
            }
            // single entry, key swept over several distinct registry keys
            for (int i = 0; i < Math.min(keys.size(), 5); i++) {
                Map<ResourceKey<GameRule<?>>, String> m = new LinkedHashMap<>();
                m.put(keys.get(i), "true");
                out.add(new ClientboundGameRuleValuesPacket(m));
            }
        }
        // map sizes 2 and 3
        for (int size : List.of(2, 3)) {
            if (keys.size() < size) continue;
            Map<ResourceKey<GameRule<?>>, String> m = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) m.put(keys.get(i), i % 2 == 0 ? "true" : String.valueOf(i * 10));
            out.add(new ClientboundGameRuleValuesPacket(m));
        }
        // the whole registry in one packet (largest realistic map, multi-byte count prefix)
        if (!keys.isEmpty()) {
            Map<ResourceKey<GameRule<?>>, String> all = new LinkedHashMap<>();
            for (ResourceKey<GameRule<?>> k : keys) all.put(k, "true");
            out.add(new ClientboundGameRuleValuesPacket(all));
        }
        return out;
    }
}
