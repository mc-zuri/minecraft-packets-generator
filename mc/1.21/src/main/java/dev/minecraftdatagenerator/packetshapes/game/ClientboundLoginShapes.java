package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ClientboundLoginPacket(int playerId[i32], boolean hardcore, Set&lt;ResourceKey&lt;Level&gt;&gt; levels,
 * int maxPlayers[varint], int chunkRadius[varint], int simulationDistance[varint],
 * boolean reducedDebugInfo, boolean showDeathScreen, boolean doLimitedCrafting,
 * CommonPlayerSpawnInfo commonPlayerSpawnInfo, boolean onlineMode, boolean enforcesSecureChat).
 * <p>
 * {@code CommonPlayerSpawnInfo} is {@code (Holder&lt;DimensionType&gt; dimensionType[registry ref],
 * ResourceKey&lt;Level&gt; dimension, long seed[i64], GameType gameType[id-mapped enum],
 * &#64;Nullable GameType previousGameType[i8 nullable id, -1 when null], boolean isDebug, boolean isFlat,
 * Optional&lt;GlobalPos&gt; lastDeathLocation, int portalCooldown[varint])}. 1.21 has no trailing
 * {@code seaLevel} varint - that was added in 1.21.2 - so there is no such dimension here.
 * <p>
 * Twenty-two wire fields in total, far past a full cartesian, so the categorical dimensions that share a
 * branch (the six top-level booleans, the two nested booleans, the nullable previousGameType, the
 * lastDeathLocation Optional, and the GameType enum)
 * are swept individually against a fixed baseline, and the numeric fields get the domain-correct
 * spreads: i32 edges for {@code playerId}, i64 edges for {@code seed}, and var-int byte-length
 * boundaries for {@code maxPlayers}/{@code chunkRadius}/{@code simulationDistance}/
 * {@code portalCooldown}.
 * <p>
 * Also covered: the {@code levels} set at sizes 0/1/2/3, every {@code DimensionType} registry holder the
 * server has, the three vanilla {@code dimension} keys, {@code previousGameType} absent plus every
 * constant present, and {@code lastDeathLocation} absent plus present in each dimension with the full
 * BlockPos spread.
 */
public class ClientboundLoginShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundLoginPacket.class;
    }

    private static final List<ResourceKey<Level>> DIMENSIONS =
            List.of(Level.OVERWORLD, Level.NETHER, Level.END);

    private CommonPlayerSpawnInfo spawn(ShapeCtx ctx, Holder<DimensionType> dimType, ResourceKey<Level> dimension,
                                        GameType gameType, GameType prevGameType,
                                        boolean isDebug, boolean isFlat, Optional<GlobalPos> lastDeath,
                                        long seed, int portalCooldown) {
        // 1.21's CommonPlayerSpawnInfo has no trailing seaLevel varint; that was added in 1.21.2.
        return new CommonPlayerSpawnInfo(dimType, dimension, seed, gameType, prevGameType,
                isDebug, isFlat, lastDeath, portalCooldown);
    }

    private Packet<?> login(Set<ResourceKey<Level>> levels, CommonPlayerSpawnInfo spawn) {
        return new ClientboundLoginPacket(0, false, levels, 20, 10, 10, false, true, false, spawn, true);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        Registry<DimensionType> dimTypeReg = ctx.registries.registryOrThrow(Registries.DIMENSION_TYPE);
        List<Holder<DimensionType>> dimTypes = new ArrayList<>();
        dimTypeReg.holders().limit(4).forEach(dimTypes::add);
        Holder<DimensionType> baseDimType = dimTypes.get(0);

        Set<ResourceKey<Level>> lvl1 = Set.of(Level.OVERWORLD);
        CommonPlayerSpawnInfo base = spawn(ctx, baseDimType, Level.OVERWORLD, GameType.SURVIVAL,
                null, false, false, Optional.empty(), 0L, 0);

        // playerId (plain i32)
        for (int id : ctx.ints())
            out.add(new ClientboundLoginPacket(id, false, lvl1, 20, 10, 10, false, true, false, base, true));

        // every top-level boolean, both states
        for (boolean b : ctx.bools()) {
            out.add(new ClientboundLoginPacket(0, b, lvl1, 20, 10, 10, false, true, false, base, true));
            out.add(new ClientboundLoginPacket(0, false, lvl1, 20, 10, 10, b, true, false, base, true));
            out.add(new ClientboundLoginPacket(0, false, lvl1, 20, 10, 10, false, b, false, base, true));
            out.add(new ClientboundLoginPacket(0, false, lvl1, 20, 10, 10, false, true, b, base, true));
            // 1.21 has no onlineMode component; enforcesSecureChat is the last boolean.
            out.add(new ClientboundLoginPacket(0, false, lvl1, 20, 10, 10, false, true, false, base, b));
        }

        // levels set: 0, 1, 2 and 3 entries
        List<Set<ResourceKey<Level>>> levelSets = List.of(
                Set.of(),
                Set.of(Level.OVERWORLD),
                Set.of(Level.OVERWORLD, Level.NETHER),
                new LinkedHashSet<>(DIMENSIONS));
        for (Set<ResourceKey<Level>> levels : levelSets)
            out.add(login(levels, base));

        // maxPlayers / chunkRadius / simulationDistance: var-int boundaries
        for (int c : ctx.varInts()) {
            out.add(new ClientboundLoginPacket(0, false, lvl1, c, 10, 10, false, true, false, base, true));
            out.add(new ClientboundLoginPacket(0, false, lvl1, 20, c, 10, false, true, false, base, true));
            out.add(new ClientboundLoginPacket(0, false, lvl1, 20, 10, c, false, true, false, base, true));
        }

        // --- CommonPlayerSpawnInfo -------------------------------------------------------------

        // dimensionType: every registry holder the server knows (registry-reference form only)
        for (Holder<DimensionType> dt : dimTypes)
            out.add(login(lvl1, spawn(ctx, dt, Level.OVERWORLD, GameType.SURVIVAL, null,
                    false, false, Optional.empty(), 0L, 0)));
        // dimension key
        for (ResourceKey<Level> dim : DIMENSIONS)
            out.add(login(lvl1, spawn(ctx, baseDimType, dim, GameType.SURVIVAL, null,
                    false, false, Optional.empty(), 0L, 0)));
        // gameType: every constant
        for (GameType gt : ctx.enumAll(GameType.class))
            out.add(login(lvl1, spawn(ctx, baseDimType, Level.OVERWORLD, gt, null,
                    false, false, Optional.empty(), 0L, 0)));
        // previousGameType: absent, plus every constant present
        out.add(login(lvl1, base));
        for (GameType gt : ctx.enumAll(GameType.class))
            out.add(login(lvl1, spawn(ctx, baseDimType, Level.OVERWORLD, GameType.SURVIVAL, gt,
                    false, false, Optional.empty(), 0L, 0)));
        // isDebug / isFlat
        for (boolean b : ctx.bools()) {
            out.add(login(lvl1, spawn(ctx, baseDimType, Level.OVERWORLD, GameType.SURVIVAL, null,
                    b, false, Optional.empty(), 0L, 0)));
            out.add(login(lvl1, spawn(ctx, baseDimType, Level.OVERWORLD, GameType.SURVIVAL, null,
                    false, b, Optional.empty(), 0L, 0)));
        }
        // lastDeathLocation: absent, plus present across dimensions and the BlockPos spread
        out.add(login(lvl1, base));
        for (ResourceKey<Level> dim : DIMENSIONS)
            out.add(login(lvl1, spawn(ctx, baseDimType, Level.OVERWORLD, GameType.SURVIVAL, null,
                    false, false, Optional.of(GlobalPos.of(dim, new BlockPos(1, -64, 2))), 0L, 0)));
        for (BlockPos pos : ctx.blockPos())
            out.add(login(lvl1, spawn(ctx, baseDimType, Level.OVERWORLD, GameType.SURVIVAL, null,
                    false, false, Optional.of(GlobalPos.of(Level.OVERWORLD, pos)), 0L, 0)));
        // seed (i64)
        for (long seed : ctx.longs())
            out.add(login(lvl1, spawn(ctx, baseDimType, Level.OVERWORLD, GameType.SURVIVAL, null,
                    false, false, Optional.empty(), seed, 0)));
        // portalCooldown: var-int boundaries
        for (int c : ctx.varInts())
            out.add(login(lvl1, spawn(ctx, baseDimType, Level.OVERWORLD, GameType.SURVIVAL, null,
                    false, false, Optional.empty(), 0L, c)));
        return out;
    }
}
