package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

import java.util.List;

/** ServerboundChatCommandPacket(String command) — single {@code writeUtf(32767)} field; the full
 *  {@code utf8()} spread (empty, ASCII, 2-byte, 3-byte and 4-byte astral code points). */
public class ServerboundChatCommandShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundChatCommandPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.utf8(), ServerboundChatCommandPacket::new);
    }
}
