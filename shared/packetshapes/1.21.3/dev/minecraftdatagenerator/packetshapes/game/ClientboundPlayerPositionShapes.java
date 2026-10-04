package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * ClientboundPlayerPositionPacket(int id, PositionMoveRotation change, Set&lt;Relative&gt; relatives).
 * Wire fields: {@code id:varint}, then {@code PositionMoveRotation} =
 * {@code position:3xf64, deltaMovement:3xf64, yRot:f32, xRot:f32}, then {@code relatives:i32} — a packed
 * bit mask over the nine {@link Relative} constants.
 * <p>
 * Coverage: the relatives bit field gets the empty set, every single bit on its own and the full set
 * (rule 1 for bit flags), crossed with a per-field sweep of the {@code PositionMoveRotation} components
 * (position X/Z over {@link ShapeCtx#horizontals()}, position Y over {@link ShapeCtx#worldYs()}, delta over
 * the double edges, yRot over {@link ShapeCtx#rotations()}, xRot over {@link ShapeCtx#pitches()}) and two
 * entity ids. The {@code id} varint gets its own boundary sweep.
 */
public class ClientboundPlayerPositionShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlayerPositionPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        PositionMoveRotation base = new PositionMoveRotation(new Vec3(0, 64, 0), Vec3.ZERO, 0f, 0f);

        List<PositionMoveRotation> changes = new ArrayList<>();
        changes.add(base);
        for (int x : ctx.horizontals()) changes.add(new PositionMoveRotation(new Vec3(x, 64, 0), Vec3.ZERO, 0f, 0f));
        for (int y : ctx.worldYs()) changes.add(new PositionMoveRotation(new Vec3(0, y, 0), Vec3.ZERO, 0f, 0f));
        for (int z : ctx.horizontals()) changes.add(new PositionMoveRotation(new Vec3(0, 64, z), Vec3.ZERO, 0f, 0f));
        for (double d : ctx.doubles()) changes.add(new PositionMoveRotation(new Vec3(0, 64, 0), new Vec3(d, d, d), 0f, 0f));
        for (float yRot : ctx.rotations()) changes.add(new PositionMoveRotation(new Vec3(0, 64, 0), Vec3.ZERO, yRot, 0f));
        for (float xRot : ctx.pitches()) changes.add(new PositionMoveRotation(new Vec3(0, 64, 0), Vec3.ZERO, 0f, xRot));

        // Bit-flag dimension: none, every single bit, all bits.
        List<Set<Relative>> relatives = new ArrayList<>();
        relatives.add(Set.of());
        for (Relative r : Relative.values()) relatives.add(EnumSet.of(r));
        relatives.add(Relative.ALL);

        List<Packet<?>> out = new ArrayList<>();
        for (int id : List.of(0, 1)) {
            for (PositionMoveRotation change : changes) {
                for (Set<Relative> rel : relatives) {
                    out.add(new ClientboundPlayerPositionPacket(id, change, rel));
                }
            }
        }
        // VarInt boundary sweep on the teleport id.
        for (int id : ctx.varInts()) {
            out.add(new ClientboundPlayerPositionPacket(id, base, Relative.ALL));
        }
        return out;
    }
}
