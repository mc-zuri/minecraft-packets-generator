package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.world.level.block.entity.CommandBlockEntity;

import java.util.List;

/**
 * ServerboundSetCommandBlockPacket(BlockPos pos, String command, CommandBlockEntity.Mode mode,
 * boolean trackOutput, boolean conditional, boolean automatic).
 * Wire: blockPos(long-packed), utf(command), enum VarInt(mode: SEQUENCE/AUTO/REDSTONE),
 * i8(flags: bit0 trackOutput, bit1 conditional, bit2 automatic).
 * <p>
 * Full cartesian of every dimension: 10 domain-correct block positions x the UTF-8 command spread (6)
 * x all 3 modes x all 8 flag-bit combinations = 1440 shapes.
 */
public class ServerboundSetCommandBlockShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSetCommandBlockPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundSetCommandBlockPacket((BlockPos) a[0], (String) a[1],
                        (CommandBlockEntity.Mode) a[2], (boolean) a[3], (boolean) a[4], (boolean) a[5]),
                ctx.blockPos(), ctx.utf8(), ctx.enumAll(CommandBlockEntity.Mode.class),
                ctx.bools(), ctx.bools(), ctx.bools());
    }
}
