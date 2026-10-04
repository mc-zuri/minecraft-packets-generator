package dev.minecraftdatagenerator.packetshapes.common;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerboundClientInformationPacket wraps ClientInformation, written in order as:
 * String language [writeUtf(16)], byte viewDistance [writeByte], ChatVisiblity chatVisibility
 * [VarInt id], boolean chatColors, unsigned-byte modelCustomisation [PlayerModelPart bit mask],
 * HumanoidArm mainHand [VarInt id], boolean textFilteringEnabled, boolean allowsListing.
 *
 * <p>1.21's ClientInformation has eight components; the trailing {@code ParticleStatus particleStatus}
 * field (a ninth VarInt enum id) was only added in 1.21.2, so there is no particle dimension here.
 *
 * <p>Categorical dimensions are fully crossed: ChatVisiblity (3) x HumanoidArm (2) x
 * chatColors x textFilteringEnabled x allowsListing (2^3) = 48 shapes.
 * The three numeric/string dimensions are then swept against that baseline:
 * language over the UTF-8 spread (all entries are within the 16-character limit of readUtf(16)),
 * viewDistance over the signed-byte corner cases, and modelCustomisation over the bit-flag
 * enumeration required by the rubric: none, each individual PlayerModelPart bit, all seven bits,
 * and 0xFF (readUnsignedByte round-trips the whole 0..255 range).
 */
public class ServerboundClientInformationShapes implements PacketShapes {
    @Override
    public Class<? extends Packet<?>> packetType() {
        return ServerboundClientInformationPacket.class;
    }

    private static ServerboundClientInformationPacket p(String lang, int view, ChatVisiblity vis,
                                                        boolean colors, int model, HumanoidArm hand,
                                                        boolean filtering, boolean listing) {
        return new ServerboundClientInformationPacket(new ClientInformation(
                lang, view, vis, colors, model, hand, filtering, listing));
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        String bl = "en_us";
        int bv = 2;
        ChatVisiblity bvis = ChatVisiblity.FULL;
        boolean bcol = true;
        int bmodel = 0;
        HumanoidArm bhand = HumanoidArm.RIGHT;
        boolean bfilter = false;
        boolean blist = true;

        List<Packet<?>> out = new ArrayList<>(ctx.cross(
                a -> p(bl, bv, (ChatVisiblity) a[0], (boolean) a[1], bmodel,
                        (HumanoidArm) a[2], (boolean) a[3], (boolean) a[4]),
                ctx.enumAll(ChatVisiblity.class), ctx.bools(), ctx.enumAll(HumanoidArm.class),
                ctx.bools(), ctx.bools()));

        // language: MAX_LANGUAGE_LENGTH = 16 chars; every utf8() entry fits.
        for (String s : ctx.utf8()) out.add(p(s, bv, bvis, bcol, bmodel, bhand, bfilter, blist));
        out.add(p("0123456789abcdef", bv, bvis, bcol, bmodel, bhand, bfilter, blist)); // exactly 16

        // viewDistance: writeByte/readByte, so the whole signed-byte range round-trips.
        for (int v : List.of(0, 1, 2, 16, 32, 127, -1, -128)) {
            out.add(p(bl, v, bvis, bcol, bmodel, bhand, bfilter, blist));
        }

        // modelCustomisation: bit flags - none, each bit alone, all defined bits, and the full byte.
        out.add(p(bl, bv, bvis, bcol, 0, bhand, bfilter, blist));
        int all = 0;
        for (PlayerModelPart part : ctx.enumAll(PlayerModelPart.class)) {
            out.add(p(bl, bv, bvis, bcol, part.getMask(), bhand, bfilter, blist));
            all |= part.getMask();
        }
        out.add(p(bl, bv, bvis, bcol, all, bhand, bfilter, blist));   // 0x7F
        out.add(p(bl, bv, bvis, bcol, 0xFF, bhand, bfilter, blist));  // every byte bit set
        return out;
    }
}
