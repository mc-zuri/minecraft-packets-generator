package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ClientboundUpdateAttributesPacket(int entityId, Collection&lt;AttributeInstance&gt; values).
 * <p>
 * Wire body: {@code entityId:varint}, then {@code attributes:list(max 128)} of AttributeSnapshot, each
 * {@code attribute:Holder<Attribute>(registry id), base:f64,
 * modifiers:collection(ArrayList) of {id:uuid(2x i64), amount:f64, operation:varint enum}}.
 * <p>
 * In 1.20.5 a modifier is identified by a raw 16-byte UUID and its {@code name} String is not written
 * at all (the decoder substitutes a fixed placeholder); 1.20.5 replaced the UUID with a
 * {@code ResourceLocation} and dropped the name field from the record, which is why the id dimension
 * here is a UUID sweep rather than an identifier sweep.
 * <p>
 * Coverage:
 * <ul>
 *   <li>entityId over every VarInt byte-length boundary ({@code varInts()});</li>
 *   <li>outer attribute list at sizes 0/1/2, the 2-entry case using two distinct registry attributes;</li>
 *   <li>inner modifier collection at sizes 0/1/2 crossed with every {@link AttributeModifier.Operation}
 *       constant (full cartesian);</li>
 *   <li>the snapshot {@code base} double over the double edge cases;</li>
 *   <li>the modifier {@code amount} double over the double edge cases;</li>
 *   <li>the modifier {@code id} UUID over both long-pair extremes (the {@code name} String is not on
 *       the wire, so it has no dimension).</li>
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
            // Distinct UUIDs: AttributeInstance keys its modifiers by UUID in 1.20.5.
            out.add(new AttributeModifier(new UUID(0L, i + 1), "modifier_" + i, amount, op));
        }
        return out;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        List<Holder.Reference<Attribute>> attrs = ctx.registries.registryOrThrow(Registries.ATTRIBUTE)
                .holders().limit(2).toList();
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

        // Modifier UUID: the zero uuid and both long-pair extremes.
        for (UUID id : List.of(new UUID(0L, 0L), new UUID(1L, 2L),
                new UUID(Long.MIN_VALUE, Long.MAX_VALUE), new UUID(-1L, -1L))) {
            out.add(new ClientboundUpdateAttributesPacket(1,
                    List.of(instance(a0, 1.0, List.of(new AttributeModifier(id, "m", 1.0, addValue))))));
        }

        return out;
    }
}
