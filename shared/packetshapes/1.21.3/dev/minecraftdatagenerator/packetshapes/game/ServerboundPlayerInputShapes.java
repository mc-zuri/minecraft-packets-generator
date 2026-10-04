package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;

import java.util.List;

/**
 * ServerboundPlayerInputPacket(Input input) — {@code Input} packs 7 booleans into a single flags byte
 * (bit0 forward, bit1 backward, bit2 left, bit3 right, bit4 jump, bit5 shift, bit6 sprint).
 * <p>
 * The flags byte is a bit-field discriminator, so every combination is emitted: a full cartesian of
 * the 7 booleans = 128 shapes (every byte value 0..127 the encoder can produce).
 */
public class ServerboundPlayerInputShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundPlayerInputPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        return ctx.cross(
                a -> new ServerboundPlayerInputPacket(new Input(
                        (boolean) a[0], (boolean) a[1], (boolean) a[2], (boolean) a[3],
                        (boolean) a[4], (boolean) a[5], (boolean) a[6])),
                ctx.bools(), ctx.bools(), ctx.bools(), ctx.bools(), ctx.bools(), ctx.bools(), ctx.bools());
    }
}
