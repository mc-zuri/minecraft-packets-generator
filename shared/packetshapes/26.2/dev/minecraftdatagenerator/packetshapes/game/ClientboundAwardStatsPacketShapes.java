package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundAwardStatsPacket(Object2IntMap&lt;Stat&lt;?&gt;&gt; stats).
 * <p>
 * Wire body: a map (size:varint, then entries) of Stat -&gt; count:varint. A Stat is itself a
 * dispatched pair: statType:registry-id varint, then the value's id in that StatType's own registry.
 * <p>
 * The StatType is the dispatch discriminator, so every one of the nine constants in {@code Stats} is
 * emitted (BLOCK_MINED, ITEM_CRAFTED/USED/BROKEN/PICKED_UP/DROPPED, ENTITY_KILLED, ENTITY_KILLED_BY,
 * CUSTOM), crossed in full with the VarInt boundary spread for the count. Map sizes 0/1/2/3 are
 * covered, and the highest-id block/item/entity-type are included so the inner registry id also
 * crosses the varint width boundaries.
 * <p>
 * Object2IntOpenHashMap is used for construction, matching the map the decoder builds, so the
 * iteration (and therefore encode) order round-trips byte-stably.
 */
public class ClientboundAwardStatsPacketShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundAwardStatsPacket.class;
    }

    private static ClientboundAwardStatsPacket of(List<Stat<?>> keys, int count) {
        // Same constructor the decoder uses (ByteBufCodecs.map(Object2IntOpenHashMap::new) passes the
        // entry count), so the open-hash iteration order - and therefore the encode order - matches.
        Object2IntMap<Stat<?>> m = new Object2IntOpenHashMap<>(keys.size());
        for (Stat<?> k : keys) m.put(k, count);
        return new ClientboundAwardStatsPacket(m);
    }

    /** One Stat per StatType constant, plus high-registry-id values for the id-varint boundaries. */
    private static List<Stat<?>> stats(ShapeCtx ctx) {
        Registry<Block> blocks = ctx.registries.lookupOrThrow(Registries.BLOCK);
        Registry<Item> items = ctx.registries.lookupOrThrow(Registries.ITEM);
        Registry<EntityType<?>> entityTypes = ctx.registries.lookupOrThrow(Registries.ENTITY_TYPE);
        Block lastBlock = blocks.byId(blocks.size() - 1);
        Item lastItem = items.byId(items.size() - 1);
        EntityType<?> lastEntityType = entityTypes.byId(entityTypes.size() - 1);

        List<Stat<?>> out = new ArrayList<>();
        // Every StatType constant, in registration order.
        out.add(Stats.BLOCK_MINED.get(Blocks.STONE));
        out.add(Stats.ITEM_CRAFTED.get(Items.STONE));
        out.add(Stats.ITEM_USED.get(Items.STONE));
        out.add(Stats.ITEM_BROKEN.get(Items.IRON_PICKAXE));
        out.add(Stats.ITEM_PICKED_UP.get(Items.STONE));
        out.add(Stats.ITEM_DROPPED.get(Items.STONE));
        out.add(Stats.ENTITY_KILLED.get(EntityTypes.PIG));
        out.add(Stats.ENTITY_KILLED_BY.get(EntityTypes.PIG));
        out.add(Stats.CUSTOM.get(Stats.JUMP));
        // A few more CUSTOM ids, since that registry is the one with the most spread-out ids.
        out.add(Stats.CUSTOM.get(Stats.DEATHS));
        out.add(Stats.CUSTOM.get(Stats.PLAY_TIME));
        // Highest ids in each backing registry -> widest inner varint.
        if (lastBlock != null) out.add(Stats.BLOCK_MINED.get(lastBlock));
        if (lastItem != null) out.add(Stats.ITEM_USED.get(lastItem));
        if (lastEntityType != null) out.add(Stats.ENTITY_KILLED.get(lastEntityType));
        return out;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Stat<?>> stats = stats(ctx);
        List<Packet<?>> out = new ArrayList<>();

        // Empty map.
        out.add(of(List.of(), 0));
        // Single entry: every stat crossed with every VarInt count boundary.
        for (Stat<?> s : stats) {
            for (int count : ctx.varInts()) out.add(of(List.of(s), count));
        }
        // Sizes 2 and 3, mixing StatTypes so the dispatch varies inside one map.
        out.add(of(List.of(stats.get(0), stats.get(8)), 1));
        out.add(of(List.of(stats.get(0), stats.get(6), stats.get(8)), 2));
        return out;
    }
}
