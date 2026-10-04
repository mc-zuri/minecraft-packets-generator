package dev.minecraftdatagenerator.packetshapes.game;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundCommandsPacket — the command tree. Wire body is
 * {@code entries:list(Entry), rootIndex:varint}, where each Entry is
 * {@code flags:u8, children:varint[], [redirect:varint if flags&8], [type-specific stub]}.
 * <p>
 * The flags byte is a discriminator: the low two bits are the node type (ROOT=0, LITERAL=1, ARGUMENT=2)
 * and the upper bits are FLAG_EXECUTABLE(4), FLAG_REDIRECT(8), FLAG_CUSTOM_SUGGESTIONS(16) and
 * FLAG_RESTRICTED(32). A LITERAL stub is {@code name:utf8}; an ARGUMENT stub is
 * {@code name:utf8, argumentTypeId:varint, <ArgumentTypeInfo payload>, [suggestionId:identifier]}.
 * <p>
 * Coverage:
 * <ul>
 *   <li>every node type, including the empty (root-only) tree;</li>
 *   <li>child-array sizes 0, 1, 2 and 3, plus a nested three-deep chain and a diamond where two parents
 *       share one child (so the same node id appears in two child arrays);</li>
 *   <li>FLAG_EXECUTABLE x FLAG_RESTRICTED crossed on both literal and argument nodes;</li>
 *   <li>FLAG_CUSTOM_SUGGESTIONS absent and present, crossed with every argument type;</li>
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
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundCommandsPacket.class;
    }

    /** Supplies the per-node flag bits the packet reads off each brigadier node. */
    private static final class Inspector implements ClientboundCommandsPacket.NodeInspector<Object> {
        private final boolean executable;
        private final boolean restricted;
        private final @Nullable Identifier suggestionId;

        Inspector(boolean executable, boolean restricted, @Nullable Identifier suggestionId) {
            this.executable = executable;
            this.restricted = restricted;
            this.suggestionId = suggestionId;
        }

        @Override
        public @Nullable Identifier suggestionId(ArgumentCommandNode<Object, ?> node) {
            return suggestionId;
        }

        // Vanilla's inspector derives these from the node itself, and a RootCommandNode never carries a
        // command or a requirement, so the real server never sets these bits on the root entry.
        @Override
        public boolean isExecutable(CommandNode<Object> node) {
            return !(node instanceof RootCommandNode) && executable;
        }

        @Override
        public boolean isRestricted(CommandNode<Object> node) {
            return !(node instanceof RootCommandNode) && restricted;
        }
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

    private static ClientboundCommandsPacket packet(RootCommandNode<Object> root, Inspector inspector) {
        return new ClientboundCommandsPacket(root, inspector);
    }

    @SafeVarargs
    private static RootCommandNode<Object> rootWith(CommandNode<Object>... children) {
        RootCommandNode<Object> root = new RootCommandNode<>();
        for (CommandNode<Object> child : children) root.addChild(child);
        return root;
    }

    private static LiteralCommandNode<Object> literal(String name) {
        return LiteralArgumentBuilder.<Object>literal(name).build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentType<Object> erase(ArgumentType<?> type) {
        return (ArgumentType<Object>) type;
    }

    private static RequiredArgumentBuilder<Object, Object> argumentBuilder(String name, ArgumentType<?> type) {
        return RequiredArgumentBuilder.argument(name, erase(type));
    }

    private static ArgumentCommandNode<Object, Object> argument(String name, ArgumentType<?> type) {
        return argumentBuilder(name, type).build();
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        Identifier askServer = ctx.id("ask_server");
        List<Identifier> suggestionIds = new ArrayList<>();
        suggestionIds.add(null);
        suggestionIds.add(askServer);
        suggestionIds.add(Identifier.parse("mymod:custom_suggestions"));

        // --- the empty tree: a single ROOT entry with no children ---
        out.add(packet(new RootCommandNode<>(), new Inspector(false, false, null)));

        // --- literal node: every UTF-8 name width x FLAG_EXECUTABLE x FLAG_RESTRICTED ---
        for (String name : ctx.utf8()) {
            for (boolean executable : ctx.bools()) {
                for (boolean restricted : ctx.bools()) {
                    out.add(packet(rootWith(literal(name)), new Inspector(executable, restricted, null)));
                }
            }
        }

        // --- child-array sizes 0, 1, 2 and 3 ---
        out.add(packet(rootWith(), new Inspector(false, false, null)));
        out.add(packet(rootWith(literal("a")), new Inspector(false, false, null)));
        out.add(packet(rootWith(literal("a"), literal("b")), new Inspector(false, false, null)));
        out.add(packet(rootWith(literal("a"), literal("b"), literal("c")),
                new Inspector(false, false, null)));

        // --- argument node: every argument-type payload x suggestion id x executable x restricted ---
        for (ArgumentType<?> type : argumentTypes()) {
            for (Identifier suggestionId : suggestionIds) {
                for (boolean executable : ctx.bools()) {
                    for (boolean restricted : ctx.bools()) {
                        out.add(packet(rootWith(argument("n", type)),
                                new Inspector(executable, restricted, suggestionId)));
                    }
                }
            }
        }

        // --- argument node: every UTF-8 name width ---
        for (String name : ctx.utf8()) {
            out.add(packet(rootWith(argument(name, IntegerArgumentType.integer())),
                    new Inspector(true, false, askServer)));
        }

        // --- FLAG_REDIRECT: literal -> literal, argument -> literal, literal -> root ---
        {
            LiteralCommandNode<Object> target = literal("a");
            LiteralCommandNode<Object> redirecting = LiteralArgumentBuilder.<Object>literal("b")
                    .redirect(target).build();
            out.add(packet(rootWith(target, redirecting), new Inspector(false, false, null)));
        }
        {
            LiteralCommandNode<Object> target = literal("a");
            ArgumentCommandNode<Object, Object> redirecting =
                    argumentBuilder("n", IntegerArgumentType.integer()).redirect(target).build();
            out.add(packet(rootWith(target, redirecting), new Inspector(true, false, askServer)));
        }
        {
            RootCommandNode<Object> root = new RootCommandNode<>();
            root.addChild(LiteralArgumentBuilder.<Object>literal("back").redirect(root).build());
            out.add(packet(root, new Inspector(false, false, null)));
        }

        // --- a three-deep chain: root -> literal -> literal -> argument ---
        {
            CommandNode<Object> built = LiteralArgumentBuilder.<Object>literal("a")
                    .then(LiteralArgumentBuilder.<Object>literal("b")
                            .then(argumentBuilder("n", IntegerArgumentType.integer(0, 9))))
                    .build();
            out.add(packet(rootWith(built), new Inspector(true, true, null)));
        }

        // --- a diamond: two literals share one child, so its node id appears in two child arrays ---
        {
            LiteralCommandNode<Object> shared = literal("shared");
            LiteralCommandNode<Object> left = literal("left");
            LiteralCommandNode<Object> right = literal("right");
            left.addChild(shared);
            right.addChild(shared);
            out.add(packet(rootWith(left, right), new Inspector(false, false, null)));
        }

        return out;
    }
}
