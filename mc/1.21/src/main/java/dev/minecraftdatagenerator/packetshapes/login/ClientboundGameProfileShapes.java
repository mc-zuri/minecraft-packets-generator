package dev.minecraftdatagenerator.packetshapes.login;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ClientboundGameProfilePacket(GameProfile gameProfile, boolean strictErrorHandling).
 * <p>
 * Wire: {@code ByteBufCodecs.GAME_PROFILE} = raw 16-byte uuid, {@code stringUtf8(16)} name, then
 * {@code GAME_PROFILE_PROPERTIES} = a varint count (max 16) followed by, per property,
 * {@code stringUtf8(64)} name, {@code stringUtf8(32767)} value and a nullable
 * {@code stringUtf8(1024)} signature; then a trailing {@code bool strictErrorHandling}.
 * <p>
 * This is 1.21's login "game profile" packet. 1.21.2 renamed it to ClientboundLoginFinishedPacket and
 * dropped the {@code strictErrorHandling} boolean, so the trailing bool dimension only exists here.
 * <p>
 * Full cartesian: profile name over the UTF-8 corner cases x profile uuid x the property-list forms
 * (0 properties, 1 unsigned with the signature absent, 1 signed with it present, and 2 mixing both)
 * x both {@code strictErrorHandling} values. On top of that a sweep of UTF-8 property names, values
 * and signatures.
 * <p>
 * The property maps are kept to at most two entries so their iteration order matches the
 * {@code ImmutableMultimap} the decoder builds, keeping the shapes byte-stable across a round trip.
 */
public class ClientboundGameProfileShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundGameProfilePacket.class;
    }

    // 1.21 ships the older authlib: PropertyMap has only a no-arg constructor and GameProfile has only
    // the (UUID, String) constructor with a mutable getProperties(), so the profile is assembled rather
    // than built from an immutable PropertyMap.
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
        List<PropertyMap> propertyMaps = List.of(
                map(),
                map(new Property("textures", "dmFsdWU=")),
                map(new Property("textures", "dmFsdWU=", "c2ln")),
                map(new Property("textures", "dmFsdWU=", "c2ln"), new Property("other", "")));

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundGameProfilePacket(
                        profile((UUID) a[1], (String) a[0], (PropertyMap) a[2]), (boolean) a[3]),
                ctx.utf8(), ctx.uuids(), propertyMaps, ctx.bools()));

        // UTF-8 sweeps inside a single property: name, value and signature
        for (String s : ctx.utf8()) {
            out.add(new ClientboundGameProfilePacket(
                    profile(new UUID(1L, 2L), "Player", map(new Property(s, "v"))), false));
            out.add(new ClientboundGameProfilePacket(
                    profile(new UUID(1L, 2L), "Player", map(new Property("textures", s))), false));
            out.add(new ClientboundGameProfilePacket(
                    profile(new UUID(1L, 2L), "Player", map(new Property("textures", "v", s))), false));
        }
        return out;
    }
}
