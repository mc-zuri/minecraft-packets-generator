package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundUpdateMobEffectPacket(int entityId, MobEffectInstance effect, boolean blend).
 * <p>
 * Wire body: {@code entityId:varint, effect:Holder<MobEffect>, amplifier:varint, duration:varint,
 * flags:byte}. The single {@code flags} byte packs four booleans — ambient(1), visible(2),
 * showIcon(4) and blend(8) — so all four are cross-producted (16 combos) to exercise every bit.
 * On top of that baseline, entityId, amplifier and duration are each swept over every VarInt
 * byte-length boundary ({@code varInts()}), since all three are VarInt-encoded.
 */
public class ClientboundUpdateMobEffectShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundUpdateMobEffectPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Holder<MobEffect> effect = ctx.holder(Registries.MOB_EFFECT);
        List<Packet<?>> out = new ArrayList<>();

        // Flags-byte coverage: all 16 combinations of ambient/visible/showIcon/blend.
        for (boolean ambient : ctx.bools()) {
            for (boolean visible : ctx.bools()) {
                for (boolean showIcon : ctx.bools()) {
                    for (boolean blend : ctx.bools()) {
                        MobEffectInstance inst = new MobEffectInstance(effect, 100, 0, ambient, visible, showIcon);
                        out.add(new ClientboundUpdateMobEffectPacket(1, inst, blend));
                    }
                }
            }
        }

        // Sweep entityId over int edges (baseline effect instance).
        for (int entityId : ctx.varInts()) {
            MobEffectInstance inst = new MobEffectInstance(effect, 100, 0, false, true, true);
            out.add(new ClientboundUpdateMobEffectPacket(entityId, inst, false));
        }

        // Sweep amplifier over int edges.
        for (int amplifier : ctx.varInts()) {
            MobEffectInstance inst = new MobEffectInstance(effect, 100, amplifier, false, true, true);
            out.add(new ClientboundUpdateMobEffectPacket(1, inst, false));
        }

        // Sweep duration over int edges.
        for (int duration : ctx.varInts()) {
            MobEffectInstance inst = new MobEffectInstance(effect, duration, 0, false, true, true);
            out.add(new ClientboundUpdateMobEffectPacket(1, inst, false));
        }
        return out;
    }
}
