package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;

import java.util.ArrayList;
import java.util.List;

/**
 * ClientboundSwingAnimationPacket(int entityId [varint], InteractionHand hand [varint enum],
 * SwingAnimation animation).
 * <p>
 * {@code SwingAnimation} is {@code (SwingAnimationType type [varint id], int duration [varint])};
 * the type ids are NONE=0, WHACK=1, STAB=2.
 * <p>
 * Coverage: full cartesian product of the VarInt boundary entity ids x both {@link InteractionHand}
 * constants x every {@link SwingAnimationType} x a non-negative VarInt boundary duration spread.
 * <p>
 * NOTE: negative durations are omitted — {@code SwingAnimation.CODEC} declares the field
 * {@code ExtraCodecs.NON_NEGATIVE_INT}, so vanilla never produces one (the stream codec itself would
 * happily encode it).
 */
public class ClientboundSwingAnimationShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSwingAnimationPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Integer> durations = new ArrayList<>();
        for (int v : ctx.varInts()) if (v >= 0) durations.add(v);

        List<SwingAnimation> animations = new ArrayList<>();
        for (SwingAnimationType type : ctx.enumAll(SwingAnimationType.class)) {
            for (int duration : durations) animations.add(new SwingAnimation(type, duration));
        }

        return ctx.cross(
                a -> new ClientboundSwingAnimationPacket((int) a[0], (InteractionHand) a[1], (SwingAnimation) a[2]),
                ctx.varInts(), ctx.enumAll(InteractionHand.class), animations);
    }
}
