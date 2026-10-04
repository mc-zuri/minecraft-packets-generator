package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.List;

/** ServerboundMovePlayerPacket.StatusOnly(boolean onGround, boolean horizontalCollision) — wire body is
 *  a single flags byte; full cross of both booleans. */
public class ServerboundMovePlayerPacketStatusOnlyShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundMovePlayerPacket.StatusOnly.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundMovePlayerPacket.StatusOnly((boolean) a[0], (boolean) a[1]),
                ctx.bools(), ctx.bools());
    }
}
