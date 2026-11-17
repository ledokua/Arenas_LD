package net.ledok.arenas_ld.registry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.activity.AdminScreens;
import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.networking.SpawnerPacketHandlers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import net.ledok.arenas_ld.platform.ArenasEvents;

public class CommandRegistry {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> arenasLdNode = literal("arenasld")
            .then(literal("reload")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    if (!ArenasLdConfig.reload()) {
                        context.getSource().sendFailure(Component.translatable("message.arenas_ld.config_reload_failed"));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.translatable("message.arenas_ld.config_reloaded"), true);
                    return 1;
                }))
            .then(literal("admin")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    HitResult hitResult = player.pick(10.0D, 0.0F, false);
                    if (hitResult.getType() != HitResult.Type.BLOCK || !(hitResult instanceof BlockHitResult hit)) {
                        context.getSource().sendFailure(Component.translatable("gui.arenas_ld.admin.failure.not_looking"));
                        return 0;
                    }
                    var be = player.level().getBlockEntity(hit.getBlockPos());
                    // At the block (not remote): the screen closes once the player walks off.
                    Component refusal = be == null
                        ? Component.translatable("gui.arenas_ld.admin.failure.no_controller")
                        : AdminScreens.open(player, be, false);
                    if (refusal != null) {
                        context.getSource().sendFailure(refusal);
                        return 0;
                    }
                    return 1;
                }))
            .then(literal("exit").executes(CommandRegistry::exitDungeon))
            .then(literal("stats").executes(CommandRegistry::showStats))
            .then(buildLobbyNode())
            .then(literal("run")
                .then(literal("details").executes(CommandRegistry::openRunDetails))
                .then(literal("last").executes(CommandRegistry::openLastRun))
                .then(literal("history").requires(source -> source.hasPermission(2))
                    .executes(ctx -> runHistory(ctx, null))
                    .then(argument("player", net.minecraft.commands.arguments.GameProfileArgument.gameProfile())
                        .executes(ctx -> runHistory(ctx, net.minecraft.commands.arguments.GameProfileArgument
                            .getGameProfiles(ctx, "player").iterator().next().getId()))))
                .then(literal("open").requires(source -> source.hasPermission(2))
                    .then(argument("runId", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(CommandRegistry::openRun))))
            .then(buildActivityNode())
            .then(literal("team")
                .requires(source -> source.hasPermission(2))
                .then(literal("release")
                    .then(argument("player", net.minecraft.commands.arguments.GameProfileArgument.gameProfile())
                        .executes(CommandRegistry::releaseRunTeam))))
            .then(net.ledok.arenas_ld.activity.InstanceCommands.node());

        dispatcher.register(arenasLdNode);
        // Convenience top-level alias so players can simply type /exit.
        dispatcher.register(literal("exit").executes(CommandRegistry::exitDungeon));
    }

    /** Prints the player's lifetime run statistics across all three systems. */
    private static int showStats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var stats = net.ledok.arenas_ld.util.PlayerStatsStore.get(player.server).stats(player.getUUID());
        player.sendSystemMessage(Component.translatable("message.arenas_ld.stats.header"));
        player.sendSystemMessage(Component.translatable("message.arenas_ld.stats.dungeon",
            stats.dungeonRuns(), stats.dungeonWins(), stats.dungeonRuns() - stats.dungeonWins()));
        player.sendSystemMessage(Component.translatable("message.arenas_ld.stats.raid",
            stats.raidRuns(), stats.raidWins(), stats.raidRuns() - stats.raidWins()));
        player.sendSystemMessage(Component.translatable("message.arenas_ld.stats.arena",
            stats.arenaRuns(), stats.arenaWins(), stats.bestArenaWave()));
        return 1;
    }

    /** Pulls the player out of a finished dungeon/raid/arena run early (also the chat-button click target). */
    private static int exitDungeon(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean exited = net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle.exitEarly(player.server, player)
            || net.ledok.arenas_ld.raid.run.RaidRunLifecycle.exitEarly(player.server, player)
            || net.ledok.arenas_ld.arena.run.ArenaRunLifecycle.exitEarly(player.server, player);
        if (!exited) {
            context.getSource().sendFailure(Component.translatable("message.arenas_ld.dungeon.exit_unavailable"));
            return 0;
        }
        return 1;
    }

    /**
     * Player-usable lobby actions, used as click targets from chat feedback. Each action
     * self-validates server-side (membership/ownership/invite), so no permission gate is needed.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> buildLobbyNode() {
        return literal("lobby")
            .then(literal("ready")
                .then(argument("dim", DimensionArgument.dimension())
                    .then(argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> lobbyAction(ctx, "ready", false)))))
            .then(literal("start")
                .then(argument("dim", DimensionArgument.dimension())
                    .then(argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> lobbyAction(ctx, "start", false)))))
            .then(literal("open")
                .executes(CommandRegistry::openOwnLobby)
                .then(argument("dim", DimensionArgument.dimension())
                    .then(argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> lobbyAction(ctx, "open", false)))))
            .then(literal("accept")
                .then(argument("dim", DimensionArgument.dimension())
                    .then(argument("pos", BlockPosArgument.blockPos())
                        .then(argument("lobbyId", UuidArgument.uuid())
                            .executes(ctx -> lobbyAction(ctx, "accept", true))))))
            .then(literal("decline")
                .then(argument("dim", DimensionArgument.dimension())
                    .then(argument("pos", BlockPosArgument.blockPos())
                        .then(argument("lobbyId", UuidArgument.uuid())
                            .executes(ctx -> lobbyAction(ctx, "decline", true))))));
    }

    /**
     * Admin view of the public API's activities: list them, open one's lobby screen for players
     * (exactly what {@code ArenasApi.openLobbies} does, gate included), end its runs
     * ({@code ArenasApi.forceEnd}), and drop a stale entry ({@code ArenasApi.forget}).
     */
    private static LiteralArgumentBuilder<CommandSourceStack> buildActivityNode() {
        return literal("activity")
            .requires(source -> source.hasPermission(2))
            .then(literal("list").executes(CommandRegistry::listActivities))
            .then(literal("open")
                .then(argument("id", ResourceLocationArgument.id())
                    .suggests(ACTIVITY_IDS)
                    .executes(ctx -> openActivity(ctx, java.util.List.of(ctx.getSource().getPlayerOrException())))
                    .then(argument("targets", EntityArgument.players())
                        .executes(ctx -> openActivity(ctx, EntityArgument.getPlayers(ctx, "targets"))))))
            .then(literal("stop")
                .then(argument("id", ResourceLocationArgument.id())
                    .suggests(ACTIVITY_IDS)
                    .executes(ctx -> stopActivity(ctx, null))
                    .then(argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> stopActivity(ctx, new ArenasApi.InstanceRef(
                            ctx.getSource().getLevel().dimension(), BlockPosArgument.getBlockPos(ctx, "pos"))))
                        .then(argument("dimension", DimensionArgument.dimension())
                            .executes(ctx -> stopActivity(ctx, new ArenasApi.InstanceRef(
                                DimensionArgument.getDimension(ctx, "dimension").dimension(),
                                BlockPosArgument.getBlockPos(ctx, "pos"))))))))
            .then(literal("resetcooldown")
                .then(argument("id", ResourceLocationArgument.id())
                    .suggests(ACTIVITY_IDS)
                    .executes(ctx -> resetCooldowns(ctx, null))
                    .then(argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> resetCooldowns(ctx, new ArenasApi.InstanceRef(
                            ctx.getSource().getLevel().dimension(), BlockPosArgument.getBlockPos(ctx, "pos"))))
                        .then(argument("dimension", DimensionArgument.dimension())
                            .executes(ctx -> resetCooldowns(ctx, new ArenasApi.InstanceRef(
                                DimensionArgument.getDimension(ctx, "dimension").dimension(),
                                BlockPosArgument.getBlockPos(ctx, "pos"))))))))
            .then(literal("forget")
                .then(argument("id", ResourceLocationArgument.id())
                    .suggests(ACTIVITY_IDS)
                    .executes(CommandRegistry::forgetActivity)))
            .then(literal("breakable")
                .then(argument("id", ResourceLocationArgument.id())
                    .suggests(ACTIVITY_IDS)
                    .executes(CommandRegistry::showBreakable)
                    .then(literal("mode")
                        .then(literal(net.ledok.arenas_ld.util.BreakableBlocks.ADD)
                            .executes(ctx -> editBreakable(ctx, b -> new net.ledok.arenas_ld.util.BreakableBlocks(
                                net.ledok.arenas_ld.util.BreakableBlocks.ADD, b.blocks()))))
                        .then(literal(net.ledok.arenas_ld.util.BreakableBlocks.REPLACE)
                            .executes(ctx -> editBreakable(ctx, b -> new net.ledok.arenas_ld.util.BreakableBlocks(
                                net.ledok.arenas_ld.util.BreakableBlocks.REPLACE, b.blocks())))))
                    .then(literal("add")
                        .then(argument("entry", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                            .executes(ctx -> editBreakable(ctx, b -> {
                                java.util.List<String> list = new java.util.ArrayList<>(b.blocks());
                                list.add(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "entry"));
                                return new net.ledok.arenas_ld.util.BreakableBlocks(b.mode(), list);
                            }))))
                    .then(literal("remove")
                        .then(argument("entry", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                            .executes(ctx -> editBreakable(ctx, b -> {
                                java.util.List<String> list = new java.util.ArrayList<>(b.blocks());
                                list.remove(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "entry").trim());
                                return new net.ledok.arenas_ld.util.BreakableBlocks(b.mode(), list);
                            }))))
                    .then(literal("clear")
                        .executes(ctx -> editBreakable(ctx, b -> net.ledok.arenas_ld.util.BreakableBlocks.DEFAULT)))))
            .then(literal("settings")
                .then(argument("id", ResourceLocationArgument.id())
                    .suggests(ACTIVITY_IDS)
                    .executes(CommandRegistry::showRunSettings)
                    .then(literal("rental")
                        .then(literal(net.ledok.arenas_ld.util.RunSettings.OPTIONAL)
                            .executes(ctx -> editRunSettings(ctx, s -> s.withRentalMode(net.ledok.arenas_ld.util.RunSettings.OPTIONAL))))
                        .then(literal(net.ledok.arenas_ld.util.RunSettings.REQUIRED)
                            .executes(ctx -> editRunSettings(ctx, s -> s.withRentalMode(net.ledok.arenas_ld.util.RunSettings.REQUIRED)))))
                    .then(literal("loot_rolls")
                        .then(literal("default")
                            .executes(ctx -> editRunSettings(ctx, s -> s.withBaseLootRolls(-1))))
                        .then(argument("rolls", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0,
                                net.ledok.arenas_ld.util.RunSettings.MAX_ROLLS))
                            .executes(ctx -> editRunSettings(ctx, s -> s.withBaseLootRolls(
                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "rolls"))))))));
    }

    /** {@code /arenasld activity breakable <id>}: the controller's list and what its runs allow with it. */
    private static int showBreakable(CommandContext<CommandSourceStack> ctx) {
        net.minecraft.resources.ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        var blocks = ArenasApi.breakableBlocks(ctx.getSource().getServer(), id).orElse(null);
        if (blocks == null) {
            ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.admin.breakable_not_supported", id.toString()));
            return 0;
        }
        var effective = blocks.effective(net.ledok.arenas_ld.config.ArenasLdConfig.getInstance().run_breakable_blocks);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.arenas_ld.admin.breakable_show", id.toString(),
            blocks.mode(), blocks.blocks().isEmpty() ? "-" : String.join(", ", blocks.blocks()),
            effective.isEmpty() ? "-" : String.join(", ", effective)), false);
        return 1;
    }

    private static int editBreakable(CommandContext<CommandSourceStack> ctx,
                                     java.util.function.UnaryOperator<net.ledok.arenas_ld.util.BreakableBlocks> change) {
        net.minecraft.resources.ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        var server = ctx.getSource().getServer();
        var blocks = ArenasApi.breakableBlocks(server, id).orElse(null);
        if (blocks == null) {
            ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.admin.breakable_not_supported", id.toString()));
            return 0;
        }
        Component refusal = ArenasApi.setBreakableBlocks(server, id, change.apply(blocks), ctx.getSource().getTextName());
        if (refusal != null) {
            ctx.getSource().sendFailure(refusal);
            return 0;
        }
        return showBreakable(ctx);
    }

    /** {@code /arenasld activity settings <id>}: the controller's rental mode and base loot rolls. */
    private static int showRunSettings(CommandContext<CommandSourceStack> ctx) {
        net.minecraft.resources.ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        var settings = net.ledok.arenas_ld.activity.ActivityAdmin.runSettings(ctx.getSource().getServer(), id).orElse(null);
        if (settings == null) {
            ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.admin.run_settings_not_supported", id.toString()));
            return 0;
        }
        String rolls = net.ledok.arenas_ld.rewards.RewardMath.number(settings.lootRolls());
        ctx.getSource().sendSuccess(() -> Component.translatable("message.arenas_ld.admin.run_settings_show", id.toString(),
            settings.rentalMode(), settings.hasOwnLootRolls() ? Component.literal(rolls)
                : Component.translatable("message.arenas_ld.admin.run_settings_global_rolls", rolls)), false);
        return 1;
    }

    private static int editRunSettings(CommandContext<CommandSourceStack> ctx,
                                       java.util.function.UnaryOperator<net.ledok.arenas_ld.util.RunSettings> change) {
        net.minecraft.resources.ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        var server = ctx.getSource().getServer();
        var settings = net.ledok.arenas_ld.activity.ActivityAdmin.runSettings(server, id).orElse(null);
        if (settings == null) {
            ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.admin.run_settings_not_supported", id.toString()));
            return 0;
        }
        Component refusal = net.ledok.arenas_ld.activity.ActivityAdmin.setRunSettings(server, id, change.apply(settings),
            ctx.getSource().getTextName());
        if (refusal != null) {
            ctx.getSource().sendFailure(refusal);
            return 0;
        }
        return showRunSettings(ctx);
    }

    /**
     * {@code /arenasld team release <player>}: takes a player off a run team ({@code ald_*}) that no
     * run has them on any more, giving back their prior team. Refused while they are in a run.
     */
    private static int releaseRunTeam(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var profiles = net.minecraft.commands.arguments.GameProfileArgument.getGameProfiles(ctx, "player");
        int released = 0;
        for (com.mojang.authlib.GameProfile profile : profiles) {
            String name = profile.getName();
            if (net.ledok.arenas_ld.util.RunLeftovers.isInRun(profile.getId())) {
                ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.team.in_run", name));
                continue;
            }
            String team = net.ledok.arenas_ld.util.RunPartyTeams.release(ctx.getSource().getServer().overworld(), name);
            if (team == null) {
                ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.team.not_on_run_team", name));
                continue;
            }
            ctx.getSource().sendSuccess(() -> Component.translatable("message.arenas_ld.team.released", name, team), true);
            released++;
        }
        return released;
    }

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> ACTIVITY_IDS =
        (ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggestResource(
            net.ledok.arenas_ld.api.ArenasApi.activities(ctx.getSource().getServer()).stream()
                .map(net.ledok.arenas_ld.api.ArenasApi.Activity::id), builder);

    private static int listActivities(CommandContext<CommandSourceStack> ctx) {
        var activities = net.ledok.arenas_ld.api.ArenasApi.activities(ctx.getSource().getServer());
        if (activities.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("message.arenas_ld.activity.list_empty"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.arenas_ld.activity.list_header", activities.size()), false);
        for (var activity : activities) {
            String difficulties = activity.difficulties().isEmpty()
                ? "-"
                : activity.difficulties().stream()
                    .map(net.ledok.arenas_ld.api.ArenasApi.Difficulty::id)
                    .collect(java.util.stream.Collectors.joining(", "));
            Component line = Component.literal(" " + activity.id() + " ")
                .append(Component.literal("[" + activity.type().name().toLowerCase(java.util.Locale.ROOT) + "] ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(activity.name())
                .append(Component.literal(" — " + difficulties).withStyle(net.minecraft.ChatFormatting.GRAY));
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return activities.size();
    }

    private static int openActivity(CommandContext<CommandSourceStack> ctx, java.util.Collection<ServerPlayer> targets) {
        net.minecraft.resources.ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        if (net.ledok.arenas_ld.api.ArenasApi.activity(ctx.getSource().getServer(), id).isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.activity.unknown", id.toString()));
            return 0;
        }
        int opened = 0;
        for (ServerPlayer target : targets) {
            if (net.ledok.arenas_ld.api.ArenasApi.openLobbies(target, id)) {
                opened++;
            }
        }
        return opened;
    }

    /** Ends the activity's runs as staff, or only the run at one instance. */
    /** {@code /arenasld activity resetcooldown <id> [pos [dimension]]}: {@code ArenasApi.resetCooldowns}. */
    private static int resetCooldowns(CommandContext<CommandSourceStack> ctx, @Nullable ArenasApi.InstanceRef instance) {
        net.minecraft.resources.ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        Component refusal = ArenasApi.resetCooldowns(ctx.getSource().getServer(), id, instance, ctx.getSource().getTextName());
        if (refusal != null) {
            ctx.getSource().sendFailure(refusal);
            return 0;
        }
        Component done = instance == null
            ? Component.translatable("message.arenas_ld.activity.cooldowns_reset", id.toString())
            : Component.translatable("message.arenas_ld.activity.cooldown_reset_at", id.toString(),
                instance.pos().toShortString() + " (" + instance.dimension().location() + ")");
        ctx.getSource().sendSuccess(() -> done, true);
        return 1;
    }

    private static int stopActivity(CommandContext<CommandSourceStack> ctx, @Nullable ArenasApi.InstanceRef instance) {
        net.minecraft.resources.ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        Component refusal = ArenasApi.forceEnd(ctx.getSource().getServer(), id, instance);
        if (refusal != null) {
            ctx.getSource().sendFailure(refusal);
            return 0;
        }
        Component done = instance == null
            ? Component.translatable("message.arenas_ld.activity.stopped", id.toString())
            : Component.translatable("message.arenas_ld.activity.stopped_at", id.toString(),
                instance.pos().toShortString() + " (" + instance.dimension().location() + ")");
        ctx.getSource().sendSuccess(() -> done, true);
        return 1;
    }

    /** Drops an activity whose controller is gone; refused while the controller is still there. */
    private static int forgetActivity(CommandContext<CommandSourceStack> ctx) {
        net.minecraft.resources.ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        Component refusal = ArenasApi.forget(ctx.getSource().getServer(), id);
        if (refusal != null) {
            ctx.getSource().sendFailure(refusal);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.arenas_ld.activity.forgotten", id.toString()), true);
        return 1;
    }

    /** {@code /arenasld lobby open}: the lobby screen of the player's own lobby, from anywhere (the HUD badge, the hub). */
    /** {@code /arenasld run details}: the live details screen of the player's dungeon or raid run. */
    private static int openRunDetails(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!ArenasApi.openRunDetails(ctx.getSource().getPlayerOrException())) {
            ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.run_details.no_run"));
            return 0;
        }
        return 1;
    }

    /** {@code /arenasld run last}: the player's last finished run (the chat summary's [Details]). */
    private static int openLastRun(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!ArenasApi.openLastRunDetails(ctx.getSource().getPlayerOrException())) {
            ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.run_stats.no_last"));
            return 0;
        }
        return 1;
    }

    /** {@code /arenasld run history [player]}: the last 10 finished runs, each with an [Open] button. */
    private static int runHistory(CommandContext<CommandSourceStack> ctx, @Nullable UUID player) {
        var runs = ArenasApi.runHistory(ctx.getSource().getServer(), null, player, 10);
        if (runs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("message.arenas_ld.run_stats.history_empty"), false);
            return 0;
        }
        var format = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(java.time.ZoneId.systemDefault());
        for (var run : runs) {
            Component line = Component.literal(format.format(run.start()) + " ")
                .append(Component.literal(run.name()).withStyle(net.minecraft.ChatFormatting.GOLD))
                .append(" " + run.difficulty() + " · ")
                .append(Component.translatable("gui.arenas_ld.run_details.result." + run.result().name().toLowerCase(java.util.Locale.ROOT)))
                .append(String.format(java.util.Locale.ROOT, " · %d:%02d · ", run.durationSeconds() / 60, run.durationSeconds() % 60))
                .append(String.join(", ", run.party()) + " ")
                .append(Component.translatable("message.arenas_ld.run_stats.open").withStyle(style -> style
                    .withColor(net.minecraft.ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/arenasld run open " + run.runId()))));
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return runs.size();
    }

    /** {@code /arenasld run open <runId>}: a finished run's details, staff view. */
    private static int openRun(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String runId = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "runId");
        if (!ArenasApi.openRunDetails(ctx.getSource().getPlayerOrException(), runId)) {
            ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.run_stats.unknown_run", runId));
            return 0;
        }
        return 1;
    }

    private static int openOwnLobby(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!net.ledok.arenas_ld.api.ArenasApi.openOwnLobby(player)) {
            ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.lobby.none"));
            return 0;
        }
        return 1;
    }

    private static int lobbyAction(CommandContext<CommandSourceStack> ctx, String action, boolean needsLobbyId) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = DimensionArgument.getDimension(ctx, "dim");
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        UUID lobbyId = needsLobbyId ? UuidArgument.getUuid(ctx, "lobbyId") : null;
        var be = level.getBlockEntity(pos);
        if (be instanceof DungeonControllerBlockEntity controller) {
            switch (action) {
                case "ready" -> SpawnerPacketHandlers.performToggleReady(player, controller);
                case "start" -> SpawnerPacketHandlers.performStartRun(player, controller);
                case "open" -> net.ledok.arenas_ld.activity.LobbyScreens.open(player, controller, true);
                case "accept" -> {
                    controller.acceptInvite(player, lobbyId);
                    controller.setChanged();
                    SpawnerPacketHandlers.broadcastDungeonControllerSnapshot(player, controller);
                }
                case "decline" -> {
                    controller.declineInvite(player, lobbyId);
                    controller.setChanged();
                    SpawnerPacketHandlers.broadcastDungeonControllerSnapshot(player, controller);
                }
                default -> { return 0; }
            }
            return 1;
        }
        if (be instanceof net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity raid) {
            switch (action) {
                case "ready" -> net.ledok.arenas_ld.raid.RaidPacketHandlers.performToggleReadyRaid(player, raid);
                case "start" -> net.ledok.arenas_ld.raid.RaidPacketHandlers.performStartRaid(player, raid);
                case "open" -> net.ledok.arenas_ld.activity.LobbyScreens.open(player, raid, true);
                case "accept" -> {
                    raid.acceptInvite(player, lobbyId);
                    net.ledok.arenas_ld.raid.RaidPacketHandlers.broadcastRaidControllerSnapshot(player, raid);
                }
                case "decline" -> {
                    raid.declineInvite(player, lobbyId);
                    net.ledok.arenas_ld.raid.RaidPacketHandlers.broadcastRaidControllerSnapshot(player, raid);
                }
                default -> { return 0; }
            }
            return 1;
        }
        if (be instanceof net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity arena) {
            switch (action) {
                case "ready" -> arena.toggleReady(player);
                case "start" -> arena.startRun(player);
                case "open" -> net.ledok.arenas_ld.activity.LobbyScreens.open(player, arena, true);
                case "accept" -> arena.acceptInvite(player, lobbyId);
                case "decline" -> arena.declineInvite(player, lobbyId);
                default -> { return 0; }
            }
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.lobby_action.no_controller"));
        return 0;
    }

    public static void initialize() {
        ArenasEvents.REGISTER_COMMANDS.add(CommandRegistry::register);
    }
}
