package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignTextSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundSignUpdatePacket(BlockPos pos, List&lt;String&gt; lines, SignTextSlot slot).
 * <p>
 * Wire: {@code BlockPos.STREAM_CODEC} (packed long), then a <b>fixed-size list of exactly 4</b>
 * {@code stringUtf8(384)} entries (no length prefix — the size is implied), then
 * {@code SignTextSlot.STREAM_CODEC} (varint id: BACK=0, FRONT=1).
 * <p>
 * Categorical dimensions are fully crossed: every {@link SignTextSlot} constant x every
 * domain-correct block position. On top of that every one of the four line positions is swept over
 * the UTF-8 corner-case strings (empty, ASCII, 2-/3-/4-byte code points) for both slots, so the
 * per-line length prefix is exercised in every position.
 */
public class ServerboundSignUpdateShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSignUpdatePacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        BlockPos basePos = new BlockPos(0, 0, 0);

        // categorical cartesian: slot x position
        for (SignTextSlot slot : ctx.enumAll(SignTextSlot.class)) {
            for (BlockPos pos : ctx.blockPos()) {
                out.add(new ServerboundSignUpdatePacket(pos, List.of("a", "b", "c", "d"), slot));
            }
        }
        // per-line string sweep, in every line position, for both slots
        for (SignTextSlot slot : ctx.enumAll(SignTextSlot.class)) {
            for (String s : ctx.utf8()) {
                for (int line = 0; line < 4; line++) {
                    List<String> lines = new ArrayList<>(List.of("", "", "", ""));
                    lines.set(line, s);
                    out.add(new ServerboundSignUpdatePacket(basePos, List.copyOf(lines), slot));
                }
            }
        }
        // all four lines non-empty / all four empty
        out.add(new ServerboundSignUpdatePacket(basePos, List.of("", "", "", ""), SignTextSlot.FRONT));
        out.add(new ServerboundSignUpdatePacket(basePos,
                List.of("minecraft:stone", "\u4f60\u597d", "\u00e9\u00e8\u00ea",
                        new String(Character.toChars(0x1F600))),
                SignTextSlot.BACK));
        return out;
    }
}
