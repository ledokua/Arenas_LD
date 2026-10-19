package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Set;

/**
 * The temporary no-friendly-fire scoreboard team a run's players share (dungeons {@code ald_d_*},
 * raids {@code ald_r_*}, arenas {@code ald_a_*}). Same-team players are allies to vanilla and to
 * the combat mods that check scoreboard teams first (Spell Engine area effects, Better Combat
 * sweeps), so run mates never hurt each other even where PvP is on.
 *
 * <p>Each player's previous team is kept in {@link PartyTeamStore} and given back when they leave
 * the run, even across a restart or while they are offline. Arenas_LD's own mob teams are never
 * kept or given back: a player found on one is taken off it.
 */
public final class RunPartyTeams {
    /** The teams Arenas_LD puts spawned mobs on by default (spawner group ids can name others). */
    public static final Set<String> MOB_TEAMS = Set.of("arenas_ld", "arenas_dungeon");

    private RunPartyTeams() {
    }

    /** Creates the run team if needed and moves the player onto it, remembering their prior team. */
    public static void add(ServerLevel world, String teamName, ServerPlayer player) {
        Scoreboard scoreboard = world.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            team.setAllowFriendlyFire(false);
            team.setSeeFriendlyInvisibles(true);
        }
        String name = player.getScoreboardName();
        PlayerTeam prior = scoreboard.getPlayersTeam(name);
        String priorName = prior != null ? prior.getName() : "";
        if (MOB_TEAMS.contains(priorName)) {
            // A player on the mobs' team is allied with every run mob; never hand that back.
            ArenasLdMod.LOGGER.warn("{} was on the mob team {}; they won't be put back on it after the run",
                name, priorName);
            priorName = "";
        }
        PartyTeamStore.get(world.getServer()).put(name, priorName);
        scoreboard.addPlayerToTeam(name, team);
    }

    /** Takes one member off the run team and gives back their prior team (works offline). */
    public static void remove(ServerLevel world, String teamName, String playerName) {
        Scoreboard scoreboard = world.getScoreboard();
        String priorName = PartyTeamStore.get(world.getServer()).take(playerName);
        if (priorName == null) {
            return; // not on a run team
        }
        PlayerTeam current = scoreboard.getPlayersTeam(playerName);
        if (current != null && current.getName().equals(teamName)) {
            scoreboard.removePlayerFromTeam(playerName, current);
        }
        if (!priorName.isEmpty() && !MOB_TEAMS.contains(priorName)) {
            PlayerTeam prior = scoreboard.getPlayerTeam(priorName);
            if (prior != null) {
                scoreboard.addPlayerToTeam(playerName, prior);
            }
        }
    }

    /** Gives every member back their prior team and deletes the run team. */
    public static void teardown(ServerLevel world, String teamName, Iterable<String> playerNames) {
        for (String name : playerNames) {
            remove(world, teamName, name);
        }
        PlayerTeam team = world.getScoreboard().getPlayerTeam(teamName);
        if (team != null) {
            world.getScoreboard().removePlayerTeam(team);
        }
    }

    /**
     * On login: a player standing on one of Arenas_LD's mob teams (left there by hand or by an old
     * build) is taken off it, since that team makes every run mob their ally.
     */
    public static void leaveMobTeamOnLogin(ServerPlayer player) {
        Scoreboard scoreboard = player.getScoreboard();
        String name = player.getScoreboardName();
        PlayerTeam team = scoreboard.getPlayersTeam(name);
        if (team != null && MOB_TEAMS.contains(team.getName())) {
            scoreboard.removePlayerFromTeam(name, team);
            ArenasLdMod.LOGGER.warn("Took {} off the mob team {} (players must never be on it)", name, team.getName());
        }
    }
}
