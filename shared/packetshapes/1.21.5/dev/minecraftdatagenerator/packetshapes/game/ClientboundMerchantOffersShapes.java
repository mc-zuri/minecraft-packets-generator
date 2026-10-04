package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * ClientboundMerchantOffersPacket(int containerId[varint], MerchantOffers offers, int villagerLevel
 * [varint], int villagerXp[varint], boolean showProgress, boolean canRestock).
 * <p>
 * {@code offers} is a length-prefixed collection of {@link MerchantOffer}, each written as
 * {@code ItemCost baseCostA, ItemStack result, Optional<ItemCost> costB, bool outOfStock, i32 uses,
 * i32 maxUses, i32 xp, i32 specialPriceDiff, f32 priceMultiplier, i32 demand}. An {@code ItemCost} is
 * {@code registry-id item, varint count, DataComponentExactPredicate components} (itself a
 * length-prefixed list of typed components).
 * <p>
 * Note that {@code outOfStock} is not a stored field: the writer emits {@code uses >= maxUses}, so both
 * branches are produced by choosing the uses/maxUses pair. {@code specialPriceDiff} is likewise only
 * reachable through {@code setSpecialPriceDiff}.
 * <p>
 * Coverage: offer-list cardinality 0/1/2/3 crossed with both booleans; per-offer sweeps of the costB
 * Optional (both branches), the outOfStock boolean (both branches), the result ItemStack spread, the
 * ItemCost count VarInt boundaries and component-predicate cardinality 0/1/2, and the i32/f32 scalar
 * corner cases of uses, maxUses, xp, specialPriceDiff, priceMultiplier and demand; plus VarInt sweeps
 * of containerId, villagerLevel and villagerXp.
 */
public class ClientboundMerchantOffersShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundMerchantOffersPacket.class;
    }

    private static MerchantOffer offer(ItemCost costA, Optional<ItemCost> costB, ItemStack result,
                                       int uses, int maxUses, int xp, float priceMultiplier, int demand,
                                       int specialPriceDiff) {
        MerchantOffer o = new MerchantOffer(costA, costB, result, uses, maxUses, xp, priceMultiplier, demand);
        o.setSpecialPriceDiff(specialPriceDiff);
        return o;
    }

    private static MerchantOffer base() {
        return offer(new ItemCost(Items.STONE), Optional.empty(), new ItemStack(Items.DIAMOND), 0, 4, 1, 0.05f, 0, 0);
    }

    private static MerchantOffer withCostB() {
        return offer(new ItemCost(Items.STONE, 2), Optional.of(new ItemCost(Items.DIRT)),
                new ItemStack(Items.DIAMOND, 3), 1, 7, 5, 0.5f, 2, -1);
    }

    private static MerchantOffers offers(MerchantOffer... entries) {
        MerchantOffers o = new MerchantOffers();
        for (MerchantOffer e : entries) o.add(e);
        return o;
    }

    private static MerchantOffers one(MerchantOffer e) {
        return offers(e);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        // Collection cardinality x showProgress x canRestock (the categorical dimensions).
        List<Supplier<MerchantOffers>> offerVariants = List.of(
                () -> offers(),
                () -> offers(base()),
                () -> offers(withCostB()),
                () -> offers(base(), withCostB()),
                () -> offers(base(), withCostB(), base()));
        for (Supplier<MerchantOffers> variant : offerVariants) {
            for (boolean showProgress : ctx.bools()) {
                for (boolean canRestock : ctx.bools()) {
                    out.add(new ClientboundMerchantOffersPacket(0, variant.get(), 1, 0, showProgress, canRestock));
                }
            }
        }

        // Per-offer field sweeps, each in a single-offer list.
        List<MerchantOffer> single = new ArrayList<>();
        // costB Optional: both branches, and a costB with its own count/components.
        single.add(offer(new ItemCost(Items.STONE), Optional.empty(), new ItemStack(Items.DIAMOND), 0, 4, 1, 0f, 0, 0));
        single.add(offer(new ItemCost(Items.STONE), Optional.of(new ItemCost(Items.DIRT, 5)),
                new ItemStack(Items.DIAMOND), 0, 4, 1, 0f, 0, 0));
        // outOfStock: written as uses >= maxUses, so both branches.
        single.add(offer(new ItemCost(Items.STONE), Optional.empty(), new ItemStack(Items.DIAMOND), 0, 4, 1, 0f, 0, 0));
        single.add(offer(new ItemCost(Items.STONE), Optional.empty(), new ItemStack(Items.DIAMOND), 4, 4, 1, 0f, 0, 0));
        // result ItemStack spread. `result` uses the non-optional ItemStack.STREAM_CODEC, which
        // throws "Empty ItemStack not allowed", so ItemStack.EMPTY is skipped here.
        for (ItemStack result : ctx.items()) {
            if (result.isEmpty()) continue;
            single.add(offer(new ItemCost(Items.STONE), Optional.empty(), result, 0, 4, 1, 0f, 0, 0));
        }
        // ItemCost count: VarInt boundaries that ItemStack can still hold.
        for (int count : List.of(0, 1, 2, 64, 127, 128, 16383, 16384)) {
            single.add(offer(new ItemCost(Items.STONE.builtInRegistryHolder(), count,
                            net.minecraft.core.component.DataComponentExactPredicate.EMPTY),
                    Optional.empty(), new ItemStack(Items.DIAMOND), 0, 4, 1, 0f, 0, 0));
        }
        // ItemCost component predicate: cardinality 0 / 1 / 2.
        single.add(offer(new ItemCost(Items.STONE).withComponents(b -> b.expect(DataComponents.DAMAGE, 5)),
                Optional.empty(), new ItemStack(Items.DIAMOND), 0, 4, 1, 0f, 0, 0));
        single.add(offer(new ItemCost(Items.STONE).withComponents(
                        b -> b.expect(DataComponents.DAMAGE, 5).expect(DataComponents.MAX_DAMAGE, 10)),
                Optional.empty(), new ItemStack(Items.DIAMOND), 0, 4, 1, 0f, 0, 0));
        // i32 scalar corners: uses, maxUses, xp, specialPriceDiff, demand.
        for (int v : ctx.ints()) {
            single.add(offer(new ItemCost(Items.STONE), Optional.empty(), new ItemStack(Items.DIAMOND), v, Integer.MAX_VALUE, 1, 0f, 0, 0));
            single.add(offer(new ItemCost(Items.STONE), Optional.empty(), new ItemStack(Items.DIAMOND), Integer.MIN_VALUE, v, 1, 0f, 0, 0));
            single.add(offer(new ItemCost(Items.STONE), Optional.empty(), new ItemStack(Items.DIAMOND), 0, 4, v, 0f, 0, 0));
            single.add(offer(new ItemCost(Items.STONE), Optional.empty(), new ItemStack(Items.DIAMOND), 0, 4, 1, 0f, v, 0));
            single.add(offer(new ItemCost(Items.STONE), Optional.empty(), new ItemStack(Items.DIAMOND), 0, 4, 1, 0f, 0, v));
        }
        // f32 priceMultiplier corners.
        for (float f : ctx.floats()) {
            single.add(offer(new ItemCost(Items.STONE), Optional.empty(), new ItemStack(Items.DIAMOND), 0, 4, 1, f, 0, 0));
        }
        for (MerchantOffer o : single) {
            out.add(new ClientboundMerchantOffersPacket(0, one(o), 1, 0, false, false));
        }

        // Packet-level VarInt sweeps.
        for (int containerId : ctx.varInts())
            out.add(new ClientboundMerchantOffersPacket(containerId, offers(base()), 1, 0, false, false));
        for (int level : ctx.varInts())
            out.add(new ClientboundMerchantOffersPacket(0, offers(base()), level, 0, false, false));
        for (int xp : ctx.varInts())
            out.add(new ClientboundMerchantOffersPacket(0, offers(base()), 1, xp, false, false));
        return out;
    }
}
