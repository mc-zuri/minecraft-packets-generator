package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.world.Difficulty;

import java.util.List;

/** ClientboundChangeDifficultyPacket(Difficulty, boolean locked) — every difficulty crossed with both
 *  locked states. */
public class ClientboundChangeDifficultyShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundChangeDifficultyPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundChangeDifficultyPacket((Difficulty) a[0], (boolean) a[1]),
                ctx.enumAll(Difficulty.class), ctx.bools());
    }
}
