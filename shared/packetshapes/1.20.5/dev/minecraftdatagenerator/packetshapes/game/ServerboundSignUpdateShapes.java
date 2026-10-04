package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundSignUpdatePacket(BlockPos pos, boolean isFrontText, String line0, String line1,
 * String line2, String line3).
 * <p>
 * Wire: {@code writeBlockPos} (packed i64), then {@code writeBoolean} for {@code isFrontText}, then
 * exactly four {@code writeUtf} strings back to back (no list length prefix - the count of 4 is
 * implied; each string is read back with a 384-char cap).
 * <p>
 * Categorical dimensions are fully crossed: both {@code isFrontText} values x every domain-correct
 * block position. On top of that every one of the four line positions is swept over the UTF-8
 * corner-case strings (empty, ASCII, 2-/3-/4-byte code points) for both boolean values, so the
 * per-line varint length prefix is exercised in every position.
 */
public class ServerboundSignUpdateShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSignUpdatePacket.class;
    }

    private static Packet<?> make(BlockPos pos, boolean front, List<String> lines) {
        return new ServerboundSignUpdatePacket(pos, front,
                lines.get(0), lines.get(1), lines.get(2), lines.get(3));
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        BlockPos basePos = new BlockPos(0, 0, 0);

        // categorical cartesian: isFrontText x position
        for (boolean front : ctx.bools()) {
            for (BlockPos pos : ctx.blockPos()) {
                out.add(make(pos, front, List.of("a", "b", "c", "d")));
            }
        }
        // per-line string sweep, in every line position, for both boolean values
        for (boolean front : ctx.bools()) {
            for (String s : ctx.utf8()) {
                for (int line = 0; line < 4; line++) {
                    List<String> lines = new ArrayList<>(List.of("", "", "", ""));
                    lines.set(line, s);
                    out.add(make(basePos, front, lines));
                }
            }
        }
        // all four lines empty / all four lines distinct non-ASCII
        out.add(make(basePos, true, List.of("", "", "", "")));
        out.add(make(basePos, false,
                List.of("minecraft:stone", "\u4f60\u597d", "\u00e9\u00e8\u00ea",
                        new String(Character.toChars(0x1F600)))));
        return out;
    }
}
