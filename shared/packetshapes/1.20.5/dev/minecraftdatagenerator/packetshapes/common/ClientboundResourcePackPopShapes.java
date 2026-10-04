package dev.minecraftdatagenerator.packetshapes.common;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** ClientboundResourcePackPopPacket(Optional&lt;UUID&gt; id) — absent + present(every UUID corner case). */
public class ClientboundResourcePackPopShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundResourcePackPopPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        out.add(new ClientboundResourcePackPopPacket(Optional.empty()));
        for (UUID u : ctx.uuids()) {
            out.add(new ClientboundResourcePackPopPacket(Optional.of(u)));
        }
        return out;
    }
}
