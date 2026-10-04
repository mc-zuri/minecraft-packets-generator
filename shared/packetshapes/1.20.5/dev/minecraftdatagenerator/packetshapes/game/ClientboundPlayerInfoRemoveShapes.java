package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;

import java.util.List;
import java.util.UUID;

/** ClientboundPlayerInfoRemovePacket(List&lt;UUID&gt; profileIds) — a single UUID list; empty / 1 / 2 entries. */
public class ClientboundPlayerInfoRemoveShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlayerInfoRemovePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<UUID> uuids = ctx.uuids();
        return ctx.list(
                new ClientboundPlayerInfoRemovePacket(List.of()),
                new ClientboundPlayerInfoRemovePacket(List.of(uuids.get(0))),
                new ClientboundPlayerInfoRemovePacket(List.of(uuids.get(0), uuids.get(1))));
    }
}
