package dev.minecraftdatagenerator.packetshapes.login;

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
 * ClientboundLoginFinishedPacket(GameProfile gameProfile).
 * <p>
 * Wire: {@code ByteBufCodecs.GAME_PROFILE} = raw 16-byte uuid, {@code stringUtf8(16)} name, then
 * {@code GAME_PROFILE_PROPERTIES} = a varint count (max 16) followed by, per property,
 * {@code stringUtf8(64)} name, {@code stringUtf8(32767)} value and a nullable {@code stringUtf8(1024)}
 * signature. 1.21.3 carries no sessionId - that field arrived in 26.2.
 * <p>
 * Full cartesian: profile name over the UTF-8 corner cases x profile uuid
 * x the property-list forms — 0 properties, 1 unsigned (signature absent), 1 signed (signature
 * present) and 2 properties mixing both. On top of that a sweep of UTF-8 property names,
 * values and signatures.
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

    // 1.21.3 ships the older authlib: PropertyMap has only a no-arg constructor and GameProfile has
    // only the (UUID, String) constructor with a mutable getProperties(), so the profile is assembled
    // rather than built from an immutable PropertyMap.
    private static PropertyMap map(Property... properties) {
        PropertyMap mm = new PropertyMap();
        for (Property p : properties) mm.put(p.name(), p);
        return mm;
    }

    private static GameProfile profile(UUID id, String name, PropertyMap properties) {
        GameProfile profile = new GameProfile(id, name);
        profile.getProperties().putAll(properties);
        return profile;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<String> names = ctx.utf8();

        List<PropertyMap> propertyMaps = List.of(
                map(),
                map(new Property("textures", "dmFsdWU=")),
                map(new Property("textures", "dmFsdWU=", "c2ln")),
                map(new Property("textures", "dmFsdWU=", "c2ln"), new Property("other", "")));

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundLoginFinishedPacket(
                        profile((UUID) a[1], (String) a[0], (PropertyMap) a[2])),
                names, ctx.uuids(), propertyMaps));

        // UTF-8 sweeps inside a single property: name, value and signature
        GameProfile base;
        for (String s : ctx.utf8()) {
            base = profile(new UUID(1L, 2L), "Player", map(new Property(s, "v")));
            out.add(new ClientboundLoginFinishedPacket(base));
            base = profile(new UUID(1L, 2L), "Player", map(new Property("textures", s)));
            out.add(new ClientboundLoginFinishedPacket(base));
            base = profile(new UUID(1L, 2L), "Player", map(new Property("textures", "v", s)));
            out.add(new ClientboundLoginFinishedPacket(base));
        }
        return out;
    }
}
