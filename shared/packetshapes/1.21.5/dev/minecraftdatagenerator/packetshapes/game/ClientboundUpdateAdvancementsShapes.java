package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import io.netty.buffer.Unpooled;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ClientboundUpdateAdvancementsPacket(boolean reset, Collection&lt;AdvancementHolder&gt; added,
 * Set&lt;ResourceLocation&gt; removed, Map&lt;ResourceLocation, AdvancementProgress&gt; progress,
 * boolean showAdvancements).
 * <p>
 * Wire body:
 * <pre>
 * reset             : bool
 * added             : list&lt;AdvancementHolder&gt;
 *   id              : ResourceLocation
 *   value           : parent : Optional&lt;ResourceLocation&gt;
 *                     display : Optional&lt;DisplayInfo&gt;
 *                     requirements : list&lt;list&lt;string&gt;&gt;
 *                     sendsTelemetryEvent : bool
 * removed           : collection&lt;ResourceLocation&gt;
 * progress          : map&lt;ResourceLocation, AdvancementProgress&gt;
 *                       criteria : map&lt;string, Nullable&lt;Instant as i64 millis&gt;&gt;
 * showAdvancements  : bool
 * </pre>
 * DisplayInfo is hand-rolled: title, description, ItemStack icon, AdvancementType (as an
 * enum var-int), an <b>i32</b> flags field (bit 1 = background present, bit 2 = showToast,
 * bit 4 = hidden), then the background ResourceLocation when bit 1 is set, then the f32 x and f32 y
 * layout coordinates. In 1.21.11 x/y live inside DisplayInfo (there is no PositionedAdvancement
 * wrapper, which 26.3 introduced), so the float sweep is applied through
 * {@link DisplayInfo#setLocation(float, float)}.
 * <p>
 * Coverage:
 * <ul>
 *   <li>reset x showAdvancements (full cross);</li>
 *   <li>added / removed / progress collections at sizes 0, 1 and 2;</li>
 *   <li>the advancement parent Optional in both forms, its telemetry bool in both forms, and its
 *       requirements at outer sizes 0/1/2 with inner lists of size 0/1/2;</li>
 *   <li>the display Optional in both forms, and when present the full cross of every
 *       {@link AdvancementType} with all eight flag-bit combinations, plus icon variants covering
 *       count and a non-empty / removal DataComponentPatch;</li>
 *   <li>AdvancementProgress criteria maps of size 0/1/2 with the CriterionProgress obtained-Instant
 *       nullable in both forms;</li>
 *   <li>DisplayInfo x/y over the float edge cases.</li>
 * </ul>
 * <p>
 * NOTE: {@code Advancement.name} and {@code DisplayInfo.announceToChat} are not written by the stream
 * codecs (the decoder reconstructs them), so they are held constant. {@code AdvancementRewards} is
 * likewise not on the wire and is always {@code AdvancementRewards.EMPTY}. An empty
 * {@code ItemStack} (AIR, or count 0) is rejected by {@code ItemStack.STREAM_CODEC}, so those are
 * omitted.
 */
public class ClientboundUpdateAdvancementsShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundUpdateAdvancementsPacket.class;
    }

    private static Advancement advancement(Optional<ResourceLocation> parent, Optional<DisplayInfo> display,
                                           AdvancementRequirements requirements, boolean telemetry) {
        return new Advancement(parent, display, AdvancementRewards.EMPTY, Map.of(),
                requirements, telemetry, Optional.empty());
    }

    private static List<ItemStack> icons() {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(Items.STONE));
        out.add(new ItemStack(Items.STONE, 99));
        out.add(new ItemStack(Items.STONE.builtInRegistryHolder(), 1,
                DataComponentPatch.builder().set(DataComponents.ITEM_NAME, Component.literal("x")).build()));
        out.add(new ItemStack(Items.STONE.builtInRegistryHolder(), 1,
                DataComponentPatch.builder().remove(DataComponents.ITEM_NAME).build()));
        return out;
    }

    private static List<AdvancementRequirements> requirements() {
        return List.of(
                AdvancementRequirements.EMPTY,                                  // outer size 0
                new AdvancementRequirements(List.of(List.of())),                // outer 1, inner 0
                new AdvancementRequirements(List.of(List.of("a"))),             // outer 1, inner 1
                new AdvancementRequirements(List.of(List.of("a", "b"))),        // outer 1, inner 2
                new AdvancementRequirements(List.of(List.of("a"), List.of("b", "c")))); // outer 2
    }

    /**
     * AdvancementProgress has no public map constructor, and {@code grantProgress} would stamp a
     * non-reproducible {@code Instant.now()}. Build it deterministically by encoding the criteria map
     * by hand and decoding it through the real network form
     * ({@code map<string, Nullable<Instant as i64 millis>>}). A null value means "not obtained".
     */
    private static AdvancementProgress progress(Map<String, Long> criteria) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(criteria.size());
        for (Map.Entry<String, Long> e : criteria.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeBoolean(e.getValue() != null);
            if (e.getValue() != null) buf.writeLong(e.getValue());
        }
        return AdvancementProgress.fromNetwork(buf);
    }

    private static DisplayInfo display(ItemStack icon, Component title, Component description,
                                       Optional<ClientAsset> background,
                                       AdvancementType type, boolean showToast, boolean hidden,
                                       float x, float y) {
        DisplayInfo info = new DisplayInfo(icon, title, description, background, type,
                showToast, false, hidden);
        info.setLocation(x, y);
        return info;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        ResourceLocation idA = ResourceLocation.withDefaultNamespace("adv_a");
        ResourceLocation idB = ResourceLocation.withDefaultNamespace("adv_b");
        ResourceLocation background = ResourceLocation.withDefaultNamespace("gui/advancements/backgrounds/stone");

        // ---- reset x showAdvancements, all collections empty ----------------------------------
        for (boolean reset : ctx.bools()) {
            for (boolean show : ctx.bools()) {
                out.add(new ClientboundUpdateAdvancementsPacket(reset, List.of(), Set.of(), Map.of(), show));
            }
        }

        // ---- removed collection at sizes 1 and 2 ----------------------------------------------
        out.add(new ClientboundUpdateAdvancementsPacket(false, List.of(),
                new LinkedHashSet<>(List.of(idA)), Map.of(), true));
        out.add(new ClientboundUpdateAdvancementsPacket(false, List.of(),
                new LinkedHashSet<>(List.of(idA, idB)), Map.of(), true));

        // ---- progress map at sizes 1 and 2, criteria maps 0/1/2, obtained present/absent -------
        List<Map<String, Long>> criteriaSpread = new ArrayList<>();
        criteriaSpread.add(new LinkedHashMap<>());                       // criteria map size 0
        Map<String, Long> notObtained = new LinkedHashMap<>();
        notObtained.put("a", null);                                      // size 1, Instant absent
        criteriaSpread.add(notObtained);
        Map<String, Long> obtained = new LinkedHashMap<>();
        obtained.put("a", 0L);                                           // size 1, Instant present
        criteriaSpread.add(obtained);
        Map<String, Long> two = new LinkedHashMap<>();                   // size 2, both branches
        two.put("a", 1L);
        two.put("b", null);
        criteriaSpread.add(two);

        for (Map<String, Long> criteria : criteriaSpread) {
            Map<ResourceLocation, AdvancementProgress> one = new LinkedHashMap<>();
            one.put(idA, progress(criteria));
            out.add(new ClientboundUpdateAdvancementsPacket(false, List.of(), Set.of(), one, true));

            Map<ResourceLocation, AdvancementProgress> pair = new LinkedHashMap<>();
            pair.put(idA, progress(criteria));
            pair.put(idB, progress(criteria));
            out.add(new ClientboundUpdateAdvancementsPacket(false, List.of(), Set.of(), pair, true));
        }

        // ---- added list: advancements without display -----------------------------------------
        List<AdvancementHolder> plain = new ArrayList<>();
        for (Optional<ResourceLocation> parent : List.of(Optional.<ResourceLocation>empty(), Optional.of(idA))) {
            for (AdvancementRequirements req : requirements()) {
                for (boolean telemetry : ctx.bools()) {
                    AdvancementHolder holder = new AdvancementHolder(idB,
                            advancement(parent, Optional.empty(), req, telemetry));
                    plain.add(holder);
                    out.add(new ClientboundUpdateAdvancementsPacket(false, List.of(holder),
                            Set.of(), Map.of(), true));
                }
            }
        }

        // ---- added list: advancements with display -------------------------------------------
        // AdvancementType x the 8 flag combinations (background / showToast / hidden).
        List<ItemStack> icons = icons();
        List<AdvancementHolder> displayed = new ArrayList<>();
        for (AdvancementType type : ctx.enumAll(AdvancementType.class)) {
            for (boolean hasBackground : ctx.bools()) {
                for (boolean showToast : ctx.bools()) {
                    for (boolean hidden : ctx.bools()) {
                        DisplayInfo info = display(icons.get(0),
                                Component.literal("title"),
                                Component.translatable("description"),
                                hasBackground
                                        ? Optional.of(new ClientAsset(background))
                                        : Optional.empty(),
                                type, showToast, hidden, 0f, 0f);
                        AdvancementHolder holder = new AdvancementHolder(idA,
                                advancement(Optional.empty(), Optional.of(info),
                                        AdvancementRequirements.EMPTY, false));
                        displayed.add(holder);
                        out.add(new ClientboundUpdateAdvancementsPacket(false, List.of(holder),
                                Set.of(), Map.of(), true));
                    }
                }
            }
        }
        // icon variants (count and DataComponentPatch branches) and component variants on the display
        for (ItemStack icon : icons) {
            for (Component text : ctx.components()) {
                DisplayInfo info = display(icon, text, text, Optional.empty(),
                        AdvancementType.TASK, true, false, 0f, 0f);
                out.add(new ClientboundUpdateAdvancementsPacket(false,
                        List.of(new AdvancementHolder(idA,
                                advancement(Optional.empty(), Optional.of(info),
                                        AdvancementRequirements.EMPTY, false))),
                        Set.of(), Map.of(), true));
            }
        }

        // ---- added list size 2 (one plain, one displayed) -------------------------------------
        out.add(new ClientboundUpdateAdvancementsPacket(false,
                List.of(plain.get(0), displayed.get(0)), Set.of(), Map.of(), true));

        // ---- DisplayInfo x/y over the float corner cases --------------------------------------
        for (float f : ctx.floats()) {
            DisplayInfo xInfo = display(icons.get(0), Component.literal("title"),
                    Component.literal("description"), Optional.empty(),
                    AdvancementType.TASK, true, false, f, 0f);
            out.add(new ClientboundUpdateAdvancementsPacket(false,
                    List.of(new AdvancementHolder(idA, advancement(Optional.empty(),
                            Optional.of(xInfo), AdvancementRequirements.EMPTY, false))),
                    Set.of(), Map.of(), true));

            DisplayInfo yInfo = display(icons.get(0), Component.literal("title"),
                    Component.literal("description"), Optional.empty(),
                    AdvancementType.TASK, true, false, 0f, f);
            out.add(new ClientboundUpdateAdvancementsPacket(false,
                    List.of(new AdvancementHolder(idA, advancement(Optional.empty(),
                            Optional.of(yInfo), AdvancementRequirements.EMPTY, false))),
                    Set.of(), Map.of(), true));
        }

        // ---- everything populated at once ------------------------------------------------------
        Map<ResourceLocation, AdvancementProgress> fullProgress = new LinkedHashMap<>();
        fullProgress.put(idA, progress(two));
        out.add(new ClientboundUpdateAdvancementsPacket(true,
                List.of(plain.get(0), displayed.get(0)),
                new LinkedHashSet<>(List.of(idA, idB)), fullProgress, true));

        return out;
    }
}
