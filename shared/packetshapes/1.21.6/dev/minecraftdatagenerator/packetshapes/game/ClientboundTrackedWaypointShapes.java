package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import net.minecraft.world.waypoints.WaypointStyleAssets;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * ClientboundTrackedWaypointPacket(Operation operation, TrackedWaypoint waypoint).
 * <p>
 * Wire body:
 * <pre>
 * operation : varint enum   (TRACK=0, UNTRACK=1, UPDATE=2)
 * waypoint  : identifier : Either&lt;UUID, String&gt;  (bool true -&gt; 16-byte UUID, false -&gt; utf string)
 *             icon       : style : ResourceLocation, color : Optional&lt;rgb, 3 bytes&gt;
 *             type       : varint enum (EMPTY=0, VEC3I=1, CHUNK=2, AZIMUTH=3)
 *             contents   : EMPTY -&gt; nothing, VEC3I -&gt; 3 varints, CHUNK -&gt; 2 varints, AZIMUTH -&gt; f32
 * </pre>
 * <p>
 * Coverage:
 * <ul>
 *   <li>every waypoint type (EMPTY via removeWaypoint, VEC3I / CHUNK / AZIMUTH via the position,
 *       chunk and azimuth factories) crossed with both operations reachable for that type;</li>
 *   <li>every icon form: both registered styles (default, bowtie) crossed with colour present
 *       and absent;</li>
 *   <li>both Either identifier branches - the UUID branch through the public factories, and the
 *       String branch by hand-encoding the body and decoding it back through the packet's own
 *       STREAM_CODEC (the TrackedWaypoint subclasses and the Operation enum are both private, so
 *       there is no constructor route to that branch);</li>
 *   <li>per-field sweeps of the VEC3I x/y/z varints, the CHUNK x/z varints and the AZIMUTH float.</li>
 * </ul>
 * <p>
 * NOTE: EMPTY is only reachable through removeWaypoint, which hard-codes Operation.UNTRACK and
 * Waypoint.Icon.NULL; the remaining operation/icon combinations for EMPTY are produced through the
 * hand-encoded String-identifier route instead.
 */
public class ClientboundTrackedWaypointShapes implements PacketShapes {
    private static final int OP_TRACK = 0;
    private static final int OP_UNTRACK = 1;
    private static final int OP_UPDATE = 2;

    private static final int TYPE_EMPTY = 0;
    private static final int TYPE_VEC3I = 1;
    private static final int TYPE_CHUNK = 2;
    private static final int TYPE_AZIMUTH = 3;

    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundTrackedWaypointPacket.class;
    }

    private static Waypoint.Icon icon(ResourceKey<WaypointStyleAsset> style, Optional<Integer> color) {
        Waypoint.Icon icon = new Waypoint.Icon();
        icon.style = style;
        icon.color = color;
        return icon;
    }

    /**
     * Builds a packet whose waypoint identifier is the Either.right(String) branch by encoding the
     * body by hand and decoding it through the real STREAM_CODEC. Returns null (and is dropped) if
     * the hand-encoding does not round-trip.
     */
    private static Packet<?> stringIdentified(ShapeCtx ctx, int operation, String name, ResourceLocation style,
                                              Integer color, int type, Consumer<FriendlyByteBuf> contents) {
        try {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), ctx.registries);
            buf.writeVarInt(operation);
            buf.writeBoolean(false); // Either.right -> String identifier
            buf.writeUtf(name);
            buf.writeResourceLocation(style);
            buf.writeBoolean(color != null); // Optional colour
            if (color != null) {
                buf.writeByte((color >> 16) & 0xFF);
                buf.writeByte((color >> 8) & 0xFF);
                buf.writeByte(color & 0xFF);
            }
            buf.writeVarInt(type);
            contents.accept(buf);
            return ClientboundTrackedWaypointPacket.STREAM_CODEC.decode(buf);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        List<Waypoint.Icon> icons = List.of(
                icon(WaypointStyleAssets.DEFAULT, Optional.empty()),
                icon(WaypointStyleAssets.DEFAULT, Optional.of(0x00FF00)),
                icon(WaypointStyleAssets.BOWTIE, Optional.empty()),
                icon(WaypointStyleAssets.BOWTIE, Optional.of(0xFF0000)));

        Vec3i pos = new Vec3i(0, 64, -128);
        ChunkPos chunk = new ChunkPos(3, -7);

        // ---- UUID identifier branch, via the public factories --------------------------------
        for (UUID id : ctx.uuids()) {
            // EMPTY type (UNTRACK only; icon is forced to Waypoint.Icon.NULL).
            out.add(ClientboundTrackedWaypointPacket.removeWaypoint(id));

            for (Waypoint.Icon icon : icons) {
                out.add(ClientboundTrackedWaypointPacket.addWaypointPosition(id, icon, pos));
                out.add(ClientboundTrackedWaypointPacket.updateWaypointPosition(id, icon, pos));
                out.add(ClientboundTrackedWaypointPacket.addWaypointChunk(id, icon, chunk));
                out.add(ClientboundTrackedWaypointPacket.updateWaypointChunk(id, icon, chunk));
                out.add(ClientboundTrackedWaypointPacket.addWaypointAzimuth(id, icon, 1.5f));
                out.add(ClientboundTrackedWaypointPacket.updateWaypointAzimuth(id, icon, -3.14f));
            }
        }

        // ---- content sweeps (UUID branch, plain icon) ----------------------------------------
        UUID id0 = ctx.uuids().get(0);
        Waypoint.Icon plain = icons.get(0);
        for (int v : ctx.varInts()) {
            out.add(ClientboundTrackedWaypointPacket.addWaypointPosition(id0, plain, new Vec3i(v, 0, 0)));
            out.add(ClientboundTrackedWaypointPacket.addWaypointPosition(id0, plain, new Vec3i(0, v, 0)));
            out.add(ClientboundTrackedWaypointPacket.addWaypointPosition(id0, plain, new Vec3i(0, 0, v)));
            out.add(ClientboundTrackedWaypointPacket.addWaypointChunk(id0, plain, new ChunkPos(v, 0)));
            out.add(ClientboundTrackedWaypointPacket.addWaypointChunk(id0, plain, new ChunkPos(0, v)));
        }
        for (int y : ctx.worldYs()) {
            out.add(ClientboundTrackedWaypointPacket.addWaypointPosition(id0, plain, new Vec3i(1, y, -1)));
        }
        for (int c : ctx.chunkCoords()) {
            out.add(ClientboundTrackedWaypointPacket.addWaypointChunk(id0, plain, new ChunkPos(c, c)));
        }
        for (float f : ctx.floats()) {
            out.add(ClientboundTrackedWaypointPacket.addWaypointAzimuth(id0, plain, f));
        }
        for (float f : ctx.rotations()) {
            out.add(ClientboundTrackedWaypointPacket.addWaypointAzimuth(id0, plain, f));
        }

        // ---- String identifier branch (Either.right) -----------------------------------------
        // Every operation x every waypoint type, plus the UTF-8 length-prefix spread on the name.
        ResourceLocation defaultStyle = WaypointStyleAssets.DEFAULT.location();
        ResourceLocation bowtieStyle = WaypointStyleAssets.BOWTIE.location();
        int[] operations = {OP_TRACK, OP_UNTRACK, OP_UPDATE};
        for (int op : operations) {
            add(out, stringIdentified(ctx, op, "waypoint", defaultStyle, null, TYPE_EMPTY, b -> { }));
            add(out, stringIdentified(ctx, op, "waypoint", defaultStyle, null, TYPE_VEC3I, b -> {
                b.writeVarInt(0);
                b.writeVarInt(64);
                b.writeVarInt(-128);
            }));
            add(out, stringIdentified(ctx, op, "waypoint", bowtieStyle, 0x00FF00, TYPE_CHUNK, b -> {
                b.writeVarInt(3);
                b.writeVarInt(-7);
            }));
            add(out, stringIdentified(ctx, op, "waypoint", bowtieStyle, 0xFF0000, TYPE_AZIMUTH,
                    b -> b.writeFloat(1.5f)));
        }
        for (String name : ctx.utf8()) {
            add(out, stringIdentified(ctx, OP_TRACK, name, defaultStyle, null, TYPE_EMPTY, b -> { }));
        }

        return out;
    }

    private static void add(List<Packet<?>> out, Packet<?> packet) {
        if (packet != null) out.add(packet);
    }
}
