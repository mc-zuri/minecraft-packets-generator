package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;

import java.util.ArrayList;
import java.util.List;

/** ClientboundRemoveEntitiesPacket(IntList entityIds) — {@code count:varint} followed by one
 *  {@code entityId:varint} per entry. Collection sizes 0/1/2/many, with the single-entry form swept over
 *  the VarInt byte-length boundaries. */
public class ClientboundRemoveEntitiesShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundRemoveEntitiesPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        out.add(new ClientboundRemoveEntitiesPacket(new int[]{}));
        List<Integer> ids = ctx.varInts();
        for (int id : ids) {
            out.add(new ClientboundRemoveEntitiesPacket(new int[]{id}));
        }
        out.add(new ClientboundRemoveEntitiesPacket(new int[]{0, 1}));
        int[] all = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) all[i] = ids.get(i);
        out.add(new ClientboundRemoveEntitiesPacket(all));
        return out;
    }
}
