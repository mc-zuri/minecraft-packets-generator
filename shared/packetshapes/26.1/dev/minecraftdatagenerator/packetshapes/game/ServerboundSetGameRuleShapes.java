package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetGameRulePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.gamerules.GameRule;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundSetGameRulePacket(List&lt;Entry&gt; entries), Entry(ResourceKey&lt;GameRule&lt;?&gt;&gt;
 * gameRuleKey, String value).
 * Wire: VarInt-prefixed list of {identifier(gameRuleKey), STRING_UTF8(value)}.
 * <p>
 * Collection sizes 0, 1, 2 and 3. The single-element case sweeps the UTF-8 string spread for
 * {@code value} and, separately, every distinct registry key we sample, so both wire fields of
 * {@code Entry} vary. Keys are taken from the live {@code GAME_RULE} registry (sorted by id so the
 * selection is stable run to run), never fabricated.
 */
public class ServerboundSetGameRuleShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSetGameRulePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Registry<GameRule<?>> reg = ctx.registries.lookupOrThrow(Registries.GAME_RULE);
        List<ResourceKey<GameRule<?>>> keys = reg.registryKeySet().stream()
                .sorted((x, y) -> x.identifier().toString().compareTo(y.identifier().toString()))
                .limit(3)
                .toList();
        ResourceKey<GameRule<?>> key = keys.get(0);

        List<Packet<?>> out = new ArrayList<>();

        // empty collection
        out.add(new ServerboundSetGameRulePacket(List.of()));

        // one element, sweeping the value string
        for (String value : ctx.utf8()) {
            out.add(new ServerboundSetGameRulePacket(
                    List.of(new ServerboundSetGameRulePacket.Entry(key, value))));
        }

        // one element, sweeping the registry key
        for (ResourceKey<GameRule<?>> k : keys) {
            out.add(new ServerboundSetGameRulePacket(
                    List.of(new ServerboundSetGameRulePacket.Entry(k, "true"))));
        }

        // two and three elements
        List<ServerboundSetGameRulePacket.Entry> entries = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            entries.add(new ServerboundSetGameRulePacket.Entry(keys.get(i), i == 0 ? "" : "true"));
            if (entries.size() >= 2) out.add(new ServerboundSetGameRulePacket(List.copyOf(entries)));
        }
        return out;
    }
}
