package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
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
 * Optional&lt;Component&gt; targetName)}. In 1.20.5 the holder is written with
 * {@code ByteBufCodecs.holderRegistry}, which is <b>not</b> a union: it writes only the registry id as
 * a var-int, so there is no inline branch and the {@code ChatType} payload itself never reaches the
 * wire. (The inline-capable {@code ByteBufCodecs.holder} form, and with it the
 * {@code ChatTypeDecoration} dispatch, arrived in 1.21.)
 * <p>
 * Covered: every one of the seven built-in chat types as a registry reference, crossed with the
 * {@code targetName} Optional present/absent branch and the message component spread, plus a UTF-8
 * sweep on the bound's {@code name} and on {@code targetName}.
 */
public class ClientboundDisguisedChatShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundDisguisedChatPacket.class;
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

        // No inline/direct branch: 1.20.5's holderRegistry codec can only encode registry references,
        // and vanilla throws "Can't find id for Direct{...}" on a direct holder.
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
