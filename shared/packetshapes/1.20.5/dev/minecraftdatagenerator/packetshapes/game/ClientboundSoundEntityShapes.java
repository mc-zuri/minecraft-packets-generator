package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSoundEntityPacket(Holder&lt;SoundEvent&gt; sound, SoundSource source [varint enum],
 * int entityId [varint], float volume [f32], float pitch [f32], long seed [i64]).
 * <p>
 * {@code SoundEvent.STREAM_CODEC} is {@code ByteBufCodecs.holder(SOUND_EVENT, DIRECT_STREAM_CODEC)},
 * so the holder has three distinguishable wire forms, all of which are emitted:
 * <ul>
 *   <li>registry reference (varint id+1),</li>
 *   <li>inline (id 0) with {@code fixedRange} absent,</li>
 *   <li>inline (id 0) with {@code fixedRange} present.</li>
 * </ul>
 * Coverage: full cross of the three holder forms x every {@link SoundSource} x the VarInt byte-length
 * boundaries of the entity id, then per-field sweeps of volume, pitch (float edges) and seed (long
 * edges).
 * <p>
 * The public constructor derives the entity id from a live {@link net.minecraft.world.entity.Entity},
 * so the packet is built from {@link ShapeCtx#sampleEntity} and the private final {@code id} field is
 * overwritten reflectively.
 */
public class ClientboundSoundEntityShapes implements PacketShapes {
    private static final Field ID;

    static {
        try {
            Field f = ClientboundSoundEntityPacket.class.getDeclaredField("id");
            f.setAccessible(true);
            ID = f;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to reflect ClientboundSoundEntityPacket.id", e);
        }
    }

    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSoundEntityPacket.class;
    }

    static List<Holder<SoundEvent>> soundHolders(ShapeCtx ctx) {
        return List.of(
                ctx.holder(Registries.SOUND_EVENT),
                Holder.direct(SoundEvent.createVariableRangeEvent(ctx.id("block.stone.step"))),
                Holder.direct(SoundEvent.createFixedRangeEvent(ctx.id("entity.generic.explode"), 16f)));
    }

    private static ClientboundSoundEntityPacket mk(ShapeCtx ctx, Holder<SoundEvent> sound, SoundSource source,
                                                   int entityId, float volume, float pitch, long seed) {
        ClientboundSoundEntityPacket packet = new ClientboundSoundEntityPacket(
                sound, source, ctx.sampleEntity, volume, pitch, seed);
        try {
            ID.setInt(packet, entityId);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set ClientboundSoundEntityPacket.id", e);
        }
        return packet;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Holder<SoundEvent>> sounds = soundHolders(ctx);
        Holder<SoundEvent> baseline = sounds.get(0);
        List<Packet<?>> out = new ArrayList<>();

        // every holder form x every SoundSource x every var-int id boundary
        for (Holder<SoundEvent> sound : sounds) {
            for (SoundSource source : ctx.enumAll(SoundSource.class)) {
                for (int id : ctx.varInts()) {
                    out.add(mk(ctx, sound, source, id, 1f, 1f, 1L));
                }
            }
        }
        // vary volume
        for (float v : ctx.floats()) {
            out.add(mk(ctx, baseline, SoundSource.MASTER, 1, v, 1f, 1L));
        }
        // vary pitch
        for (float p : ctx.floats()) {
            out.add(mk(ctx, baseline, SoundSource.MASTER, 1, 1f, p, 1L));
        }
        // vary seed
        for (long s : ctx.longs()) {
            out.add(mk(ctx, baseline, SoundSource.MASTER, 1, 1f, 1f, s));
        }
        return out;
    }
}
