package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.List;

/** ServerboundCommandSuggestionPacket(int id[varint], String command[stringUtf8(32500)]) — full cross of
 *  every VarInt byte-length boundary x the {@code utf8()} string spread. */
public class ServerboundCommandSuggestionShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundCommandSuggestionPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundCommandSuggestionPacket((int) a[0], (String) a[1]),
                ctx.varInts(), ctx.utf8());
    }
}
