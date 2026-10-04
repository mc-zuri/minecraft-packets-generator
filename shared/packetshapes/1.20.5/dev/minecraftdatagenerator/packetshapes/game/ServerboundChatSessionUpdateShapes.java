package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.world.entity.player.ProfilePublicKey;

import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ServerboundChatSessionUpdatePacket(RemoteChatSession.Data chatSession).
 * <p>
 * Wire body: {@code sessionId:UUID(2 x i64)}, then ProfilePublicKey.Data
 * {@code expiresAt:i64(epochMilli), key:publicKey(varint-prefixed X.509 bytes),
 * keySignature:byteArray(max 4096)}.
 * <p>
 * Coverage: {@code sessionId} over the UUID corner cases; {@code expiresAt} over the long edge cases
 * (the field is a plain {@code i64} epoch-milli); and {@code keySignature} at lengths 0, 1, 64, 256 and
 * the codec's documented maximum of 4096 — larger arrays are omitted because
 * {@code ByteBufCodecs.byteArray(4096)} throws on encode. The public key is a freshly generated 2048-bit
 * RSA key (what {@code ByteBufCodecs.PUBLIC_KEY} expects); a second 1024-bit key is also used so the
 * varint-prefixed key-blob length varies on the wire.
 */
public class ServerboundChatSessionUpdateShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundChatSessionUpdatePacket.class;
    }

    private static PublicKey rsaKey(int size) {
        try {
            return dev.minecraftdatagenerator.packetgen.StaticFixtures.rsaPublicKey(size);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ServerboundChatSessionUpdatePacket packet(UUID session, Instant expiresAt,
                                                             PublicKey key, byte[] signature) {
        return new ServerboundChatSessionUpdatePacket(new RemoteChatSession.Data(
                session, new ProfilePublicKey.Data(expiresAt, key, signature)));
    }

    private static byte[] sig(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[i] = (byte) i;
        return bytes;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        PublicKey key2048 = rsaKey(2048);
        PublicKey key1024 = rsaKey(1024);

        Instant baseExpiry = Instant.ofEpochMilli(0L);
        byte[] baseSig = sig(4);
        UUID baseSession = new UUID(1L, 2L);

        // sessionId over the UUID corner cases.
        for (UUID session : ctx.uuids()) {
            out.add(packet(session, baseExpiry, key2048, baseSig));
        }

        // expiresAt is an i64 epoch-milli: sweep the long edge cases.
        for (long millis : ctx.longs()) {
            out.add(packet(baseSession, Instant.ofEpochMilli(millis), key2048, baseSig));
        }

        // keySignature length: 0, 1, 64, 256 and the codec maximum (4096).
        for (int length : List.of(0, 1, 64, 256, 4096)) {
            out.add(packet(baseSession, baseExpiry, key2048, sig(length)));
        }

        // Key-blob length variation (1024-bit vs 2048-bit X.509 encoding).
        out.add(packet(baseSession, baseExpiry, key1024, baseSig));

        return out;
    }
}
