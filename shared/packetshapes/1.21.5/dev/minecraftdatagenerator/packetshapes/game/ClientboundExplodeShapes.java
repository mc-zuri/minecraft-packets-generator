package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ClientboundExplodePacket(Vec3 center, Optional&lt;Vec3&gt; playerKnockback,
 * ParticleOptions explosionParticle, Holder&lt;SoundEvent&gt; explosionSound).
 * <p>
 * Four fields, two of which are themselves unions, so the shapes are per-field sweeps over a fixed
 * valid baseline rather than one cartesian.
 * <p>
 * Covered: center Vec3 corner cases; the {@code playerKnockback} Optional in BOTH branches; every
 * distinct {@code ParticleOptions} dispatch payload for {@code explosionParticle}; BOTH branches of
 * {@code SoundEvent.STREAM_CODEC} — registry reference AND the inline direct form, whose payload is
 * {@code (ResourceLocation location, Optional&lt;Float&gt; fixedRange)} in both of its optional states.
 * <p>
 * NOTE: 1.21.8's explosion packet carries no {@code radius}, {@code blockCount} or
 * {@code blockParticles} field (those, and the {@code WeightedList<ExplosionParticleInfo>} type they
 * use, were introduced in 26.1), and no trailing {@code playSound} boolean (26.3-only), so there are
 * no such dimensions here.
 */
public class ClientboundExplodeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundExplodePacket.class;
    }

    private static final Vec3 BASE_CENTER = new Vec3(0, 64, 0);
    private static final Optional<Vec3> BASE_KNOCKBACK = Optional.of(new Vec3(0.1, 0.2, 0.3));

    private ClientboundExplodePacket make(Vec3 center, Optional<Vec3> knockback,
                                          ParticleOptions particle, Holder<SoundEvent> sound) {
        return new ClientboundExplodePacket(center, knockback, particle, sound);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        Holder<SoundEvent> refSound = ctx.holder(Registries.SOUND_EVENT);

        // center
        for (Vec3 c : List.of(new Vec3(0, 0, 0), new Vec3(-1.5, 2.5, -3.5),
                new Vec3(3.0E7, 319, -3.0E7), new Vec3(-3.0E7, -2032, 3.0E7),
                new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 3.5))) {
            out.add(make(c, BASE_KNOCKBACK, ParticleTypes.CLOUD, refSound));
        }
        // center: per-axis double sweep
        for (double v : ctx.doubles()) {
            out.add(make(new Vec3(v, 64, 0), BASE_KNOCKBACK, ParticleTypes.CLOUD, refSound));
            out.add(make(new Vec3(0, v, 0), BASE_KNOCKBACK, ParticleTypes.CLOUD, refSound));
            out.add(make(new Vec3(0, 64, v), BASE_KNOCKBACK, ParticleTypes.CLOUD, refSound));
        }
        // playerKnockback: Optional absent + present corner cases
        for (Optional<Vec3> kb : List.of(Optional.<Vec3>empty(), Optional.of(new Vec3(0, 0, 0)),
                Optional.of(new Vec3(-9, 9, -9)),
                Optional.of(new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 3.5)))) {
            out.add(make(BASE_CENTER, kb, ParticleTypes.CLOUD, refSound));
        }
        for (double v : ctx.doubles()) {
            out.add(make(BASE_CENTER, Optional.of(new Vec3(v, 0, 0)), ParticleTypes.CLOUD, refSound));
        }
        // explosionParticle: every dispatch branch of ParticleTypes.STREAM_CODEC
        for (ParticleOptions p : ClientboundLevelParticlesShapes.particles(ctx)) {
            out.add(make(BASE_CENTER, BASE_KNOCKBACK, p, refSound));
        }
        // explosionSound: both holder branches, and both fixedRange optional states inline
        List<Holder<SoundEvent>> sounds = List.of(
                refSound,
                Holder.direct(SoundEvent.createVariableRangeEvent(ctx.id("entity.generic.explode"))),
                Holder.direct(SoundEvent.createFixedRangeEvent(ctx.id("entity.generic.explode"), 0f)),
                Holder.direct(SoundEvent.createFixedRangeEvent(ctx.id("block.stone.step"), 16.0f)));
        for (Holder<SoundEvent> s : sounds) {
            out.add(make(BASE_CENTER, BASE_KNOCKBACK, ParticleTypes.CLOUD, s));
        }
        return out;
    }
}
