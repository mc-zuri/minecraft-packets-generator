package dev.minecraftdatagenerator.packetshapes.game;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundCommandsPacket — the command tree. Wire body is
 * {@code entries:list(Entry), rootIndex:varint}, where each Entry is
 * {@code flags:u8, children:varint[], [redirect:varint if flags&8], [type-specific stub]}.
 * <p>
 * The flags byte is a discriminator: the low two bits are the node type (ROOT=0, LITERAL=1, ARGUMENT=2)
 * and the upper bits are FLAG_EXECUTABLE(4), FLAG_REDIRECT(8) and FLAG_CUSTOM_SUGGESTIONS(16). A LITERAL
 * stub is {@code name:utf8}; an ARGUMENT stub is
 * {@code name:utf8, argumentTypeId:varint, <ArgumentTypeInfo payload>, [suggestionId:identifier]}.
 * <p>
 * In 1.20.5 the packet has no {@code NodeInspector}: it derives every flag from the brigadier node
 * itself, so FLAG_EXECUTABLE is driven by attaching a {@link Command} and FLAG_CUSTOM_SUGGESTIONS by
 * attaching a registered {@link SuggestionProvider} (the written identifier is that provider's
 * registered name). FLAG_RESTRICTED(32) was introduced in 1.21.6 and does not exist here.
 * <p>
 * Coverage:
 * <ul>
 *   <li>every node type, including the empty (root-only) tree;</li>
 *   <li>child-array sizes 0, 1, 2 and 3, plus a nested three-deep chain and a diamond where two parents
 *       share one child (so the same node id appears in two child arrays);</li>
 *   <li>FLAG_EXECUTABLE both ways on both literal and argument nodes;</li>
 *   <li>FLAG_CUSTOM_SUGGESTIONS absent and present, crossed with every argument type, over every
 *       vanilla-registered suggestion id plus a custom-namespace one registered here;</li>
 *   <li>FLAG_REDIRECT on a literal, on an argument, and pointing back at the root;</li>
 *   <li>literal/argument names over the whole UTF-8 spread, exercising the utf8 length prefix;</li>
 *   <li>every ArgumentTypeInfo payload discriminator that can be built without a CommandBuildContext:
 *       the empty singleton payload, the numeric min/max flag byte in all four combinations, the
 *       brigadier:string StringType enum in all three, the entity flag byte in all four and the
 *       score_holder flag byte in both.</li>
 * </ul>
 * <p>
 * OMITTED: the context-aware argument types ({@code block_state}, {@code item_stack}, {@code particle},
 * {@code resource}/{@code resource_key}/{@code resource_or_tag}, ...) cannot be instantiated here — they
 * need a live {@code CommandBuildContext} — but their stubs are byte-identical to the singleton /
 * registry-id forms already covered.
 */
public class ClientboundCommandsShapes implements PacketShapes {
    /** Attached to a node purely to make vanilla set FLAG_EXECUTABLE; never executed. */
    private static final Command<SharedSuggestionProvider> RUN = ctx -> 0;

    /**
     * A provider under a non-vanilla namespace. Vanilla writes {@code SuggestionProviders.getName(p)},
     * which only yields the real id for a registered wrapper, so the id has to be registered to appear
     * on the wire at all.
     */
    private static final SuggestionProvider<SharedSuggestionProvider> CUSTOM_SUGGESTIONS =
            SuggestionProviders.register(new ResourceLocation("mymod:custom_suggestions"),
                    (c, b) -> b.buildFuture());

    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundCommandsPacket.class;
    }

    /** Every ArgumentTypeInfo network payload shape reachable without a CommandBuildContext. */
    private static List<ArgumentType<?>> argumentTypes() {
        List<ArgumentType<?>> types = new ArrayList<>();

        // SingletonArgumentInfo: registry id only, empty payload.
        types.add(BoolArgumentType.bool());
        types.add(BlockPosArgument.blockPos());
        types.add(UuidArgument.uuid());
        types.add(MessageArgument.message());

        // Numeric infos: a flags byte with HAS_MIN(1)/HAS_MAX(2), all four combinations each.
        types.add(IntegerArgumentType.integer());                                 // flags 0
        types.add(IntegerArgumentType.integer(0));                                // flags 1
        types.add(IntegerArgumentType.integer(Integer.MIN_VALUE, 10));            // flags 2
        types.add(IntegerArgumentType.integer(-5, 5));                            // flags 3
        types.add(LongArgumentType.longArg());                                    // flags 0
        types.add(LongArgumentType.longArg(0L));                                  // flags 1
        types.add(LongArgumentType.longArg(Long.MIN_VALUE, 10L));                 // flags 2
        types.add(LongArgumentType.longArg(-5L, 5L));                             // flags 3
        types.add(FloatArgumentType.floatArg());                                  // flags 0
        types.add(FloatArgumentType.floatArg(0f));                                // flags 1
        types.add(FloatArgumentType.floatArg(-Float.MAX_VALUE, 10f));             // flags 2
        types.add(FloatArgumentType.floatArg(-3.5f, 3.5f));                       // flags 3
        types.add(DoubleArgumentType.doubleArg());                                // flags 0
        types.add(DoubleArgumentType.doubleArg(0d));                              // flags 1
        types.add(DoubleArgumentType.doubleArg(-Double.MAX_VALUE, 10d));          // flags 2
        types.add(DoubleArgumentType.doubleArg(-3.5d, 3.5d));                     // flags 3

        // StringArgumentSerializer: the StringType enum, every constant.
        types.add(StringArgumentType.word());
        types.add(StringArgumentType.string());
        types.add(StringArgumentType.greedyString());

        // EntityArgument.Info: a flags byte with SINGLE(1)/PLAYERS_ONLY(2), all four combinations.
        types.add(EntityArgument.entities());   // flags 0
        types.add(EntityArgument.entity());     // flags 1
        types.add(EntityArgument.players());    // flags 2
        types.add(EntityArgument.player());     // flags 3

        // ScoreHolderArgument.Info: a flags byte with MULTIPLE(1), both states.
        types.add(ScoreHolderArgument.scoreHolder());
        types.add(ScoreHolderArgument.scoreHolders());

        return types;
    }

    private static ClientboundCommandsPacket packet(RootCommandNode<SharedSuggestionProvider> root) {
        return new ClientboundCommandsPacket(root);
    }

    @SafeVarargs
    private static RootCommandNode<SharedSuggestionProvider> rootWith(
            CommandNode<SharedSuggestionProvider>... children) {
        RootCommandNode<SharedSuggestionProvider> root = new RootCommandNode<>();
        for (CommandNode<SharedSuggestionProvider> child : children) root.addChild(child);
        return root;
    }

    private static LiteralArgumentBuilder<SharedSuggestionProvider> literalBuilder(String name, boolean executable) {
        LiteralArgumentBuilder<SharedSuggestionProvider> b = LiteralArgumentBuilder.literal(name);
        if (executable) b.executes(RUN);
        return b;
    }

    private static LiteralCommandNode<SharedSuggestionProvider> literal(String name) {
        return literalBuilder(name, false).build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentType<Object> erase(ArgumentType<?> type) {
        return (ArgumentType<Object>) type;
    }

    private static RequiredArgumentBuilder<SharedSuggestionProvider, Object> argumentBuilder(
            String name, ArgumentType<?> type, boolean executable,
            SuggestionProvider<SharedSuggestionProvider> suggestions) {
        RequiredArgumentBuilder<SharedSuggestionProvider, Object> b =
                RequiredArgumentBuilder.argument(name, erase(type));
        if (executable) b.executes(RUN);
        // null leaves getCustomSuggestions() null, so FLAG_CUSTOM_SUGGESTIONS stays clear
        if (suggestions != null) b.suggests(suggestions);
        return b;
    }

    private static ArgumentCommandNode<SharedSuggestionProvider, Object> argument(
            String name, ArgumentType<?> type, boolean executable,
            SuggestionProvider<SharedSuggestionProvider> suggestions) {
        return argumentBuilder(name, type, executable, suggestions).build();
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        List<SuggestionProvider<SharedSuggestionProvider>> suggestionProviders = new ArrayList<>();
        suggestionProviders.add(null);
        suggestionProviders.add(SuggestionProviders.ASK_SERVER);
        // the two CommandSourceStack-typed vanilla providers are the same registered wrappers, so they
        // still write their own ids; fetch them by name to keep the generics honest
        suggestionProviders.add(SuggestionProviders.getProvider(ctx.id("available_sounds")));
        suggestionProviders.add(SuggestionProviders.getProvider(ctx.id("summonable_entities")));
        suggestionProviders.add(CUSTOM_SUGGESTIONS);

        // --- the empty tree: a single ROOT entry with no children ---
        out.add(packet(new RootCommandNode<>()));

        // --- literal node: every UTF-8 name width x FLAG_EXECUTABLE ---
        for (String name : ctx.utf8()) {
            for (boolean executable : ctx.bools()) {
                out.add(packet(rootWith(literalBuilder(name, executable).build())));
            }
        }

        // --- child-array sizes 0, 1, 2 and 3 ---
        out.add(packet(rootWith()));
        out.add(packet(rootWith(literal("a"))));
        out.add(packet(rootWith(literal("a"), literal("b"))));
        out.add(packet(rootWith(literal("a"), literal("b"), literal("c"))));

        // --- argument node: every argument-type payload x suggestion id x FLAG_EXECUTABLE ---
        for (ArgumentType<?> type : argumentTypes()) {
            for (SuggestionProvider<SharedSuggestionProvider> suggestions : suggestionProviders) {
                for (boolean executable : ctx.bools()) {
                    out.add(packet(rootWith(argument("n", type, executable, suggestions))));
                }
            }
        }

        // --- argument node: every UTF-8 name width ---
        for (String name : ctx.utf8()) {
            out.add(packet(rootWith(argument(name, IntegerArgumentType.integer(), true,
                    SuggestionProviders.ASK_SERVER))));
        }

        // --- FLAG_REDIRECT: literal -> literal, argument -> literal, literal -> root ---
        {
            LiteralCommandNode<SharedSuggestionProvider> target = literal("a");
            LiteralCommandNode<SharedSuggestionProvider> redirecting =
                    LiteralArgumentBuilder.<SharedSuggestionProvider>literal("b").redirect(target).build();
            out.add(packet(rootWith(target, redirecting)));
        }
        {
            LiteralCommandNode<SharedSuggestionProvider> target = literal("a");
            ArgumentCommandNode<SharedSuggestionProvider, Object> redirecting =
                    argumentBuilder("n", IntegerArgumentType.integer(), true, SuggestionProviders.ASK_SERVER)
                            .redirect(target).build();
            out.add(packet(rootWith(target, redirecting)));
        }
        {
            RootCommandNode<SharedSuggestionProvider> root = new RootCommandNode<>();
            root.addChild(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("back")
                    .redirect(root).build());
            out.add(packet(root));
        }

        // --- a three-deep chain: root -> literal -> literal -> argument ---
        {
            CommandNode<SharedSuggestionProvider> built =
                    LiteralArgumentBuilder.<SharedSuggestionProvider>literal("a")
                            .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("b")
                                    .then(argumentBuilder("n", IntegerArgumentType.integer(0, 9), true, null)))
                            .build();
            out.add(packet(rootWith(built)));
        }

        // --- a diamond: two literals share one child, so its node id appears in two child arrays ---
        {
            LiteralCommandNode<SharedSuggestionProvider> shared = literal("shared");
            LiteralCommandNode<SharedSuggestionProvider> left = literal("left");
            LiteralCommandNode<SharedSuggestionProvider> right = literal("right");
            left.addChild(shared);
            right.addChild(shared);
            out.add(packet(rootWith(left, right)));
        }

        return out;
    }
}
