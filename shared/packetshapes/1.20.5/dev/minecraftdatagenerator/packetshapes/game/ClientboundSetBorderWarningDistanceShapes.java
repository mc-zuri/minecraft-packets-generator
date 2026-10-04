package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.world.level.border.WorldBorder;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetBorderWarningDistancePacket — wire body is {@code warningBlocks:varInt}.
 * The only public constructor takes a {@link WorldBorder}; {@code setWarningBlocks(v)} stores the
 * value verbatim (no clamping), so the full VarInt byte-length boundary sweep is encodable.
 */
public class ClientboundSetBorderWarningDistanceShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetBorderWarningDistancePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        for (int blocks : ctx.varInts()) {
            WorldBorder b = new WorldBorder();
            b.setWarningBlocks(blocks);
            out.add(new ClientboundSetBorderWarningDistancePacket(b));
        }
        return out;
    }
}
