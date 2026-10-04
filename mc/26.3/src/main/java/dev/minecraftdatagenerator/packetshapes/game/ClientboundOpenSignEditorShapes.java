package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.world.level.block.entity.SignTextSlot;

import java.util.List;

/** ClientboundOpenSignEditorPacket(BlockPos pos, SignTextSlot slot) — cross of the BlockPos spread and
 *  every SignTextSlot constant (26.3 replaced the isFrontText boolean with the SignTextSlot enum). */
public class ClientboundOpenSignEditorShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundOpenSignEditorPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundOpenSignEditorPacket((BlockPos) a[0], (SignTextSlot) a[1]),
                ctx.blockPos(), ctx.enumAll(SignTextSlot.class));
    }
}
