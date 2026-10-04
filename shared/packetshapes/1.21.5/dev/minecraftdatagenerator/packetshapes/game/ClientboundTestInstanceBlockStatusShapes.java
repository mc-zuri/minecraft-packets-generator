package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundTestInstanceBlockStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ClientboundTestInstanceBlockStatus(Component status, Optional&lt;Vec3i&gt; size).
 * <p>
 * {@code Vec3i.STREAM_CODEC} is three varints, and the Optional adds a leading bool discriminator.
 * Coverage: full cross of the component spread with {empty, a few concrete sizes}, plus a per-axis
 * sweep of the size over the VarInt byte-length boundaries.
 */
public class ClientboundTestInstanceBlockStatusShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundTestInstanceBlockStatus.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Optional<Vec3i>> sizes = List.of(
                Optional.empty(),
                Optional.of(Vec3i.ZERO),
                Optional.of(new Vec3i(1, 2, 3)),
                Optional.of(new Vec3i(-1, -2, -3)));

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ClientboundTestInstanceBlockStatus((Component) a[0], castSize(a[1])),
                ctx.components(), sizes));

        Component status = Component.literal("x");
        for (int v : ctx.varInts()) {
            out.add(new ClientboundTestInstanceBlockStatus(status, Optional.of(new Vec3i(v, 0, 0))));
            out.add(new ClientboundTestInstanceBlockStatus(status, Optional.of(new Vec3i(0, v, 0))));
            out.add(new ClientboundTestInstanceBlockStatus(status, Optional.of(new Vec3i(0, 0, v))));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Optional<Vec3i> castSize(Object o) {
        return (Optional<Vec3i>) o;
    }
}
