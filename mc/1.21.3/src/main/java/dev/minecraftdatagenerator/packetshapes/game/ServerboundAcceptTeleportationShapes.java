package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;

import java.util.List;

/**
 * ServerboundAcceptTeleportationPacket(int id).
 * <p>
 * Wire body: {@code id:varint} — the acknowledged teleport id is the only field in 1.21.3 (the
 * acknowledged position and rotation were added later).
 * <p>
 * Coverage: the single {@code id} field over every VarInt byte-length boundary (1..5 bytes) plus the
 * negative forms, which always encode as the full five bytes.
 */
public class ServerboundAcceptTeleportationShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundAcceptTeleportationPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.each(ctx.varInts(), ServerboundAcceptTeleportationPacket::new);
    }
}
