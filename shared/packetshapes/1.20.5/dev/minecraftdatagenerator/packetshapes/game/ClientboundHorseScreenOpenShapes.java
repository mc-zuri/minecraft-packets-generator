package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundHorseScreenOpenPacket(int containerId[varint], int inventoryColumns[varint],
 * int entityId[i32]) — {@code writeContainerId} is a plain VarInt, so all three fields are unbounded.
 * Full cross of the containerId VarInt boundary spread x small column counts x the i32 edge cases
 * (13 x 3 x 5 = 195), plus a VarInt boundary sweep of inventoryColumns against a baseline.
 * <p>
 * NOTE: this packet is named {@code ClientboundMountScreenOpenPacket} from 1.21.11 onwards; the wire
 * body is unchanged.
 */
public class ClientboundHorseScreenOpenShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundHorseScreenOpenPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundHorseScreenOpenPacket((int) a[0], (int) a[1], (int) a[2]),
                ctx.varInts(), ctx.counts(), ctx.ints()));
        for (int columns : ctx.varInts()) {
            out.add(new ClientboundHorseScreenOpenPacket(0, columns, 0));
        }
        return out;
    }
}
