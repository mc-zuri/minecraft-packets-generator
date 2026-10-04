package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDebugChunkValuePacket;
import net.minecraft.util.debug.DebugSubscription;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundDebugChunkValuePacket(ChunkPos chunkPos, DebugSubscription.Update&lt;?&gt; update) — the same
 * registry-dispatched Update discriminator as the block-value variant, with a packed-i64 ChunkPos in
 * front instead of a BlockPos.
 * <p>
 * The update spread (every DEBUG_SUBSCRIPTION branch that has a value codec, in both the absent and the
 * present branch of the dispatched optional, with the inner optionals / list sizes / enums of each value
 * record exercised) is shared with {@link ClientboundDebugBlockValueShapes#updates} so the two providers
 * cannot drift apart. It is crossed here with the full chunk-coordinate corner spread (X x Z).
 * <p>
 * OMITTED: {@code minecraft:dedicated_server_tick_time} has a {@code null} value codec, so vanilla can
 * never encode it as an Update; it is deliberately excluded.
 */
public class ClientboundDebugChunkValueShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundDebugChunkValuePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<DebugSubscription.Update<?>> updates = ClientboundDebugBlockValueShapes.updates(ctx);

        List<Packet<?>> out = new ArrayList<>();

        // one representative update crossed with every chunk-coordinate corner (X x Z)
        DebugSubscription.Update<?> baseUpdate = updates.get(0);
        for (int x : ctx.chunkCoords()) {
            for (int z : ctx.chunkCoords()) {
                out.add(new ClientboundDebugChunkValuePacket(new ChunkPos(x, z), baseUpdate));
            }
        }

        // every update branch at the origin chunk
        for (DebugSubscription.Update<?> update : updates) {
            out.add(new ClientboundDebugChunkValuePacket(ChunkPos.ZERO, update));
        }
        return out;
    }
}
