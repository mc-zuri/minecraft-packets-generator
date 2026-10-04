package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;

import java.util.List;

/**
 * ServerboundContainerSlotStateChangedPacket(int slotId[VarInt], int containerId[VarInt],
 * boolean newState).
 * <p>
 * Both ids are plain VarInts ({@code writeContainerId} delegates to {@code VarInt.write}), so the full
 * cartesian of the VarInt byte-length boundaries on both x both booleans is emitted (13x13x2 = 338).
 */
public class ServerboundContainerSlotStateChangedShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundContainerSlotStateChangedPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundContainerSlotStateChangedPacket((int) a[0], (int) a[1], (boolean) a[2]),
                ctx.varInts(), ctx.varInts(), ctx.bools());
    }
}
