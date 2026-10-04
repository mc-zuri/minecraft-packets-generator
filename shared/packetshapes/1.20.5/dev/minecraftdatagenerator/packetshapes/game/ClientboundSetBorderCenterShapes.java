package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.world.level.border.WorldBorder;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetBorderCenterPacket — wire body is {@code newCenterX:f64, newCenterZ:f64}.
 * The only public constructor takes a {@link WorldBorder}, so we synthesize a border and drive
 * {@code setCenter(x, z)} to control both doubles; full cross of the double edge-case spread extended
 * with the +-{@code WorldBorder.MAX_CENTER_COORDINATE} limits.
 */
public class ClientboundSetBorderCenterShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetBorderCenterPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Double> coords = new ArrayList<>(ctx.doubles());
        coords.add(WorldBorder.MAX_CENTER_COORDINATE);
        coords.add(-WorldBorder.MAX_CENTER_COORDINATE);

        List<Packet<?>> out = new ArrayList<>();
        for (double x : coords) {
            for (double z : coords) {
                WorldBorder b = new WorldBorder();
                b.setCenter(x, z);
                out.add(new ClientboundSetBorderCenterPacket(b));
            }
        }
        return out;
    }
}
