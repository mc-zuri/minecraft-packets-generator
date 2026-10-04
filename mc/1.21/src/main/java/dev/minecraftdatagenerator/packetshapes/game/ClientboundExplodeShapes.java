package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundExplodePacket(double x, double y, double z, float power, List&lt;BlockPos&gt; toBlow,
 * &#64;Nullable Vec3 knockback, Explosion.BlockInteraction blockInteraction,
 * ParticleOptions smallExplosionParticles, ParticleOptions largeExplosionParticles,
 * Holder&lt;SoundEvent&gt; explosionSound).
 * <p>
 * Wire order: {@code x}/{@code y}/{@code z} f64, {@code power} f32, then {@code toBlow} as a var-int
 * count followed by three <b>signed bytes</b> per entry holding the offset from
 * {@code (floor(x), floor(y), floor(z))}, then {@code knockbackX}/{@code Y}/{@code Z} f32 (a null
 * knockback is written as three zeroes), {@code blockInteraction} as a var-int enum id, the two
 * registry-dispatched particle payloads, and finally {@code SoundEvent.STREAM_CODEC}.
 * <p>
 * 1.21's explosion packet is the pre-1.21.2 layout: it still carries {@code power}, the affected-block
 * list and two separate particle payloads, and its knockback is a nullable {@code Vec3} written as
 * three bare floats rather than an {@code Optional}. 1.21.2 dropped {@code power}, {@code toBlow},
 * {@code blockInteraction} and the second particle set, so none of those dimensions exist in the
 * newer providers.
 * <p>
 * Ten fields, two of them registry dispatches, so the shapes are per-field sweeps over a fixed valid
 * baseline rather than one cartesian. Covered: the three centre doubles per axis; {@code power} over
 * the float corner cases; {@code toBlow} at sizes 0/1/2/3 with the offsets pushed to both ends of the
 * signed-byte range they are encoded in; {@code knockback} null (the zero-vector branch) and present
 * over the double corner cases; every {@link Explosion.BlockInteraction} constant; every distinct
 * {@code ParticleOptions} dispatch payload, independently for the small and the large particle field;
 * and both branches of {@code SoundEvent.STREAM_CODEC} - the registry reference and the inline direct
 * form, whose payload is {@code (ResourceLocation location, Optional&lt;Float&gt; fixedRange)} in both of
 * its optional states.
 */
public class ClientboundExplodeShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundExplodePacket.class;
    }

    private static final double BX = 0, BY = 64, BZ = 0;
    private static final float BPOWER = 4.0f;
    private static final Vec3 BASE_KNOCKBACK = new Vec3(0.1, 0.2, 0.3);
    private static final Explosion.BlockInteraction BASE_INTERACTION = Explosion.BlockInteraction.DESTROY;

    private static ClientboundExplodePacket make(double x, double y, double z, float power,
                                                 List<BlockPos> toBlow, Vec3 knockback,
                                                 Explosion.BlockInteraction interaction,
                                                 ParticleOptions small, ParticleOptions large,
                                                 Holder<SoundEvent> sound) {
        return new ClientboundExplodePacket(x, y, z, power, toBlow, knockback, interaction,
                small, large, sound);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        Holder<SoundEvent> refSound = ctx.holder(Registries.SOUND_EVENT);
        ParticleOptions cloud = ParticleTypes.CLOUD;
        List<BlockPos> noBlocks = List.of();

        // centre: a domain-correct spread, then a per-axis double sweep
        for (double[] c : List.of(
                new double[]{0, 0, 0},
                new double[]{-1.5, 2.5, -3.5},
                new double[]{3.0E7, 319, -3.0E7},
                new double[]{-3.0E7, -2032, 3.0E7},
                new double[]{Double.MAX_VALUE, -Double.MAX_VALUE, 3.5})) {
            out.add(make(c[0], c[1], c[2], BPOWER, noBlocks, BASE_KNOCKBACK, BASE_INTERACTION,
                    cloud, cloud, refSound));
        }
        for (double v : ctx.doubles()) {
            out.add(make(v, BY, BZ, BPOWER, noBlocks, BASE_KNOCKBACK, BASE_INTERACTION, cloud, cloud, refSound));
            out.add(make(BX, v, BZ, BPOWER, noBlocks, BASE_KNOCKBACK, BASE_INTERACTION, cloud, cloud, refSound));
            out.add(make(BX, BY, v, BPOWER, noBlocks, BASE_KNOCKBACK, BASE_INTERACTION, cloud, cloud, refSound));
        }
        // power
        for (float p : ctx.floats()) {
            out.add(make(BX, BY, BZ, p, noBlocks, BASE_KNOCKBACK, BASE_INTERACTION, cloud, cloud, refSound));
        }
        // toBlow: sizes 0/1/2/3. Each entry is written as three signed-byte offsets from the floored
        // centre, so the offsets are kept inside -128..127 and both ends of that range are used.
        List<List<BlockPos>> blockLists = List.of(
                List.of(),
                List.of(new BlockPos(0, 64, 0)),
                List.of(new BlockPos(1, 65, -1), new BlockPos(-1, 63, 1)),
                List.of(new BlockPos(127, 64 + 127, 127),
                        new BlockPos(-128, 64 - 128, -128),
                        new BlockPos(0, 64, 0)));
        for (List<BlockPos> blocks : blockLists) {
            out.add(make(BX, BY, BZ, BPOWER, blocks, BASE_KNOCKBACK, BASE_INTERACTION, cloud, cloud, refSound));
        }
        // knockback: the null branch (three zero floats) and the present corner cases
        out.add(make(BX, BY, BZ, BPOWER, noBlocks, null, BASE_INTERACTION, cloud, cloud, refSound));
        for (Vec3 kb : List.of(new Vec3(0, 0, 0), new Vec3(-9, 9, -9),
                new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 3.5))) {
            out.add(make(BX, BY, BZ, BPOWER, noBlocks, kb, BASE_INTERACTION, cloud, cloud, refSound));
        }
        for (double v : ctx.doubles()) {
            out.add(make(BX, BY, BZ, BPOWER, noBlocks, new Vec3(v, 0, 0), BASE_INTERACTION, cloud, cloud, refSound));
            out.add(make(BX, BY, BZ, BPOWER, noBlocks, new Vec3(0, v, 0), BASE_INTERACTION, cloud, cloud, refSound));
            out.add(make(BX, BY, BZ, BPOWER, noBlocks, new Vec3(0, 0, v), BASE_INTERACTION, cloud, cloud, refSound));
        }
        // blockInteraction: every enum constant
        for (Explosion.BlockInteraction interaction : ctx.enumAll(Explosion.BlockInteraction.class)) {
            out.add(make(BX, BY, BZ, BPOWER, noBlocks, BASE_KNOCKBACK, interaction, cloud, cloud, refSound));
        }
        // both particle fields: every dispatch branch of ParticleTypes.STREAM_CODEC, one field at a time
        for (ParticleOptions p : ClientboundLevelParticlesShapes.particles(ctx)) {
            out.add(make(BX, BY, BZ, BPOWER, noBlocks, BASE_KNOCKBACK, BASE_INTERACTION, p, cloud, refSound));
            out.add(make(BX, BY, BZ, BPOWER, noBlocks, BASE_KNOCKBACK, BASE_INTERACTION, cloud, p, refSound));
        }
        // explosionSound: both holder branches, and both fixedRange optional states inline
        List<Holder<SoundEvent>> sounds = List.of(
                refSound,
                Holder.direct(SoundEvent.createVariableRangeEvent(ctx.id("entity.generic.explode"))),
                Holder.direct(SoundEvent.createFixedRangeEvent(ctx.id("entity.generic.explode"), 0f)),
                Holder.direct(SoundEvent.createFixedRangeEvent(ctx.id("block.stone.step"), 16.0f)));
        for (Holder<SoundEvent> s : sounds) {
            out.add(make(BX, BY, BZ, BPOWER, noBlocks, BASE_KNOCKBACK, BASE_INTERACTION, cloud, cloud, s));
        }
        return out;
    }
}
