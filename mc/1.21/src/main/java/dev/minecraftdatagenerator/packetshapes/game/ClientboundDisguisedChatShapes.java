package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ChatTypeDecoration;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ClientboundDisguisedChatPacket(Component message, ChatType.Bound chatType).
 * <p>
 * {@code chatType} is {@code ChatType.Bound(Holder&lt;ChatType&gt; chatType, Component name,
 * Optional&lt;Component&gt; targetName)}. The holder is written with {@code ByteBufCodecs.holder}, which
 * is itself a discriminated union: a var-int {@code registryId + 1} for a registry reference, or
 * {@code 0} followed by the inline {@code ChatType(ChatTypeDecoration chat, ChatTypeDecoration
 * narration)} payload. A {@code ChatTypeDecoration} is {@code (String translationKey,
 * List&lt;Parameter&gt; parameters, Style style)} with {@code Parameter} an id-mapped enum
 * (SENDER/TARGET/CONTENT).
 * <p>
 * Covered: BOTH holder branches — every one of the seven built-in chat types as a registry reference,
 * and several inline direct holders exercising the decoration's parameter list (sizes 0/1/3, every
 * Parameter constant), an empty vs non-empty {@code Style}, and UTF-8 translation keys — crossed with
 * the {@code targetName} Optional present/absent branch and the message component spread, plus a UTF-8
 * sweep on the bound's {@code name}.
 */
public class ClientboundDisguisedChatShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundDisguisedChatPacket.class;
    }

    private static ChatTypeDecoration deco(String key, List<ChatTypeDecoration.Parameter> params, Style style) {
        return new ChatTypeDecoration(key, params, style);
    }

    static List<Holder<ChatType>> chatTypeHolders(ShapeCtx ctx) {
        List<Holder<ChatType>> out = new ArrayList<>();
        Registry<ChatType> reg = ctx.registries.registryOrThrow(Registries.CHAT_TYPE);

        // reference branch: every built-in chat type
        for (ResourceKey<ChatType> key : List.of(ChatType.CHAT, ChatType.SAY_COMMAND,
                ChatType.MSG_COMMAND_INCOMING, ChatType.MSG_COMMAND_OUTGOING,
                ChatType.TEAM_MSG_COMMAND_INCOMING, ChatType.TEAM_MSG_COMMAND_OUTGOING,
                ChatType.EMOTE_COMMAND)) {
            out.add(reg.getHolderOrThrow(key));
        }

        // direct/inline branch: the decoration payload itself
        Style styled = Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true).withBold(true);
        out.add(Holder.direct(new ChatType(
                deco("", List.of(), Style.EMPTY),
                deco("", List.of(), Style.EMPTY))));
        out.add(Holder.direct(new ChatType(
                deco("chat.type.text", List.of(ChatTypeDecoration.Parameter.SENDER), Style.EMPTY),
                deco("chat.type.text.narrate", List.of(ChatTypeDecoration.Parameter.CONTENT), styled))));
        out.add(Holder.direct(new ChatType(
                deco("你好", List.of(ChatTypeDecoration.Parameter.SENDER,
                        ChatTypeDecoration.Parameter.TARGET, ChatTypeDecoration.Parameter.CONTENT), styled),
                deco(new String(Character.toChars(0x1F600)), List.of(ChatTypeDecoration.Parameter.TARGET), Style.EMPTY))));
        return out;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Holder<ChatType>> holders = chatTypeHolders(ctx);
        List<Optional<Component>> targets = List.of(Optional.empty(), Optional.of(Component.literal("target")));

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> {
                    @SuppressWarnings("unchecked")
                    Holder<ChatType> h = (Holder<ChatType>) a[1];
                    @SuppressWarnings("unchecked")
                    Optional<Component> t = (Optional<Component>) a[2];
                    return new ClientboundDisguisedChatPacket((Component) a[0],
                            new ChatType.Bound(h, Component.literal("sender"), t));
                },
                ctx.components(), holders, targets));

        // bound name: UTF-8 length-prefix sweep against a fixed reference holder
        Holder<ChatType> base = holders.get(0);
        for (String s : ctx.utf8()) {
            out.add(new ClientboundDisguisedChatPacket(Component.literal("m"),
                    new ChatType.Bound(base, Component.literal(s), Optional.of(Component.literal(s)))));
        }
        return out;
    }
}
