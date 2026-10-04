package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;

import java.util.List;

/** ServerboundLockDifficultyPacket(boolean locked) — single boolean; both states. */
public class ServerboundLockDifficultyShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundLockDifficultyPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.bools(), ServerboundLockDifficultyPacket::new);
    }
}
