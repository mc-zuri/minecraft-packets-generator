package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.world.entity.player.Abilities;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundPlayerAbilitiesPacket(Abilities) — wire is {@code i8 flags, f32 flyingSpeed,
 * f32 walkingSpeed}, where the flags byte packs {@code invulnerable|flying|canFly|instabuild}. The
 * packet is built from an {@link Abilities} instance, so we synthesize one per shape. All 16 flag
 * combinations are covered (full cross of the four booleans) with baseline speeds, plus a sweep of the
 * flying/walking speed floats.
 */
public class ClientboundPlayerAbilitiesShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlayerAbilitiesPacket.class;
    }

    private static ClientboundPlayerAbilitiesPacket mk(boolean invuln, boolean flying, boolean mayfly,
                                                       boolean instabuild, float flySpeed, float walkSpeed) {
        Abilities a = new Abilities();
        a.invulnerable = invuln;
        a.flying = flying;
        a.mayfly = mayfly;
        a.instabuild = instabuild;
        a.setFlyingSpeed(flySpeed);
        a.setWalkingSpeed(walkSpeed);
        return new ClientboundPlayerAbilitiesPacket(a);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        // All 16 flag combinations, baseline speeds.
        for (boolean invuln : ctx.bools())
            for (boolean flying : ctx.bools())
                for (boolean mayfly : ctx.bools())
                    for (boolean instabuild : ctx.bools())
                        out.add(mk(invuln, flying, mayfly, instabuild, 0.05f, 0.1f));
        // Speed sweeps against a fixed flag baseline.
        for (float f : ctx.floats()) {
            out.add(mk(false, false, false, false, f, 0.1f));
            out.add(mk(false, false, false, false, 0.05f, f));
        }
        return out;
    }
}
