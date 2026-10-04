package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ClientboundBossEventPacket — discriminator packet. Wire body is {@code id:uuid, type:enum(OperationType)}
 * followed by per-operation fields:
 * <ul>
 *   <li>ADD: name(Component), progress(float), color(enum), overlay(enum), properties(byte bitfield)</li>
 *   <li>REMOVE: nothing</li>
 *   <li>UPDATE_PROGRESS: progress(float)</li>
 *   <li>UPDATE_NAME: name(Component)</li>
 *   <li>UPDATE_STYLE: color(enum), overlay(enum)</li>
 *   <li>UPDATE_PROPERTIES: properties(byte bitfield)</li>
 * </ul>
 * Built through the public static factory methods (which read a {@link BossEvent}); we synthesize a
 * minimal BossEvent per shape carrying exactly the fields written. Every operation branch is covered.
 * ADD is emitted as a full cartesian of its categorical fields (7 colors x 5 overlays x the three
 * property bits = 280 shapes), plus per-field sweeps for the float progress and the Component name.
 * UPDATE_STYLE crosses all colors x overlays, UPDATE_PROPERTIES emits all 8 flag-bit combinations.
 */
public class ClientboundBossEventShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundBossEventPacket.class;
    }

    private static BossEvent boss(Component name, float progress, BossBarColor color, BossBarOverlay overlay,
                                  boolean darken, boolean music, boolean fog) {
        BossEvent e = new BossEvent(new UUID(1L, 2L), name, color, overlay) {
        };
        e.setProgress(progress);
        e.setDarkenScreen(darken);
        e.setPlayBossMusic(music);
        e.setCreateWorldFog(fog);
        return e;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        Component baseName = Component.literal("bar");
        BossBarColor baseColor = BossBarColor.PINK;
        BossBarOverlay baseOverlay = BossBarOverlay.PROGRESS;

        // REMOVE
        for (UUID id : ctx.uuids()) {
            out.add(ClientboundBossEventPacket.createRemovePacket(id));
        }

        // ADD: full cartesian of every categorical field — color x overlay x the three property bits
        // (7 x 5 x 2 x 2 x 2 = 280 shapes).
        for (BossBarColor color : ctx.enumAll(BossBarColor.class)) {
            for (BossBarOverlay overlay : ctx.enumAll(BossBarOverlay.class)) {
                for (boolean darken : ctx.bools()) {
                    for (boolean music : ctx.bools()) {
                        for (boolean fog : ctx.bools()) {
                            out.add(ClientboundBossEventPacket.createAddPacket(
                                    boss(baseName, 1.0f, color, overlay, darken, music, fog)));
                        }
                    }
                }
            }
        }
        // ADD: progress spread and name spread
        for (float progress : ctx.floats()) {
            out.add(ClientboundBossEventPacket.createAddPacket(
                    boss(baseName, progress, baseColor, baseOverlay, false, false, false)));
        }
        for (Component name : ctx.components()) {
            out.add(ClientboundBossEventPacket.createAddPacket(
                    boss(name, 1.0f, baseColor, baseOverlay, false, false, false)));
        }

        // UPDATE_PROGRESS
        for (float progress : ctx.floats()) {
            out.add(ClientboundBossEventPacket.createUpdateProgressPacket(
                    boss(baseName, progress, baseColor, baseOverlay, false, false, false)));
        }
        // UPDATE_NAME
        for (Component name : ctx.components()) {
            out.add(ClientboundBossEventPacket.createUpdateNamePacket(
                    boss(name, 1.0f, baseColor, baseOverlay, false, false, false)));
        }
        // UPDATE_STYLE: color x overlay
        for (BossBarColor color : ctx.enumAll(BossBarColor.class)) {
            for (BossBarOverlay overlay : ctx.enumAll(BossBarOverlay.class)) {
                out.add(ClientboundBossEventPacket.createUpdateStylePacket(
                        boss(baseName, 1.0f, color, overlay, false, false, false)));
            }
        }
        // UPDATE_PROPERTIES: all 8 flag combinations
        for (boolean darken : ctx.bools()) {
            for (boolean music : ctx.bools()) {
                for (boolean fog : ctx.bools()) {
                    out.add(ClientboundBossEventPacket.createUpdatePropertiesPacket(
                            boss(baseName, 1.0f, baseColor, baseOverlay, darken, music, fog)));
                }
            }
        }
        return out;
    }
}
