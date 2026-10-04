package dev.minecraftdatagenerator.packetshapes.configuration;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientboundCodeOfConductPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundCodeOfConductPacket(String codeOfConduct) — a single {@code ByteBufCodecs.STRING_UTF8}
 * field (VarInt byte-length prefix + UTF-8 bytes). Sweeps the UTF-8 spread (empty, ASCII, 2-byte,
 * 3-byte and 4-byte/astral code points) plus a long value that pushes the length prefix to two bytes.
 */
public class ClientboundCodeOfConductPacketShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundCodeOfConductPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        for (String s : ctx.utf8()) out.add(new ClientboundCodeOfConductPacket(s));
        // 200 chars -> 200 bytes -> two-byte VarInt length prefix (STRING_UTF8 allows 32767 chars).
        out.add(new ClientboundCodeOfConductPacket("a".repeat(200)));
        return out;
    }
}
