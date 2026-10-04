package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundSetJigsawBlockPacket(BlockPos pos, ResourceLocation name, ResourceLocation target, ResourceLocation pool,
 * String finalState, JigsawBlockEntity.JointType joint, int selectionPriority, int placementPriority).
 * Wire: blockPos(long-packed), identifier(name), identifier(target), identifier(pool),
 * utf(finalState), utf(joint.getSerializedName() — "rollable"/"aligned"),
 * varInt(selectionPriority), varInt(placementPriority).
 * <p>
 * Categorical cartesian: 10 domain-correct block positions x both JointType constants x the UTF-8
 * {@code finalState} spread (6) = 120 shapes. The three identifiers and the two VarInt priorities are
 * then swept one field at a time over a valid baseline.
 * NOTE: {@code joint} is decoded by name with an {@code ALIGNED} fallback, so only the two real
 * serialized names round-trip; no fabricated name is emitted.
 */
public class ServerboundSetJigsawBlockShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundSetJigsawBlockPacket.class;
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        BlockPos basePos = new BlockPos(0, 0, 0);
        ResourceLocation baseId = ctx.id("empty");
        String baseState = "";
        JigsawBlockEntity.JointType baseJoint = JigsawBlockEntity.JointType.ALIGNED;

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> new ServerboundSetJigsawBlockPacket((BlockPos) a[0], baseId, baseId, baseId,
                        (String) a[1], (JigsawBlockEntity.JointType) a[2], 0, 0),
                ctx.blockPos(), ctx.utf8(), ctx.enumAll(JigsawBlockEntity.JointType.class)));

        // identifier sweep, once per identifier field (namespace + nested path forms)
        List<ResourceLocation> ids = List.of(
                ctx.id("empty"),
                ctx.id("x"),
                ctx.id("pattern/village/plains/town_centers"),
                new ResourceLocation("my_namespace", "foo/bar"),
                new ResourceLocation("a", "b"));
        for (ResourceLocation id : ids) {
            out.add(new ServerboundSetJigsawBlockPacket(basePos, id, baseId, baseId, baseState, baseJoint, 0, 0));
            out.add(new ServerboundSetJigsawBlockPacket(basePos, baseId, id, baseId, baseState, baseJoint, 0, 0));
            out.add(new ServerboundSetJigsawBlockPacket(basePos, baseId, baseId, id, baseState, baseJoint, 0, 0));
        }
        // VarInt boundary sweeps for the two priorities
        for (int sel : ctx.varInts()) {
            out.add(new ServerboundSetJigsawBlockPacket(basePos, baseId, baseId, baseId, baseState, baseJoint, sel, 0));
        }
        for (int place : ctx.varInts()) {
            out.add(new ServerboundSetJigsawBlockPacket(basePos, baseId, baseId, baseId, baseState, baseJoint, 0, place));
        }
        return out;
    }
}
