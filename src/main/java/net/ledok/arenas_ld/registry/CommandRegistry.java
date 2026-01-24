package net.ledok.arenas_ld.registry;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class CommandRegistry {
    public static void initialize() {
        NeoForge.EVENT_BUS.addListener(CommandRegistry::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arenas_ld")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(context -> {
                            ArenasLdMod.CONFIG.reloadFromFile();
                            context.getSource().sendSuccess(() -> Component.translatable("message.arenas_ld.config_reloaded"), true);
                            return 1;
                        })));
    }
}
