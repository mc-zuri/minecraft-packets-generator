package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatPacket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * ServerboundChatPacket(String message, Instant timeStamp, long salt, &#64;Nullable MessageSignature signature,
 * LastSeenMessages.Update lastSeenMessages).
 * <p>
 * Wire body: {@code message:stringUtf8(256), timeStamp:i64(epochMilli), salt:i64,
 * signature:nullable(bool + 256 raw bytes), lastSeen:{offset:varint, acknowledged:fixedBitSet(20) = 3
 * bytes}}. There is no trailing {@code checksum:i8} on the acknowledgement in 1.21 -- it was added
 * in 1.21.5 -- so that dimension does not exist here.
 * <p>
 * Coverage:
 * <ul>
 *   <li>categorical cartesian: {@code signature} null/present x five acknowledged-bitset shapes
 *       (empty, lowest bit, highest in-range bit 19, all 20 bits, a scattered mix);</li>
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
        LastSeenMessages.Update baseSeen = new LastSeenMessages.Update(0, bits());
        // Nullable in 1.21; the wire still carries the boolean presence prefix.
        MessageSignature noSig = null;
        MessageSignature sig = new MessageSignature(new byte[MessageSignature.BYTES]);

        // Categorical cartesian: nullable signature x acknowledged bitset.
        for (MessageSignature s : Arrays.asList(noSig, sig)) {
            for (BitSet ack : ackSets()) {
                out.add(new ServerboundChatPacket("x", baseTime, 0L, s,
                        new LastSeenMessages.Update(0, ack)));
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
                    new LastSeenMessages.Update(offset, bits(0, 3, 19))));
        }

        return out;
    }
}
