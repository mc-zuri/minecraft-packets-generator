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
 * ClientboundPlayerChatPacket(int globalIndex[varint], UUID sender, int index[varint],
 * Optional&lt;MessageSignature&gt; signature, SignedMessageBody.Packed body, Optional&lt;Component&gt;
 * unsignedContent, FilterMask filterMask, ChatType.Bound chatType).
 * <p>
 * Sub-structures:
 * <ul>
 *   <li>{@code signature}: bool prefix then exactly {@code MessageSignature.BYTES} (256) raw bytes.</li>
 *   <li>{@code body} = {@code stringUtf8(256) content, i64 timeStamp[millis], i64 salt,
 *       LastSeenMessages.Packed lastSeen}; {@code lastSeen} is a list capped at 20 of
 *       {@code MessageSignature.Packed}, each written as {@code varint (id + 1)} and, when that is 0,
 *       a full 256-byte signature.</li>
 *   <li>{@code filterMask}: a varint type id — 0 PASS_THROUGH, 1 FULLY_FILTERED, 2 PARTIALLY_FILTERED
 *       followed by a BitSet (long array).</li>
 *   <li>{@code chatType} = {@code registry-id chatType, Component name, Optional&lt;Component&gt; targetName}.</li>
 * </ul>
 * Coverage: full cartesian of every categorical dimension — signature Optional (2) x lastSeen form (4)
 * x unsignedContent Optional (2) x filterMask branch (4, including a PARTIALLY_FILTERED mask with bits
 * actually set, which is the only way to get a non-empty BitSet on the wire) x targetName Optional (2)
 * = 128 shapes — plus per-field sweeps of globalIndex/index (VarInt boundaries), sender UUID, content
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

        List<Optional<MessageSignature>> signatures = List.of(Optional.empty(), Optional.of(sig));
        List<Optional<Component>> unsigned = List.of(Optional.empty(), Optional.of(Component.literal("u")));
        List<Optional<Component>> targets = List.of(Optional.empty(), Optional.of(Component.literal("t")));

        // Full cartesian of the categorical dimensions.
        for (Optional<MessageSignature> signature : signatures) {
            for (LastSeenMessages.Packed lastSeen : lastSeens) {
                for (Optional<Component> unsignedContent : unsigned) {
                    for (FilterMask mask : masks) {
                        for (Optional<Component> target : targets) {
                            out.add(new ClientboundPlayerChatPacket(0, sender, 0, signature,
                                    body("x", 0L, 0L, lastSeen), unsignedContent, mask,
                                    new ChatType.Bound(chatType, Component.literal("n"), target)));
                        }
                    }
                }
            }
        }

        ChatType.Bound bound = new ChatType.Bound(chatType, Component.literal("n"), Optional.empty());
        SignedMessageBody.Packed baseBody = body("x", 0L, 0L, LastSeenMessages.Packed.EMPTY);

        // globalIndex / index: VarInt boundaries.
        for (int v : ctx.varInts()) {
            out.add(new ClientboundPlayerChatPacket(v, sender, 0, Optional.empty(), baseBody, Optional.empty(), FilterMask.PASS_THROUGH, bound));
            out.add(new ClientboundPlayerChatPacket(0, sender, v, Optional.empty(), baseBody, Optional.empty(), FilterMask.PASS_THROUGH, bound));
        }
        // sender UUID.
        for (UUID u : ctx.uuids()) {
            out.add(new ClientboundPlayerChatPacket(0, u, 0, Optional.empty(), baseBody, Optional.empty(), FilterMask.PASS_THROUGH, bound));
        }
        // body.content: UTF-8 length-prefix spread (the codec caps content at 256 chars).
        for (String s : ctx.utf8()) {
            out.add(new ClientboundPlayerChatPacket(0, sender, 0, Optional.empty(), body(s, 0L, 0L, LastSeenMessages.Packed.EMPTY),
                    Optional.empty(), FilterMask.PASS_THROUGH, bound));
        }
        // body.timeStamp and body.salt: i64 corners.
        for (long v : ctx.longs()) {
            out.add(new ClientboundPlayerChatPacket(0, sender, 0, Optional.empty(), body("x", v, 0L, LastSeenMessages.Packed.EMPTY),
                    Optional.empty(), FilterMask.PASS_THROUGH, bound));
            out.add(new ClientboundPlayerChatPacket(0, sender, 0, Optional.empty(), body("x", 0L, v, LastSeenMessages.Packed.EMPTY),
                    Optional.empty(), FilterMask.PASS_THROUGH, bound));
        }
        // lastSeen id references: VarInt boundaries of the packed id (written as id + 1, so >= -1).
        for (int id : List.of(0, 1, 126, 127, 16382, 16383, Integer.MAX_VALUE - 1)) {
            out.add(new ClientboundPlayerChatPacket(0, sender, 0, Optional.empty(),
                    body("x", 0L, 0L, new LastSeenMessages.Packed(List.of(new MessageSignature.Packed(id)))),
                    Optional.empty(), FilterMask.PASS_THROUGH, bound));
        }
        // lastSeen at the documented 20-entry cap.
        List<MessageSignature.Packed> full = new ArrayList<>();
        for (int i = 0; i < 20; i++) full.add(new MessageSignature.Packed(i));
        out.add(new ClientboundPlayerChatPacket(0, sender, 0, Optional.empty(),
                body("x", 0L, 0L, new LastSeenMessages.Packed(full)), Optional.empty(), FilterMask.PASS_THROUGH, bound));
        // unsignedContent / chatType.name / chatType.targetName component spreads.
        for (Component c : ctx.components()) {
            out.add(new ClientboundPlayerChatPacket(0, sender, 0, Optional.empty(), baseBody, Optional.of(c), FilterMask.PASS_THROUGH, bound));
            out.add(new ClientboundPlayerChatPacket(0, sender, 0, Optional.empty(), baseBody, Optional.empty(), FilterMask.PASS_THROUGH,
                    new ChatType.Bound(chatType, c, Optional.empty())));
            out.add(new ClientboundPlayerChatPacket(0, sender, 0, Optional.empty(), baseBody, Optional.empty(), FilterMask.PASS_THROUGH,
                    new ChatType.Bound(chatType, Component.literal("n"), Optional.of(c))));
        }
        // Every registered chat type (the registry id is a VarInt).
        for (Holder.Reference<ChatType> type : ctx.registries.lookupOrThrow(Registries.CHAT_TYPE).listElements().toList()) {
            out.add(new ClientboundPlayerChatPacket(0, sender, 0, Optional.empty(), baseBody, Optional.empty(), FilterMask.PASS_THROUGH,
                    new ChatType.Bound(type, Component.literal("n"), Optional.empty())));
        }
        return out;
    }
}
