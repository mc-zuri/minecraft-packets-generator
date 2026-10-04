package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * ClientboundBlockUpdatePacket(BlockPos pos, BlockState blockState) — {@code BlockPos.STREAM_CODEC}
 * (a packed i64) plus {@code ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY)} (a VarInt state id).
 * <p>
 * Full cross of the domain-correct block-position spread with a block-state spread chosen to straddle
 * every VarInt byte-length boundary of the state id (ids 0/1, 127/128, 16383/16384 and the very last
 * registered state), plus a couple of named states. Ids are taken from the live
 * {@code BLOCK_STATE_REGISTRY} rather than fabricated, so every one round-trips.
 */
public class ClientboundBlockUpdateShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundBlockUpdatePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        LinkedHashSet<BlockState> states = new LinkedHashSet<>();
        states.add(Blocks.AIR.defaultBlockState());
        states.add(Blocks.STONE.defaultBlockState());
        states.add(Blocks.OAK_STAIRS.defaultBlockState());

        int size = Block.BLOCK_STATE_REGISTRY.size();
        for (int id : new int[]{0, 1, 127, 128, 16383, 16384, size - 1}) {
            if (id >= 0 && id < size) {
                BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
                if (state != null) states.add(state);
            }
        }

        return ctx.cross(
                a -> new ClientboundBlockUpdatePacket((BlockPos) a[0], (BlockState) a[1]),
                ctx.blockPos(), new ArrayList<>(states));
    }
}
