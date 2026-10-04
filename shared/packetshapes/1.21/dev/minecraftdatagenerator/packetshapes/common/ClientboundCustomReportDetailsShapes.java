package dev.minecraftdatagenerator.packetshapes.common;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomReportDetailsPacket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ClientboundCustomReportDetailsPacket(Map&lt;String,String&gt; details) —
 * {@code ByteBufCodecs.map(HashMap::new, stringUtf8(128), stringUtf8(4096), 32)}: a VarInt entry
 * count followed by key/value UTF-8 pairs. Covers map sizes 0/1/2 and the decoder's 32-entry
 * maximum, plus a per-field sweep of the UTF-8 key spread and the UTF-8 value spread, and a value
 * long enough to force a two-byte length prefix.
 * Sizes above 32 are omitted: {@code ByteBufCodecs.map} rejects them on decode.
 */
public class ClientboundCustomReportDetailsShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundCustomReportDetailsPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        out.add(new ClientboundCustomReportDetailsPacket(Map.of()));

        Map<String, String> one = new LinkedHashMap<>();
        one.put("k", "v");
        out.add(new ClientboundCustomReportDetailsPacket(one));

        Map<String, String> two = new LinkedHashMap<>();
        two.put("", "");
        two.put("key", "value");
        out.add(new ClientboundCustomReportDetailsPacket(two));

        // MAX_DETAIL_COUNT = 32 (the decoder's upper bound).
        Map<String, String> max = new LinkedHashMap<>();
        for (int i = 0; i < 32; i++) max.put("k" + i, "v" + i);
        out.add(new ClientboundCustomReportDetailsPacket(max));

        // key sweep (max 128 chars) and value sweep (max 4096 chars)
        for (String k : ctx.utf8()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put(k, "v");
            out.add(new ClientboundCustomReportDetailsPacket(m));
        }
        for (String v : ctx.utf8()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("k", v);
            out.add(new ClientboundCustomReportDetailsPacket(m));
        }
        Map<String, String> longs = new LinkedHashMap<>();
        longs.put("k".repeat(128), "v".repeat(300)); // both length prefixes at/over one byte
        out.add(new ClientboundCustomReportDetailsPacket(longs));

        return out;
    }
}
