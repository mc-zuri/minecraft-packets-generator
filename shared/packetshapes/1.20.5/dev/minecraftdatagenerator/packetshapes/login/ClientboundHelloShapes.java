package dev.minecraftdatagenerator.packetshapes.login;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundHelloPacket(String serverId, byte[] publicKey, byte[] challenge, boolean shouldAuthenticate).
 * <p>
 * Wire: {@code writeUtf(serverId)} (read back with a 20-char cap), {@code byteArray(publicKey)},
 * {@code byteArray(challenge)} (each a varint length followed by the raw bytes), {@code bool}.
 * <p>
 * Full cartesian: serverId over the UTF-8 corner cases (all within the 20-char cap) x both byte
 * arrays over empty / small / signed-byte / 200-byte (2-byte varint length) forms x both boolean
 * states. 6 x 4 x 4 x 2 = 192 shapes.
 */
public class ClientboundHelloShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundHelloPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        byte[] big = new byte[200];
        for (int i = 0; i < big.length; i++) big[i] = (byte) i;
        List<byte[]> arrays = List.of(new byte[]{}, new byte[]{1, 2, 3}, new byte[]{(byte) -1, 0, 127}, big);

        List<Packet<?>> out = new ArrayList<>();
        // every utf8() value is <= 20 chars, the decoder's cap for this field
        for (String serverId : ctx.utf8()) {
            for (byte[] publicKey : arrays) {
                for (byte[] challenge : arrays) {
                    for (boolean shouldAuthenticate : ctx.bools()) {
                        out.add(new ClientboundHelloPacket(serverId, publicKey, challenge, shouldAuthenticate));
                    }
                }
            }
        }
        return out;
    }
}
