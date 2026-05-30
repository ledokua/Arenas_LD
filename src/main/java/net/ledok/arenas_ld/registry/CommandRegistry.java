package net.ledok.arenas_ld.registry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminMenuProvider;
import net.ledok.arenas_ld.raid.screen.RaidControllerAdminMenuProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
            .then(literal("debug")
                .requires(source -> source.hasPermission(2))
                .then(literal("clearTrackedPlayers")
                    .executes(context -> {
                        ArenasLdMod.MOB_ARENA_MANAGER.clear();
                        context.getSource().sendSuccess(() -> Component.literal("Cleared all tracked players from Mob Arenas."), true);
                        return 1;
                    })))
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
                }));

        dispatcher.register(arenasLdNode);
    }

    public static void initialize() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            register(dispatcher);
        });
    }
}
