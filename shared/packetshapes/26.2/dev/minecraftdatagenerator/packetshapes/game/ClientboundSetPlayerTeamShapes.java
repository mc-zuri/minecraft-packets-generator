package dev.minecraftdatagenerator.packetshapes.game;

import dev.minecraftdatagenerator.packetshapes.PacketShapes;
import dev.minecraftdatagenerator.packetshapes.ShapeCtx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Discriminator/switch packet built via its static factories. The wire body is
 * {@code name:utf8, method:byte}; a {@code Parameters} block
 * ({@code displayName:Component, playerPrefix:Component, playerSuffix:Component,
 * nameTagVisibility:varInt-enum, collisionRule:varInt-enum, color:Optional<TeamColor>,
 * options:byte}) is present only for method 0 (ADD) and 2 (CHANGE); a
 * {@code players:list<utf8>} block is present only for method 0 (ADD), 3 (JOIN) and 4 (LEAVE).
 * All five branches are covered:
 * <ul>
 *   <li>REMOVE (1): bare, swept over the UTF-8 team-name spread.</li>
 *   <li>ADD (0) / CHANGE (2): full cartesian over every {@link Team.Visibility} (4), every
 *       {@link Team.CollisionRule} (4), the colour {@code Optional} (absent plus all 16
 *       {@link TeamColor} constants) and both option bits (friendlyFire x seeFriendlyInvisibles,
 *       i.e. every value of the packed options byte) - 2*4*4*17*2*2 = 2176 shapes - plus
 *       per-field sweeps over displayName / prefix / suffix, the team name, and, for ADD,
 *       player-list sizes 0/1/2.</li>
 *   <li>JOIN (3) / LEAVE (4): the single-player list the factory produces, swept over UTF-8 player
 *       names. NOTE: createPlayerPacket always emits exactly one name and the packet constructor is
 *       private, so player-list sizes 0 and 2 are not constructible for these two methods.</li>
 * </ul>
 * Packets are produced from a {@link PlayerTeam} synthesized per shape and registered in a live
 * {@link Scoreboard} so the mutating setters behave.
 */
public class ClientboundSetPlayerTeamShapes implements PacketShapes {
    private final Scoreboard sb = new Scoreboard();
    private int counter = 0;

    @Override
    public Class<? extends Packet<?>> packetType() {
        return ClientboundSetPlayerTeamPacket.class;
    }

    private PlayerTeam newTeam() {
        return sb.addPlayerTeam("team_" + (counter++));
    }

    /** Applies the given parameters to a registered team. */
    private PlayerTeam param(PlayerTeam t, Team.Visibility vis, Team.CollisionRule coll, Optional<TeamColor> color,
                             boolean friendlyFire, boolean seeInvis,
                             Component displayName, Component prefix, Component suffix) {
        t.setNameTagVisibility(vis);
        t.setCollisionRule(coll);
        t.setColor(color);
        t.setAllowFriendlyFire(friendlyFire);
        t.setSeeFriendlyInvisibles(seeInvis);
        t.setDisplayName(displayName);
        t.setPlayerPrefix(prefix);
        t.setPlayerSuffix(suffix);
        return t;
    }

    private PlayerTeam param(Team.Visibility vis, Team.CollisionRule coll, Optional<TeamColor> color,
                             boolean friendlyFire, boolean seeInvis,
                             Component displayName, Component prefix, Component suffix) {
        return param(newTeam(), vis, coll, color, friendlyFire, seeInvis, displayName, prefix, suffix);
    }

    private PlayerTeam baseline() {
        return param(Team.Visibility.ALWAYS, Team.CollisionRule.ALWAYS, Optional.<TeamColor>empty(),
                true, true, Component.literal("disp"), Component.empty(), Component.empty());
    }

    @Override
    public List<Packet<?>> shapes(ShapeCtx ctx) {
        List<Packet<?>> out = new ArrayList<>();

        // color is Optional<TeamColor> on the wire: absent plus every one of the 16 constants.
        List<Optional<TeamColor>> colors = new ArrayList<>();
        colors.add(Optional.<TeamColor>empty());
        for (TeamColor c : ctx.enumAll(TeamColor.class)) colors.add(Optional.of(c));

        // REMOVE (1): bare body, swept over the UTF-8 name spread.
        for (String name : ctx.utf8()) {
            out.add(ClientboundSetPlayerTeamPacket.createRemovePacket(sb.addPlayerTeam("rm_" + name)));
        }
        out.add(ClientboundSetPlayerTeamPacket.createRemovePacket(baseline()));

        // ADD (0) and CHANGE (2): full Parameters cartesian.
        // createNew=true -> method 0 (ADD, with player list); false -> method 2 (CHANGE, no list).
        for (boolean createNew : new boolean[]{true, false}) {
            for (Team.Visibility vis : ctx.enumAll(Team.Visibility.class)) {
                for (Team.CollisionRule coll : ctx.enumAll(Team.CollisionRule.class)) {
                    for (Optional<TeamColor> color : colors) {
                        for (boolean ff : ctx.bools()) {
                            for (boolean si : ctx.bools()) {
                                out.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(
                                        param(vis, coll, color, ff, si,
                                                Component.literal("disp"), Component.empty(), Component.empty()),
                                        createNew));
                            }
                        }
                    }
                }
            }
            // per-field sweeps for the three Component fields (too wide to fold into the cartesian)
            for (Component c : ctx.components()) {
                out.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(
                        param(Team.Visibility.ALWAYS, Team.CollisionRule.ALWAYS, Optional.<TeamColor>empty(), true, true,
                                c, Component.empty(), Component.empty()), createNew));
                out.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(
                        param(Team.Visibility.ALWAYS, Team.CollisionRule.ALWAYS, Optional.<TeamColor>empty(), true, true,
                                Component.literal("disp"), c, Component.empty()), createNew));
                out.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(
                        param(Team.Visibility.ALWAYS, Team.CollisionRule.ALWAYS, Optional.<TeamColor>empty(), true, true,
                                Component.literal("disp"), Component.empty(), c), createNew));
            }
            // team-name UTF-8 sweep
            for (String name : ctx.utf8()) {
                PlayerTeam t = param(sb.addPlayerTeam((createNew ? "add_" : "chg_") + name),
                        Team.Visibility.ALWAYS, Team.CollisionRule.ALWAYS, Optional.<TeamColor>empty(), true, true,
                        Component.literal("disp"), Component.empty(), Component.empty());
                out.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(t, createNew));
            }
        }

        // ADD (0) player-list sizes 0/1/2, with UTF-8 member names.
        for (int n : new int[]{0, 1, 2}) {
            PlayerTeam t = baseline();
            for (int i = 0; i < n; i++) {
                sb.addPlayerToTeam(ctx.utf8().get(i + 3) + "_" + counter + "_" + i, t);
            }
            out.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(t, true));
        }

        // JOIN (3) and LEAVE (4): single-player list, UTF-8 name sweep.
        for (String player : ctx.utf8()) {
            out.add(ClientboundSetPlayerTeamPacket.createPlayerPacket(baseline(), player,
                    ClientboundSetPlayerTeamPacket.Action.ADD));
            out.add(ClientboundSetPlayerTeamPacket.createPlayerPacket(baseline(), player,
                    ClientboundSetPlayerTeamPacket.Action.REMOVE));
        }

        return out;
    }
}
