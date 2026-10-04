package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;

import java.util.List;

/**
 * ClientboundOpenSignEditorPacket(BlockPos pos, boolean isFrontText).
 * <p>
 * Wire: {@code writeBlockPos} (packed i64), then {@code writeBoolean} for {@code isFrontText}.
 * <p>
 * Full cartesian of both boolean values x the ten domain-correct block positions (Y sweep from the
 * packed-field extremes through the build range, plus the +-30,000,000 world-border X/Z).
 */
public class ClientboundOpenSignEditorShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundOpenSignEditorPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundOpenSignEditorPacket((BlockPos) a[0], (boolean) a[1]),
                ctx.blockPos(), ctx.bools());
    }
}
