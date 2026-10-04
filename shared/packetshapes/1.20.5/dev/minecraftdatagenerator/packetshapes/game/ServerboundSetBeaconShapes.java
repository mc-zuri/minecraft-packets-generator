package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.world.effect.MobEffect;

import java.util.List;
import java.util.Optional;

/**
 * ServerboundSetBeaconPacket(Optional&lt;Holder&lt;MobEffect&gt;&gt; primary,
 * Optional&lt;Holder&lt;MobEffect&gt;&gt; secondary) — each optional written as present/absent, the
 * present form being a registry Holder reference. Full cross of both optionals (2×2 = 4).
 */
public class ServerboundSetBeaconShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSetBeaconPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Holder<MobEffect> effect = ctx.holder(Registries.MOB_EFFECT);
        List<Optional<Holder<MobEffect>>> opts = List.of(Optional.empty(), Optional.of(effect));
        return ctx.cross(
                a -> {
                    @SuppressWarnings("unchecked")
                    Optional<Holder<MobEffect>> primary = (Optional<Holder<MobEffect>>) a[0];
                    @SuppressWarnings("unchecked")
                    Optional<Holder<MobEffect>> secondary = (Optional<Holder<MobEffect>>) a[1];
                    return new ServerboundSetBeaconPacket(primary, secondary);
                },
                opts, opts);
    }
}
