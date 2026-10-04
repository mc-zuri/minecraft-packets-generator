package dev.minecraftdatagenerator.packetshapes.handshake;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;

import java.util.List;

/**
 * ClientIntentionPacket(int protocolVersion, String hostName, int port, ClientIntent intention).
 * <p>
 * Wire: {@code varInt(protocolVersion)}, {@code writeUtf(hostName)} (read back with a 255-char cap),
 * {@code writeShort(port)} (read back with {@code readUnsignedShort}), {@code varInt(intention.id())}
 * — note the id is 1/2/3, not the ordinal.
 * <p>
 * Full cartesian: protocolVersion over the VarInt byte-length boundaries (13) x hostName over the
 * UTF-8 corner cases (6) x port (4) x every {@link ClientIntent} constant (3) = 936 shapes.
 * <p>
 * OMITTED: ports outside 0..65535 — {@code writeShort} truncates to 16 bits and
 * {@code readUnsignedShort} returns 0..65535, so such values would not round-trip.
 */
@SuppressWarnings("deprecation")
public class ClientIntentionShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientIntentionPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Integer> ports = List.of(0, 1, 25565, 65535);
        return ctx.cross(
                a -> new ClientIntentionPacket((int) a[0], (String) a[1], (int) a[2], (ClientIntent) a[3]),
                ctx.varInts(), ctx.utf8(), ports, ctx.enumAll(ClientIntent.class));
    }
}
