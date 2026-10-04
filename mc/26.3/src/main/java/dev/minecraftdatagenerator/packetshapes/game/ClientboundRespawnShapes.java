package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ClientboundRespawnPacket(CommonPlayerSpawnInfo info, byte dataToKeep).
 * <p>
 * Wire fields: {@code dimensionType:registry varint, dimension:identifier, seed:i64,
 * gameType:i8-id, previousGameType:optional varint, isDebug:bool, isFlat:bool,
 * lastDeathLocation:optional (identifier + packed position), portalCooldown:varint, seaLevel:varint},
 * then {@code dataToKeep:i8}.
 * <p>
 * Categorical cartesian (rule 1): every {@link GameType} x {@code previousGameType} absent plus every
 * GameType x {@code isDebug} x {@code isFlat} x {@code lastDeathLocation} absent/present x every value of
 * the two-bit {@code dataToKeep} flag field (none, KEEP_ATTRIBUTE_MODIFIERS, KEEP_ENTITY_DATA,
 * KEEP_ALL_DATA). Per-field sweeps follow for the dimension key, the seed, the portal cooldown varint,
 * the sea level and the death-location block position.
 */
public class ClientboundRespawnShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundRespawnPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Holder<DimensionType> dimType = ctx.holder(Registries.DIMENSION_TYPE);
        GlobalPos deathPresent = GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 64, 0));
        List<Optional<GlobalPos>> deaths = List.of(Optional.empty(), Optional.of(deathPresent));

        List<Optional<GameType>> prevGameTypes = new ArrayList<>();
        prevGameTypes.add(Optional.empty());
        for (GameType gt : ctx.enumAll(GameType.class)) prevGameTypes.add(Optional.of(gt));

        byte[] keeps = {0, ClientboundRespawnPacket.KEEP_ATTRIBUTE_MODIFIERS,
                ClientboundRespawnPacket.KEEP_ENTITY_DATA, ClientboundRespawnPacket.KEEP_ALL_DATA};

        List<Packet<?>> out = new ArrayList<>();
        for (GameType gameType : ctx.enumAll(GameType.class)) {
            for (Optional<GameType> prev : prevGameTypes) {
                for (Optional<GlobalPos> death : deaths) {
                    for (boolean isDebug : ctx.bools()) {
                        for (boolean isFlat : ctx.bools()) {
                            for (byte keep : keeps) {
                                out.add(new ClientboundRespawnPacket(new CommonPlayerSpawnInfo(
                                        dimType, Level.OVERWORLD, 0L, gameType, prev,
                                        isDebug, isFlat, death, 0, 63), keep));
                            }
                        }
                    }
                }
            }
        }

        // Per-field sweeps for the non-categorical fields, everything else at a baseline.
        for (ResourceKey<Level> dimension : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            out.add(new ClientboundRespawnPacket(new CommonPlayerSpawnInfo(
                    dimType, dimension, 0L, GameType.SURVIVAL, Optional.empty(),
                    false, false, Optional.empty(), 0, 63), (byte) 0));
        }
        for (long seed : ctx.longs()) {
            out.add(new ClientboundRespawnPacket(new CommonPlayerSpawnInfo(
                    dimType, Level.OVERWORLD, seed, GameType.SURVIVAL, Optional.empty(),
                    false, false, Optional.empty(), 0, 63), (byte) 0));
        }
        for (int portalCooldown : ctx.varInts()) {
            out.add(new ClientboundRespawnPacket(new CommonPlayerSpawnInfo(
                    dimType, Level.OVERWORLD, 0L, GameType.SURVIVAL, Optional.empty(),
                    false, false, Optional.empty(), portalCooldown, 63), (byte) 0));
        }
        for (int seaLevel : ctx.buildYs()) {
            out.add(new ClientboundRespawnPacket(new CommonPlayerSpawnInfo(
                    dimType, Level.OVERWORLD, 0L, GameType.SURVIVAL, Optional.empty(),
                    false, false, Optional.empty(), 0, seaLevel), (byte) 0));
        }
        for (BlockPos pos : ctx.blockPos()) {
            out.add(new ClientboundRespawnPacket(new CommonPlayerSpawnInfo(
                    dimType, Level.OVERWORLD, 0L, GameType.SURVIVAL, Optional.empty(),
                    false, false, Optional.of(GlobalPos.of(Level.NETHER, pos)), 0, 63), (byte) 0));
        }
        return out;
    }
}
