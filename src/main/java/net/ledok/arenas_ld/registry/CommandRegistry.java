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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.ledok.arenas_ld.util.DungeonInstanceRef;
import net.ledok.arenas_ld.util.InstanceState;
import net.ledok.arenas_ld.util.Lobby;
import net.ledok.arenas_ld.util.LobbyVisibility;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

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
                                                    if (!controller.getInstances().isEmpty()) {
                                                        boolean added = ArenasLdMod.DUNGEON_BOSS_MANAGER.registerDungeon(
                                                                context.getSource().getServer(),
                                                                name,
                                                                controller.getBlockPos(),
                                                                player.level().dimension()
                                                        );
                                                        context.getSource().sendSuccess(() -> Component.literal(added ? "Dungeon registered." : "Dungeon name already registered."), true);
                                                        return 1;
                                                    }
                                                    context.getSource().sendFailure(Component.literal("Controller has no linked dungeon instances."));
                                                    return 0;
                                                }
                                            }
                                            context.getSource().sendFailure(Component.literal("Look at a dungeon controller to register."));
                                            return 0;
                                        }))))
                        .then(literal("addinstance")
                                .requires(source -> source.hasPermission(2))
                                .then(argument("ctrl_x", IntegerArgumentType.integer())
                                        .then(argument("ctrl_y", IntegerArgumentType.integer())
                                                .then(argument("ctrl_z", IntegerArgumentType.integer())
                                                        .then(argument("spawner_x", IntegerArgumentType.integer())
                                                                .then(argument("spawner_y", IntegerArgumentType.integer())
                                                                        .then(argument("spawner_z", IntegerArgumentType.integer())
                                                                                .then(argument("dimension", StringArgumentType.word())
                                                                                        .executes(context -> {
                                                                                            BlockPos controllerPos = new BlockPos(
                                                                                                    IntegerArgumentType.getInteger(context, "ctrl_x"),
                                                                                                    IntegerArgumentType.getInteger(context, "ctrl_y"),
                                                                                                    IntegerArgumentType.getInteger(context, "ctrl_z")
                                                                                            );
                                                                                            BlockPos spawnerPos = new BlockPos(
                                                                                                    IntegerArgumentType.getInteger(context, "spawner_x"),
                                                                                                    IntegerArgumentType.getInteger(context, "spawner_y"),
                                                                                                    IntegerArgumentType.getInteger(context, "spawner_z")
                                                                                            );
                                                                                            ResourceLocation dimId;
                                                                                            try {
                                                                                                dimId = ResourceLocation.parse(StringArgumentType.getString(context, "dimension"));
                                                                                            } catch (Exception e) {
                                                                                                context.getSource().sendFailure(Component.literal("Invalid dimension id."));
                                                                                                return 0;
                                                                                            }
                                                                                            ResourceKey<Level> spawnerDim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimId);
                                                                                            Level controllerLevel = context.getSource().getLevel();
                                                                                            if (!(controllerLevel.getBlockEntity(controllerPos) instanceof DungeonControllerBlockEntity controller)) {
                                                                                                context.getSource().sendFailure(Component.literal("No dungeon controller at given controller position."));
                                                                                                return 0;
                                                                                            }
                                                                                            boolean added = controller.addInstance(new DungeonInstanceRef(spawnerPos, spawnerDim));
                                                                                            context.getSource().sendSuccess(() -> Component.literal(added ? "Dungeon instance added." : "Instance already exists or cannot be added."), true);
                                                                                            return added ? 1 : 0;
                                                                                        }))))))))
                        .then(literal("removeinstance")
                                .requires(source -> source.hasPermission(2))
                                .then(argument("ctrl_x", IntegerArgumentType.integer())
                                        .then(argument("ctrl_y", IntegerArgumentType.integer())
                                                .then(argument("ctrl_z", IntegerArgumentType.integer())
                                                        .then(argument("spawner_x", IntegerArgumentType.integer())
                                                                .then(argument("spawner_y", IntegerArgumentType.integer())
                                                                        .then(argument("spawner_z", IntegerArgumentType.integer())
                                                                                .executes(context -> {
                                                                                    BlockPos controllerPos = new BlockPos(
                                                                                            IntegerArgumentType.getInteger(context, "ctrl_x"),
                                                                                            IntegerArgumentType.getInteger(context, "ctrl_y"),
                                                                                            IntegerArgumentType.getInteger(context, "ctrl_z")
                                                                                    );
                                                                                    BlockPos spawnerPos = new BlockPos(
                                                                                            IntegerArgumentType.getInteger(context, "spawner_x"),
                                                                                            IntegerArgumentType.getInteger(context, "spawner_y"),
                                                                                            IntegerArgumentType.getInteger(context, "spawner_z")
                                                                                    );
                                                                                    Level controllerLevel = context.getSource().getLevel();
                                                                                    if (!(controllerLevel.getBlockEntity(controllerPos) instanceof DungeonControllerBlockEntity controller)) {
                                                                                        context.getSource().sendFailure(Component.literal("No dungeon controller at given controller position."));
                                                                                        return 0;
                                                                                    }
                                                                                    // Remove matching position in any dimension first; fallback to controller dimension.
                                                                                    DungeonInstanceRef target = null;
                                                                                    for (InstanceState instance : controller.getInstances()) {
                                                                                        if (instance.ref().spawnerPos().equals(spawnerPos)) {
                                                                                            target = instance.ref();
                                                                                            break;
                                                                                        }
                                                                                    }
                                                                                    if (target == null) {
                                                                                        target = new DungeonInstanceRef(spawnerPos, controllerLevel.dimension());
                                                                                    }
                                                                                    boolean removed = controller.removeInstance(target);
                                                                                    context.getSource().sendSuccess(() -> Component.literal(removed ? "Dungeon instance removed." : "Instance not found or currently running."), true);
                                                                                    return removed ? 1 : 0;
                                                                                })))))))
                        .then(literal("listinstances")
                                .requires(source -> source.hasPermission(2))
                                .then(argument("ctrl_x", IntegerArgumentType.integer())
                                        .then(argument("ctrl_y", IntegerArgumentType.integer())
                                                .then(argument("ctrl_z", IntegerArgumentType.integer())
                                                        .executes(context -> {
                                                            BlockPos controllerPos = new BlockPos(
                                                                    IntegerArgumentType.getInteger(context, "ctrl_x"),
                                                                    IntegerArgumentType.getInteger(context, "ctrl_y"),
                                                                    IntegerArgumentType.getInteger(context, "ctrl_z")
                                                            );
                                                            Level controllerLevel = context.getSource().getLevel();
                                                            if (!(controllerLevel.getBlockEntity(controllerPos) instanceof DungeonControllerBlockEntity controller)) {
                                                                context.getSource().sendFailure(Component.literal("No dungeon controller at given controller position."));
                                                                return 0;
                                                            }
                                                            if (controller.getInstances().isEmpty()) {
                                                                context.getSource().sendSuccess(() -> Component.literal("No dungeon instances linked."), false);
                                                                return 1;
                                                            }
                                                            context.getSource().sendSuccess(() -> Component.literal("Dungeon instances:"), false);
                                                            for (InstanceState instance : controller.getInstances()) {
                                                                int cooldownSec = (instance.cooldownTicksRemaining() + 19) / 20;
                                                                String line = String.format(
                                                                        "- %s @ %s (%s%s)",
                                                                        instance.ref().spawnerPos().toShortString(),
                                                                        instance.ref().dimension().location(),
                                                                        instance.status().name(),
                                                                        instance.status() == net.ledok.arenas_ld.util.InstanceStatus.COOLDOWN ? ", " + formatSeconds(cooldownSec) : ""
                                                                );
                                                                context.getSource().sendSuccess(() -> Component.literal(line), false);
                                                            }
                                                            return 1;
                                                        })))))
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
                .then(literal("lobby")
                        .then(literal("create")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    if (findControllerByLobbyMember(context.getSource().getServer(), player.getUUID()) != null) {
                                        context.getSource().sendFailure(Component.literal("You are already in a lobby."));
                                        return 0;
                                    }
                                    HitResult hitResult = player.pick(10.0D, 0.0F, false);
                                    if (hitResult.getType() != HitResult.Type.BLOCK || !(hitResult instanceof BlockHitResult hit)) {
                                        context.getSource().sendFailure(Component.literal("Look at a dungeon controller to create a lobby."));
                                        return 0;
                                    }
                                    if (!(player.level().getBlockEntity(hit.getBlockPos()) instanceof DungeonControllerBlockEntity controller)) {
                                        context.getSource().sendFailure(Component.literal("Look at a dungeon controller to create a lobby."));
                                        return 0;
                                    }
                                    Lobby created = controller.createLobby(player.getUUID(), player.getGameProfile().getName());
                                    if (created == null) {
                                        context.getSource().sendFailure(Component.literal("Failed to create lobby."));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal("Lobby created: " + created.id), true);
                                    return 1;
                                }))
                        .then(literal("disband")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    DungeonControllerBlockEntity controller = findControllerByLobbyMember(context.getSource().getServer(), player.getUUID());
                                    if (controller == null) {
                                        context.getSource().sendFailure(Component.literal("You are not in a lobby."));
                                        return 0;
                                    }
                                    boolean ok = controller.disbandLobby(player.getUUID());
                                    if (!ok) {
                                        context.getSource().sendFailure(Component.literal("Only the lobby owner can disband the lobby."));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal("Lobby disbanded."), true);
                                    return 1;
                                }))
                        .then(literal("visibility")
                                .then(literal("open")
                                        .executes(context -> setLobbyVisibility(context, LobbyVisibility.OPEN)))
                                .then(literal("invite_only")
                                        .executes(context -> setLobbyVisibility(context, LobbyVisibility.INVITE_ONLY))))
                        .then(literal("list")
                                .executes(context -> {
                                    MinecraftServer server = context.getSource().getServer();
                                    int total = 0;
                                    for (DungeonControllerBlockEntity.ControllerKey key : DungeonControllerBlockEntity.getControllers()) {
                                        ServerLevel level = server.getLevel(key.dimension());
                                        if (level == null) {
                                            continue;
                                        }
                                        if (!(level.getBlockEntity(key.pos()) instanceof DungeonControllerBlockEntity controller)) {
                                            continue;
                                        }
                                        if (controller.getLobbies().isEmpty()) {
                                            continue;
                                        }
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                "Controller " + key.pos().toShortString() + " @ " + key.dimension().location()
                                        ), false);
                                        int queuedIndex = 0;
                                        for (Lobby lobby : controller.getLobbies()) {
                                            int queuePosition = 0;
                                            if (lobby.status == net.ledok.arenas_ld.util.LobbyStatus.QUEUED) {
                                                queuedIndex++;
                                                queuePosition = queuedIndex;
                                            }
                                            String ownerName = lobby.ownerName != null && !lobby.ownerName.isEmpty()
                                                    ? lobby.ownerName
                                                    : "Unknown";
                                            int size = 1 + lobby.members.size();
                                            String line = String.format(
                                                    "- id=%s owner=%s size=%d/%d visibility=%s status=%s queue=%s",
                                                    lobby.id,
                                                    ownerName,
                                                    size,
                                                    controller.getMaxPartySize(),
                                                    lobby.visibility.name().toLowerCase(),
                                                    lobby.status.name().toLowerCase(),
                                                    queuePosition > 0 ? Integer.toString(queuePosition) : "-"
                                            );
                                            context.getSource().sendSuccess(() -> Component.literal(line), false);
                                            total++;
                                        }
                                    }
                                    if (total == 0) {
                                        context.getSource().sendSuccess(() -> Component.literal("No active lobbies."), false);
                                    } else {
                                        int finalTotal = total;
                                        context.getSource().sendSuccess(() -> Component.literal("Total lobbies: " + finalTotal), false);
                                    }
                                    return 1;
                                }))
                        .then(literal("info")
                                .then(argument("lobbyId", StringArgumentType.word())
                                        .suggests(CommandRegistry::suggestActiveLobbyIds)
                                        .executes(context -> {
                                            UUID lobbyId;
                                            try {
                                                lobbyId = UUID.fromString(StringArgumentType.getString(context, "lobbyId"));
                                            } catch (IllegalArgumentException e) {
                                                context.getSource().sendFailure(Component.literal("Invalid lobby id."));
                                                return 0;
                                            }
                                            MinecraftServer server = context.getSource().getServer();
                                            for (DungeonControllerBlockEntity.ControllerKey key : DungeonControllerBlockEntity.getControllers()) {
                                                ServerLevel level = server.getLevel(key.dimension());
                                                if (level == null) {
                                                    continue;
                                                }
                                                if (!(level.getBlockEntity(key.pos()) instanceof DungeonControllerBlockEntity controller)) {
                                                    continue;
                                                }
                                                Lobby lobby = controller.getLobbyById(lobbyId);
                                                if (lobby == null) {
                                                    continue;
                                                }
                                                int size = 1 + lobby.members.size();
                                                context.getSource().sendSuccess(() -> Component.literal(
                                                        "Lobby " + lobby.id + " @ " + key.pos().toShortString() + " (" + key.dimension().location() + ")"
                                                ), false);
                                                context.getSource().sendSuccess(() -> Component.literal(
                                                        "owner=" + (lobby.ownerName == null || lobby.ownerName.isEmpty() ? "Unknown" : lobby.ownerName) +
                                                                " size=" + size + "/" + controller.getMaxPartySize() +
                                                                " visibility=" + lobby.visibility.name().toLowerCase() +
                                                                " status=" + lobby.status.name().toLowerCase() +
                                                                " tier=" + lobby.selectedTier.name().toLowerCase() +
                                                                " hardcore=" + lobby.hardcoreEnabled
                                                ), false);
                                                if (!lobby.members.isEmpty()) {
                                                    context.getSource().sendSuccess(() -> Component.literal("Members:"), false);
                                                    for (UUID memberUuid : lobby.members) {
                                                        ServerPlayer member = server.getPlayerList().getPlayer(memberUuid);
                                                        String memberName = member != null ? member.getGameProfile().getName() : memberUuid.toString();
                                                        context.getSource().sendSuccess(() -> Component.literal("- " + memberName), false);
                                                    }
                                                }
                                                if (!lobby.pendingInvites.isEmpty()) {
                                                    context.getSource().sendSuccess(() -> Component.literal("Pending invites: " + lobby.pendingInvites.size()), false);
                                                }
                                                return 1;
                                            }
                                            context.getSource().sendFailure(Component.literal("Lobby not found."));
                                            return 0;
                                        })))
                        .then(literal("invites")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    MinecraftServer server = context.getSource().getServer();
                                    int count = 0;
                                    for (DungeonControllerBlockEntity.ControllerKey key : DungeonControllerBlockEntity.getControllers()) {
                                        ServerLevel level = server.getLevel(key.dimension());
                                        if (level == null) {
                                            continue;
                                        }
                                        if (!(level.getBlockEntity(key.pos()) instanceof DungeonControllerBlockEntity controller)) {
                                            continue;
                                        }
                                        for (Lobby lobby : controller.getLobbies()) {
                                            Long expireAt = lobby.pendingInvites.get(player.getUUID());
                                            if (expireAt == null) {
                                                continue;
                                            }
                                            long now = player.serverLevel().getGameTime();
                                            long remainingTicks = Math.max(0L, expireAt - now);
                                            int remainingSeconds = (int) ((remainingTicks + 19L) / 20L);
                                            String ownerName = lobby.ownerName != null && !lobby.ownerName.isEmpty() ? lobby.ownerName : "Unknown";
                                            String line = String.format(
                                                    "- lobby=%s owner=%s visibility=%s expires_in=%s",
                                                    lobby.id,
                                                    ownerName,
                                                    lobby.visibility.name().toLowerCase(),
                                                    formatSeconds(remainingSeconds)
                                            );
                                            context.getSource().sendSuccess(() -> Component.literal(line), false);
                                            count++;
                                        }
                                    }
                                    if (count == 0) {
                                        context.getSource().sendSuccess(() -> Component.literal("No pending lobby invites."), false);
                                    } else {
                                        int finalCount = count;
                                        context.getSource().sendSuccess(() -> Component.literal("Pending invites: " + finalCount), false);
                                    }
                                    return 1;
                                }))
                        .then(literal("invite")
                                .then(argument("playerName", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayer owner = context.getSource().getPlayerOrException();
                                            String targetName = StringArgumentType.getString(context, "playerName");
                                            ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);
                                            if (target == null) {
                                                context.getSource().sendFailure(Component.literal("Player must be online to receive an invite."));
                                                return 0;
                                            }
                                            if (owner.getUUID().equals(target.getUUID())) {
                                                context.getSource().sendFailure(Component.literal("You cannot invite yourself."));
                                                return 0;
                                            }
                                            DungeonControllerBlockEntity controller = findControllerByLobbyMember(context.getSource().getServer(), owner.getUUID());
                                            if (controller == null) {
                                                context.getSource().sendFailure(Component.literal("You are not in a lobby."));
                                                return 0;
                                            }
                                            Lobby lobby = controller.getLobbyByMember(owner.getUUID());
                                            if (lobby == null || !lobby.ownerUuid.equals(owner.getUUID())) {
                                                context.getSource().sendFailure(Component.literal("Only the lobby owner can invite players."));
                                                return 0;
                                            }
                                            long nowTick = owner.serverLevel().getGameTime();
                                            long expireAt = nowTick + (5L * 60L * 20L);
                                            if (!controller.invitePlayer(owner.getUUID(), target.getUUID(), expireAt)) {
                                                context.getSource().sendFailure(Component.literal("Failed to send invite."));
                                                return 0;
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("Invited " + target.getGameProfile().getName() + "."), true);
                                            sendClickableLobbyInvite(target, owner.getGameProfile().getName(), lobby.id);
                                            return 1;
                                        })))
                        .then(literal("accept")
                                .then(argument("lobbyId", StringArgumentType.word())
                                        .suggests(CommandRegistry::suggestPendingLobbyInvites)
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            UUID lobbyId;
                                            try {
                                                lobbyId = UUID.fromString(StringArgumentType.getString(context, "lobbyId"));
                                            } catch (IllegalArgumentException e) {
                                                context.getSource().sendFailure(Component.literal("Invalid lobby id."));
                                                return 0;
                                            }
                                            DungeonControllerBlockEntity controller = findControllerByLobbyId(context.getSource().getServer(), lobbyId);
                                            if (controller == null) {
                                                context.getSource().sendFailure(Component.literal("Lobby not found."));
                                                return 0;
                                            }
                                            long nowTick = player.serverLevel().getGameTime();
                                            boolean accepted = controller.acceptInvite(lobbyId, player.getUUID(), player.getGameProfile().getName(), nowTick);
                                            if (!accepted) {
                                                context.getSource().sendFailure(Component.literal("Invite is missing, expired, or lobby is full."));
                                                return 0;
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("Joined lobby."), true);
                                            return 1;
                                        })))
                        .then(literal("decline")
                                .then(argument("lobbyId", StringArgumentType.word())
                                        .suggests(CommandRegistry::suggestPendingLobbyInvites)
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            UUID lobbyId;
                                            try {
                                                lobbyId = UUID.fromString(StringArgumentType.getString(context, "lobbyId"));
                                            } catch (IllegalArgumentException e) {
                                                context.getSource().sendFailure(Component.literal("Invalid lobby id."));
                                                return 0;
                                            }
                                            DungeonControllerBlockEntity controller = findControllerByLobbyId(context.getSource().getServer(), lobbyId);
                                            if (controller == null) {
                                                context.getSource().sendFailure(Component.literal("Lobby not found."));
                                                return 0;
                                            }
                                            boolean declined = controller.declineInvite(lobbyId, player.getUUID());
                                            if (!declined) {
                                                context.getSource().sendFailure(Component.literal("No pending invite for this lobby."));
                                                return 0;
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("Invite declined."), true);
                                            return 1;
                                        })))
                        .then(literal("leave")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    DungeonControllerBlockEntity controller = findControllerByLobbyMember(context.getSource().getServer(), player.getUUID());
                                    if (controller == null) {
                                        context.getSource().sendFailure(Component.literal("You are not in a lobby."));
                                        return 0;
                                    }
                                    boolean left = controller.leaveLobby(player.getUUID());
                                    if (!left) {
                                        context.getSource().sendFailure(Component.literal("Failed to leave lobby."));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal("Left lobby."), true);
                                    return 1;
                                }))
                        .then(literal("kick")
                                .then(argument("playerName", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayer owner = context.getSource().getPlayerOrException();
                                            String targetName = StringArgumentType.getString(context, "playerName");
                                            ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);
                                            if (target == null) {
                                                context.getSource().sendFailure(Component.literal("Target player must be online."));
                                                return 0;
                                            }
                                            DungeonControllerBlockEntity controller = findControllerByLobbyMember(context.getSource().getServer(), owner.getUUID());
                                            if (controller == null) {
                                                context.getSource().sendFailure(Component.literal("You are not in a lobby."));
                                                return 0;
                                            }
                                            boolean kicked = controller.kickFromLobby(owner.getUUID(), target.getUUID());
                                            if (!kicked) {
                                                context.getSource().sendFailure(Component.literal("Failed to kick player. Owner-only command; target must be in your lobby."));
                                                return 0;
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("Kicked " + targetName + "."), true);
                                            target.sendSystemMessage(Component.literal("You were removed from the dungeon lobby by " + owner.getGameProfile().getName() + "."));
                                            return 1;
                                        })))
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
                                        if (level != null && level.getBlockEntity(key.pos()) instanceof DungeonControllerBlockEntity controller) {
                                            String status;
                                            if (controller.isLocked()) {
                                                status = "In Progress";
                                            } else {
                                                int seconds = controller.getNextAvailableCooldownSeconds();
                                                status = seconds > 0 ? formatSeconds(seconds) : "Ready";
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal(name + " - " + status), false);
                                        } else {
                                            context.getSource().sendSuccess(() -> Component.literal(name + " - Unloaded"), false);
                                        }
                                    }
                                    return 1;
                                })))));

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

    private static CompletableFuture<Suggestions> suggestActiveLobbyIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        MinecraftServer server = context.getSource().getServer();
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (DungeonControllerBlockEntity.ControllerKey key : DungeonControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                continue;
            }
            if (!(level.getBlockEntity(key.pos()) instanceof DungeonControllerBlockEntity controller)) {
                continue;
            }
            for (Lobby lobby : controller.getLobbies()) {
                ids.add(lobby.id.toString());
            }
        }
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    private static CompletableFuture<Suggestions> suggestPendingLobbyInvites(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (Exception e) {
            return Suggestions.empty();
        }
        MinecraftServer server = context.getSource().getServer();
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (DungeonControllerBlockEntity.ControllerKey key : DungeonControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                continue;
            }
            if (!(level.getBlockEntity(key.pos()) instanceof DungeonControllerBlockEntity controller)) {
                continue;
            }
            for (Lobby lobby : controller.getLobbies()) {
                if (lobby.pendingInvites.containsKey(player.getUUID())) {
                    ids.add(lobby.id.toString());
                }
            }
        }
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    private static DungeonControllerBlockEntity findControllerByLobbyMember(MinecraftServer server, UUID playerUuid) {
        for (DungeonControllerBlockEntity.ControllerKey key : DungeonControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                continue;
            }
            if (level.getBlockEntity(key.pos()) instanceof DungeonControllerBlockEntity controller
                    && controller.getLobbyByMember(playerUuid) != null) {
                return controller;
            }
        }
        return null;
    }

    private static DungeonControllerBlockEntity findControllerByLobbyId(MinecraftServer server, UUID lobbyId) {
        for (DungeonControllerBlockEntity.ControllerKey key : DungeonControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                continue;
            }
            if (level.getBlockEntity(key.pos()) instanceof DungeonControllerBlockEntity controller
                    && controller.getLobbyById(lobbyId) != null) {
                return controller;
            }
        }
        return null;
    }

    private static int setLobbyVisibility(CommandContext<CommandSourceStack> context, LobbyVisibility visibility) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DungeonControllerBlockEntity controller = findControllerByLobbyMember(context.getSource().getServer(), player.getUUID());
        if (controller == null) {
            context.getSource().sendFailure(Component.literal("You are not in a lobby."));
            return 0;
        }
        boolean ok = controller.setLobbyVisibility(player.getUUID(), visibility);
        if (!ok) {
            context.getSource().sendFailure(Component.literal("Only the lobby owner can change lobby visibility."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Lobby visibility set to " + visibility.name().toLowerCase().replace('_', ' ') + "."), true);
        return 1;
    }

    private static void sendClickableLobbyInvite(ServerPlayer target, String ownerName, UUID lobbyId) {
        String acceptCommand = "/arenasld lobby accept " + lobbyId;
        String declineCommand = "/arenasld lobby decline " + lobbyId;
        MutableComponent header = Component.translatable("message.arenas_ld.lobby_invite_from", ownerName);
        MutableComponent accept = Component.translatable("message.arenas_ld.lobby_invite_accept")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, acceptCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(acceptCommand))));
        MutableComponent decline = Component.translatable("message.arenas_ld.lobby_invite_decline")
                .withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, declineCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(declineCommand))));
        target.sendSystemMessage(header.append(accept).append(Component.literal(" ")).append(decline));
    }
}
