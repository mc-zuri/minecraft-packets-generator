package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * ClientboundDamageEventPacket(int entityId[varint], Holder&lt;DamageType&gt; sourceType[registry varint
 * id], int sourceCauseId[varint id+1], int sourceDirectId[varint id+1],
 * Optional&lt;Vec3&gt; sourcePosition[bool + 3 x f64]).
 * <p>
 * {@code DamageType.STREAM_CODEC} is {@code ByteBufCodecs.holderRegistry}, i.e. a bare VarInt registry id
 * with no inline/direct-holder branch, so the holder dimension is just a spread of registry entries at
 * ids straddling the VarInt byte-length boundaries.
 * <p>
 * The two cause/direct ids are "optional entity ids" written as {@code id + 1}, so -1 is the "none"
 * sentinel that encodes as a single {@code 0} byte. Categorical cartesian: entity id x damage type x
 * cause id x direct id x the Optional position (both the empty and the present branch, with double
 * corner values). The three VarInt id fields are additionally swept over the full VarInt spread.
 */
public class ClientboundDamageEventShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundDamageEventPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Registry<DamageType> registry = ctx.registries.registryOrThrow(Registries.DAMAGE_TYPE);
        int size = registry.size();
        LinkedHashSet<Holder<DamageType>> types = new LinkedHashSet<>();
        for (int id : new int[]{0, 1, 127, 128, size - 1}) {
            if (id >= 0 && id < size) {
                registry.getHolder(id).ifPresent(types::add);
            }
        }
        if (types.isEmpty()) types.add(ctx.holder(Registries.DAMAGE_TYPE));

        List<Optional<Vec3>> positions = List.of(
                Optional.empty(),
                Optional.of(new Vec3(0, 0, 0)),
                Optional.of(new Vec3(1.5, -2.0, 3.0)),
                Optional.of(new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 0)));

        List<Integer> ids = List.of(-1, 0, 1);

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> {
                    @SuppressWarnings("unchecked")
                    Holder<DamageType> type = (Holder<DamageType>) a[1];
                    @SuppressWarnings("unchecked")
                    Optional<Vec3> pos = (Optional<Vec3>) a[4];
                    return new ClientboundDamageEventPacket((int) a[0], type, (int) a[2], (int) a[3], pos);
                },
                ids, new ArrayList<>(types), ids, ids, positions));

        Holder<DamageType> baseType = types.iterator().next();
        for (int entityId : ctx.varInts()) {
            out.add(new ClientboundDamageEventPacket(entityId, baseType, -1, -1, Optional.empty()));
        }
        for (int cause : ctx.varInts()) {
            out.add(new ClientboundDamageEventPacket(0, baseType, cause, -1, Optional.empty()));
        }
        for (int direct : ctx.varInts()) {
            out.add(new ClientboundDamageEventPacket(0, baseType, -1, direct, Optional.empty()));
        }
        return out;
    }
}
