package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundSetStructureBlockPacket — 14 fields. Wire, in order:
 * blockPos(pos), enum VarInt(updateType: UPDATE_DATA/SAVE_AREA/LOAD_AREA/SCAN_AREA),
 * enum VarInt(mode: SAVE/LOAD/CORNER/DATA), utf(name),
 * i8(offsetX), i8(offsetY), i8(offsetZ), i8(sizeX), i8(sizeY), i8(sizeZ),
 * enum VarInt(mirror: NONE/LEFT_RIGHT/FRONT_BACK), idMapper VarInt(rotation: NONE/CLOCKWISE_90/
 * CLOCKWISE_180/COUNTERCLOCKWISE_90), utf(data, max 128), f32(integrity), varLong(seed),
 * i8(flags: bit0 ignoreEntities, bit1 showAir, bit2 showBoundingBox).
 * <p>
 * 1.21 has no {@code strict} flag (bit3) -- it was added in 1.21.5 -- so the flag byte has only
 * three meaningful bits here.
 * <p>
 * Categorical cartesian: updateType(4) x mode(4) x mirror(3) x rotation(4) x a flag spread of
 * {none, all, and each of the 3 bits one-hot} (5) = 960 shapes. All 8 flag-byte combinations are
 * additionally emitted at the baseline, and every remaining field is swept individually.
 * <p>
 * NOTE (round-trip validity): offset/size are single signed bytes clamped on read — offset to
 * [-48,48] and size to [0,48] — and integrity is clamped to [0,1], so only in-range values are
 * emitted. {@code data} is read with {@code readUtf(128)}, so only short strings are used.
 */
public class ServerboundSetStructureBlockShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSetStructureBlockPacket.class;
    }

    private static final BlockPos BASE_POS = new BlockPos(0, 0, 0);
    private static final StructureBlockEntity.UpdateType BASE_UPDATE = StructureBlockEntity.UpdateType.UPDATE_DATA;
    private static final StructureMode BASE_MODE = StructureMode.SAVE;
    private static final String BASE_NAME = "";
    private static final BlockPos BASE_OFFSET = new BlockPos(0, 0, 0);
    private static final Vec3i BASE_SIZE = new Vec3i(0, 0, 0);
    private static final Mirror BASE_MIRROR = Mirror.NONE;
    private static final Rotation BASE_ROTATION = Rotation.NONE;
    private static final String BASE_DATA = "";
    private static final float BASE_INTEGRITY = 1.0f;
    private static final long BASE_SEED = 0L;

    private static ServerboundSetStructureBlockPacket make(
            BlockPos pos, StructureBlockEntity.UpdateType updateType, StructureMode mode, String name,
            BlockPos offset, Vec3i size, Mirror mirror, Rotation rotation, String data,
            boolean[] flags, float integrity, long seed) {
        return new ServerboundSetStructureBlockPacket(pos, updateType, mode, name, offset, size, mirror,
                rotation, data, flags[0], flags[1], flags[2], integrity, seed);
    }

    /** {ignoreEntities, showAir, showBoundingBox} — constructor order. */
    private static boolean[] flags(boolean ignoreEntities, boolean showAir, boolean showBoundingBox) {
        return new boolean[]{ignoreEntities, showAir, showBoundingBox};
    }

    private static final boolean[] NO_FLAGS = flags(false, false, false);

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        // flag spread: none, all, and each bit one-hot
        List<boolean[]> flagSpread = List.of(
                NO_FLAGS,
                flags(true, true, true),
                flags(true, false, false),   // bit0 ignoreEntities
                flags(false, true, false),   // bit1 showAir
                flags(false, false, true));  // bit2 showBoundingBox

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> make(BASE_POS, (StructureBlockEntity.UpdateType) a[0], (StructureMode) a[1],
                        BASE_NAME, BASE_OFFSET, BASE_SIZE, (Mirror) a[2], (Rotation) a[3], BASE_DATA,
                        (boolean[]) a[4], BASE_INTEGRITY, BASE_SEED),
                ctx.enumAll(StructureBlockEntity.UpdateType.class), ctx.enumAll(StructureMode.class),
                ctx.enumAll(Mirror.class), ctx.enumAll(Rotation.class), flagSpread));

        // every one of the 8 flag-byte values, at the baseline
        out.addAll(ctx.cross(
                a -> make(BASE_POS, BASE_UPDATE, BASE_MODE, BASE_NAME, BASE_OFFSET, BASE_SIZE,
                        BASE_MIRROR, BASE_ROTATION, BASE_DATA,
                        flags((boolean) a[0], (boolean) a[1], (boolean) a[2]),
                        BASE_INTEGRITY, BASE_SEED),
                ctx.bools(), ctx.bools(), ctx.bools()));

        for (BlockPos pos : ctx.blockPos()) {
            out.add(make(pos, BASE_UPDATE, BASE_MODE, BASE_NAME, BASE_OFFSET, BASE_SIZE, BASE_MIRROR,
                    BASE_ROTATION, BASE_DATA, NO_FLAGS, BASE_INTEGRITY, BASE_SEED));
        }
        for (String name : ctx.utf8()) {
            out.add(make(BASE_POS, BASE_UPDATE, BASE_MODE, name, BASE_OFFSET, BASE_SIZE, BASE_MIRROR,
                    BASE_ROTATION, BASE_DATA, NO_FLAGS, BASE_INTEGRITY, BASE_SEED));
        }
        // offset: each component over the clamped [-48, 48] range
        for (BlockPos offset : List.of(new BlockPos(-48, -48, -48), new BlockPos(-1, 0, 1),
                new BlockPos(1, -2, 3), new BlockPos(48, 48, 48), new BlockPos(48, -48, 0))) {
            out.add(make(BASE_POS, BASE_UPDATE, BASE_MODE, BASE_NAME, offset, BASE_SIZE, BASE_MIRROR,
                    BASE_ROTATION, BASE_DATA, NO_FLAGS, BASE_INTEGRITY, BASE_SEED));
        }
        // size: each component over the clamped [0, 48] range
        for (Vec3i size : List.of(new Vec3i(0, 0, 0), new Vec3i(1, 2, 3), new Vec3i(48, 48, 48),
                new Vec3i(48, 0, 1))) {
            out.add(make(BASE_POS, BASE_UPDATE, BASE_MODE, BASE_NAME, BASE_OFFSET, size, BASE_MIRROR,
                    BASE_ROTATION, BASE_DATA, NO_FLAGS, BASE_INTEGRITY, BASE_SEED));
        }
        for (String data : ctx.utf8()) {
            out.add(make(BASE_POS, BASE_UPDATE, BASE_MODE, BASE_NAME, BASE_OFFSET, BASE_SIZE,
                    BASE_MIRROR, BASE_ROTATION, data, NO_FLAGS, BASE_INTEGRITY, BASE_SEED));
        }
        // integrity: clamped to [0, 1] on read
        for (float integrity : List.of(0.0f, 0.25f, 0.5f, 1.0f)) {
            out.add(make(BASE_POS, BASE_UPDATE, BASE_MODE, BASE_NAME, BASE_OFFSET, BASE_SIZE,
                    BASE_MIRROR, BASE_ROTATION, BASE_DATA, NO_FLAGS, integrity, BASE_SEED));
        }
        // seed: every VarLong byte-length boundary
        for (long seed : ctx.varLongs()) {
            out.add(make(BASE_POS, BASE_UPDATE, BASE_MODE, BASE_NAME, BASE_OFFSET, BASE_SIZE,
                    BASE_MIRROR, BASE_ROTATION, BASE_DATA, NO_FLAGS, BASE_INTEGRITY, seed));
        }
        return out;
    }
}
