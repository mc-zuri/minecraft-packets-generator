package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.world.effect.MobEffect;

import java.util.List;

/** ClientboundRemoveMobEffectPacket(int entityId[varint], Holder&lt;MobEffect&gt; effect[registry varint]) —
 *  the entity id is swept over the VarInt byte-length boundaries against a live mob-effect registry
 *  reference (the only registry-safe holder available). */
public class ClientboundRemoveMobEffectShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundRemoveMobEffectPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Holder<MobEffect> effect = ctx.holder(Registries.MOB_EFFECT);
        return ctx.each(ctx.varInts(), id -> new ClientboundRemoveMobEffectPacket(id, effect));
    }
}
