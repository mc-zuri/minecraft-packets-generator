package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;

import java.util.List;

/** ServerboundContainerButtonClickPacket(int containerId[CONTAINER_ID], int buttonId[varint]) — both
 *  fields are plain VarInts ({@code FriendlyByteBuf.writeContainerId} is {@code VarInt.write}), so this is
 *  the full cross of every VarInt byte-length boundary against itself. */
public class ServerboundContainerButtonClickShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundContainerButtonClickPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundContainerButtonClickPacket((int) a[0], (int) a[1]),
                ctx.varInts(), ctx.varInts());
    }
}
