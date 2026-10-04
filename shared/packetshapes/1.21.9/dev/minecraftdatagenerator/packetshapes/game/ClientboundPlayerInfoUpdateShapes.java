package dev.minecraftdatagenerator.packetshapes.game;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.level.GameType;

import java.lang.reflect.Field;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * <b>Discriminator packet.</b> The wire body is {@code EnumSet<Action> actions} (a bit-flag byte set)
 * followed by a length-prefixed list of entries; per entry the writer emits {@code UUID profileId} and
 * then, for each {@link Action} present in {@code actions} <i>in declaration order</i>, that action's
 * own payload:
 * <ul>
 *   <li>ADD_PLAYER — {@code stringUtf8(16) name}, then the profile property list (count capped at 16,
 *       each {@code name<=64, value<=32767, Optional signature<=1024}).</li>
 *   <li>INITIALIZE_CHAT — nullable {@code RemoteChatSession.Data}
 *       ({@code UUID sessionId, i64 expiresAt, X.509 publicKey, byte[] keySignature}).</li>
 *   <li>UPDATE_GAME_MODE — {@code GameType} id (varint).</li>
 *   <li>UPDATE_LISTED — bool.</li>
 *   <li>UPDATE_LATENCY — varint.</li>
 *   <li>UPDATE_DISPLAY_NAME — nullable Component.</li>
 *   <li>UPDATE_LIST_ORDER — varint.</li>
 *   <li>UPDATE_HAT — bool.</li>
 * </ul>
 * Coverage: the {@code actions} bit-flag set over the empty set, every single action and the full set;
 * entry-list cardinality 0/1/2; the full cartesian of the per-entry categorical dimensions under the
 * full action set (GameType x listed x showHat x displayName present/absent x chatSession
 * present/absent = 64); and per-field sweeps of the profile name/properties, latency, listOrder,
 * profileId, display name and chat-session fields.
 * <p>
 * The packet has no public constructor taking a prebuilt entry list (both take live
 * {@link ServerPlayer}s), so an empty-player packet is created and its {@code entries} field is set
 * reflectively — the same technique other providers in this package use for package-private state.
 * <p>
 * OMITTED: an entry with a null profile combined with ADD_PLAYER — the writer calls
 * {@code Objects.requireNonNull(entry.profile())} and would throw, so every entry carries a profile.
 */
public class ClientboundPlayerInfoUpdateShapes implements PacketShapes {
    private static final Field ENTRIES_FIELD;

    static {
        try {
            Field f = ClientboundPlayerInfoUpdatePacket.class.getDeclaredField("entries");
            f.setAccessible(true);
            ENTRIES_FIELD = f;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to reflect ClientboundPlayerInfoUpdatePacket.entries", e);
        }
    }

    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundPlayerInfoUpdatePacket.class;
    }

    private static ClientboundPlayerInfoUpdatePacket mk(EnumSet<Action> actions, List<Entry> entries) {
        ClientboundPlayerInfoUpdatePacket packet =
                new ClientboundPlayerInfoUpdatePacket(actions, List.<ServerPlayer>of());
        try {
            ENTRIES_FIELD.set(packet, entries);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set ClientboundPlayerInfoUpdatePacket.entries", e);
        }
        return packet;
    }

    private static PropertyMap properties(Property... props) {
        Multimap<String, Property> mm = LinkedHashMultimap.create();
        for (Property p : props) mm.put(p.name(), p);
        return new PropertyMap(mm);
    }

    private static PublicKey rsaKey() {
        try {
            return dev.minecraftdatagenerator.packetgen.StaticFixtures.rsaPublicKey(2048);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Entry entry(UUID id, GameProfile profile, boolean listed, int latency, GameType gameMode,
                               Component displayName, boolean showHat, int listOrder,
                               RemoteChatSession.Data chatSession) {
        return new Entry(id, profile, listed, latency, gameMode, displayName, showHat, listOrder, chatSession);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        UUID id = new UUID(1L, 2L);
        GameProfile profile = new GameProfile(id, "Player", properties());
        PublicKey key = rsaKey();
        RemoteChatSession.Data session = new RemoteChatSession.Data(new UUID(3L, 4L),
                new ProfilePublicKey.Data(Instant.ofEpochMilli(0L), key, new byte[]{1, 2, 3, 4}));

        Entry baseline = entry(id, profile, true, 1, GameType.SURVIVAL, Component.literal("d"), false, 0, session);
        Entry second = entry(new UUID(5L, 6L), new GameProfile(new UUID(5L, 6L), "x", properties()),
                false, 2, GameType.CREATIVE, null, true, 1, null);

        // actions bit-flag set: empty, every single action, the full set; crossed with entry-list
        // cardinality 0/1/2.
        List<EnumSet<Action>> actionSets = new ArrayList<>();
        actionSets.add(EnumSet.noneOf(Action.class));
        for (Action action : Action.values()) actionSets.add(EnumSet.of(action));
        actionSets.add(EnumSet.allOf(Action.class));
        List<List<Entry>> entryLists = List.of(List.<Entry>of(), List.of(baseline), List.of(baseline, second));
        for (EnumSet<Action> actions : actionSets) {
            for (List<Entry> entries : entryLists) {
                out.add(mk(EnumSet.copyOf(actions), entries));
            }
        }

        EnumSet<Action> all = EnumSet.allOf(Action.class);

        // Full cartesian of the per-entry categorical dimensions, under the full action set.
        for (GameType gameMode : ctx.enumAll(GameType.class)) {
            for (boolean listed : ctx.bools()) {
                for (boolean showHat : ctx.bools()) {
                    for (boolean hasName : ctx.bools()) {
                        for (boolean hasSession : ctx.bools()) {
                            out.add(mk(EnumSet.copyOf(all), List.of(entry(id, profile, listed, 1, gameMode,
                                    hasName ? Component.literal("d") : null, showHat, 0,
                                    hasSession ? session : null))));
                        }
                    }
                }
            }
        }

        // Per-field sweeps, all under the full action set.
        List<Entry> variants = new ArrayList<>();
        // profileId
        for (UUID u : ctx.uuids()) {
            variants.add(entry(u, new GameProfile(u, "Player", properties()), true, 1, GameType.SURVIVAL,
                    Component.literal("d"), false, 0, session));
        }
        // profile name: the UTF-8 spread, all within the 16-character PLAYER_NAME cap.
        for (String name : ctx.utf8()) {
            variants.add(entry(id, new GameProfile(id, name, properties()), true, 1, GameType.SURVIVAL,
                    Component.literal("d"), false, 0, session));
        }
        // profile properties: cardinality 0/1/2, signature Optional in both forms.
        variants.add(entry(id, new GameProfile(id, "Player", properties()), true, 1, GameType.SURVIVAL,
                Component.literal("d"), false, 0, session));
        variants.add(entry(id, new GameProfile(id, "Player", properties(new Property("textures", "dmFsdWU="))),
                true, 1, GameType.SURVIVAL, Component.literal("d"), false, 0, session));
        variants.add(entry(id, new GameProfile(id, "Player", properties(new Property("textures", "dmFsdWU=", "c2ln"))),
                true, 1, GameType.SURVIVAL, Component.literal("d"), false, 0, session));
        variants.add(entry(id, new GameProfile(id, "Player", properties(
                new Property("textures", "dmFsdWU="), new Property("other", "", "c2ln"))),
                true, 1, GameType.SURVIVAL, Component.literal("d"), false, 0, session));
        // latency / listOrder: VarInt boundaries.
        for (int v : ctx.varInts()) {
            variants.add(entry(id, profile, true, v, GameType.SURVIVAL, Component.literal("d"), false, 0, session));
            variants.add(entry(id, profile, true, 1, GameType.SURVIVAL, Component.literal("d"), false, v, session));
        }
        // displayName: the component spread (the null branch is in the cartesian above).
        for (Component c : ctx.components()) {
            variants.add(entry(id, profile, true, 1, GameType.SURVIVAL, c, false, 0, session));
        }
        // chatSession: sessionId spread, expiry i64 corners and keySignature array cardinality.
        for (UUID u : ctx.uuids()) {
            variants.add(entry(id, profile, true, 1, GameType.SURVIVAL, Component.literal("d"), false, 0,
                    new RemoteChatSession.Data(u, new ProfilePublicKey.Data(Instant.ofEpochMilli(0L), key, new byte[]{1}))));
        }
        for (long millis : ctx.longs()) {
            variants.add(entry(id, profile, true, 1, GameType.SURVIVAL, Component.literal("d"), false, 0,
                    new RemoteChatSession.Data(new UUID(3L, 4L),
                            new ProfilePublicKey.Data(Instant.ofEpochMilli(millis), key, new byte[]{1}))));
        }
        for (int len : List.of(0, 1, 2, 127, 128, 256)) {
            byte[] sigBytes = new byte[len];
            for (int i = 0; i < len; i++) sigBytes[i] = (byte) i;
            variants.add(entry(id, profile, true, 1, GameType.SURVIVAL, Component.literal("d"), false, 0,
                    new RemoteChatSession.Data(new UUID(3L, 4L),
                            new ProfilePublicKey.Data(Instant.ofEpochMilli(0L), key, sigBytes))));
        }
        for (Entry e : variants) {
            out.add(mk(EnumSet.copyOf(all), List.of(e)));
        }
        return out;
    }
}
