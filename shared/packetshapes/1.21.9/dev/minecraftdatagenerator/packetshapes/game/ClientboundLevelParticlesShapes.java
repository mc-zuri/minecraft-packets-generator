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
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.core.particles.SpellParticleOption;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundLevelParticlesPacket(ParticleOptions particle, boolean overrideLimiter, boolean alwaysShow,
 * double x, double y, double z, float xDist, float yDist, float zDist, float maxSpeed, int count).
 * <p>
 * Wire order (it differs from the constructor order): {@code overrideLimiter} bool,
 * {@code alwaysShow} bool, {@code x}/{@code y}/{@code z} f64, {@code xDist}/{@code yDist}/{@code zDist}
 * f32, a single {@code maxSpeed} f32, {@code count} as a plain big-endian <b>i32</b> (not a var-int),
 * then the registry-dispatched {@code particle} payload last.
 * <p>
 * {@code particle} is dispatched on {@code minecraft:particle_type}, with a per-type payload.
 * Covered: the categorical cartesian of {@code overrideLimiter} x {@code alwaysShow} x every distinct
 * particle payload class (simple/empty, BlockParticleOption, ColorParticleOption, DustParticleOptions,
 * DustColorTransitionOptions, ItemParticleOption, PowerParticleOption, SculkChargeParticleOptions, ShriekParticleOption, SpellParticleOption,
 * TrailParticleOption and VibrationParticleOption in both of its PositionSource dispatch branches),
 * then per-field sweeps for the position doubles, each distance float, the single {@code maxSpeed}
 * float, and {@code count} over the var-int boundary spread (a superset of the plain i32 corner cases,
 * which is what this field actually is).
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

        // BlockParticleOption: a block-state id var-int; all five particle types that use it
        out.add(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()));
        out.add(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, Blocks.AIR.defaultBlockState()));
        out.add(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()));
        out.add(new BlockParticleOption(ParticleTypes.DUST_PILLAR, Blocks.MUD.defaultBlockState()));
        out.add(new BlockParticleOption(ParticleTypes.BLOCK_CRUMBLE, Blocks.DIRT.defaultBlockState()));

        // ColorParticleOption: packed ARGB i32
        out.add(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0));
        out.add(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, -1));
        out.add(ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0x7FFFFFFF));
        out.add(ColorParticleOption.create(ParticleTypes.FLASH, 0x00FF8040));

        // DustParticleOptions / DustColorTransitionOptions: colour i32(s) + f32 scale (clamped 0.01..4)
        out.add(new DustParticleOptions(0, 0.01f));
        out.add(new DustParticleOptions(0xFF0000, 4.0f));
        out.add(new DustColorTransitionOptions(0, -1, 0.01f));
        out.add(DustColorTransitionOptions.SCULK_TO_REDSTONE);

        // ItemParticleOption: an ItemStack payload (an empty stack -- AIR or count 0 -- is rejected
        // by ItemStack.STREAM_CODEC, so only non-empty stacks are emitted)
        out.add(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.STONE)));
        out.add(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.DIAMOND, 64)));

        // PowerParticleOption: single f32
        out.add(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 0f));
        out.add(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, -1f));

        // SpellParticleOption: colour + power
        out.add(SpellParticleOption.create(ParticleTypes.EFFECT, 0, 0f));
        out.add(SpellParticleOption.create(ParticleTypes.INSTANT_EFFECT, -1, 3.5f));

        // SculkChargeParticleOptions: single f32 roll
        out.add(new SculkChargeParticleOptions(0f));
        out.add(new SculkChargeParticleOptions(-1f));

        // ShriekParticleOption: single var-int delay
        out.add(new ShriekParticleOption(0));
        out.add(new ShriekParticleOption(16384));

        // TrailParticleOption: Vec3 target + colour i32 + var-int duration
        out.add(new TrailParticleOption(new Vec3(0, 0, 0), 0, 1));
        out.add(new TrailParticleOption(new Vec3(-3.0E7, 319, 3.0E7), -1, 16384));

        // VibrationParticleOption: a registry-dispatched PositionSource (both branches) + var-int ticks
        out.add(new VibrationParticleOption(new BlockPositionSource(new BlockPos(0, 0, 0)), 0));
        out.add(new VibrationParticleOption(new BlockPositionSource(new BlockPos(30000000, 2031, -30000000)), 16384));
        out.add(new VibrationParticleOption(new EntityPositionSource(ctx.sampleEntity, 0f), 0));
        out.add(new VibrationParticleOption(new EntityPositionSource(ctx.sampleEntity, 3.5f), 127));

        return out;
    }

    private static Packet<?> make(ParticleOptions p, boolean overrideLimiter, boolean alwaysShow,
                                  double x, double y, double z,
                                  float xDist, float yDist, float zDist,
                                  float maxSpeed, int count) {
        return new ClientboundLevelParticlesPacket(p, overrideLimiter, alwaysShow, x, y, z,
                xDist, yDist, zDist, maxSpeed, count);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<ParticleOptions> particles = particles(ctx);
        ParticleOptions base = ParticleTypes.CLOUD;

        // categorical cartesian: particle payload x overrideLimiter x alwaysShow
        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> make((ParticleOptions) a[0], (boolean) a[1], (boolean) a[2],
                        0, 64, 0, 0f, 0f, 0f, 0f, 0),
                particles, ctx.bools(), ctx.bools()));

        // position doubles, one axis at a time
        for (double v : ctx.doubles()) {
            out.add(make(base, false, false, v, 64, 0, 0f, 0f, 0f, 0f, 0));
            out.add(make(base, false, false, 0, v, 0, 0f, 0f, 0f, 0f, 0));
            out.add(make(base, false, false, 0, 64, v, 0f, 0f, 0f, 0f, 0));
        }
        // distance floats, one axis at a time
        for (float v : ctx.floats()) {
            out.add(make(base, false, false, 0, 64, 0, v, 0f, 0f, 0f, 0));
            out.add(make(base, false, false, 0, 64, 0, 0f, v, 0f, 0f, 0));
            out.add(make(base, false, false, 0, 64, 0, 0f, 0f, v, 0f, 0));
        }
        // maxSpeed: 26.2 sends a single f32 covering all three axes
        for (float v : ctx.floats()) {
            out.add(make(base, false, false, 0, 64, 0, 0f, 0f, 0f, v, 0));
        }
        // count: var-int boundary spread (a superset of the i32 corner cases)
        for (int c : ctx.varInts()) {
            out.add(make(base, false, false, 0, 64, 0, 0f, 0f, 0f, 0f, c));
        }
        return out;
    }
}
