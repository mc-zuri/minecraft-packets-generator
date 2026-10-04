package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetDefaultSpawnPositionPacket(BlockPos pos, float angle) — wire body is
 * {@code pos:packed-BlockPos} then {@code angle:f32}.
 * <p>
 * Full cross of the block-position corner cases with the domain-correct yaw spread, plus a raw f32
 * edge-case sweep on the angle.
 * <p>
 * NOTE: 1.21.8 carries a bare {@code BlockPos} + single angle; the {@code LevelData.RespawnData}
 * wrapper (a GlobalPos with a dimension plus separate yaw and pitch) is a 26.1 addition, so there is
 * no dimension or pitch dimension here.
 */
public class ClientboundSetDefaultSpawnPositionShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetDefaultSpawnPositionPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundSetDefaultSpawnPositionPacket((BlockPos) a[0], (float) a[1]),
                ctx.blockPos(), ctx.rotations()));
        // Raw f32 edge cases on the angle.
        for (float f : ctx.floats()) {
            out.add(new ClientboundSetDefaultSpawnPositionPacket(BlockPos.ZERO, f));
        }
        return out;
    }
}
