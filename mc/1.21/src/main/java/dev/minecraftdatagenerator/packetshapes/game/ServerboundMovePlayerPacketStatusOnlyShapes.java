package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.List;

/** ServerboundMovePlayerPacket.StatusOnly(boolean onGround) — wire body is a single {@code u8}
 *  carrying {@code onGround} as 0 or 1; both values emitted. The {@code horizontalCollision} bit was
 *  added to that byte in 1.21.2, so 1.21 has only the one flag. */
public class ServerboundMovePlayerPacketStatusOnlyShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundMovePlayerPacket.StatusOnly.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.bools(), ServerboundMovePlayerPacket.StatusOnly::new);
    }
}
