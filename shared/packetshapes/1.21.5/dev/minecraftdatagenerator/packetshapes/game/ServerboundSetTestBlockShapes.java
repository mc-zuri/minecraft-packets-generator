package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetTestBlockPacket;
import net.minecraft.world.level.block.state.properties.TestBlockMode;

import java.util.List;

/**
 * ServerboundSetTestBlockPacket(BlockPos position, TestBlockMode mode, String message).
 * Wire: blockPos(long-packed), idMapper VarInt(mode: START/LOG/FAIL/ACCEPT), STRING_UTF8(message).
 * <p>
 * Full cartesian: 10 domain-correct block positions x all 4 TestBlockMode constants x the UTF-8
 * string spread (6) = 240 shapes.
 */
public class ServerboundSetTestBlockShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSetTestBlockPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundSetTestBlockPacket((BlockPos) a[0], (TestBlockMode) a[1], (String) a[2]),
                ctx.blockPos(), ctx.enumAll(TestBlockMode.class), ctx.utf8());
    }
}
