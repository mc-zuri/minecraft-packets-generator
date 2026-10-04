package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddTransientBlockPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundAddTransientBlockPacket(BlockPos pos, BlockState blockState).
 * <p>
 * Wire body: pos:packed-long block position, blockState:varint id from
 * {@code Block.BLOCK_STATE_REGISTRY} (an IdMapper, not a registry, so the id is a raw index).
 * <p>
 * Full cross-product of the domain-correct block positions (Y sweep plus the world-border X/Z) and a
 * block-state spread chosen to walk the state-id varint width boundaries: id 0 (air), 1, 127, 128,
 * 16383, 16384 and the highest id in the mapper, plus a couple of named multi-property states.
 */
public class ClientboundAddTransientBlockShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundAddTransientBlockPacket.class;
    }

    /** Block states covering the id-varint width boundaries plus a few named states. */
    private static List<BlockState> states() {
        List<BlockState> out = new ArrayList<>();
        int max = Block.BLOCK_STATE_REGISTRY.size() - 1;
        for (int id : List.of(0, 1, 127, 128, 16383, 16384, max)) {
            if (id < 0 || id > max) continue;
            BlockState s = Block.BLOCK_STATE_REGISTRY.byId(id);
            if (s != null && !out.contains(s)) out.add(s);
        }
        for (BlockState s : List.of(
                Blocks.AIR.defaultBlockState(),
                Blocks.STONE.defaultBlockState(),
                Blocks.OAK_STAIRS.defaultBlockState(),
                Blocks.REDSTONE_WIRE.defaultBlockState())) {
            if (!out.contains(s)) out.add(s);
        }
        return out;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundAddTransientBlockPacket((BlockPos) a[0], (BlockState) a[1]),
                ctx.blockPos(), states());
    }
}
