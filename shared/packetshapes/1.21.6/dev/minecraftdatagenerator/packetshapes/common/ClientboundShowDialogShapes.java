package dev.minecraftdatagenerator.packetshapes.common;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.DialogListDialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.ServerLinksDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ClientboundShowDialogPacket(Holder&lt;Dialog&gt; dialog) — the wire form is
 * {@code ByteBufCodecs.holder(Registries.DIALOG, fromCodecWithRegistriesTrusted(Dialog.DIRECT_CODEC))}:
 * a VarInt holder discriminator that is either "registry id + 1" (a reference) or 0 followed by the
 * whole dialog serialised inline as NBT.
 *
 * <p>Coverage:
 * <ul>
 *   <li>both holder branches — every entry of the DIALOG registry as a reference, plus direct holders;</li>
 *   <li>every one of the five registered dialog types: notice, confirmation, multi_action,
 *       server_links, dialog_list;</li>
 *   <li>CommonDialogData's categorical fields fully crossed on the notice type: title x
 *       external_title (absent + each component) x can_close_with_escape x the five legal
 *       (pause, after_action) pairs;</li>
 *   <li>every DialogBody type (plain_message, item — the latter with its description Optional in both
 *       states) and body list sizes 0/1/2, with the width/height ranges at both ends;</li>
 *   <li>every InputControl type (boolean, text, single_option, number_range, with their nested
 *       Optionals in both states) and input list sizes 0/1/4;</li>
 *   <li>ActionButton's tooltip and action Optionals in both states, with two StaticAction kinds.</li>
 * </ul>
 *
 * <p>Deliberately omitted: (pause = true, after_action = NONE). CommonDialogData.MAP_CODEC carries a
 * validator that rejects a pausing dialog whose after_action does not unpause, so that combination
 * would fail to encode.
 */
public class ClientboundShowDialogShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundShowDialogPacket.class;
    }

    private static CommonDialogData common(Component title, Optional<Component> externalTitle,
                                           boolean canClose, boolean pause, DialogAction after,
                                           List<DialogBody> body, List<Input> inputs) {
        return new CommonDialogData(title, externalTitle, canClose, pause, after, body, inputs);
    }

    private static Packet<?> direct(Dialog d) {
        return new ClientboundShowDialogPacket(Holder.direct(d));
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        Component title = Component.literal("t");
        List<Packet<?>> out = new ArrayList<>();

        CommonDialogData base = common(title, Optional.empty(), true, false, DialogAction.CLOSE,
                List.of(), List.of());

        ActionButton plainButton = new ActionButton(
                new CommonButtonData(Component.literal("ok"), Optional.empty(), 150), Optional.empty());
        ActionButton tooltipButton = new ActionButton(
                new CommonButtonData(Component.literal("ok"), Optional.of(Component.literal("tip")), 1),
                Optional.empty());
        ActionButton wideButton = new ActionButton(
                new CommonButtonData(Component.literal("ok"), Optional.empty(), 1024), Optional.empty());
        ActionButton runCommandButton = new ActionButton(
                new CommonButtonData(Component.literal("run"), Optional.empty(), 150),
                Optional.<Action>of(new StaticAction(new ClickEvent.RunCommand("say hi"))));
        ActionButton openUrlButton = new ActionButton(
                new CommonButtonData(Component.literal("url"), Optional.empty(), 150),
                Optional.<Action>of(new StaticAction(new ClickEvent.OpenUrl(URI.create("https://example.com")))));

        // ---- holder reference branch: every entry of the DIALOG registry -------------------------
        ctx.registries.lookup(Registries.DIALOG).ifPresent(lookup ->
                lookup.listElements().forEach(h -> out.add(new ClientboundShowDialogPacket(h))));

        // ---- direct branch, notice type: full cartesian of CommonDialogData's categorical fields --
        List<Optional<Component>> externalTitles = new ArrayList<>();
        externalTitles.add(Optional.empty());
        for (Component c : ctx.components()) externalTitles.add(Optional.of(c));

        // The (pause, after_action) pairs CommonDialogData's validator accepts.
        List<Object[]> pauseAndAction = List.of(
                new Object[]{Boolean.FALSE, DialogAction.CLOSE},
                new Object[]{Boolean.FALSE, DialogAction.NONE},
                new Object[]{Boolean.FALSE, DialogAction.WAIT_FOR_RESPONSE},
                new Object[]{Boolean.TRUE, DialogAction.CLOSE},
                new Object[]{Boolean.TRUE, DialogAction.WAIT_FOR_RESPONSE});

        for (Component t : ctx.components()) {
            for (Optional<Component> ext : externalTitles) {
                for (boolean canClose : ctx.bools()) {
                    for (Object[] pa : pauseAndAction) {
                        out.add(direct(new NoticeDialog(
                                common(t, ext, canClose, (Boolean) pa[0], (DialogAction) pa[1],
                                        List.of(), List.of()),
                                plainButton)));
                    }
                }
            }
        }

        // ---- body: every DialogBody type, list sizes 0/1/2 ---------------------------------------
        PlainMessage plain = new PlainMessage(Component.literal("m"), 200);
        PlainMessage narrow = new PlainMessage(Component.translatable("m"), 1);    // WIDTH_CODEC min
        PlainMessage wide = new PlainMessage(Component.empty(), 1024);             // WIDTH_CODEC max
        ItemBody itemPlain = new ItemBody(new ItemStack(Items.STONE), Optional.empty(),
                true, true, 16, 16);
        // ItemBody width/height are intRange(1, 256). The item goes through ItemStack.STRICT_CODEC,
        // which rejects a count above the item's own max stack size, so 64 is the largest
        // representable count for stone here.
        ItemBody itemFull = new ItemBody(new ItemStack(Items.STONE, 64),
                Optional.of(plain), false, false, 1, 256);
        for (List<DialogBody> body : List.<List<DialogBody>>of(
                List.of(),
                List.of(plain),
                List.of(narrow),
                List.of(wide),
                List.of(itemPlain),
                List.of(itemFull),
                List.of(plain, itemFull))) {
            out.add(direct(new NoticeDialog(
                    common(title, Optional.empty(), true, false, DialogAction.CLOSE, body, List.of()),
                    plainButton)));
        }

        // ---- inputs: every InputControl type, list sizes 0/1/4 -----------------------------------
        Input boolInput = new Input("b", new BooleanInput(Component.literal("b"), true, "yes", "no"));
        Input boolInputDefaults = new Input("b2", new BooleanInput(Component.literal("b"), false, "true", "false"));
        Input textInput = new Input("t", new TextInput(200, Component.literal("t"), true, "", 32,
                Optional.empty()));
        Input textInputMulti = new Input("t2", new TextInput(1, Component.literal("t"), false, "ab", 2,
                Optional.of(new TextInput.MultilineOptions(Optional.of(4), Optional.of(512)))));
        Input optionInput = new Input("o", new SingleOptionInput(200,
                List.of(new SingleOptionInput.Entry("a", Optional.empty(), false),
                        new SingleOptionInput.Entry("b", Optional.of(Component.literal("B")), true)),
                Component.literal("o"), true));
        Input rangeInput = new Input("r", new NumberRangeInput(200, Component.literal("r"),
                "options.generic_value",
                new NumberRangeInput.RangeInfo(0f, 10f, Optional.empty(), Optional.empty())));
        Input rangeInputFull = new Input("r2", new NumberRangeInput(1024, Component.literal("r"),
                "x", new NumberRangeInput.RangeInfo(-10f, 10f, Optional.of(0f), Optional.of(0.5f))));
        for (List<Input> inputs : List.<List<Input>>of(
                List.of(),
                List.of(boolInput),
                List.of(boolInputDefaults),
                List.of(textInput),
                List.of(textInputMulti),
                List.of(optionInput),
                List.of(rangeInput),
                List.of(rangeInputFull),
                List.of(boolInput, textInput, optionInput, rangeInput))) {
            out.add(direct(new NoticeDialog(
                    common(title, Optional.empty(), true, false, DialogAction.CLOSE, List.of(), inputs),
                    plainButton)));
        }

        // ---- notice: button variants -------------------------------------------------------------
        for (ActionButton b : List.of(NoticeDialog.DEFAULT_ACTION, plainButton, tooltipButton,
                wideButton, runCommandButton, openUrlButton)) {
            out.add(direct(new NoticeDialog(base, b)));
        }

        // ---- confirmation ------------------------------------------------------------------------
        out.add(direct(new ConfirmationDialog(base, plainButton, plainButton)));
        out.add(direct(new ConfirmationDialog(base, runCommandButton, tooltipButton)));

        // ---- multi_action: non-empty action list, exit_action both ways, columns sweep ------------
        for (List<ActionButton> actions : List.of(
                List.of(plainButton),
                List.of(plainButton, runCommandButton),
                List.of(plainButton, runCommandButton, openUrlButton))) {
            for (Optional<ActionButton> exit : List.of(
                    Optional.<ActionButton>empty(), Optional.of(tooltipButton))) {
                for (int columns : List.of(1, 2, 3)) {   // ExtraCodecs.POSITIVE_INT
                    out.add(direct(new MultiActionDialog(base, actions, exit, columns)));
                }
            }
        }

        // ---- server_links ------------------------------------------------------------------------
        for (Optional<ActionButton> exit : List.of(
                Optional.<ActionButton>empty(), Optional.of(tooltipButton))) {
            for (int buttonWidth : List.of(1, 150, 1024)) {   // WIDTH_CODEC range
                out.add(direct(new ServerLinksDialog(base, exit, 2, buttonWidth)));
            }
        }

        // ---- dialog_list: HolderSet of registry-bound dialogs, sizes 0/1/2 ------------------------
        ctx.registries.lookup(Registries.DIALOG).ifPresent(lookup -> {
            List<Holder.Reference<Dialog>> all = lookup.listElements().toList();
            List<HolderSet<Dialog>> sets = new ArrayList<>();
            sets.add(HolderSet.direct(List.of()));
            if (!all.isEmpty()) sets.add(HolderSet.direct(List.of(all.get(0))));
            if (all.size() >= 2) sets.add(HolderSet.direct(List.of(all.get(0), all.get(1))));
            for (HolderSet<Dialog> set : sets) {
                for (Optional<ActionButton> exit : List.of(
                        Optional.<ActionButton>empty(), Optional.of(tooltipButton))) {
                    out.add(direct(new DialogListDialog(base, set, exit, 2, 150)));
                }
            }
        });

        return out;
    }
}
