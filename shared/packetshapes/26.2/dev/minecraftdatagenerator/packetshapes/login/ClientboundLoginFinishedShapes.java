package dev.minecraftdatagenerator.packetshapes.login;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ClientboundLoginFinishedPacket(GameProfile gameProfile, UUID sessionId).
 * <p>
 * Wire: {@code ByteBufCodecs.GAME_PROFILE} = raw 16-byte uuid, {@code stringUtf8(16)} name, then
 * {@code GAME_PROFILE_PROPERTIES} = a varint count (max 16) followed by, per property,
 * {@code stringUtf8(64)} name, {@code stringUtf8(32767)} value and a nullable {@code stringUtf8(1024)}
 * signature; then the sessionId as a raw 16-byte uuid.
 * <p>
 * Full cartesian: profile name over the UTF-8 corner cases x profile uuid
 * x the property-list forms — 0 properties, 1 unsigned (signature absent), 1 signed (signature
 * present) and 2 properties mixing both — x sessionId. On top of that a sweep of UTF-8 property
 * names, values and signatures.
 * <p>
 * The property maps are built with an insertion-ordered {@link LinkedHashMultimap} and kept to at
 * most two entries so their iteration order matches the {@code ImmutableMultimap} the decoder builds,
 * keeping the shapes byte-stable across a round trip.
  */
public class ClientboundLoginFinishedShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundLoginFinishedPacket.class;
    }

    private static PropertyMap map(Property... properties) {
        Multimap<String, Property> mm = LinkedHashMultimap.create();
        for (Property p : properties) mm.put(p.name(), p);
        return new PropertyMap(mm);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<String> names = ctx.utf8();

        List<PropertyMap> propertyMaps = List.of(
                map(),
                map(new Property("textures", "dmFsdWU=")),
                map(new Property("textures", "dmFsdWU=", "c2ln")),
                map(new Property("textures", "dmFsdWU=", "c2ln"), new Property("other", "")));

        List<UUID> sessionIds = List.of(new UUID(0L, 0L), new UUID(-1L, -1L));

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundLoginFinishedPacket(
                        new GameProfile((UUID) a[1], (String) a[0], (PropertyMap) a[2]), (UUID) a[3]),
                names, ctx.uuids(), propertyMaps, sessionIds));

        // UTF-8 sweeps inside a single property: name, value and signature
        GameProfile base;
        for (String s : ctx.utf8()) {
            base = new GameProfile(new UUID(1L, 2L), "Player", map(new Property(s, "v")));
            out.add(new ClientboundLoginFinishedPacket(base, new UUID(3L, 4L)));
            base = new GameProfile(new UUID(1L, 2L), "Player", map(new Property("textures", s)));
            out.add(new ClientboundLoginFinishedPacket(base, new UUID(3L, 4L)));
            base = new GameProfile(new UUID(1L, 2L), "Player", map(new Property("textures", "v", s)));
            out.add(new ClientboundLoginFinishedPacket(base, new UUID(3L, 4L)));
        }
        return out;
    }
}
