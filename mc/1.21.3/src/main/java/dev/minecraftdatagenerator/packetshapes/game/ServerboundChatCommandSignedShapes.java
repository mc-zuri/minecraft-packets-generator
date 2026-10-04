package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * ServerboundChatCommandSignedPacket(String command, Instant timeStamp, long salt,
 * ArgumentSignatures argumentSignatures, LastSeenMessages.Update lastSeenMessages).
 * <p>
 * Wire body: {@code command:stringUtf8(32767), timeStamp:i64(epochMilli), salt:i64,
 * argumentSignatures:list(max 8) of {name:stringUtf8(16), signature:256 raw bytes},
 * lastSeen:{offset:varint, acknowledged:fixedBitSet(20) = 3 bytes}}. There is no trailing
 * {@code checksum:i8} on the acknowledgement in 1.21.3 -- it was added in 1.21.5 -- so that dimension
 * does not exist here.
 * <p>
 * Coverage:
 * <ul>
 *   <li>categorical cartesian: argument-signature list sizes 0/1/2 x five acknowledged-bitset shapes
 *       (empty, lowest bit, highest in-range bit 19, all 20 bits, a scattered mix);</li>
 *   <li>{@code command} over the full {@code utf8()} spread;</li>
 *   <li>{@code salt} and {@code timeStamp} (an {@code i64} epoch-milli) over the long edge cases;</li>
 *   <li>{@code lastSeen.offset} over every VarInt byte-length boundary;</li>
 *   <li>the argument-name string over the {@code utf8()} spread (all of which fit the 16-char cap) and
 *       the list at its documented maximum of 8 entries.</li>
 * </ul>
 * Every {@code MessageSignature} is exactly {@link MessageSignature#BYTES} (256) bytes, and no
 * acknowledged bit &gt;= 20 is set, because {@code fixedBitSet(20)} would truncate it.
 */
public class ServerboundChatCommandSignedShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundChatCommandSignedPacket.class;
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

    private static MessageSignature signature(byte fill) {
        byte[] bytes = new byte[MessageSignature.BYTES];
        java.util.Arrays.fill(bytes, fill);
        return new MessageSignature(bytes);
    }

    private static ArgumentSignatures argSigs(int count) {
        List<ArgumentSignatures.Entry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(new ArgumentSignatures.Entry("arg" + i, signature((byte) i)));
        }
        return new ArgumentSignatures(entries);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        Instant baseTime = Instant.ofEpochMilli(0L);
        LastSeenMessages.Update baseSeen = new LastSeenMessages.Update(0, bits());
        ArgumentSignatures noArgs = ArgumentSignatures.EMPTY;

        // Categorical cartesian: arg-signature count x acknowledged bitset.
        for (int count : ctx.counts()) {
            for (BitSet ack : ackSets()) {
                out.add(new ServerboundChatCommandSignedPacket("cmd", baseTime, 0L, argSigs(count),
                        new LastSeenMessages.Update(0, ack)));
            }
        }

        // command over the full UTF-8 spread.
        for (String command : ctx.utf8()) {
            out.add(new ServerboundChatCommandSignedPacket(command, baseTime, 0L, noArgs, baseSeen));
        }

        // salt over long edges.
        for (long salt : ctx.longs()) {
            out.add(new ServerboundChatCommandSignedPacket("cmd", baseTime, salt, noArgs, baseSeen));
        }

        // timeStamp is an i64 epoch-milli: sweep the same long edges.
        for (long millis : ctx.longs()) {
            out.add(new ServerboundChatCommandSignedPacket("cmd", Instant.ofEpochMilli(millis), 0L, noArgs, baseSeen));
        }

        // lastSeen.offset over every VarInt byte-length boundary.
        for (int offset : ctx.varInts()) {
            out.add(new ServerboundChatCommandSignedPacket("cmd", baseTime, 0L, argSigs(1),
                    new LastSeenMessages.Update(offset, bits(0, 3, 19))));
        }

        // Argument name over the UTF-8 spread (every value is within the 16-char cap).
        for (String name : ctx.utf8()) {
            out.add(new ServerboundChatCommandSignedPacket("cmd", baseTime, 0L,
                    new ArgumentSignatures(List.of(new ArgumentSignatures.Entry(name, signature((byte) 0)))),
                    baseSeen));
        }

        // The list at its documented maximum of 8 entries.
        out.add(new ServerboundChatCommandSignedPacket("cmd", baseTime, 0L, argSigs(8), baseSeen));

        return out;
    }
}
