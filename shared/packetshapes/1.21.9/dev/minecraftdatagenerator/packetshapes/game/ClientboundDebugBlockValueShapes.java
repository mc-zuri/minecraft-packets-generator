package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDebugBlockValuePacket;
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
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.Target;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ClientboundDebugBlockValuePacket(BlockPos blockPos, DebugSubscription.Update&lt;?&gt; update).
 * <p>
 * {@code DebugSubscription.Update.STREAM_CODEC} is a registry dispatch: it writes the
 * DEBUG_SUBSCRIPTION registry id, then {@code optional(value)} using that subscription's own value
 * codec. Both parts are discriminators, so every registered subscription that has a value codec is
 * emitted, each in the absent and the present branch of the optional, and the present branch also
 * exercises the inner optionals / list sizes / enums of the value record. The whole update spread is
 * crossed with the domain-correct block-position spread.
 * <p>
 * OMITTED: {@code minecraft:dedicated_server_tick_time} is registered with a {@code null} value codec,
 * so {@code Update.streamCodec} throws a NullPointerException for it — vanilla can never encode it as an
 * Update and it is deliberately excluded.
 */
public class ClientboundDebugBlockValueShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundDebugBlockValuePacket.class;
    }

    /** A Path carrying the debug data the DEBUG_STREAM_CODEC requires (non-null, non-empty targets). */
    static Path debugPath() {
        Node a = new Node(0, 64, 0);
        a.type = PathType.WALKABLE;
        a.walkedDistance = 1.5f;
        a.costMalus = -1.0f;
        a.closed = true;
        a.f = 2.5f;
        Node b = new Node(1, -64, -1);
        Target target = new Target(30000000, 319, -30000000);

        Path path = new Path(List.of(a, b), new BlockPos(1, 2, 3), true);
        path.setNextNodeIndex(1);
        // Path.setDebug is package-private and its open/closed set parameters are Node[] in 26.2
        // (they became List<Node> in 26.3), so resolve the method by name and adapt the arguments
        // to whichever signature this version declares.
        try {
            Method setDebug = null;
            for (Method m : Path.class.getDeclaredMethods()) {
                if (m.getName().equals("setDebug") && m.getParameterCount() == 3) {
                    setDebug = m;
                    break;
                }
            }
            if (setDebug == null) throw new NoSuchMethodException("Path.setDebug");
            setDebug.setAccessible(true);
            Class<?> firstParam = setDebug.getParameterTypes()[0];
            Object openSet = firstParam.isArray() ? new Node[]{a} : List.of(a);
            Object closedSet = firstParam.isArray() ? new Node[]{b} : List.of(b);
            setDebug.invoke(path, openSet, closedSet, Set.of(target));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to attach debug data to Path", e);
        }
        return path;
    }

    /**
     * Every DEBUG_SUBSCRIPTION branch that has a value codec, in the empty and the present form, with
     * extra present-value variants wherever the value record itself has optionals, lists or enums.
     */
    static List<DebugSubscription.Update<?>> updates(ShapeCtx ctx) {
        List<DebugSubscription.Update<?>> out = new ArrayList<>();
        BlockPos p0 = new BlockPos(0, 0, 0);
        BlockPos p1 = new BlockPos(1, -64, 2);
        BlockPos p2 = new BlockPos(-30000000, 319, 30000000);

        // --- bees: two optionals, a varint and a list ---
        out.add(DebugSubscriptions.BEES.emptyUpdate());
        out.add(DebugSubscriptions.BEES.packUpdate(
                new DebugBeeInfo(Optional.empty(), Optional.empty(), 0, List.of())));
        out.add(DebugSubscriptions.BEES.packUpdate(
                new DebugBeeInfo(Optional.of(p1), Optional.empty(), 1, List.of(p0))));
        out.add(DebugSubscriptions.BEES.packUpdate(
                new DebugBeeInfo(Optional.of(p1), Optional.of(p2), -1, List.of(p0, p1))));

        // --- brains: many strings, lists and block-pos sets ---
        out.add(DebugSubscriptions.BRAINS.emptyUpdate());
        out.add(DebugSubscriptions.BRAINS.packUpdate(new DebugBrainDump(
                "", "", 0, 0f, 0f, "", false, 0,
                List.of(), List.of(), List.of(), List.of(), Set.of(), Set.of())));
        out.add(DebugSubscriptions.BRAINS.packUpdate(new DebugBrainDump(
                "name", "minecraft:farmer", Integer.MAX_VALUE, 3.5f, -3.5f, "inv", true, Integer.MIN_VALUE,
                List.of("core"), List.of("a", "b"), List.of(""), ctx.utf8(),
                Set.of(p0), Set.of(p1, p2))));

        // --- breezes: optional varint + optional block pos, all four combinations ---
        out.add(DebugSubscriptions.BREEZES.emptyUpdate());
        for (Optional<Integer> attack : List.of(Optional.<Integer>empty(), Optional.of(0), Optional.of(-1))) {
            for (Optional<BlockPos> jump : List.of(Optional.<BlockPos>empty(), Optional.of(p1))) {
                out.add(DebugSubscriptions.BREEZES.packUpdate(new DebugBreezeInfo(attack, jump)));
            }
        }

        // --- goal selectors: list of (varint, bool, string) ---
        out.add(DebugSubscriptions.GOAL_SELECTORS.emptyUpdate());
        out.add(DebugSubscriptions.GOAL_SELECTORS.packUpdate(new DebugGoalInfo(List.of())));
        out.add(DebugSubscriptions.GOAL_SELECTORS.packUpdate(new DebugGoalInfo(
                List.of(new DebugGoalInfo.DebugGoal(0, false, "")))));
        out.add(DebugSubscriptions.GOAL_SELECTORS.packUpdate(new DebugGoalInfo(
                List.of(new DebugGoalInfo.DebugGoal(1, true, "minecraft:melee"),
                        new DebugGoalInfo.DebugGoal(-1, false, "你好")))));

        // --- entity paths ---
        out.add(DebugSubscriptions.ENTITY_PATHS.emptyUpdate());
        out.add(DebugSubscriptions.ENTITY_PATHS.packUpdate(new DebugPathInfo(debugPath(), 0f)));
        out.add(DebugSubscriptions.ENTITY_PATHS.packUpdate(new DebugPathInfo(debugPath(), 3.5f)));

        // --- entity block intersections: an enum, every constant ---
        out.add(DebugSubscriptions.ENTITY_BLOCK_INTERSECTIONS.emptyUpdate());
        for (DebugEntityBlockIntersection value : ctx.enumAll(DebugEntityBlockIntersection.class)) {
            out.add(DebugSubscriptions.ENTITY_BLOCK_INTERSECTIONS.packUpdate(value));
        }

        // --- bee hives: registry block + two varints + bool ---
        out.add(DebugSubscriptions.BEE_HIVES.emptyUpdate());
        for (boolean sedated : ctx.bools()) {
            out.add(DebugSubscriptions.BEE_HIVES.packUpdate(
                    new DebugHiveInfo(Blocks.BEEHIVE, 0, 0, sedated)));
        }
        out.add(DebugSubscriptions.BEE_HIVES.packUpdate(
                new DebugHiveInfo(Blocks.BEE_NEST, 3, 5, true)));

        // --- pois: block pos + registry holder + varint ---
        out.add(DebugSubscriptions.POIS.emptyUpdate());
        out.add(DebugSubscriptions.POIS.packUpdate(
                new DebugPoiInfo(p0, ctx.holder(Registries.POINT_OF_INTEREST_TYPE), 0)));
        out.add(DebugSubscriptions.POIS.packUpdate(
                new DebugPoiInfo(p2, ctx.holder(Registries.POINT_OF_INTEREST_TYPE), -1)));

        // --- redstone wire orientations: a 48-value id mapper ---
        out.add(DebugSubscriptions.REDSTONE_WIRE_ORIENTATIONS.emptyUpdate());
        for (Orientation.SideBias bias : ctx.enumAll(Orientation.SideBias.class)) {
            out.add(DebugSubscriptions.REDSTONE_WIRE_ORIENTATIONS.packUpdate(
                    Orientation.of(Direction.UP, Direction.NORTH, bias)));
            out.add(DebugSubscriptions.REDSTONE_WIRE_ORIENTATIONS.packUpdate(
                    Orientation.of(Direction.WEST, Direction.DOWN, bias)));
        }

        // --- village sections: Unit, i.e. an empty payload ---
        out.add(DebugSubscriptions.VILLAGE_SECTIONS.emptyUpdate());
        out.add(DebugSubscriptions.VILLAGE_SECTIONS.packUpdate(Unit.INSTANCE));

        // --- raids: a list of block positions ---
        out.add(DebugSubscriptions.RAIDS.emptyUpdate());
        out.add(DebugSubscriptions.RAIDS.packUpdate(List.of()));
        out.add(DebugSubscriptions.RAIDS.packUpdate(List.of(p0)));
        out.add(DebugSubscriptions.RAIDS.packUpdate(List.of(p1, p2)));

        // --- structures: a list of (bounding box, list of (bounding box, bool)) ---
        BoundingBox box0 = new BoundingBox(0, 0, 0, 0, 0, 0);
        BoundingBox box1 = new BoundingBox(-30000000, -64, -30000000, 30000000, 319, 30000000);
        out.add(DebugSubscriptions.STRUCTURES.emptyUpdate());
        out.add(DebugSubscriptions.STRUCTURES.packUpdate(List.of()));
        out.add(DebugSubscriptions.STRUCTURES.packUpdate(
                List.of(new DebugStructureInfo(box0, List.of()))));
        out.add(DebugSubscriptions.STRUCTURES.packUpdate(List.of(
                new DebugStructureInfo(box0, List.of(new DebugStructureInfo.Piece(box0, true))),
                new DebugStructureInfo(box1, List.of(new DebugStructureInfo.Piece(box1, false),
                        new DebugStructureInfo.Piece(box0, true))))));

        // --- game event listeners: a single varint ---
        out.add(DebugSubscriptions.GAME_EVENT_LISTENERS.emptyUpdate());
        for (int radius : List.of(0, 1, 127, 128, -1)) {
            out.add(DebugSubscriptions.GAME_EVENT_LISTENERS.packUpdate(
                    new DebugGameEventListenerInfo(radius)));
        }

        // --- neighbor updates: a bare block pos ---
        out.add(DebugSubscriptions.NEIGHBOR_UPDATES.emptyUpdate());
        for (BlockPos pos : ctx.blockPos()) {
            out.add(DebugSubscriptions.NEIGHBOR_UPDATES.packUpdate(pos));
        }

        // --- game events: registry holder + Vec3 ---
        out.add(DebugSubscriptions.GAME_EVENTS.emptyUpdate());
        out.add(DebugSubscriptions.GAME_EVENTS.packUpdate(
                new DebugGameEventInfo(ctx.holder(Registries.GAME_EVENT), Vec3.ZERO)));
        out.add(DebugSubscriptions.GAME_EVENTS.packUpdate(
                new DebugGameEventInfo(ctx.holder(Registries.GAME_EVENT),
                        new Vec3(Double.MAX_VALUE, -3.5, 1.0))));

        return out;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<DebugSubscription.Update<?>> updates = updates(ctx);
        // One representative update crossed with every block position, then every update at the origin:
        // a full cross would be ~60 x 10 = 600 shapes of almost entirely redundant position bytes.
        List<Packet<?>> out = new ArrayList<>();
        DebugSubscription.Update<?> baseUpdate = updates.get(0);
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>(ctx.blockPos());
        for (BlockPos pos : positions) {
            out.add(new ClientboundDebugBlockValuePacket(pos, baseUpdate));
        }
        BlockPos basePos = new BlockPos(0, 0, 0);
        for (DebugSubscription.Update<?> update : updates) {
            out.add(new ClientboundDebugBlockValuePacket(basePos, update));
        }
        return out;
    }
}
