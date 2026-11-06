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

    /** Whether {@code teamName} is a run team of any activity ({@code ald_d_}, {@code ald_r_}, {@code ald_a_}). */
    public static boolean isRunTeam(String teamName) {
        return teamName.startsWith("ald_d_") || teamName.startsWith("ald_r_") || teamName.startsWith("ald_a_");
    }

    /**
     * The run team of an instance: {@code prefix} plus the instance's dimension and exact position.
     * It used to be the position's hash code alone, which two instances share when they sit at the
     * same coordinates in two dimensions (copied instance dimensions) or merely collide; one run's
     * end then deleted the other's team, and with it the party's no-friendly-fire protection.
     */
    public static String teamName(String prefix, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                  net.minecraft.core.BlockPos pos) {
        return prefix + Integer.toHexString(dimension.location().toString().hashCode()) + "_" + Long.toHexString(pos.asLong());
    }

    /** The team name runs started before {@link #teamName} used, for cleaning up after them. */
    public static String legacyTeamName(String prefix, net.minecraft.core.BlockPos pos) {
        return prefix + Integer.toHexString(pos.hashCode());
    }

    /**
     * {@link #teardown}, plus deleting a team left under the legacy name by a run that was going when
     * the server updated (its members leave it with it).
     */
    public static void teardown(ServerLevel world, String teamName, String legacyTeamName, Iterable<String> playerNames) {
        teardown(world, teamName, playerNames);
        PlayerTeam legacy = world.getScoreboard().getPlayerTeam(legacyTeamName);
        if (legacy != null) {
            world.getScoreboard().removePlayerTeam(legacy);
        }
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
        PartyTeamStore store = PartyTeamStore.get(world.getServer());
        if (isRunTeam(priorName)) {
            // Still on another run's team (left there by a missed exit): never hand that back. The
            // prior team stored when they joined it is the real one, so keep it.
            ArenasLdMod.LOGGER.warn("{} was still on the run team {}; they won't be put back on it after this run",
                name, priorName);
            if (!store.has(name)) {
                store.put(name, "");
            }
        } else {
            store.put(name, priorName);
        }
        scoreboard.addPlayerToTeam(name, team);
    }

    /** Takes one member off the run team and gives back their prior team (works offline). */
    public static void remove(ServerLevel world, String teamName, String playerName) {
        Scoreboard scoreboard = world.getScoreboard();
        PlayerTeam onNow = scoreboard.getPlayersTeam(playerName);
        if (onNow != null && isRunTeam(onNow.getName()) && !onNow.getName().equals(teamName)) {
            // On another run's team now (an old run ending late, e.g. one restored after a
            // restart): the stored prior team is that run's to give back, so leave it all alone.
            return;
        }
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
     * Takes a player off a stale run team: the one they are on when no run has them (as the
     * leftover index has it, {@code RunLeftovers.isInRun}). They get their stored prior team back,
     * and the run team is deleted once nobody is left on it.
     *
     * @return the team they were taken off, or null when they weren't on a run team
     */
    public static @org.jetbrains.annotations.Nullable String release(ServerLevel world, String playerName) {
        Scoreboard scoreboard = world.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(playerName);
        if (team == null || !isRunTeam(team.getName())) {
            return null;
        }
        String teamName = team.getName();
        remove(world, teamName, playerName);
        PlayerTeam still = scoreboard.getPlayersTeam(playerName);
        if (still != null && still.getName().equals(teamName)) {
            scoreboard.removePlayerFromTeam(playerName, still); // no prior team was stored
        }
        PlayerTeam left = scoreboard.getPlayerTeam(teamName);
        if (left != null && left.getPlayers().isEmpty()) {
            scoreboard.removePlayerTeam(left);
        }
        ArenasLdMod.LOGGER.info("[Arenas] Took {} off the stale run team {}", playerName, teamName);
        return teamName;
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
