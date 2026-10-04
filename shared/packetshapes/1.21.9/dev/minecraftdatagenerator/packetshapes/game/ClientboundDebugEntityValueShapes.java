package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDebugEntityValuePacket;
import net.minecraft.util.Unit;
import net.minecraft.util.debug.DebugBeeInfo;
import net.minecraft.util.debug.DebugBrainDump;
import net.minecraft.util.debug.DebugBreezeInfo;
import net.minecraft.util.debug.DebugEntityBlockIntersection;
import net.minecraft.util.debug.DebugGameEventInfo;
import net.minecraft.util.debug.DebugGameEventListenerInfo;
import net.minecraft.util.debug.DebugGoalInfo;
import net.minecraft.util.debug.DebugHiveInfo;
import net.minecraft.util.debug.DebugPathInfo;
import net.minecraft.util.debug.DebugPoiInfo;
import net.minecraft.util.debug.DebugStructureInfo;
import net.minecraft.util.debug.DebugSubscription;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ClientboundDebugEntityValuePacket(int entityId[varint], DebugSubscription.Update&lt;?&gt; update).
 * <p>
 * {@code update} is registry-dispatched on {@code minecraft:debug_subscription}: it writes the
 * subscription's registry id as a var-int, then an {@code Optional<value>} (bool prefix) whose payload
 * codec is the subscription's own value codec. The discriminator therefore has one branch per registered
 * subscription, and each branch has both an empty and a present form.
 * <p>
 * Covered: every value-carrying subscription of {@code DebugSubscriptions} — bees, brains, breezes,
 * goal_selectors, entity_paths, entity_block_intersections, bee_hives, pois, redstone_wire_orientations,
 * village_sections, raids, structures, game_event_listeners, neighbor_updates, game_events — each in its
 * absent form and in one or more present forms that exercise the value codec's own optionals, lists and
 * numeric edges. Finally a var-int boundary sweep on {@code entityId}.
 * <p>
 * {@code entity_paths} carries a {@code Path} whose {@code DEBUG_STREAM_CODEC} requires attached
 * pathfinder debug data; that data is attached through the package-private {@code Path.setDebug} by
 * {@link ClientboundDebugBlockValueShapes#debugPath()}, which this provider reuses.
 * <p>
 * OMITTED (vanilla cannot encode it): {@code dedicated_server_tick_time} — its
 * {@code valueStreamCodec} is {@code null}, so {@code Update.streamCodec} throws on dispatch.
 */
public class ClientboundDebugEntityValueShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundDebugEntityValuePacket.class;
    }

    /** Every value-carrying subscription, as absent + present updates. Shared shape with the event packet. */
    static List<DebugSubscription.Update<?>> updates(ShapeCtx ctx) {
        List<DebugSubscription.Update<?>> out = new ArrayList<>();

        // --- absent branch for every value-carrying subscription -------------------------------
        List<DebugSubscription<?>> all = new ArrayList<>();
        all.add(DebugSubscriptions.BEES);
        all.add(DebugSubscriptions.BRAINS);
        all.add(DebugSubscriptions.BREEZES);
        all.add(DebugSubscriptions.GOAL_SELECTORS);
        all.add(DebugSubscriptions.ENTITY_PATHS);
        all.add(DebugSubscriptions.ENTITY_BLOCK_INTERSECTIONS);
        all.add(DebugSubscriptions.BEE_HIVES);
        all.add(DebugSubscriptions.POIS);
        all.add(DebugSubscriptions.REDSTONE_WIRE_ORIENTATIONS);
        all.add(DebugSubscriptions.VILLAGE_SECTIONS);
        all.add(DebugSubscriptions.RAIDS);
        all.add(DebugSubscriptions.STRUCTURES);
        all.add(DebugSubscriptions.GAME_EVENT_LISTENERS);
        all.add(DebugSubscriptions.NEIGHBOR_UPDATES);
        all.add(DebugSubscriptions.GAME_EVENTS);
        for (DebugSubscription<?> sub : all) {
            out.add(sub.emptyUpdate());
        }

        // --- present branch, one entry per value shape -----------------------------------------
        for (Object v : values(ctx)) {
            out.add(pack(v));
        }
        return out;
    }

    /**
     * One representative (and several corner-case) values per value-carrying subscription. Returned as
     * raw objects so both the Update and the Event provider can pack them.
     */
    static List<Object> values(ShapeCtx ctx) {
        List<Object> out = new ArrayList<>();

        // bees: Optional<BlockPos> hivePos, Optional<BlockPos> flowerPos, varint travelTicks, List<BlockPos>
        out.add(new DebugBeeInfo(Optional.empty(), Optional.empty(), 0, List.of()));
        out.add(new DebugBeeInfo(Optional.of(new BlockPos(1, -64, 2)), Optional.empty(), 1, List.of(new BlockPos(0, 0, 0))));
        out.add(new DebugBeeInfo(Optional.empty(), Optional.of(new BlockPos(-1, 319, -2)), 16384, List.of()));
        out.add(new DebugBeeInfo(Optional.of(new BlockPos(30000000, 2031, -30000000)),
                Optional.of(new BlockPos(-30000000, -2032, 30000000)), Integer.MAX_VALUE,
                List.of(new BlockPos(1, 1, 1), new BlockPos(2, 2, 2))));

        // brains: 4 strings, i32 xp, 2 floats, string, bool, i32 anger, 4 string lists, 2 BlockPos sets
        out.add(new DebugBrainDump("", "", 0, 0f, 0f, "", false, -1,
                List.of(), List.of(), List.of(), List.of(), Set.of(), Set.of()));
        out.add(new DebugBrainDump("name", "minecraft:farmer", Integer.MAX_VALUE, 20f, 20f, "inv", true, 3,
                List.of("core"), List.of("a", "b"), List.of("m"), List.of("g"),
                blockPosSet(new BlockPos(0, 64, 0)),
                blockPosSet(new BlockPos(1, -64, 2), new BlockPos(-1, 319, -2))));
        out.add(new DebugBrainDump("éèê", "你好", Integer.MIN_VALUE, -1f, 3.5f,
                new String(Character.toChars(0x1F600)), false, Integer.MAX_VALUE,
                List.of(), List.of(), List.of(), List.of(), Set.of(), Set.of()));

        // breezes: Optional<varint> attackTarget, Optional<BlockPos> jumpTarget (all 4 optional combos)
        out.add(new DebugBreezeInfo(Optional.empty(), Optional.empty()));
        out.add(new DebugBreezeInfo(Optional.of(0), Optional.empty()));
        out.add(new DebugBreezeInfo(Optional.empty(), Optional.of(new BlockPos(1, -64, 2))));
        out.add(new DebugBreezeInfo(Optional.of(Integer.MAX_VALUE), Optional.of(new BlockPos(30000000, 319, -30000000))));

        // goal_selectors: List<DebugGoal(varint priority, bool isRunning, string(255) name)>
        out.add(new DebugGoalInfo(List.of()));
        out.add(new DebugGoalInfo(List.of(new DebugGoalInfo.DebugGoal(0, false, ""))));
        out.add(new DebugGoalInfo(List.of(
                new DebugGoalInfo.DebugGoal(1, true, "x"),
                new DebugGoalInfo.DebugGoal(16384, false, "你好"))));

        // entity_paths: Path (with its required debug data) + float maxNodeDistance
        out.add(new DebugPathInfo(ClientboundDebugBlockValueShapes.debugPath(), 0f));
        out.add(new DebugPathInfo(ClientboundDebugBlockValueShapes.debugPath(), 3.5f));
        out.add(new DebugPathInfo(ClientboundDebugBlockValueShapes.debugPath(), -1f));

        // entity_block_intersections: enum (every constant)
        out.addAll(ctx.enumAll(DebugEntityBlockIntersection.class));

        // bee_hives: Block registry id, 2 varints, bool
        out.add(new DebugHiveInfo(Blocks.BEEHIVE, 0, 0, false));
        out.add(new DebugHiveInfo(Blocks.BEE_NEST, 3, 5, true));
        out.add(new DebugHiveInfo(Blocks.STONE, Integer.MAX_VALUE, 16384, false));

        // pois: BlockPos, Holder<PoiType>, varint freeTicketCount
        out.add(new DebugPoiInfo(new BlockPos(0, 0, 0), ctx.holder(Registries.POINT_OF_INTEREST_TYPE), 0));
        out.add(new DebugPoiInfo(new BlockPos(30000000, 2031, -30000000),
                ctx.holder(Registries.POINT_OF_INTEREST_TYPE), Integer.MAX_VALUE));

        // redstone_wire_orientations: id-mapped over 48 orientations; sample the id extremes plus a middle
        out.add(Orientation.of(Direction.UP, Direction.NORTH, Orientation.SideBias.LEFT));
        out.add(Orientation.of(Direction.UP, Direction.NORTH, Orientation.SideBias.RIGHT));
        out.add(Orientation.of(Direction.DOWN, Direction.SOUTH, Orientation.SideBias.LEFT));
        out.add(Orientation.of(Direction.EAST, Direction.UP, Orientation.SideBias.RIGHT));
        out.add(Orientation.of(Direction.WEST, Direction.DOWN, Orientation.SideBias.LEFT));

        // village_sections: Unit (zero-length payload)
        out.add(Unit.INSTANCE);

        // raids: List<BlockPos>, sizes 0/1/2
        out.add(List.<BlockPos>of());
        out.add(List.of(new BlockPos(0, 0, 0)));
        out.add(List.of(new BlockPos(30000000, 2031, -30000000), new BlockPos(-30000000, -2032, 30000000)));

        // structures: List<DebugStructureInfo(BoundingBox, List<Piece(BoundingBox, bool)>)>
        out.add(List.<DebugStructureInfo>of());
        out.add(List.of(new DebugStructureInfo(new BoundingBox(0, 0, 0, 0, 0, 0), List.of())));
        out.add(List.of(
                new DebugStructureInfo(new BoundingBox(-1, -64, -1, 1, 319, 1),
                        List.of(new DebugStructureInfo.Piece(new BoundingBox(0, 0, 0, 1, 1, 1), true))),
                new DebugStructureInfo(new BoundingBox(-30000000, -2032, -30000000, 30000000, 2031, 30000000),
                        List.of(new DebugStructureInfo.Piece(new BoundingBox(0, 0, 0, 1, 1, 1), true),
                                new DebugStructureInfo.Piece(new BoundingBox(2, 2, 2, 3, 3, 3), false)))));

        // game_event_listeners: single varint radius
        out.add(new DebugGameEventListenerInfo(0));
        out.add(new DebugGameEventListenerInfo(16384));
        out.add(new DebugGameEventListenerInfo(Integer.MAX_VALUE));

        // neighbor_updates: BlockPos (full domain-correct spread)
        out.addAll(ctx.blockPos());

        // game_events: Holder<GameEvent> + Vec3
        out.add(new DebugGameEventInfo(ctx.holder(Registries.GAME_EVENT), new Vec3(0, 0, 0)));
        out.add(new DebugGameEventInfo(ctx.holder(Registries.GAME_EVENT), new Vec3(-3.0E7, 319.5, 3.0E7)));

        return out;
    }

    private static Set<BlockPos> blockPosSet(BlockPos... positions) {
        return new LinkedHashSet<>(List.of(positions));
    }

    /** Route a raw value object back to the subscription that owns its codec. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static DebugSubscription.Update<?> pack(Object value) {
        return ((DebugSubscription) subscriptionFor(value)).packUpdate(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static DebugSubscription.Event<?> packEvent(Object value) {
        return ((DebugSubscription) subscriptionFor(value)).packEvent(value);
    }

    private static DebugSubscription<?> subscriptionFor(Object value) {
        if (value instanceof DebugBeeInfo) return DebugSubscriptions.BEES;
        if (value instanceof DebugBrainDump) return DebugSubscriptions.BRAINS;
        if (value instanceof DebugBreezeInfo) return DebugSubscriptions.BREEZES;
        if (value instanceof DebugGoalInfo) return DebugSubscriptions.GOAL_SELECTORS;
        if (value instanceof DebugPathInfo) return DebugSubscriptions.ENTITY_PATHS;
        if (value instanceof DebugEntityBlockIntersection) return DebugSubscriptions.ENTITY_BLOCK_INTERSECTIONS;
        if (value instanceof DebugHiveInfo) return DebugSubscriptions.BEE_HIVES;
        if (value instanceof DebugPoiInfo) return DebugSubscriptions.POIS;
        if (value instanceof Orientation) return DebugSubscriptions.REDSTONE_WIRE_ORIENTATIONS;
        if (value instanceof Unit) return DebugSubscriptions.VILLAGE_SECTIONS;
        if (value instanceof DebugGameEventListenerInfo) return DebugSubscriptions.GAME_EVENT_LISTENERS;
        if (value instanceof BlockPos) return DebugSubscriptions.NEIGHBOR_UPDATES;
        if (value instanceof DebugGameEventInfo) return DebugSubscriptions.GAME_EVENTS;
        if (value instanceof List<?> list) {
            // raids is List<BlockPos>, structures is List<DebugStructureInfo>
            if (!list.isEmpty() && list.get(0) instanceof DebugStructureInfo) return DebugSubscriptions.STRUCTURES;
            if (!list.isEmpty()) return DebugSubscriptions.RAIDS;
            // an empty list is ambiguous; emit it for both by alternating is not possible here, so raids
            return DebugSubscriptions.RAIDS;
        }
        throw new IllegalArgumentException("no subscription for " + value.getClass());
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        // every subscription branch x (absent | present)
        for (DebugSubscription.Update<?> u : updates(ctx)) {
            out.add(new ClientboundDebugEntityValuePacket(0, u));
        }
        // the empty STRUCTURES list is routed to RAIDS by subscriptionFor; add it explicitly
        out.add(new ClientboundDebugEntityValuePacket(0, DebugSubscriptions.STRUCTURES.packUpdate(List.of())));

        // entityId: var-int byte-length boundaries against a fixed update
        DebugSubscription.Update<?> base = DebugSubscriptions.VILLAGE_SECTIONS.packUpdate(Unit.INSTANCE);
        for (int id : ctx.varInts()) {
            out.add(new ClientboundDebugEntityValuePacket(id, base));
        }
        return out;
    }
}
