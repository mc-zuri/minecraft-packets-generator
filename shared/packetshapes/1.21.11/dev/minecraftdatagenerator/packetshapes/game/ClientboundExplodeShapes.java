package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ClientboundExplodePacket(Vec3 center, float radius, int blockCount[i32], Optional&lt;Vec3&gt;
 * playerKnockback, ParticleOptions explosionParticle, Holder&lt;SoundEvent&gt; explosionSound,
 * WeightedList&lt;ExplosionParticleInfo&gt; blockParticles).
 * <p>
 * Seven fields, two of which are themselves unions, so the shapes are per-field sweeps over a fixed
 * valid baseline rather than one cartesian.
 * <p>
 * Covered: center Vec3 corner cases; radius float edges; {@code blockCount} i32 edges; the
 * {@code playerKnockback} Optional in BOTH branches; several {@code ParticleOptions} dispatch branches
 * for {@code explosionParticle}; BOTH branches of {@code SoundEvent.STREAM_CODEC} — registry reference
 * AND the inline direct form, whose payload is {@code (Identifier location, Optional&lt;Float&gt;
 * fixedRange)} in both of its optional states; {@code blockParticles} weighted lists of size 0/1/2/3
 * with weight 0 and var-int weight boundaries, and with per-entry particle/scaling/speed variation.
 * <p>
 * NOTE: 26.2 has no trailing {@code playSound} boolean (that field is 26.3-only), so there is no
 * such dimension here.
 */
public class ClientboundExplodeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundExplodePacket.class;
    }

    private static final Vec3 BASE_CENTER = new Vec3(0, 64, 0);
    private static final float BASE_RADIUS = 3.0f;
    private static final int BASE_BLOCK_COUNT = 1;
    private static final Optional<Vec3> BASE_KNOCKBACK = Optional.of(new Vec3(0.1, 0.2, 0.3));

    private static WeightedList<ExplosionParticleInfo> baseParticles() {
        return WeightedList.of(List.of(new Weighted<>(new ExplosionParticleInfo(ParticleTypes.CLOUD, 1.0f, 1.0f), 1)));
    }

    private ClientboundExplodePacket make(ShapeCtx ctx, Vec3 center, float radius, int blockCount,
                                          Optional<Vec3> knockback, ParticleOptions particle,
                                          Holder<SoundEvent> sound,
                                          WeightedList<ExplosionParticleInfo> blockParticles) {
        return new ClientboundExplodePacket(center, radius, blockCount, knockback, particle, sound,
                blockParticles);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        Holder<SoundEvent> refSound = ctx.holder(Registries.SOUND_EVENT);

        // center
        for (Vec3 c : List.of(new Vec3(0, 0, 0), new Vec3(-1.5, 2.5, -3.5),
                new Vec3(3.0E7, 319, -3.0E7), new Vec3(-3.0E7, -2032, 3.0E7),
                new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 3.5))) {
            out.add(make(ctx, c, BASE_RADIUS, BASE_BLOCK_COUNT, BASE_KNOCKBACK,
                    ParticleTypes.CLOUD, refSound, baseParticles()));
        }
        // radius
        for (float r : ctx.floats()) {
            out.add(make(ctx, BASE_CENTER, r, BASE_BLOCK_COUNT, BASE_KNOCKBACK,
                    ParticleTypes.CLOUD, refSound, baseParticles()));
        }
        // blockCount (plain i32)
        for (int bc : ctx.ints()) {
            out.add(make(ctx, BASE_CENTER, BASE_RADIUS, bc, BASE_KNOCKBACK,
                    ParticleTypes.CLOUD, refSound, baseParticles()));
        }
        // playerKnockback: Optional absent + present corner cases
        for (Optional<Vec3> kb : List.of(Optional.<Vec3>empty(), Optional.of(new Vec3(0, 0, 0)),
                Optional.of(new Vec3(-9, 9, -9)),
                Optional.of(new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 3.5)))) {
            out.add(make(ctx, BASE_CENTER, BASE_RADIUS, BASE_BLOCK_COUNT, kb,
                    ParticleTypes.CLOUD, refSound, baseParticles()));
        }
        // explosionParticle: several dispatch branches of ParticleTypes.STREAM_CODEC
        for (ParticleOptions p : ClientboundLevelParticlesShapes.particles(ctx)) {
            out.add(make(ctx, BASE_CENTER, BASE_RADIUS, BASE_BLOCK_COUNT, BASE_KNOCKBACK,
                    p, refSound, baseParticles()));
        }
        // explosionSound: both holder branches, and both fixedRange optional states inline
        List<Holder<SoundEvent>> sounds = List.of(
                refSound,
                Holder.direct(SoundEvent.createVariableRangeEvent(ctx.id("entity.generic.explode"))),
                Holder.direct(SoundEvent.createFixedRangeEvent(ctx.id("entity.generic.explode"), 0f)),
                Holder.direct(SoundEvent.createFixedRangeEvent(ctx.id("block.stone.step"), 16.0f)));
        for (Holder<SoundEvent> s : sounds) {
            out.add(make(ctx, BASE_CENTER, BASE_RADIUS, BASE_BLOCK_COUNT, BASE_KNOCKBACK,
                    ParticleTypes.CLOUD, s, baseParticles()));
        }
        // blockParticles: list sizes and weight/scaling/speed edges (weight is a non-negative var-int)
        List<WeightedList<ExplosionParticleInfo>> particleLists = new ArrayList<>();
        particleLists.add(WeightedList.of(List.of()));
        particleLists.add(WeightedList.of(List.of(new Weighted<>(
                new ExplosionParticleInfo(ParticleTypes.CLOUD, 2.0f, 0.5f), 0))));
        particleLists.add(WeightedList.of(List.of(new Weighted<>(
                new ExplosionParticleInfo(ParticleTypes.CRIT, 0f, -1f), 127))));
        particleLists.add(WeightedList.of(List.of(
                new Weighted<>(new ExplosionParticleInfo(ParticleTypes.CLOUD, 1f, 1f), 1),
                new Weighted<>(new ExplosionParticleInfo(ParticleTypes.CRIT, Float.MAX_VALUE, -Float.MAX_VALUE), 128))));
        // Multi-entry list at wide var-int weights. WeightedList requires the weight SUM to stay
        // <= Integer.MAX_VALUE, so the maximum weight only appears alone, in its own list below.
        particleLists.add(WeightedList.of(List.of(
                new Weighted<>(new ExplosionParticleInfo(ParticleTypes.CLOUD, 1f, 1f), 16384),
                new Weighted<>(new ExplosionParticleInfo(
                        new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), 1f, 1f),
                        2097151),
                new Weighted<>(new ExplosionParticleInfo(ParticleTypes.CRIT, 3.5f, 3.5f), 1))));
        particleLists.add(WeightedList.of(List.of(new Weighted<>(
                new ExplosionParticleInfo(
                        new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), 1f, 1f),
                Integer.MAX_VALUE))));
        for (WeightedList<ExplosionParticleInfo> bp : particleLists) {
            out.add(make(ctx, BASE_CENTER, BASE_RADIUS, BASE_BLOCK_COUNT, BASE_KNOCKBACK,
                    ParticleTypes.CLOUD, refSound, bp));
        }
        return out;
    }
}
