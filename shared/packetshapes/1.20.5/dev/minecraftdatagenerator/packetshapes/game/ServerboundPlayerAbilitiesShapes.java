package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.world.entity.player.Abilities;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundPlayerAbilitiesPacket — the wire body is a single flags byte where only bit 2
 * ({@code isFlying}) is ever written; every other bit is always 0. The only public constructor takes
 * an {@link Abilities}, of which just {@code flying} reaches the wire. Two shapes: flying off / on.
 */
public class ServerboundPlayerAbilitiesShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPlayerAbilitiesPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        for (boolean flying : ctx.bools()) {
            Abilities a = new Abilities();
            a.flying = flying;
            out.add(new ServerboundPlayerAbilitiesPacket(a));
        }
        return out;
    }
}
