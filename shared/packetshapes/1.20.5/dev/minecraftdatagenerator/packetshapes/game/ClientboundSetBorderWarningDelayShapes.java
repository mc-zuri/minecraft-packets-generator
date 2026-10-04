package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.world.level.border.WorldBorder;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetBorderWarningDelayPacket — wire body is {@code warningDelay:varInt}.
 * The only public constructor takes a {@link WorldBorder}; {@code setWarningTime(v)} controls the field,
 * which is swept over every VarInt byte-length boundary plus both negative forms.
 */
public class ClientboundSetBorderWarningDelayShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetBorderWarningDelayPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        for (int delay : ctx.varInts()) {
            WorldBorder b = new WorldBorder();
            b.setWarningTime(delay);
            out.add(new ClientboundSetBorderWarningDelayPacket(b));
        }
        return out;
    }
}
