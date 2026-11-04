package net.ledok.arenas_ld.util;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.platform.ArenasEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reward commands: admin-written commands run as the server when a reward is paid (a room is
 * cleared, a dungeon or raid tier is won). Command-block permission level, output suppressed.
 *
 * <p>A command containing {@code @dungeonplayer} or {@code @s} runs once per winner with the
 * placeholder replaced by that player's name; any other command runs once. Per-player commands
 * for winners who are offline can be queued ({@link PendingRewardCommandsStore}) and run when
 * they next join, after any pending restore teleport has brought them home.
 */
public final class RewardCommands {
    public static final int MAX_COMMANDS = 8;
    public static final int MAX_LENGTH = 256;
    public static final String PLAYER_PLACEHOLDER = "@dungeonplayer";
    // \b keeps "@s[...]" working while leaving longer selectors like "@sa" untouched.
    private static final Pattern SELF = Pattern.compile("@s\\b");
    private static final Pattern SELF_EMPTY_ARGS = Pattern.compile("@s\\[\\s*]");
    private static final Pattern SELF_ARGS = Pattern.compile("@s\\[");
    // What Mojang accounts allow. Offline-mode servers accept far more (vanilla only rejects
    // control characters), and a name like "@a" would turn "give @dungeonplayer ..." into a
    // command for everyone, run at permission level 2. The optional leading '.' is Floodgate's
    // default prefix for Bedrock players; it can't change what a selector targets.
    private static final Pattern SAFE_NAME = Pattern.compile("\\.?[A-Za-z0-9_]{1,16}");

    private RewardCommands() {
    }

    /**
     * Normalizes one admin-typed command: trimmed, leading slashes dropped, cut at
     * {@link #MAX_LENGTH}. Returns an empty string for a blank command.
     */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String command = raw.trim();
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        return command.length() > MAX_LENGTH ? command.substring(0, MAX_LENGTH).trim() : command;
    }

    /** Cleans a client-sent command list: blank entries dropped, at most {@link #MAX_COMMANDS}. */
    public static List<String> sanitize(List<String> commands) {
        if (commands == null) {
            return List.of();
        }
        return commands.stream()
            .map(RewardCommands::clean)
            .filter(command -> !command.isEmpty())
            .limit(MAX_COMMANDS)
            .toList();
    }

    /** Whether the command names a winner ({@code @dungeonplayer} or {@code @s}) and so runs once per winner. */
    public static boolean isPerPlayer(String command) {
        return command.contains(PLAYER_PLACEHOLDER) || SELF.matcher(command).find();
    }

    /**
     * Replaces both placeholders with the player's name. {@code @s[...]} becomes
     * {@code @a[name=<name>,...]}, since a bare name can't carry selector arguments.
     */
    public static String resolve(String command, String playerName) {
        String resolved = command.replace(PLAYER_PLACEHOLDER, playerName);
        resolved = SELF_EMPTY_ARGS.matcher(resolved).replaceAll(Matcher.quoteReplacement("@a[name=" + playerName + "]"));
        resolved = SELF_ARGS.matcher(resolved).replaceAll(Matcher.quoteReplacement("@a[name=" + playerName + ","));
        return SELF.matcher(resolved).replaceAll(Matcher.quoteReplacement(playerName));
    }

    /** Whether a player name can be substituted into a command without changing its meaning. */
    public static boolean isSafePlayerName(String name) {
        return name != null && SAFE_NAME.matcher(name).matches();
    }

    /** Runs a per-player command for one winner, unless their name could rewrite the command. */
    private static void executeFor(CommandSourceStack source, String command, String playerName) {
        if (!isSafePlayerName(playerName)) {
            ArenasLdMod.LOGGER.warn("Skipping reward command '{}' for player '{}': the name is not a plain "
                + "Minecraft name and could change what the command targets", command, playerName);
            return;
        }
        execute(source, resolve(command, playerName));
    }

    /**
     * Runs reward commands for a set of winners.
     *
     * @param origin       where commands without a target run (the boss spawner, the room controller)
     * @param winners      the loot-eligible players
     * @param queueOffline queue the per-player commands of offline winners for their next join;
     *                     {@code false} skips them
     */
    public static void run(ServerLevel level, Vec3 origin, Collection<UUID> winners, List<String> commands,
                           boolean queueOffline) {
        if (commands.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        CommandSourceStack source = server.createCommandSourceStack()
            .withLevel(level)
            .withPosition(origin)
            .withPermission(2)
            .withSuppressedOutput();
        for (String raw : commands) {
            String command = clean(raw);
            if (command.isEmpty()) {
                continue;
            }
            if (!isPerPlayer(command)) {
                execute(source, command);
                continue;
            }
            for (UUID uuid : winners) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    executeFor(source, command, player.getGameProfile().getName());
                } else if (queueOffline) {
                    PendingRewardCommandsStore.get(server).add(uuid, command);
                }
            }
        }
    }

    /** Hooks the join event that runs queued commands. */
    public static void register() {
        // Two ticks out: the other join listeners queue their restore teleports for the next tick,
        // and these commands should find the player home, not in the instance.
        ArenasEvents.PLAYER_JOIN.add((joining, server) -> {
            UUID uuid = joining.getUUID();
            ServerTaskScheduler.nextTick(first -> ServerTaskScheduler.nextTick(s -> {
                ServerPlayer player = s.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    runPending(player);
                }
            }));
        });
    }

    /**
     * Runs and forgets the commands queued for a player while they were offline, with their current
     * name, positioned at the player.
     */
    public static void runPending(ServerPlayer player) {
        List<String> pending = PendingRewardCommandsStore.get(player.server).take(player.getUUID());
        if (pending.isEmpty()) {
            return;
        }
        CommandSourceStack source = player.server.createCommandSourceStack()
            .withLevel(player.serverLevel())
            .withPosition(player.position())
            .withPermission(2)
            .withSuppressedOutput();
        String name = player.getGameProfile().getName();
        for (String command : pending) {
            executeFor(source, command, name);
        }
    }

    /** Runs one command; a bad command is logged and never breaks the reward it belongs to. */
    private static void execute(CommandSourceStack source, String command) {
        try {
            Commands commands = source.getServer().getCommands();
            ParseResults<CommandSourceStack> parsed = commands.getDispatcher().parse(command, source);
            Commands.validateParseResults(parsed);
            commands.performCommand(parsed, command);
        } catch (CommandSyntaxException e) {
            ArenasLdMod.LOGGER.warn("Reward command '{}' is invalid: {}", command, e.getMessage());
        } catch (Exception e) {
            ArenasLdMod.LOGGER.error("Reward command '{}' failed", command, e);
        }
    }
}
