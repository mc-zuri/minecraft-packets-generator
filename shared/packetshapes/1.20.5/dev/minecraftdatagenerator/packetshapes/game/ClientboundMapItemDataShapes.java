package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ClientboundMapItemDataPacket(MapId mapId[varint], byte scale, boolean locked,
 * Optional&lt;List&lt;MapDecoration&gt;&gt; decorations, Optional&lt;MapPatch&gt; colorPatch).
 * <p>
 * Each {@code MapDecoration} is {@code registry-id type, i8 x, i8 y, i8 rot, Optional&lt;Component&gt; name};
 * the record constructor masks {@code rot} with {@code &amp; 15}, so only 0..15 are reachable on the wire.
 * <p>
 * {@code MapPatch} has a hand-written codec that does NOT use a boolean prefix: the absent case writes a
 * single {@code 0} width byte, and the present case writes
 * {@code u8 width, u8 height, u8 startX, u8 startY, varint-prefixed byte[] colors}. Width 0 therefore
 * decodes as absent, so every present patch uses a width of at least 1.
 * <p>
 * Coverage: full cartesian of the two optional/collection dimensions (decorations absent/0/1/2 and
 * colorPatch absent/present) with {@code locked}; sweeps of scale (i8 corners), mapId (VarInt
 * boundaries), every decoration field (all registered MapDecorationTypes, i8 x/y, the 0..15 rot range,
 * the name Optional in both forms and the component spread) and every MapPatch geometry byte plus the
 * color array cardinality.
 * <p>
 * OMITTED: a present MapPatch with width 0 — vanilla's reader treats a leading 0 as the absent branch,
 * so such a shape would not round-trip. Geometry bytes are written with {@code writeByte(int)} and read
 * back unsigned, so only 0..255 are used.
 */
public class ClientboundMapItemDataShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundMapItemDataPacket.class;
    }

    private static ClientboundMapItemDataPacket mk(MapId id, byte scale, boolean locked,
                                                   Optional<List<MapDecoration>> decorations,
                                                   Optional<MapItemSavedData.MapPatch> patch) {
        return new ClientboundMapItemDataPacket(id, scale, locked, decorations, patch);
    }

    private static MapItemSavedData.MapPatch patch(int startX, int startY, int width, int height, int colors) {
        byte[] bytes = new byte[colors];
        for (int i = 0; i < colors; i++) bytes[i] = (byte) (i + 1);
        return new MapItemSavedData.MapPatch(startX, startY, width, height, bytes);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        Holder<MapDecorationType> type = ctx.holder(Registries.MAP_DECORATION_TYPE);
        MapDecoration named = new MapDecoration(type, (byte) 4, (byte) -4, (byte) 3, Optional.of(Component.literal("x")));
        MapDecoration unnamed = new MapDecoration(type, (byte) 0, (byte) 0, (byte) 0, Optional.empty());

        List<Optional<List<MapDecoration>>> decoLists = new ArrayList<>();
        decoLists.add(Optional.empty());                                       // absent
        decoLists.add(Optional.of(List.<MapDecoration>of()));                  // present, 0
        decoLists.add(Optional.of(List.of(named)));                            // present, 1
        decoLists.add(Optional.of(List.of(named, unnamed)));                   // present, 2
        MapItemSavedData.MapPatch basePatch = patch(0, 0, 1, 1, 1);
        List<Optional<MapItemSavedData.MapPatch>> patches = List.of(Optional.empty(), Optional.of(basePatch));

        MapId mapId = new MapId(0);

        // Full cartesian of the categorical dimensions: decorations x colorPatch x locked.
        for (Optional<List<MapDecoration>> decos : decoLists) {
            for (Optional<MapItemSavedData.MapPatch> p : patches) {
                for (boolean locked : ctx.bools()) {
                    out.add(mk(mapId, (byte) 0, locked, decos, p));
                }
            }
        }
        // scale: full i8 corner spread, plus the 0..4 range real maps use.
        for (byte scale : ctx.bytes()) {
            out.add(mk(mapId, scale, false, Optional.empty(), Optional.empty()));
        }
        for (byte scale = 0; scale <= 4; scale++) {
            out.add(mk(mapId, scale, false, Optional.empty(), Optional.empty()));
        }
        // mapId: every VarInt byte-length boundary.
        for (int id : ctx.varInts()) {
            out.add(mk(new MapId(id), (byte) 0, false, Optional.empty(), Optional.empty()));
        }
        // Decoration fields: every registered type, the i8 x/y corners, the reachable rot range and
        // the name Optional in both forms.
        Registry<MapDecorationType> types = ctx.registries.registryOrThrow(Registries.MAP_DECORATION_TYPE);
        for (Holder.Reference<MapDecorationType> t : types.holders().toList()) {
            out.add(mk(mapId, (byte) 0, false,
                    Optional.of(List.of(new MapDecoration(t, (byte) 0, (byte) 0, (byte) 0, Optional.empty()))),
                    Optional.empty()));
        }
        for (byte b : ctx.bytes()) {
            out.add(mk(mapId, (byte) 0, false,
                    Optional.of(List.of(new MapDecoration(type, b, (byte) 0, (byte) 0, Optional.empty()))), Optional.empty()));
            out.add(mk(mapId, (byte) 0, false,
                    Optional.of(List.of(new MapDecoration(type, (byte) 0, b, (byte) 0, Optional.empty()))), Optional.empty()));
        }
        for (byte rot = 0; rot < 16; rot++) {
            out.add(mk(mapId, (byte) 0, false,
                    Optional.of(List.of(new MapDecoration(type, (byte) 0, (byte) 0, rot, Optional.empty()))), Optional.empty()));
        }
        for (Component name : ctx.components()) {
            out.add(mk(mapId, (byte) 0, false,
                    Optional.of(List.of(new MapDecoration(type, (byte) 0, (byte) 0, (byte) 0, Optional.of(name)))), Optional.empty()));
        }
        // MapPatch geometry: the reachable unsigned-byte range in every slot, and color-array sizes.
        List<Integer> unsignedBytes = List.of(0, 1, 2, 127, 128, 255);
        for (int v : unsignedBytes) {
            out.add(mk(mapId, (byte) 0, false, Optional.empty(), Optional.of(patch(v, 0, 1, 1, 1))));
            out.add(mk(mapId, (byte) 0, false, Optional.empty(), Optional.of(patch(0, v, 1, 1, 1))));
            if (v > 0) {
                // width 0 is the absent branch, so only non-zero widths are emitted.
                out.add(mk(mapId, (byte) 0, false, Optional.empty(), Optional.of(patch(0, 0, v, 1, v))));
            }
            out.add(mk(mapId, (byte) 0, false, Optional.empty(), Optional.of(patch(0, 0, 1, v, Math.max(v, 0)))));
        }
        for (int colors : List.of(0, 1, 2, 127, 128)) {
            out.add(mk(mapId, (byte) 0, false, Optional.empty(), Optional.of(patch(0, 0, 1, colors, colors))));
        }
        return out;
    }
}
