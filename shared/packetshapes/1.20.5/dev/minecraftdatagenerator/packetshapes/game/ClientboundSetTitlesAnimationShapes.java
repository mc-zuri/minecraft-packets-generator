package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;

import java.util.List;

/** ClientboundSetTitlesAnimationPacket(int fadeIn, int stay, int fadeOut) — three i32 fields
 *  (writeInt); full cross of int edge cases. 5*5*5 = 125 shapes. */
public class ClientboundSetTitlesAnimationShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetTitlesAnimationPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundSetTitlesAnimationPacket((int) a[0], (int) a[1], (int) a[2]),
                ctx.ints(), ctx.ints(), ctx.ints());
    }
}
