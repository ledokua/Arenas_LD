package net.ledok.arenas_ld.registry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminMenuProvider;
import net.ledok.arenas_ld.networking.SpawnerPacketHandlers;
import net.ledok.arenas_ld.raid.screen.RaidControllerAdminMenuProvider;
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

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import net.ledok.arenas_ld.platform.ArenasMenus;
import net.ledok.arenas_ld.platform.ArenasEvents;

public class CommandRegistry {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> arenasLdNode = literal("arenasld")
            .then(literal("reload")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ArenasLdConfig.reload();
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
                    if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity dungeon) {
                        ArenasMenus.open(player, new DungeonControllerAdminMenuProvider(dungeon));
                        return 1;
                    }
                    if (be instanceof net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity raid) {
                        ArenasMenus.open(player, new RaidControllerAdminMenuProvider(raid));
                        return 1;
                    }
                    if (be instanceof net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity arena) {
                        ArenasMenus.open(player, new net.ledok.arenas_ld.arena.screen.ArenaControllerAdminMenuProvider(arena));
                        return 1;
                    }
                    context.getSource().sendFailure(Component.translatable("gui.arenas_ld.admin.failure.no_controller"));
                    return 0;
                }))
            .then(literal("exit").executes(CommandRegistry::exitDungeon))
            .then(literal("stats").executes(CommandRegistry::showStats))
            .then(buildLobbyNode())
            .then(buildActivityNode());

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
     * (exactly what {@code ArenasApi.openLobbies} does, gate included), and drop a stale entry.
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
            .then(literal("forget")
                .then(argument("id", ResourceLocationArgument.id())
                    .suggests(ACTIVITY_IDS)
                    .executes(CommandRegistry::forgetActivity)));
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

    private static int forgetActivity(CommandContext<CommandSourceStack> ctx) {
        net.minecraft.resources.ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        if (!net.ledok.arenas_ld.activity.ActivityRegistry.forget(ctx.getSource().getServer(), id)) {
            ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.activity.unknown", id.toString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.arenas_ld.activity.forgotten", id.toString()), true);
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
