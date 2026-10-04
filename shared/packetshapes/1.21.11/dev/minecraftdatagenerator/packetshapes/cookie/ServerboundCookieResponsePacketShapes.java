package dev.minecraftdatagenerator.packetshapes.cookie;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundCookieResponsePacket(Identifier key, byte[] payload).
 * <p>
 * Wire body: key:identifier, then {@code writeNullable}: a present/absent boolean followed, when
 * present, by a varint-length-prefixed byte array (ClientboundStoreCookiePacket.PAYLOAD_STREAM_CODEC,
 * {@code ByteBufCodecs.byteArray(5120)}).
 * <p>
 * Full cross-product of the identifier spread x the payload spread. The nullable is a categorical
 * dimension, so both branches are present; the payload lengths walk the varint length-prefix
 * boundaries (0, 1, 127, 128) and the decoder's 5120-byte maximum.
 * <p>
 * Payloads longer than 5120 bytes are deliberately omitted: {@code byteArray(5120)} throws on decode.
 */
public class ServerboundCookieResponsePacketShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundCookieResponsePacket.class;
    }

    private static byte[] filled(int len) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) b[i] = (byte) i;
        return b;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<byte[]> payloads = new ArrayList<>();
        payloads.add(null);                            // absent branch
        payloads.add(new byte[0]);                     // present, length 0
        payloads.add(new byte[]{0, 1, -1, 127, -128}); // present, byte value extremes
        payloads.add(filled(127));                     // last one-byte length prefix
        payloads.add(filled(128));                     // first two-byte length prefix
        payloads.add(filled(5120));                    // decoder maximum

        List<Packet<?>> out = new ArrayList<>();
        for (Identifier key : ClientboundCookieRequestPacketShapes.keys(ctx)) {
            for (byte[] payload : payloads) {
                out.add(new ServerboundCookieResponsePacket(key, payload));
            }
        }
        return out;
    }
}
