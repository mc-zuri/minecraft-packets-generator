package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatPacket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/**
 * ServerboundChatPacket(String message, Instant timeStamp, long salt, Optional&lt;MessageSignature&gt; signature,
 * LastSeenMessages.Update lastSeenMessages).
 * <p>
 * Wire body: {@code message:stringUtf8(256), timeStamp:i64(epochMilli), salt:i64,
 * signature:optional(bool + 256 raw bytes), lastSeen:{offset:varint, acknowledged:fixedBitSet(20) = 3
 * bytes, checksum:i8}}.
 * <p>
 * Coverage:
 * <ul>
 *   <li>categorical cartesian: {@code signature} absent/present x five acknowledged-bitset shapes
 *       (empty, lowest bit, highest in-range bit 19, all 20 bits, a scattered mix) x the byte edge cases
 *       for {@code checksum};</li>
 *   <li>{@code message} over the full {@code utf8()} spread;</li>
 *   <li>{@code salt} and {@code timeStamp} (an {@code i64} epoch-milli) over the long edge cases;</li>
 *   <li>{@code lastSeen.offset} over every VarInt byte-length boundary.</li>
 * </ul>
 * Bits &gt;= 20 are never set: {@code fixedBitSet(20)} silently truncates them, so such a shape would not
 * survive the round trip.
 */
public class ServerboundChatShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundChatPacket.class;
    }

    private static BitSet bits(int... set) {
        BitSet b = new BitSet();
        for (int i : set) b.set(i);
        return b;
    }

    private static List<BitSet> ackSets() {
        int[] all = new int[20];
        for (int i = 0; i < 20; i++) all[i] = i;
        return List.of(bits(), bits(0), bits(19), bits(all), bits(1, 2, 4, 8, 16));
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        Instant baseTime = Instant.ofEpochMilli(0L);
        LastSeenMessages.Update baseSeen = new LastSeenMessages.Update(0, bits(), (byte) 0);
        Optional<MessageSignature> noSig = Optional.empty();
        Optional<MessageSignature> sig = Optional.of(new MessageSignature(new byte[MessageSignature.BYTES]));

        // Categorical cartesian: optional signature x acknowledged bitset x checksum byte.
        for (Optional<MessageSignature> s : List.of(noSig, sig)) {
            for (BitSet ack : ackSets()) {
                for (byte checksum : ctx.bytes()) {
                    out.add(new ServerboundChatPacket("x", baseTime, 0L, s,
                            new LastSeenMessages.Update(0, ack, checksum)));
                }
            }
        }

        // message over the full UTF-8 spread.
        for (String message : ctx.utf8()) {
            out.add(new ServerboundChatPacket(message, baseTime, 0L, noSig, baseSeen));
        }

        // salt over long edges.
        for (long salt : ctx.longs()) {
            out.add(new ServerboundChatPacket("x", baseTime, salt, noSig, baseSeen));
        }

        // timeStamp is an i64 epoch-milli: sweep the same long edges.
        for (long millis : ctx.longs()) {
            out.add(new ServerboundChatPacket("x", Instant.ofEpochMilli(millis), 0L, noSig, baseSeen));
        }

        // lastSeen.offset over every VarInt byte-length boundary.
        for (int offset : ctx.varInts()) {
            out.add(new ServerboundChatPacket("x", baseTime, 0L, sig,
                    new LastSeenMessages.Update(offset, bits(0, 3, 19), (byte) 1)));
        }

        return out;
    }
}
