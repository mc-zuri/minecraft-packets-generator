package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSoundPacket(Holder&lt;SoundEvent&gt; sound, SoundSource source [varint enum], int x [i32],
 * int y [i32], int z [i32], float volume [f32], float pitch [f32], long seed [i64]).
 * <p>
 * The constructor takes doubles and quantizes them to fixed-point ints ({@code (int)(v * 8.0)}), so the
 * sweeps below pass {@code block / 8.0 * 8.0} block coordinates and land on exact wire values.
 * {@code SoundEvent.STREAM_CODEC} is {@code ByteBufCodecs.holder(...)}, giving three holder wire forms
 * (registry reference, inline without fixedRange, inline with fixedRange) — all three are emitted.
 * <p>
 * Coverage: three holder forms x every {@link SoundSource}; then per-field sweeps of x/z over the
 * world-border horizontals, y over the legal world Y range, and volume/pitch/seed over the numeric
 * edge cases.
 */
public class ClientboundSoundShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSoundPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Holder<SoundEvent>> sounds = ClientboundSoundEntityShapes.soundHolders(ctx);
        Holder<SoundEvent> baseline = sounds.get(0);
        List<Packet<?>> out = new ArrayList<>();

        // every holder form x every SoundSource
        for (Holder<SoundEvent> sound : sounds) {
            for (SoundSource source : ctx.enumAll(SoundSource.class)) {
                out.add(new ClientboundSoundPacket(sound, source, 0, 0, 0, 1f, 1f, 1L));
            }
        }
        // vary x / z over the world-border horizontals (x8 still fits in the i32 field)
        for (int h : ctx.horizontals()) {
            out.add(new ClientboundSoundPacket(baseline, SoundSource.MASTER, h, 0, 0, 1f, 1f, 1L));
            out.add(new ClientboundSoundPacket(baseline, SoundSource.MASTER, 0, 0, h, 1f, 1f, 1L));
        }
        // vary y over the legal world Y range
        for (int y : ctx.worldYs()) {
            out.add(new ClientboundSoundPacket(baseline, SoundSource.MASTER, 0, y, 0, 1f, 1f, 1L));
        }
        // sub-block (fractional) positions, to pin the x8 fixed-point quantization
        out.add(new ClientboundSoundPacket(baseline, SoundSource.MASTER, 0.125, 0.5, -0.375, 1f, 1f, 1L));
        out.add(new ClientboundSoundPacket(baseline, SoundSource.MASTER, -1.5, 63.25, 2.75, 1f, 1f, 1L));
        // vary volume
        for (float v : ctx.floats()) {
            out.add(new ClientboundSoundPacket(baseline, SoundSource.MASTER, 0, 0, 0, v, 1f, 1L));
        }
        // vary pitch
        for (float p : ctx.floats()) {
            out.add(new ClientboundSoundPacket(baseline, SoundSource.MASTER, 0, 0, 0, 1f, p, 1L));
        }
        // vary seed
        for (long s : ctx.longs()) {
            out.add(new ClientboundSoundPacket(baseline, SoundSource.MASTER, 0, 0, 0, 1f, 1f, s));
        }
        return out;
    }
}
