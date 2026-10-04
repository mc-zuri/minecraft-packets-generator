package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>Discriminator packet.</b> Wire fields in order:
 * {@code fromAnchor:enum(varint), x:f64, y:f64, z:f64, atEntity:bool}
 * and, only when {@code atEntity} is true, {@code entity:varint, toAnchor:enum(varint)}.
 * <ul>
 *   <li>{@code atEntity=false}: ctor(fromAnchor, x, y, z).</li>
 *   <li>{@code atEntity=true}: ctor(fromAnchor, entity, toAnchor) — the three doubles are derived from
 *       the entity's position through {@code toAnchor}.</li>
 * </ul>
 * Coverage: every {@link EntityAnchorArgument.Anchor} constant crossed with the {@code atEntity=false}
 * position spread (x/z over {@link ShapeCtx#horizontals()} and the double edge cases, y over
 * {@link ShapeCtx#worldYs()}), plus the full {@code fromAnchor} x {@code toAnchor} cartesian for the
 * {@code atEntity=true} branch, crossed there with the VarInt byte-length boundaries of {@code entity}.
 * <p>
 * The true-branch constructor takes a live {@link net.minecraft.world.entity.Entity}, so the packet is
 * built from {@link ShapeCtx#sampleEntity} and the private final {@code entity} field is then
 * overwritten reflectively — the same technique {@code ClientboundPlayerInfoUpdateShapes} uses.
 */
public class ClientboundPlayerLookAtShapes implements PacketShapes {
    private static final Field ENTITY;

    static {
        try {
            Field f = ClientboundPlayerLookAtPacket.class.getDeclaredField("entity");
            f.setAccessible(true);
            ENTITY = f;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to reflect ClientboundPlayerLookAtPacket.entity", e);
        }
    }

    private static ClientboundPlayerLookAtPacket atEntity(ShapeCtx ctx, EntityAnchorArgument.Anchor from,
                                                          EntityAnchorArgument.Anchor to, int entityId) {
        ClientboundPlayerLookAtPacket packet =
                new ClientboundPlayerLookAtPacket(from, ctx.sampleEntity, to);
        try {
            ENTITY.setInt(packet, entityId);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set ClientboundPlayerLookAtPacket.entity", e);
        }
        return packet;
    }

    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlayerLookAtPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        List<EntityAnchorArgument.Anchor> anchors = ctx.enumAll(EntityAnchorArgument.Anchor.class);

        // Position spread for the atEntity = false branch: one field swept at a time.
        List<double[]> positions = new ArrayList<>();
        positions.add(new double[]{0, 0, 0});
        positions.add(new double[]{0.5, 63.5, -0.5});
        for (int x : ctx.horizontals()) positions.add(new double[]{x, 64, 0});
        for (int y : ctx.worldYs()) positions.add(new double[]{0, y, 0});
        for (int z : ctx.horizontals()) positions.add(new double[]{0, 64, z});
        for (double d : ctx.doubles()) positions.add(new double[]{d, d, d});

        for (EntityAnchorArgument.Anchor from : anchors) {
            for (double[] p : positions) {
                out.add(new ClientboundPlayerLookAtPacket(from, p[0], p[1], p[2]));
            }
        }
        // atEntity = true branch: full anchor x anchor x var-int entity-id cartesian.
        for (EntityAnchorArgument.Anchor from : anchors) {
            for (EntityAnchorArgument.Anchor to : anchors) {
                for (int entityId : ctx.varInts()) {
                    out.add(atEntity(ctx, from, to, entityId));
                }
            }
        }
        return out;
    }
}
