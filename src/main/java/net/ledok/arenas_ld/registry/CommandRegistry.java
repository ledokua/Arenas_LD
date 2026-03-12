package net.ledok.arenas_ld.registry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.concurrent.CompletableFuture;

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
                .then(literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(literal("clearTrackedPlayers")
                                .executes(context -> {
                                    ArenasLdMod.MOB_ARENA_MANAGER.clear();
                                    context.getSource().sendSuccess(() -> Component.literal("Cleared all tracked players from Mob Arenas."), true);
                                    return 1;
                                }))
                        .then(literal("endDungeon")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    HitResult hitResult = player.pick(10.0D, 0.0F, false);
                                    if (hitResult.getType() == HitResult.Type.BLOCK && hitResult instanceof BlockHitResult hit) {
                                        if (player.level().getBlockEntity(hit.getBlockPos()) instanceof DungeonBossSpawnerBlockEntity spawner) {
                                            spawner.debugEndDungeon();
                                            context.getSource().sendSuccess(() -> Component.literal("Dungeon spawner ended."), true);
                                            return 1;
                                        }
                                    }
                                    context.getSource().sendFailure(Component.literal("Look at a dungeon boss spawner to end it."));
                                    return 0;
                                })))
                .then(literal("dungeon")
                        .then(literal("register")
                                .requires(source -> source.hasPermission(2))
                                .then(argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            HitResult hitResult = player.pick(10.0D, 0.0F, false);
                                            if (hitResult.getType() == HitResult.Type.BLOCK && hitResult instanceof BlockHitResult hit) {
                                                if (player.level().getBlockEntity(hit.getBlockPos()) instanceof DungeonControllerBlockEntity controller) {
                                                    if (controller.dungeonSpawnerPos != BlockPos.ZERO) {
                                                        boolean added = ArenasLdMod.DUNGEON_BOSS_MANAGER.registerDungeon(context.getSource().getServer(), name, controller.dungeonSpawnerPos, controller.dungeonSpawnerDimension);
                                                        context.getSource().sendSuccess(() -> Component.literal(added ? "Dungeon registered." : "Dungeon name already registered."), true);
                                                        return 1;
                                                    }
                                                    context.getSource().sendFailure(Component.literal("Controller is not linked to a dungeon spawner."));
                                                    return 0;
                                                }
                                            }
                                            context.getSource().sendFailure(Component.literal("Look at a dungeon controller to register."));
                                            return 0;
                                        })))
                        .then(literal("unregister")
                                .requires(source -> source.hasPermission(2))
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(CommandRegistry::suggestDungeonNames)
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            boolean removed = ArenasLdMod.DUNGEON_BOSS_MANAGER.unregisterDungeon(context.getSource().getServer(), name);
                                            context.getSource().sendSuccess(() -> Component.literal(removed ? "Dungeon unregistered." : "Dungeon not registered."), true);
                                            return 1;
                                        })))
                        .then(literal("subscribe")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(CommandRegistry::suggestDungeonNames)
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            boolean ok = ArenasLdMod.DUNGEON_BOSS_MANAGER.subscribe(player, name);
                                            if (ok) {
                                                context.getSource().sendSuccess(() -> Component.literal("Subscribed to dungeon."), true);
                                            } else {
                                                context.getSource().sendFailure(Component.literal("Dungeon is not registered."));
                                            }
                                            return 1;
                                        })))
                        .then(literal("sub")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(CommandRegistry::suggestDungeonNames)
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            boolean ok = ArenasLdMod.DUNGEON_BOSS_MANAGER.subscribe(player, name);
                                            if (ok) {
                                                context.getSource().sendSuccess(() -> Component.literal("Subscribed to dungeon."), true);
                                            } else {
                                                context.getSource().sendFailure(Component.literal("Dungeon is not registered."));
                                            }
                                            return 1;
                                        })))
                        .then(literal("unsubscribe")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(CommandRegistry::suggestDungeonNames)
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            boolean ok = ArenasLdMod.DUNGEON_BOSS_MANAGER.unsubscribe(player, name);
                                            context.getSource().sendSuccess(() -> Component.literal(ok ? "Unsubscribed from dungeon." : "Dungeon not in your subscriptions."), true);
                                            return 1;
                                        })))
                        .then(literal("unsub")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(CommandRegistry::suggestDungeonNames)
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            boolean ok = ArenasLdMod.DUNGEON_BOSS_MANAGER.unsubscribe(player, name);
                                            context.getSource().sendSuccess(() -> Component.literal(ok ? "Unsubscribed from dungeon." : "Dungeon not in your subscriptions."), true);
                                            return 1;
                                        }))))
                .then(literal("subscriptions")
                        .then(literal("list")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    var subs = ArenasLdMod.DUNGEON_BOSS_MANAGER.getSubscriptions(player);
                                    if (subs.isEmpty()) {
                                        context.getSource().sendSuccess(() -> Component.literal("No dungeon subscriptions."), false);
                                        return 1;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal("Subscriptions:"), false);
                                    for (var name : subs) {
                                        context.getSource().sendSuccess(() -> Component.literal("- " + name), false);
                                    }
                                    return 1;
                                }))
                        .then(literal("unsubscribeAll")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ArenasLdMod.DUNGEON_BOSS_MANAGER.unsubscribeAll(player);
                                    context.getSource().sendSuccess(() -> Component.literal("All dungeon subscriptions cleared."), true);
                                    return 1;
                                }))
                        .then(literal("cooldowns")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    var subs = ArenasLdMod.DUNGEON_BOSS_MANAGER.getSubscriptions(player);
                                    if (subs.isEmpty()) {
                                        context.getSource().sendSuccess(() -> Component.literal("No dungeon subscriptions."), false);
                                        return 1;
                                    }
                                    for (var name : subs) {
                                        var key = ArenasLdMod.DUNGEON_BOSS_MANAGER.getRegisteredDungeon(context.getSource().getServer(), name);
                                        if (key == null) {
                                            context.getSource().sendSuccess(() -> Component.literal(name + " - Unregistered"), false);
                                            continue;
                                        }
                                        Level level = player.server.getLevel(key.dimension());
                                        if (level != null && level.getBlockEntity(key.pos()) instanceof DungeonBossSpawnerBlockEntity spawner) {
                                            String status;
                                            if (spawner.isDungeonRunning()) {
                                                status = "In Progress";
                                            } else {
                                                int seconds = spawner.getRespawnCooldownSeconds();
                                                status = seconds > 0 ? formatSeconds(seconds) : "Ready";
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal(name + " - " + status), false);
                                        } else {
                                            context.getSource().sendSuccess(() -> Component.literal(name + " - Unloaded"), false);
                                        }
                                    }
                                    return 1;
                                })));

        dispatcher.register(arenasLdNode);
    }

    public static void initialize() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            register(dispatcher);
        });
    }

    private static String formatSeconds(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static CompletableFuture<Suggestions> suggestDungeonNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        var server = context.getSource().getServer();
        var names = ArenasLdMod.DUNGEON_BOSS_MANAGER.getRegisteredDungeons(server).keySet();
        return SharedSuggestionProvider.suggest(names, builder);
    }
}
