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
                        player.openMenu(new DungeonControllerAdminMenuProvider(dungeon));
                        return 1;
                    }
                    if (be instanceof net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity raid) {
                        player.openMenu(new RaidControllerAdminMenuProvider(raid));
                        return 1;
                    }
                    if (be instanceof net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity arena) {
                        player.openMenu(new net.ledok.arenas_ld.arena.screen.ArenaControllerAdminMenuProvider(arena));
                        return 1;
                    }
                    context.getSource().sendFailure(Component.translatable("gui.arenas_ld.admin.failure.no_controller"));
                    return 0;
                }))
            .then(literal("exit").executes(CommandRegistry::exitDungeon))
            .then(buildLobbyNode());

        dispatcher.register(arenasLdNode);
        // Convenience top-level alias so players can simply type /exit.
        dispatcher.register(literal("exit").executes(CommandRegistry::exitDungeon));
    }

    /** Pulls the player out of a finished dungeon run early (also the click target of the chat button). */
    private static int exitDungeon(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean exited = net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle.exitEarly(player.server, player);
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
                case "open" -> player.openMenu(controller);
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
                case "open" -> player.openMenu(raid);
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
        ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.lobby_action.no_controller"));
        return 0;
    }

    public static void initialize() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            register(dispatcher);
        });
    }
}
