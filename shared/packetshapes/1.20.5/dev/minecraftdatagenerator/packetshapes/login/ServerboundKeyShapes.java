package dev.minecraftdatagenerator.packetshapes.login;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Opaque encrypted-body fixtures, decoded by vanilla to avoid random ciphertext on resume. */
public class ServerboundKeyShapes implements PacketShapes {
    @Override public Class<? extends Packet<?>> packetType() { return ServerboundKeyPacket.class; }
    @Override public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        for (byte pattern : new byte[]{0, -1}) {
            byte[] encrypted = new byte[128]; Arrays.fill(encrypted, pattern);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeByteArray(encrypted); buffer.writeByteArray(encrypted);
                out.add(ServerboundKeyPacket.STREAM_CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
        return out;
    }
}
