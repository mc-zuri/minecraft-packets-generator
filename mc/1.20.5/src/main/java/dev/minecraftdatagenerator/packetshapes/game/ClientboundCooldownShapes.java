package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundCooldownPacket(Item item, int duration[varint]) — {@code item} is written by
 * {@code ByteBufCodecs.registry(Registries.ITEM)} as the item's numeric registry id (a varint), not as
 * an identifier: 1.20.5 puts the cooldown on a specific item, and the string {@code cooldownGroup}
 * identifier only replaced it in 1.21.2.
 * <p>
 * Coverage: the full cross of an item spread (the first, second and last entries of the item registry,
 * so the registry-id varint spans one and two bytes) with the VarInt byte-length spread for
 * {@code duration}.
 */
public class ClientboundCooldownShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundCooldownPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        // Only registered items encode: the codec writes the numeric registry id.
        List<Item> all = new ArrayList<>();
        ctx.registries.registryOrThrow(Registries.ITEM).forEach(all::add);
        List<Item> items = new ArrayList<>();
        items.add(all.get(0));
        if (all.size() > 1) items.add(all.get(1));
        if (all.size() > 2) items.add(all.get(all.size() - 1));   // a multi-byte registry id
        return ctx.cross(
                a -> new ClientboundCooldownPacket((Item) a[0], (int) a[1]),
                items, ctx.varInts());
    }
}
