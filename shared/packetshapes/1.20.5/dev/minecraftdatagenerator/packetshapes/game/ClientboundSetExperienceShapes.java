package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;

import java.util.List;

/**
 * ClientboundSetExperiencePacket(float experienceProgress, int totalExperience, int experienceLevel)
 * — wire body is {@code progress:f32, experienceLevel:varInt, totalExperience:varInt} (the wire
 * order is level-then-total, the reverse of the constructor's argument order).
 * Full cross: float edge cases on progress x VarInt byte-length boundaries on both varint fields
 * (6 * 13 * 13 = 1014 shapes).
 */
public class ClientboundSetExperienceShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetExperiencePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ClientboundSetExperiencePacket((float) a[0], (int) a[1], (int) a[2]),
                ctx.floats(), ctx.varInts(), ctx.varInts());
    }
}
