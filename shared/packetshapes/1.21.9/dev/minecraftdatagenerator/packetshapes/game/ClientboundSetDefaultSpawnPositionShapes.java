package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;

import java.util.List;

/**
 * ClientboundSetDefaultSpawnPositionPacket(LevelData.RespawnData) — wire body is
 * {@code GlobalPos(dimension:identifier, pos:packed-BlockPos)} then {@code yaw:f32, pitch:f32}.
 * <p>
 * The record's canonical constructor is used directly (the {@code RespawnData.of} factory would wrap
 * the yaw and clamp the pitch); the stream codec writes both angles as raw floats, so the
 * domain-correct rotation spreads round-trip byte-stably.
 * Full cross: 10 block positions x 3 vanilla dimensions x 7 yaws x 5 pitches = 1050 shapes.
 */
public class ClientboundSetDefaultSpawnPositionShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetDefaultSpawnPositionPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<ResourceKey<Level>> dimensions = List.of(Level.OVERWORLD, Level.NETHER, Level.END);
        return ctx.cross(a -> {
            GlobalPos gp = GlobalPos.of(castKey(a[1]), (BlockPos) a[0]);
            return new ClientboundSetDefaultSpawnPositionPacket(
                    new LevelData.RespawnData(gp, (float) a[2], (float) a[3]));
        }, ctx.blockPos(), dimensions, ctx.rotations(), ctx.pitches());
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> castKey(Object o) {
        return (ResourceKey<Level>) o;
    }
}
