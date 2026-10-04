package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundLevelParticlesPacket(ParticleOptions particle, boolean overrideLimiter,
 * double x, double y, double z, float xDist, float yDist, float zDist, float maxSpeed, int count).
 * <p>
 * Wire order (it differs from the constructor order): {@code overrideLimiter} bool,
 * {@code x}/{@code y}/{@code z} f64, {@code xDist}/{@code yDist}/{@code zDist} f32, a single
 * {@code maxSpeed} f32, {@code count} as a plain big-endian <b>i32</b> (not a var-int), then the
 * registry-dispatched {@code particle} payload last.
 * <p>
 * {@code particle} is dispatched on {@code minecraft:particle_type}, with a per-type payload.
 * Covered: the categorical cartesian of {@code overrideLimiter} x every distinct particle payload
 * class (simple/empty, BlockParticleOption, ColorParticleOption, DustParticleOptions,
 * DustColorTransitionOptions, ItemParticleOption, SculkChargeParticleOptions, ShriekParticleOption
 * and VibrationParticleOption in both of its PositionSource dispatch
 * branches), then per-field sweeps for the position doubles, each distance float, the single
 * {@code maxSpeed} float, and {@code count} over the var-int boundary spread (a superset of the plain
 * i32 corner cases, which is what this field actually is).
 * <p>
 * NOTE: 1.20.5 has no {@code alwaysShow} bool -- that was added in 1.21.5 -- so there is no such
 * dimension here.
 * <p>
 * NOTE: {@code ScalableParticleOptionsBase} clamps its scale to [0.01, 4.0] in the constructor, so dust
 * scale corner cases beyond that range are not distinguishable on the wire and are not emitted.
 * An empty {@code ItemStack} (AIR, or count 0) is rejected by {@code ItemStack.STREAM_CODEC},
 * so those are omitted too.
 */
public class ClientboundLevelParticlesShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundLevelParticlesPacket.class;
    }

    /** One value per distinct particle payload shape (shared with the explode provider). */
    static List<ParticleOptions> particles(ShapeCtx ctx) {
        List<ParticleOptions> out = new ArrayList<>();

        // SimpleParticleType: zero-length payload
        out.add(ParticleTypes.CLOUD);
        out.add(ParticleTypes.CRIT);
        out.add(ParticleTypes.BUBBLE);
        // 1.20.5 still registers these four as SimpleParticleType; PowerParticleOption (dragon_breath)
        // and SpellParticleOption (effect / instant_effect) only arrived in a later version, and
        // flash only became a ColorParticleOption then too, so here they carry no payload.
        out.add(ParticleTypes.DRAGON_BREATH);
        out.add(ParticleTypes.FLASH);
        out.add(ParticleTypes.EFFECT);
        out.add(ParticleTypes.INSTANT_EFFECT);

        // BlockParticleOption: a block-state id var-int; all five particle types that use it
        out.add(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()));
        out.add(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, Blocks.AIR.defaultBlockState()));
        out.add(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()));
        out.add(new BlockParticleOption(ParticleTypes.DUST_PILLAR, Blocks.MUD.defaultBlockState()));
        // ParticleTypes.BLOCK_CRUMBLE is not registered in 1.20.5; it was added in 1.21.2.

        // ColorParticleOption: packed ARGB i32. entity_effect is 1.20.5's only ColorParticleOption
        // type (tinted_leaves arrived in 1.21.5), so all four colour corner cases go through it.
        out.add(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0));
        out.add(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, -1));
        out.add(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0x7FFFFFFF));
        out.add(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0x00FF8040));

        // DustParticleOptions / DustColorTransitionOptions: in 1.20.5 the colour is a Vector3f written as
        // three f32s (ByteBufCodecs.VECTOR3F); the packed-i32 colour form arrived in 1.21.2. Scale is an
        // f32 clamped to 0.01..4.
        out.add(new DustParticleOptions(new Vector3f(0f, 0f, 0f), 0.01f));
        out.add(new DustParticleOptions(new Vector3f(1f, 0f, 0f), 4.0f));
        out.add(new DustColorTransitionOptions(new Vector3f(0f, 0f, 0f), new Vector3f(1f, 1f, 1f), 0.01f));
        out.add(DustColorTransitionOptions.SCULK_TO_REDSTONE);

        // ItemParticleOption: an ItemStack payload (an empty stack -- AIR or count 0 -- is rejected
        // by ItemStack.STREAM_CODEC, so only non-empty stacks are emitted)
        out.add(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.STONE)));
        out.add(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.DIAMOND, 64)));

        // SculkChargeParticleOptions: single f32 roll
        out.add(new SculkChargeParticleOptions(0f));
        out.add(new SculkChargeParticleOptions(-1f));

        // ShriekParticleOption: single var-int delay
        out.add(new ShriekParticleOption(0));
        out.add(new ShriekParticleOption(16384));

        // TargetColorParticleOption (1.21.3's payload for the trail particle) does not exist in 1.20.5;
        // the trail particle itself was added in 1.21.2.

        // VibrationParticleOption: a registry-dispatched PositionSource (both branches) + var-int ticks
        out.add(new VibrationParticleOption(new BlockPositionSource(new BlockPos(0, 0, 0)), 0));
        out.add(new VibrationParticleOption(new BlockPositionSource(new BlockPos(30000000, 2031, -30000000)), 16384));
        out.add(new VibrationParticleOption(new EntityPositionSource(ctx.sampleEntity, 0f), 0));
        out.add(new VibrationParticleOption(new EntityPositionSource(ctx.sampleEntity, 3.5f), 127));

        return out;
    }

    private static Packet<?> make(ParticleOptions p, boolean overrideLimiter,
                                  double x, double y, double z,
                                  float xDist, float yDist, float zDist,
                                  float maxSpeed, int count) {
        return new ClientboundLevelParticlesPacket(p, overrideLimiter, x, y, z,
                xDist, yDist, zDist, maxSpeed, count);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<ParticleOptions> particles = particles(ctx);
        ParticleOptions base = ParticleTypes.CLOUD;

        // categorical cartesian: particle payload x overrideLimiter
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> make((ParticleOptions) a[0], (boolean) a[1],
                        0, 64, 0, 0f, 0f, 0f, 0f, 0),
                particles, ctx.bools()));

        // position doubles, one axis at a time
        for (double v : ctx.doubles()) {
            out.add(make(base, false, v, 64, 0, 0f, 0f, 0f, 0f, 0));
            out.add(make(base, false, 0, v, 0, 0f, 0f, 0f, 0f, 0));
            out.add(make(base, false, 0, 64, v, 0f, 0f, 0f, 0f, 0));
        }
        // distance floats, one axis at a time
        for (float v : ctx.floats()) {
            out.add(make(base, false, 0, 64, 0, v, 0f, 0f, 0f, 0));
            out.add(make(base, false, 0, 64, 0, 0f, v, 0f, 0f, 0));
            out.add(make(base, false, 0, 64, 0, 0f, 0f, v, 0f, 0));
        }
        // maxSpeed: 1.20.5 sends a single f32 covering all three axes
        for (float v : ctx.floats()) {
            out.add(make(base, false, 0, 64, 0, 0f, 0f, 0f, v, 0));
        }
        // count: var-int boundary spread (a superset of the i32 corner cases)
        for (int c : ctx.varInts()) {
            out.add(make(base, false, 0, 64, 0, 0f, 0f, 0f, 0f, c));
        }
        return out;
    }
}
