package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundUpdateAttributesPacket(int entityId, Collection&lt;AttributeInstance&gt; values).
 * <p>
 * Wire body: {@code entityId:varint}, then {@code attributes:list(max 128)} of AttributeSnapshot, each
 * {@code attribute:Holder<Attribute>(registry id), base:f64,
 * modifiers:collection(ArrayList) of {id:ResourceLocation, amount:f64, operation:varint enum}}.
 * <p>
 * Coverage:
 * <ul>
 *   <li>entityId over every VarInt byte-length boundary ({@code varInts()});</li>
 *   <li>outer attribute list at sizes 0/1/2, the 2-entry case using two distinct registry attributes;</li>
 *   <li>inner modifier collection at sizes 0/1/2 crossed with every {@link AttributeModifier.Operation}
 *       constant (full cartesian);</li>
 *   <li>the snapshot {@code base} double over the double edge cases;</li>
 *   <li>the modifier {@code amount} double over the double edge cases;</li>
 *   <li>the modifier {@code id} ResourceLocation over short/long/namespaced paths (ResourceLocation paths are
 *       restricted to {@code [a-z0-9/._-]}, so the astral {@code utf8()} spread is not legal here).</li>
 * </ul>
 */
public class ClientboundUpdateAttributesShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundUpdateAttributesPacket.class;
    }

    private static AttributeInstance instance(Holder<Attribute> attr, double base,
                                              List<AttributeModifier> modifiers) {
        AttributeInstance ai = new AttributeInstance(attr, a -> {});
        ai.setBaseValue(base);
        for (AttributeModifier m : modifiers) ai.addPermanentModifier(m);
        return ai;
    }

    private static List<AttributeModifier> modifiers(int count, double amount, AttributeModifier.Operation op) {
        List<AttributeModifier> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(new AttributeModifier(ResourceLocation.withDefaultNamespace("modifier_" + i), amount, op));
        }
        return out;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        List<Holder.Reference<Attribute>> attrs = ctx.registries.lookupOrThrow(Registries.ATTRIBUTE)
                .listElements().limit(2).toList();
        Holder<Attribute> a0 = attrs.get(0);
        Holder<Attribute> a1 = attrs.size() > 1 ? attrs.get(1) : attrs.get(0);

        AttributeModifier.Operation addValue = AttributeModifier.Operation.ADD_VALUE;

        // entityId over every VarInt byte-length boundary.
        for (int entityId : ctx.varInts()) {
            out.add(new ClientboundUpdateAttributesPacket(entityId,
                    List.of(instance(a0, 1.0, modifiers(0, 0.0, addValue)))));
        }

        // Outer attribute list at sizes 0 / 1 / 2 (two distinct registry attributes).
        out.add(new ClientboundUpdateAttributesPacket(1, List.of()));
        out.add(new ClientboundUpdateAttributesPacket(1,
                List.of(instance(a0, 1.0, modifiers(0, 0.0, addValue)))));
        out.add(new ClientboundUpdateAttributesPacket(1, List.of(
                instance(a0, 0.0, modifiers(0, 0.0, addValue)),
                instance(a1, 2.0, modifiers(1, 0.5, addValue)))));

        // Full cartesian: modifier-collection size 0/1/2 x every Operation constant.
        for (AttributeModifier.Operation op : ctx.enumAll(AttributeModifier.Operation.class)) {
            for (int count : ctx.counts()) {
                out.add(new ClientboundUpdateAttributesPacket(1,
                        List.of(instance(a0, 1.0, modifiers(count, 1.0, op)))));
            }
        }

        // Snapshot base value over double edges.
        for (double base : ctx.doubles()) {
            out.add(new ClientboundUpdateAttributesPacket(1,
                    List.of(instance(a0, base, modifiers(0, 0.0, addValue)))));
        }

        // Modifier amount over double edges.
        for (double amount : ctx.doubles()) {
            out.add(new ClientboundUpdateAttributesPacket(1,
                    List.of(instance(a0, 1.0, modifiers(1, amount, addValue)))));
        }

        // Modifier ResourceLocation: default namespace short/long path, and an explicit non-default namespace.
        for (ResourceLocation id : List.of(
                ResourceLocation.withDefaultNamespace("a"),
                ResourceLocation.withDefaultNamespace("modifier"),
                ResourceLocation.withDefaultNamespace("a/very/long_path.name-1"),
                ResourceLocation.fromNamespaceAndPath("example", "mod_id"))) {
            out.add(new ClientboundUpdateAttributesPacket(1,
                    List.of(instance(a0, 1.0, List.of(new AttributeModifier(id, 1.0, addValue))))));
        }

        return out;
    }
}
