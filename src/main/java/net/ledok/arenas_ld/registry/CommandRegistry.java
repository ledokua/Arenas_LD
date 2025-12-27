package net.ledok.arenas_ld.registry;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.BossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.manager.DungeonManager;
import net.ledok.arenas_ld.screen.LobbyData;
import net.ledok.arenas_ld.screen.LobbyScreenHandler;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class CommandRegistry {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("arenas_ld")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("reload")
                            .executes(context -> {
                                ArenasLdMod.CONFIG.reloadFromFile();
                                DungeonManager.load();
                                context.getSource().sendSuccess(() -> Component.literal("Arenas LD config reloaded!"), true);
                                return 1;
                            }))
                    .then(Commands.literal("lobby")
                            .executes(context -> {
                                if (context.getSource().getEntity() instanceof ServerPlayer player) {
                                    player.openMenu(new ExtendedScreenHandlerFactory<LobbyData>() {
                                        @Override
                                        public LobbyData getScreenOpeningData(ServerPlayer player) {
                                            return new LobbyData();
                                        }

                                        @Override
                                        public Component getDisplayName() {
                                            return Component.literal("Dungeon Lobby");
                                        }

                                        @Nullable
                                        @Override
                                        public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
                                            return new LobbyScreenHandler(i, inventory);
                                        }
                                    });
                                }
                                return 1;
                            }))
                    .then(Commands.literal("create_dungeon")
                            .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                    .then(Commands.argument("priority", IntegerArgumentType.integer())
                                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                                    .executes(context -> {
                                                        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
                                                        int priority = IntegerArgumentType.getInteger(context, "priority");
                                                        String name = StringArgumentType.getString(context, "name");
                                                        
                                                        BlockEntity be = context.getSource().getLevel().getBlockEntity(pos);
                                                        boolean isRaid;
                                                        if (be instanceof BossSpawnerBlockEntity) {
                                                            isRaid = true;
                                                        } else {
                                                            isRaid = false;
                                                            if (!(be instanceof DungeonBossSpawnerBlockEntity)) {
                                                                context.getSource().sendFailure(Component.literal("Block at " + pos + " is not a valid spawner."));
                                                                return 0;
                                                            }
                                                        }
                                                        
                                                        DungeonManager.addDungeon(name, pos, priority, isRaid);
                                                        context.getSource().sendSuccess(() -> Component.literal("Added " + (isRaid ? "raid" : "dungeon") + " '" + name + "' at " + pos), true);
                                                        return 1;
                                                    })))))
                    .then(Commands.literal("modify_dungeon")
                            .then(Commands.argument("name", StringArgumentType.string())
                                    .suggests((context, builder) -> {
                                        DungeonManager.getDungeons().forEach(d -> builder.suggest(d.name()));
                                        return builder.buildFuture();
                                    })
                                    .then(Commands.literal("priority")
                                            .then(Commands.argument("value", IntegerArgumentType.integer())
                                                    .executes(context -> {
                                                        String name = StringArgumentType.getString(context, "name");
                                                        int priority = IntegerArgumentType.getInteger(context, "value");
                                                        Optional<DungeonManager.DungeonEntry> entryOpt = DungeonManager.getDungeon(name);
                                                        if (entryOpt.isPresent()) {
                                                            DungeonManager.DungeonEntry entry = entryOpt.get();
                                                            DungeonManager.removeDungeon(name);
                                                            DungeonManager.addDungeon(name, entry.pos(), priority, entry.isRaid());
                                                            context.getSource().sendSuccess(() -> Component.literal("Updated priority for '" + name + "' to " + priority), true);
                                                            return 1;
                                                        } else {
                                                            context.getSource().sendFailure(Component.literal("Dungeon '" + name + "' not found."));
                                                            return 0;
                                                        }
                                                    })))
                                    .then(Commands.literal("name")
                                            .then(Commands.argument("new_name", StringArgumentType.greedyString())
                                                    .executes(context -> {
                                                        String name = StringArgumentType.getString(context, "name");
                                                        String newName = StringArgumentType.getString(context, "new_name");
                                                        Optional<DungeonManager.DungeonEntry> entryOpt = DungeonManager.getDungeon(name);
                                                        if (entryOpt.isPresent()) {
                                                            DungeonManager.DungeonEntry entry = entryOpt.get();
                                                            DungeonManager.removeDungeon(name);
                                                            DungeonManager.addDungeon(newName, entry.pos(), entry.priority(), entry.isRaid());
                                                            context.getSource().sendSuccess(() -> Component.literal("Renamed '" + name + "' to '" + newName + "'"), true);
                                                            return 1;
                                                        } else {
                                                            context.getSource().sendFailure(Component.literal("Dungeon '" + name + "' not found."));
                                                            return 0;
                                                        }
                                                    })))))
            );
        });
    }
}
