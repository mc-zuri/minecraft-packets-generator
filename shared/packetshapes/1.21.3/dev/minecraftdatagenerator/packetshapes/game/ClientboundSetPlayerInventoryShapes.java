package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSetPlayerInventoryPacket(int slot, ItemStack contents) — wire body is
 * {@code slot:varInt} (ByteBufCodecs.VAR_INT) then {@code contents:OPTIONAL_ItemStack}.
 * Full cross of the VarInt byte-length boundary sweep on the slot with the item spread
 * (empty, count 1, count 64, and a stack carrying a patched data component): 13 * 4 = 52 shapes.
 */
public class ClientboundSetPlayerInventoryShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetPlayerInventoryPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<ItemStack> stacks = new ArrayList<>(ctx.items());
        ItemStack named = new ItemStack(Items.STONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("x"));
        stacks.add(named);
        return ctx.cross(
                a -> new ClientboundSetPlayerInventoryPacket((int) a[0], (ItemStack) a[1]),
                ctx.varInts(), stacks);
    }
}
