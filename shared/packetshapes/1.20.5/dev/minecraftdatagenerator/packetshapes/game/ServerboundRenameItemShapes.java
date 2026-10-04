package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;

import java.util.List;

/**
 * ServerboundRenameItemPacket(String name[utf]) — one field, {@code writeUtf}/{@code readUtf} with the
 * default 32767 limit. Swept over the UTF-8 spread (empty, ASCII, 2-byte, 3-byte and 4-byte astral
 * code points) so the byte-vs-char length prefix is exercised.
 */
public class ServerboundRenameItemShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundRenameItemPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.utf8(), ServerboundRenameItemPacket::new);
    }
}
