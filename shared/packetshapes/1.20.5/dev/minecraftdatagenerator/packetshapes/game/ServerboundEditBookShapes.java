package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ServerboundEditBookPacket(int slot[VarInt], List&lt;String&gt; pages[VarInt count, max 100; each
 * stringUtf8(1024)], Optional&lt;String&gt; title[bool prefix + stringUtf8(32)]).
 * <p>
 * Full cartesian of every VarInt byte-length boundary for {@code slot} x page lists of size 0/1/2
 * carrying the UTF-8 spread (ASCII, 2-byte, 3-byte and astral 4-byte code points, plus the empty
 * string) x {absent} + every UTF-8 title (13 x 5 x 7 = 455). Both the empty and present branches of the
 * optional are covered, and the page list exercises the collection-count prefix at 0, 1 and 2.
 */
public class ServerboundEditBookShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundEditBookPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        String astral = new String(Character.toChars(0x1F600));
        List<List<String>> pageLists = List.of(
                List.of(),                      // count 0
                List.of(""),                    // count 1, zero-length string
                List.of("page one"),            // count 1, ASCII
                List.of("éèê", "你好"),          // count 2, 2-byte and 3-byte code points
                List.of(astral, "minecraft:stone"));  // count 2, astral (4-byte) code point

        // title is stringUtf8(32) - every ctx.utf8() value is well inside that bound.
        List<Optional<String>> titles = new ArrayList<>();
        titles.add(Optional.empty());
        for (String s : ctx.utf8()) titles.add(Optional.of(s));

        return ctx.cross(
                a -> new ServerboundEditBookPacket((int) a[0],
                        castPages(a[1]), castTitle(a[2])),
                ctx.varInts(), pageLists, titles);
    }

    @SuppressWarnings("unchecked")
    private static List<String> castPages(Object o) {
        return (List<String>) o;
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> castTitle(Object o) {
        return (Optional<String>) o;
    }
}
