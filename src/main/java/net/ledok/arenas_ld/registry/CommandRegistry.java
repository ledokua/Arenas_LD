package net.ledok.arenas_ld.registry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

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
                                })));

        dispatcher.register(arenasLdNode);
    }

    public static void initialize() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            register(dispatcher);
        });
    }
}
