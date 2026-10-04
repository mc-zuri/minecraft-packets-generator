package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.inventory.MenuType;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundOpenScreenPacket(int containerId[varint], MenuType type[registry id varint],
 * Component title[TRUSTED_STREAM_CODEC]).
 * <p>
 * {@code writeContainerId} is a plain VarInt, so the id is swept over every VarInt byte-length
 * boundary and crossed with the component spread. The MenuType is a registry id (also a VarInt), so
 * every registered MenuType is additionally emitted once against a baseline id/title.
 */
public class ClientboundOpenScreenShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundOpenScreenPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        MenuType<?> menu = ctx.anyOf(Registries.MENU);
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundOpenScreenPacket((int) a[0], menu, (Component) a[1]),
                ctx.varInts(), ctx.components()));

        Registry<MenuType<?>> menus = ctx.registries.lookupOrThrow(Registries.MENU);
        for (MenuType<?> type : menus) {
            out.add(new ClientboundOpenScreenPacket(0, type, Component.literal("x")));
        }
        return out;
    }
}
