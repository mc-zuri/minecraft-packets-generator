package dev.minecraftdatagenerator.packetshapes.login;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload;

import java.util.Arrays;
import java.util.List;

/**
 * ServerboundCustomQueryAnswerPacket(int transactionId, nullable CustomQueryAnswerPayload payload).
 * <p>
 * Wire: {@code varInt(transactionId)} then {@code writeNullable(payload)} — a present/absent boolean
 * followed by the payload body. The only payload type the reader ever produces is
 * {@link DiscardedQueryAnswerPayload}, whose {@code write} emits no bytes, so the body is always empty.
 * <p>
 * Full cartesian: transactionId over the VarInt byte-length boundaries (13) x payload absent (null)
 * vs present. 13 x 2 = 26 shapes.
 */
public class ServerboundCustomQueryAnswerShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundCustomQueryAnswerPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<CustomQueryAnswerPayload> payloads = Arrays.asList(null, DiscardedQueryAnswerPayload.INSTANCE);
        return ctx.cross(
                a -> new ServerboundCustomQueryAnswerPacket((int) a[0], (CustomQueryAnswerPayload) a[1]),
                ctx.varInts(), payloads);
    }
}
