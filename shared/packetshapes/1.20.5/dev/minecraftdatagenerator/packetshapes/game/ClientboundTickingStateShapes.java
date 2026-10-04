package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;

import java.util.List;

/** ClientboundTickingStatePacket(float tickRate, boolean isFrozen) — writeFloat + writeBoolean;
 *  full cross-product of float edge cases and both boolean states. */
public class ClientboundTickingStateShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundTickingStatePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundTickingStatePacket((float) a[0], (boolean) a[1]),
                ctx.floats(), ctx.bools());
    }
}
