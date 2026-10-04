package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundStopSoundPacket(byte flags, [SoundSource source], [ResourceLocation name]) built from
 * (@Nullable ResourceLocation name, @Nullable SoundSource source).
 * <p>
 * The leading flags byte is an explicit discriminator with four branches: 0 = neither, 1 = source
 * only, 2 = name only, 3 = both (source is written before name). All four branches are emitted, as
 * the full cartesian product of {null + every {@link SoundSource}} x {null + a spread of identifiers}.
 * <p>
 * The identifier spread exercises the default namespace, a custom namespace and a long multi-segment
 * path; {@code utf8()} is deliberately not used here because the {@code ResourceLocation} constructor rejects
 * non-[a-z0-9_.-/] characters, so those strings would fail to decode.
 */
public class ClientboundStopSoundShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundStopSoundPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<ResourceLocation> names = new ArrayList<>();
        names.add(null);
        names.add(ctx.id("x"));
        names.add(ctx.id("block.stone.step"));
        names.add(new ResourceLocation("custom_namespace", "a/deeply/nested/sound.name-1"));

        List<SoundSource> sources = new ArrayList<>();
        sources.add(null);
        sources.addAll(ctx.enumAll(SoundSource.class));

        List<Packet<?>> out = new ArrayList<>();
        for (ResourceLocation name : names) {
            for (SoundSource source : sources) {
                out.add(new ClientboundStopSoundPacket(name, source));
            }
        }
        return out;
    }
}
