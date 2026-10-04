package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ClientboundServerDataPacket(Component motd, Optional&lt;byte[]&gt; iconBytes) — {@code motd:nbt component}
 * then a {@code present:bool} prefixed {@code iconBytes:varint length + raw bytes}.
 * <p>
 * Full cross of the component variants with the optional icon: absent, zero-length, one byte, a few bytes
 * and a 300-byte payload (which pushes the length prefix past the one-byte VarInt boundary).
 */
public class ClientboundServerDataShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundServerDataPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        byte[] large = new byte[300];
        for (int i = 0; i < large.length; i++) large[i] = (byte) i;

        List<Optional<byte[]>> icons = List.of(
                Optional.empty(),
                Optional.of(new byte[0]),
                Optional.of(new byte[]{0}),
                Optional.of(new byte[]{1, 2, 3}),
                Optional.of(large));
        List<Packet<?>> out = new ArrayList<>();
        for (Component motd : ctx.components()) {
            for (Optional<byte[]> icon : icons) {
                out.add(new ClientboundServerDataPacket(motd, icon));
            }
        }
        return out;
    }
}
