package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ServerboundTestInstanceBlockActionPacket(BlockPos pos, Action action, TestInstanceBlockEntity.Data data).
 * <p>
 * Wire: {@code BlockPos.STREAM_CODEC}, {@code idMapper(Action)} (varint 0..6), then
 * {@code Data.STREAM_CODEC} = optional(ResourceKey&lt;GameTestInstance&gt; as a plain ResourceLocation),
 * {@code Vec3i.STREAM_CODEC} (3 varints), {@code idMapper(Rotation)} (varint 0..3),
 * {@code bool(ignoreEntities)}, {@code idMapper(Status)} (varint 0..2),
 * {@code optional(Component errorMessage)}.
 * <p>
 * The categorical dimensions are fully crossed: every {@link ServerboundTestInstanceBlockActionPacket.Action}
 * (7) x test key absent/present (2) x every {@link Rotation} (4) x both {@code ignoreEntities} (2) x
 * every {@link TestInstanceBlockEntity.Status} (3) x errorMessage absent plus each component form (4)
 * = 1344 shapes. On top of that: a block-position sweep, a size sweep over VarInt boundaries on each
 * axis, and a sweep of test-key identifier forms.
 * <p>
 * NOTE: {@code ResourceKey.streamCodec(Registries.TEST_INSTANCE)} is a pure ResourceLocation mapping — it
 * performs no registry lookup on either side — so the {@code Optional.present} branch is fully valid
 * even though the datapack-driven TEST_INSTANCE registry is empty on a bare server.
 */
public class ServerboundTestInstanceBlockActionShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundTestInstanceBlockActionPacket.class;
    }

    private static ResourceKey<GameTestInstance> testKey(ResourceLocation id) {
        return ResourceKey.create(Registries.TEST_INSTANCE, id);
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();
        BlockPos basePos = new BlockPos(0, 0, 0);
        Vec3i baseSize = new Vec3i(1, 1, 1);

        List<Optional<ResourceKey<GameTestInstance>>> tests = List.of(
                Optional.empty(),
                Optional.of(testKey(ResourceLocation.withDefaultNamespace("x"))));

        List<Optional<Component>> errors = new ArrayList<>();
        errors.add(Optional.empty());
        for (Component c : ctx.components()) errors.add(Optional.of(c));

        // full categorical cartesian
        out.addAll(ctx.cross(a -> new ServerboundTestInstanceBlockActionPacket(basePos,
                        (ServerboundTestInstanceBlockActionPacket.Action) a[0],
                        new TestInstanceBlockEntity.Data(
                                castTest(a[1]), baseSize, (Rotation) a[2], (boolean) a[3],
                                (TestInstanceBlockEntity.Status) a[4], castError(a[5]))),
                ctx.enumAll(ServerboundTestInstanceBlockActionPacket.Action.class),
                tests,
                ctx.enumAll(Rotation.class),
                ctx.bools(),
                ctx.enumAll(TestInstanceBlockEntity.Status.class),
                errors));

        ServerboundTestInstanceBlockActionPacket.Action a = ServerboundTestInstanceBlockActionPacket.Action.INIT;
        TestInstanceBlockEntity.Data baseData = new TestInstanceBlockEntity.Data(Optional.empty(),
                baseSize, Rotation.NONE, false, TestInstanceBlockEntity.Status.CLEARED, Optional.empty());

        // block-position sweep
        for (BlockPos pos : ctx.blockPos()) {
            out.add(new ServerboundTestInstanceBlockActionPacket(pos, a, baseData));
        }
        // size sweep: each axis over the VarInt byte-length boundaries, plus a zero and an asymmetric size
        for (int v : ctx.varInts()) {
            out.add(sized(basePos, a, new Vec3i(v, 0, 0)));
            out.add(sized(basePos, a, new Vec3i(0, v, 0)));
            out.add(sized(basePos, a, new Vec3i(0, 0, v)));
        }
        out.add(sized(basePos, a, Vec3i.ZERO));
        out.add(sized(basePos, a, new Vec3i(1, 2, 3)));
        // test-key identifier forms
        for (ResourceLocation id : List.of(ResourceLocation.withDefaultNamespace("x"),
                ResourceLocation.withDefaultNamespace("foo/bar"),
                ResourceLocation.fromNamespaceAndPath("mynamespace", "a_b.c-d/e"))) {
            out.add(new ServerboundTestInstanceBlockActionPacket(basePos, a,
                    new TestInstanceBlockEntity.Data(Optional.of(testKey(id)), baseSize, Rotation.NONE,
                            false, TestInstanceBlockEntity.Status.CLEARED, Optional.empty())));
        }
        return out;
    }

    private static Packet<?> sized(BlockPos pos, ServerboundTestInstanceBlockActionPacket.Action action, Vec3i size) {
        return new ServerboundTestInstanceBlockActionPacket(pos, action,
                new TestInstanceBlockEntity.Data(Optional.empty(), size, Rotation.NONE, false,
                        TestInstanceBlockEntity.Status.CLEARED, Optional.empty()));
    }

    @SuppressWarnings("unchecked")
    private static Optional<ResourceKey<GameTestInstance>> castTest(Object o) {
        return (Optional<ResourceKey<GameTestInstance>>) o;
    }

    @SuppressWarnings("unchecked")
    private static Optional<Component> castError(Object o) {
        return (Optional<Component>) o;
    }
}
