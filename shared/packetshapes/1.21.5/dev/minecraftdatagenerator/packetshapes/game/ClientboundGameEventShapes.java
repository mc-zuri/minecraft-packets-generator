package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;

import java.util.List;

/**
 * ClientboundGameEventPacket(Type event, float param) — wire body is {@code event.id:unsignedByte,
 * param:float}. {@code Type} is an int-id holder (not an enum); every known event constant is crossed
 * with the float param edge cases.
 */
public class ClientboundGameEventShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundGameEventPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<ClientboundGameEventPacket.Type> events = List.of(
                ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE,
                ClientboundGameEventPacket.START_RAINING,
                ClientboundGameEventPacket.STOP_RAINING,
                ClientboundGameEventPacket.CHANGE_GAME_MODE,
                ClientboundGameEventPacket.WIN_GAME,
                ClientboundGameEventPacket.DEMO_EVENT,
                ClientboundGameEventPacket.PLAY_ARROW_HIT_SOUND,
                ClientboundGameEventPacket.RAIN_LEVEL_CHANGE,
                ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE,
                ClientboundGameEventPacket.PUFFER_FISH_STING,
                ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT,
                ClientboundGameEventPacket.IMMEDIATE_RESPAWN,
                ClientboundGameEventPacket.LIMITED_CRAFTING,
                ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START);
        return ctx.cross(
                a -> new ClientboundGameEventPacket((ClientboundGameEventPacket.Type) a[0], (float) a[1]),
                events, ctx.floats());
    }
}
