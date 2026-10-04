package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundDeleteChatPacket(MessageSignature.Packed messageSignature).
 * <p>
 * {@code Packed} is a discriminated union on the wire: it writes {@code id + 1} as a var-int, and when
 * the decoded id is {@code -1 (FULL_SIGNATURE)} the full {@code MessageSignature.BYTES == 256} raw bytes
 * follow; otherwise nothing follows.
 * <p>
 * Covered: the cached-id branch swept across the var-int byte-length boundaries of the {@code id + 1}
 * field (so 0, 126, 127, 16382, 16383, ... on the wire), and the full-signature branch with several
 * distinct 256-byte payloads.
 * <p>
 * OMITTED: {@code id = -1} in the cached branch — that is exactly the {@code FULL_SIGNATURE} marker, so
 * a {@code Packed(-1, null)} encodes a var-int 0 and the decoder would then try to read 256 bytes that
 * were never written. Likewise {@code Integer.MAX_VALUE} is skipped because {@code id + 1} overflows.
 */
public class ClientboundDeleteChatShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundDeleteChatPacket.class;
    }

    private static byte[] signature(int fill) {
        byte[] bytes = new byte[MessageSignature.BYTES];
        if (fill == -2) {
            for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        } else {
            java.util.Arrays.fill(bytes, (byte) fill);
        }
        return bytes;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        // cached-id branch: var-int boundaries of the written (id + 1) value
        for (int id : List.of(0, 1, 2, 126, 127, 128, 16382, 16383, 16384,
                2097150, 2097151, 2097152, 268435454, 268435455, 268435456)) {
            out.add(new ClientboundDeleteChatPacket(new MessageSignature.Packed(id)));
        }

        // full-signature branch: exactly 256 bytes, several distinct payloads
        for (int fill : List.of(0, -1, 127, -2)) {
            out.add(new ClientboundDeleteChatPacket(
                    new MessageSignature.Packed(new MessageSignature(signature(fill)))));
        }
        return out;
    }
}
