package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSectionBlocksUpdatePacket(SectionPos sectionPos, ShortSet changes, LevelChunkSection section).
 * Wire body: {@code sectionPos:i64} (packed x:22/y:20/z:22), {@code count:varint}, then one
 * {@code varlong} per change carrying {@code blockStateId << 12 | packedPositionInSection}.
 * <p>
 * A standalone {@link LevelChunkSection} is built from the live registries and seeded with distinct block
 * states, so non-empty change sets encode real state ids (including the highest id in
 * {@code Block.BLOCK_STATE_REGISTRY}, which exercises the wide VarLong forms). Coverage: change-set sizes
 * 0/1/2/many over every corner of the 12-bit in-section position (0, 4095 and mixed x/y/z), crossed with a
 * section-position spread over {@link ShapeCtx#chunkCoords()} and {@link ShapeCtx#sectionYs()}.
 */
public class ClientboundSectionBlocksUpdateShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSectionBlocksUpdatePacket.class;
    }

    /** Packing used by SectionPos: {@code x << 8 | z << 4 | y}, each nibble 0..15. */
    private static short packed(int x, int y, int z) {
        return (short) (x << 8 | z << 4 | y);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        // 1.21.8 builds a standalone section straight from the biome registry; the
        // PalettedContainerFactory indirection only arrived in a later version.
        LevelChunkSection section = new LevelChunkSection(ctx.registries.lookupOrThrow(Registries.BIOME));
        BlockState highest = Block.BLOCK_STATE_REGISTRY.byId(Block.BLOCK_STATE_REGISTRY.size() - 1);
        // checkThreading = false: this section is not owned by a level, so no lock is held.
        section.setBlockState(0, 0, 0, Blocks.STONE.defaultBlockState(), false);
        section.setBlockState(1, 2, 3, Blocks.DIRT.defaultBlockState(), false);
        section.setBlockState(15, 15, 15, highest == null ? Blocks.BEDROCK.defaultBlockState() : highest, false);
        // (15, 0, 15) is left as air, i.e. block state id 0.

        List<ShortSet> changeSets = new ArrayList<>();
        changeSets.add(new ShortOpenHashSet());                                     // count = 0
        changeSets.add(set(packed(0, 0, 0)));                                       // lowest packed position
        changeSets.add(set(packed(15, 15, 15)));                                    // highest packed position (4095)
        changeSets.add(set(packed(15, 0, 15)));                                     // air -> state id 0
        changeSets.add(set(packed(0, 0, 0), packed(1, 2, 3)));                      // count = 2
        changeSets.add(set(packed(0, 0, 0), packed(1, 2, 3), packed(15, 15, 15), packed(15, 0, 15)));

        List<SectionPos> positions = new ArrayList<>();
        positions.add(SectionPos.of(0, 0, 0));
        for (int x : ctx.chunkCoords()) positions.add(SectionPos.of(x, 0, 0));
        for (int y : ctx.sectionYs()) positions.add(SectionPos.of(0, y, 0));
        for (int z : ctx.chunkCoords()) positions.add(SectionPos.of(0, 0, z));
        positions.add(SectionPos.of(1875000, 19, -1875000));
        positions.add(SectionPos.of(-1875000, -4, 1875000));

        List<Packet<?>> out = new ArrayList<>();
        for (SectionPos pos : positions) {
            for (ShortSet changes : changeSets) {
                out.add(new ClientboundSectionBlocksUpdatePacket(pos, changes, section));
            }
        }
        return out;
    }

    private static ShortSet set(short... values) {
        ShortOpenHashSet s = new ShortOpenHashSet();
        for (short v : values) s.add(v);
        return s;
    }
}
