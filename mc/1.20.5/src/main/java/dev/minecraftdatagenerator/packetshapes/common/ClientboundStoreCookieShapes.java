package dev.minecraftdatagenerator.packetshapes.common;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundStoreCookiePacket(ResourceLocation key, byte[] payload) — {@code writeResourceLocation} followed by
 * {@code ByteBufCodecs.byteArray(5120)} (VarInt length + raw bytes). Full cartesian of the identifier
 * variants (default and explicit namespace, short and long paths) against the payload length
 * corner cases: 0, 1, 2, 127/128 (VarInt length-prefix boundary) and the 5120-byte maximum.
 * Payloads above 5120 bytes are omitted: the decoder rejects them.
 */
public class ClientboundStoreCookieShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundStoreCookiePacket.class;
    }

    private static byte[] filled(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) i;
        return b;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<byte[]> payloads = List.of(
                new byte[0],
                new byte[]{0},
                new byte[]{(byte) -1, (byte) 127},
                filled(127),   // last one-byte VarInt length
                filled(128),   // first two-byte VarInt length
                filled(5120)); // MAX_PAYLOAD_SIZE
        List<ResourceLocation> keys = List.of(
                ctx.id("cookie"),
                ctx.id("a"),
                new ResourceLocation("x", "y"),
                new ResourceLocation("some_namespace", "some/nested/path"));
        List<Packet<?>> out = new ArrayList<>();
        for (ResourceLocation k : keys) {
            for (byte[] p : payloads) {
                out.add(new ClientboundStoreCookiePacket(k, p));
            }
        }
        return out;
    }
}
