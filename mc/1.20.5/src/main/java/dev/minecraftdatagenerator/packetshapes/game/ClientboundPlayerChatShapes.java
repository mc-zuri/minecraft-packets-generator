package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ClientboundPlayerChatPacket(UUID sender, int index[varint], &#64;Nullable MessageSignature signature,
 * SignedMessageBody.Packed body, &#64;Nullable Component unsignedContent, FilterMask filterMask,
 * ChatType.Bound chatType).
 * <p>
 * NOTE: 1.20.5 has no leading {@code globalIndex} var-int -- that was added in 1.21.5 -- so
 * {@code index} is the only index field swept here.
 * <p>
 * Sub-structures:
 * <ul>
 *   <li>{@code signature}: nullable — a bool presence prefix then exactly {@code MessageSignature.BYTES}
 *       (256) raw bytes.</li>
 *   <li>{@code body} = {@code stringUtf8(256) content, i64 timeStamp[millis], i64 salt,
 *       LastSeenMessages.Packed lastSeen}; {@code lastSeen} is a list capped at 20 of
 *       {@code MessageSignature.Packed}, each written as {@code varint (id + 1)} and, when that is 0,
 *       a full 256-byte signature.</li>
 *   <li>{@code unsignedContent}: nullable — a bool presence prefix then a trusted Component.</li>
 *   <li>{@code filterMask}: a varint type id — 0 PASS_THROUGH, 1 FULLY_FILTERED, 2 PARTIALLY_FILTERED
 *       followed by a BitSet (long array).</li>
 *   <li>{@code chatType} = {@code registry-id chatType, Component name, Optional&lt;Component&gt; targetName}.</li>
 * </ul>
 * Coverage: full cartesian of every categorical dimension — signature null/present (2) x lastSeen form (4)
 * x unsignedContent null/present (2) x filterMask branch (4, including a PARTIALLY_FILTERED mask with bits
 * actually set, which is the only way to get a non-empty BitSet on the wire) x targetName Optional (2)
 * = 128 shapes — plus per-field sweeps of index (VarInt boundaries), sender UUID, content
 * (UTF-8 length-prefix spread), timeStamp and salt (i64 corners), the name/targetName component
 * spreads and every registered chat type.
 */
public class ClientboundPlayerChatShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlayerChatPacket.class;
    }

    private static SignedMessageBody.Packed body(String content, long millis, long salt, LastSeenMessages.Packed lastSeen) {
        return new SignedMessageBody.Packed(content, Instant.ofEpochMilli(millis), salt, lastSeen);
    }

    private static MessageSignature sig(byte fill) {
        byte[] bytes = new byte[MessageSignature.BYTES];
        java.util.Arrays.fill(bytes, fill);
        return new MessageSignature(bytes);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        UUID sender = new UUID(1L, 2L);
        MessageSignature sig = sig((byte) 0);
        MessageSignature sig2 = sig((byte) -1);
        Holder<ChatType> chatType = ctx.holder(Registries.CHAT_TYPE);

        List<LastSeenMessages.Packed> lastSeens = List.of(
                LastSeenMessages.Packed.EMPTY,
                new LastSeenMessages.Packed(List.of(new MessageSignature.Packed(0))),
                new LastSeenMessages.Packed(List.of(new MessageSignature.Packed(sig))),
                new LastSeenMessages.Packed(List.of(
                        new MessageSignature.Packed(0),
                        new MessageSignature.Packed(sig2))));

        FilterMask partialEmpty = new FilterMask(4);
        FilterMask partialSet = new FilterMask(8);
        partialSet.setFiltered(0);
        partialSet.setFiltered(3);
        partialSet.setFiltered(7);
        List<FilterMask> masks = List.of(FilterMask.PASS_THROUGH, FilterMask.FULLY_FILTERED, partialEmpty, partialSet);

        // Nullable in 1.20.5 (the wire still carries a boolean presence prefix for both).
        List<MessageSignature> signatures = new ArrayList<>();
        signatures.add(null);
        signatures.add(sig);
        List<Component> unsigned = new ArrayList<>();
        unsigned.add(null);
        unsigned.add(Component.literal("u"));
        List<Optional<Component>> targets = List.of(Optional.empty(), Optional.of(Component.literal("t")));

        // Full cartesian of the categorical dimensions.
        for (MessageSignature signature : signatures) {
            for (LastSeenMessages.Packed lastSeen : lastSeens) {
                for (Component unsignedContent : unsigned) {
                    for (FilterMask mask : masks) {
                        for (Optional<Component> target : targets) {
                            out.add(new ClientboundPlayerChatPacket(sender, 0, signature,
                                    body("x", 0L, 0L, lastSeen), unsignedContent, mask,
                                    new ChatType.Bound(chatType, Component.literal("n"), target)));
                        }
                    }
                }
            }
        }

        ChatType.Bound bound = new ChatType.Bound(chatType, Component.literal("n"), Optional.empty());
        SignedMessageBody.Packed baseBody = body("x", 0L, 0L, LastSeenMessages.Packed.EMPTY);

        // index: VarInt boundaries.
        for (int v : ctx.varInts()) {
            out.add(new ClientboundPlayerChatPacket(sender, v, null, baseBody, null, FilterMask.PASS_THROUGH, bound));
        }
        // sender UUID.
        for (UUID u : ctx.uuids()) {
            out.add(new ClientboundPlayerChatPacket(u, 0, null, baseBody, null, FilterMask.PASS_THROUGH, bound));
        }
        // body.content: UTF-8 length-prefix spread (the codec caps content at 256 chars).
        for (String s : ctx.utf8()) {
            out.add(new ClientboundPlayerChatPacket(sender, 0, null, body(s, 0L, 0L, LastSeenMessages.Packed.EMPTY),
                    null, FilterMask.PASS_THROUGH, bound));
        }
        // body.timeStamp and body.salt: i64 corners.
        for (long v : ctx.longs()) {
            out.add(new ClientboundPlayerChatPacket(sender, 0, null, body("x", v, 0L, LastSeenMessages.Packed.EMPTY),
                    null, FilterMask.PASS_THROUGH, bound));
            out.add(new ClientboundPlayerChatPacket(sender, 0, null, body("x", 0L, v, LastSeenMessages.Packed.EMPTY),
                    null, FilterMask.PASS_THROUGH, bound));
        }
        // lastSeen id references: VarInt boundaries of the packed id (written as id + 1, so >= -1).
        for (int id : List.of(0, 1, 126, 127, 16382, 16383, Integer.MAX_VALUE - 1)) {
            out.add(new ClientboundPlayerChatPacket(sender, 0, null,
                    body("x", 0L, 0L, new LastSeenMessages.Packed(List.of(new MessageSignature.Packed(id)))),
                    null, FilterMask.PASS_THROUGH, bound));
        }
        // lastSeen at the documented 20-entry cap.
        List<MessageSignature.Packed> full = new ArrayList<>();
        for (int i = 0; i < 20; i++) full.add(new MessageSignature.Packed(i));
        out.add(new ClientboundPlayerChatPacket(sender, 0, null,
                body("x", 0L, 0L, new LastSeenMessages.Packed(full)), null, FilterMask.PASS_THROUGH, bound));
        // unsignedContent / chatType.name / chatType.targetName component spreads.
        for (Component c : ctx.components()) {
            out.add(new ClientboundPlayerChatPacket(sender, 0, null, baseBody, c, FilterMask.PASS_THROUGH, bound));
            out.add(new ClientboundPlayerChatPacket(sender, 0, null, baseBody, null, FilterMask.PASS_THROUGH,
                    new ChatType.Bound(chatType, c, Optional.empty())));
            out.add(new ClientboundPlayerChatPacket(sender, 0, null, baseBody, null, FilterMask.PASS_THROUGH,
                    new ChatType.Bound(chatType, Component.literal("n"), Optional.of(c))));
        }
        // Every registered chat type (the registry id is a VarInt).
        for (Holder.Reference<ChatType> type : ctx.registries.registryOrThrow(Registries.CHAT_TYPE).holders().toList()) {
            out.add(new ClientboundPlayerChatPacket(sender, 0, null, baseBody, null, FilterMask.PASS_THROUGH,
                    new ChatType.Bound(type, Component.literal("n"), Optional.empty())));
        }
        return out;
    }
}
