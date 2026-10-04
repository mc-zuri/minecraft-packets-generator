package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import io.netty.buffer.ByteBuf;
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
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket.PositionedAdvancement;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ClientboundUpdateAdvancementsPacket(boolean shouldReset, List&lt;PositionedAdvancement&gt; added,
 * Set&lt;Identifier&gt; removed, Map&lt;Identifier, AdvancementProgress&gt; progress,
 * boolean showAdvancements).
 * <p>
 * Wire body:
 * <pre>
 * shouldReset       : bool
 * added             : list&lt;PositionedAdvancement&gt;
 *   advancement     : id : Identifier
 *                     value : parent : Optional&lt;Identifier&gt;
 *                             display : Optional&lt;DisplayInfo&gt;
 *                             requirements : list&lt;list&lt;string&gt;&gt;
 *                             sendsTelemetryEvent : bool
 *   x, y            : f32, f32
 * removed           : collection&lt;Identifier&gt;
 * progress          : map&lt;Identifier, AdvancementProgress&gt;
 *                       criteria : map&lt;string, Optional&lt;Instant as i64&gt;&gt;
 * showAdvancements  : bool
 * </pre>
 * DisplayInfo itself is hand-rolled: title, description, ItemStackTemplate icon, AdvancementType,
 * an i32 flags field (bit 1 = background present, bit 2 = showToast, bit 4 = hidden) and then the
 * background Identifier when bit 1 is set.
 * <p>
 * Coverage:
 * <ul>
 *   <li>shouldReset x showAdvancements (full cross);</li>
 *   <li>added / removed / progress collections at sizes 0, 1 and 2;</li>
 *   <li>the advancement's parent Optional in both forms, its telemetry bool in both forms, and its
 *       requirements at outer sizes 0/1/2 with inner lists of size 0/1/2;</li>
 *   <li>the display Optional in both forms, and when present the full cross of every
 *       {@link AdvancementType} with all eight flag-bit combinations, plus icon variants covering
 *       count and a non-empty / removal DataComponentPatch;</li>
 *   <li>AdvancementProgress criteria maps of size 0/1/2 with the CriterionProgress obtained-Instant
 *       Optional in both forms;</li>
 *   <li>PositionedAdvancement x/y over the float edge cases.</li>
 * </ul>
 * <p>
 * NOTE: {@code Advancement.name} and {@code DisplayInfo.announceToChat} are not written by the stream
 * codecs (the decoder reconstructs them), so they are held constant. {@code AdvancementRewards} is
 * likewise not on the wire and is always {@code AdvancementRewards.EMPTY}. {@code ItemStackTemplate}
 * rejects AIR and count 0 in its constructor, so those are omitted.
 */
public class ClientboundUpdateAdvancementsShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundUpdateAdvancementsPacket.class;
    }

    private static Advancement advancement(Optional<Identifier> parent, Optional<DisplayInfo> display,
                                           AdvancementRequirements requirements, boolean telemetry) {
        return new Advancement(parent, display, AdvancementRewards.EMPTY, Map.of(),
                requirements, telemetry, Optional.empty());
    }

    private static List<ItemStackTemplate> icons() {
        List<ItemStackTemplate> out = new ArrayList<>();
        out.add(new ItemStackTemplate(Items.STONE));
        out.add(new ItemStackTemplate(Items.STONE, 99));
        out.add(new ItemStackTemplate(Items.STONE.builtInRegistryHolder(), 1,
                DataComponentPatch.builder().set(DataComponents.ITEM_NAME, Component.literal("x")).build()));
        out.add(new ItemStackTemplate(Items.STONE.builtInRegistryHolder(), 1,
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
     * by hand and decoding it through the real STREAM_CODEC
     * ({@code map<string, Optional<Instant as i64 millis>>}). A null value means "not obtained".
     */
    private static AdvancementProgress progress(Map<String, Long> criteria) {
        ByteBuf buf = Unpooled.buffer();
        VarInt.write(buf, criteria.size());
        for (Map.Entry<String, Long> e : criteria.entrySet()) {
            Utf8String.write(buf, e.getKey(), 32767);
            buf.writeBoolean(e.getValue() != null);
            if (e.getValue() != null) buf.writeLong(e.getValue());
        }
        return AdvancementProgress.STREAM_CODEC.decode(buf);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        Identifier idA = Identifier.withDefaultNamespace("adv_a");
        Identifier idB = Identifier.withDefaultNamespace("adv_b");
        Identifier background = Identifier.withDefaultNamespace("gui/advancements/backgrounds/stone");

        // ---- shouldReset x showAdvancements, all collections empty ----------------------------
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
            Map<Identifier, AdvancementProgress> one = new LinkedHashMap<>();
            one.put(idA, progress(criteria));
            out.add(new ClientboundUpdateAdvancementsPacket(false, List.of(), Set.of(), one, true));

            Map<Identifier, AdvancementProgress> pair = new LinkedHashMap<>();
            pair.put(idA, progress(criteria));
            pair.put(idB, progress(criteria));
            out.add(new ClientboundUpdateAdvancementsPacket(false, List.of(), Set.of(), pair, true));
        }

        // ---- added list: advancements without display -----------------------------------------
        List<PositionedAdvancement> plain = new ArrayList<>();
        for (Optional<Identifier> parent : List.of(Optional.<Identifier>empty(), Optional.of(idA))) {
            for (AdvancementRequirements req : requirements()) {
                for (boolean telemetry : ctx.bools()) {
                    PositionedAdvancement pa = new PositionedAdvancement(
                            new AdvancementHolder(idB, advancement(parent, Optional.empty(), req, telemetry)),
                            0f, 0f);
                    plain.add(pa);
                    out.add(new ClientboundUpdateAdvancementsPacket(false, List.of(pa), Set.of(), Map.of(), true));
                }
            }
        }

        // ---- added list: advancements with display -------------------------------------------
        // AdvancementType x the 8 flag combinations (background / showToast / hidden).
        List<ItemStackTemplate> icons = icons();
        List<PositionedAdvancement> displayed = new ArrayList<>();
        for (AdvancementType type : ctx.enumAll(AdvancementType.class)) {
            for (boolean hasBackground : ctx.bools()) {
                for (boolean showToast : ctx.bools()) {
                    for (boolean hidden : ctx.bools()) {
                        DisplayInfo display = new DisplayInfo(
                                icons.get(0),
                                Component.literal("title"),
                                Component.translatable("description"),
                                hasBackground
                                        ? Optional.of(new ClientAsset.ResourceTexture(background))
                                        : Optional.empty(),
                                type, showToast, false, hidden);
                        PositionedAdvancement pa = new PositionedAdvancement(
                                new AdvancementHolder(idA, advancement(Optional.empty(),
                                        Optional.of(display), AdvancementRequirements.EMPTY, false)),
                                0f, 0f);
                        displayed.add(pa);
                        out.add(new ClientboundUpdateAdvancementsPacket(false, List.of(pa), Set.of(), Map.of(), true));
                    }
                }
            }
        }
        // icon variants (count and DataComponentPatch branches) and component variants on the display
        for (ItemStackTemplate icon : icons) {
            for (Component text : ctx.components()) {
                DisplayInfo display = new DisplayInfo(icon, text, text, Optional.empty(),
                        AdvancementType.TASK, true, false, false);
                out.add(new ClientboundUpdateAdvancementsPacket(false,
                        List.of(new PositionedAdvancement(new AdvancementHolder(idA,
                                advancement(Optional.empty(), Optional.of(display),
                                        AdvancementRequirements.EMPTY, false)), 0f, 0f)),
                        Set.of(), Map.of(), true));
            }
        }

        // ---- added list size 2 (one plain, one displayed) -------------------------------------
        out.add(new ClientboundUpdateAdvancementsPacket(false,
                List.of(plain.get(0), displayed.get(0)), Set.of(), Map.of(), true));

        // ---- PositionedAdvancement x/y over the float corner cases ----------------------------
        AdvancementHolder holder = new AdvancementHolder(idA,
                advancement(Optional.empty(), Optional.empty(), AdvancementRequirements.EMPTY, false));
        for (float f : ctx.floats()) {
            out.add(new ClientboundUpdateAdvancementsPacket(false,
                    List.of(new PositionedAdvancement(holder, f, 0f)), Set.of(), Map.of(), true));
            out.add(new ClientboundUpdateAdvancementsPacket(false,
                    List.of(new PositionedAdvancement(holder, 0f, f)), Set.of(), Map.of(), true));
        }

        // ---- everything populated at once ------------------------------------------------------
        Map<Identifier, AdvancementProgress> fullProgress = new LinkedHashMap<>();
        fullProgress.put(idA, progress(two));
        out.add(new ClientboundUpdateAdvancementsPacket(true,
                List.of(plain.get(0), displayed.get(0)),
                new LinkedHashSet<>(List.of(idA, idB)), fullProgress, true));

        return out;
    }
}
