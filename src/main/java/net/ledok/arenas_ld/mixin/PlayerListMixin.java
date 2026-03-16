package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.manager.MobArenaManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void onPlayerConnect(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        var dungeonExit = ArenasLdMod.DUNGEON_BOSS_MANAGER.getDisconnectedDungeonInfo(player.getUUID());
        if (dungeonExit != null) {
            ServerLevel exitLevel = player.server.getLevel(dungeonExit.dimension());
            if (exitLevel != null) {
                BlockPos exitPos = dungeonExit.pos();
                var chunkPos = new net.minecraft.world.level.ChunkPos(exitPos);
                exitLevel.setChunkForced(chunkPos.x, chunkPos.z, true);
                player.setGameMode(GameType.SURVIVAL);
                player.teleportTo(exitLevel, exitPos.getX() + 0.5, exitPos.getY(), exitPos.getZ() + 0.5, player.getYRot(), player.getXRot());
                exitLevel.setChunkForced(chunkPos.x, chunkPos.z, false);
            }
            ArenasLdMod.DUNGEON_BOSS_MANAGER.removeDisconnectedDungeonPlayer(player.getUUID());
        }

        MobArenaManager.ArenaInfo arenaInfo = ArenasLdMod.MOB_ARENA_MANAGER.getDisconnectedArenaInfo(player.getUUID());
        if (arenaInfo != null) {
            ServerLevel world = player.server.getLevel(arenaInfo.dimension());
            if (world != null && world.getBlockEntity(arenaInfo.pos()) instanceof MobArenaSpawnerBlockEntity spawner) {
                player.setGameMode(GameType.SURVIVAL);
                BlockPos exitPos = arenaInfo.pos().offset(spawner.exitPosition);
                ServerLevel exitLevel = player.server.getLevel(spawner.exitDimension);
                if (exitLevel != null) {
                    var chunkPos = new net.minecraft.world.level.ChunkPos(exitPos);
                    exitLevel.setChunkForced(chunkPos.x, chunkPos.z, true);
                    player.teleportTo(exitLevel, exitPos.getX() + 0.5, exitPos.getY(), exitPos.getZ() + 0.5, player.getYRot(), player.getXRot());
                    exitLevel.setChunkForced(chunkPos.x, chunkPos.z, false);
                }
            }
            ArenasLdMod.MOB_ARENA_MANAGER.removeDisconnectedPlayer(player.getUUID());
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onPlayerDisconnect(ServerPlayer player, CallbackInfo ci) {
        var server = player.server;
        if (server != null) {
            for (var key : net.ledok.arenas_ld.block.entity.MobArenaControllerBlockEntity.getControllers()) {
                ServerLevel level = server.getLevel(key.dimension());
                if (level == null) continue;
                var be = level.getBlockEntity(key.pos());
                if (be instanceof net.ledok.arenas_ld.block.entity.MobArenaControllerBlockEntity controller) {
                    if (controller.partyMembers.remove(player.getUUID())) {
                        controller.setChanged();
                        level.sendBlockUpdated(controller.getBlockPos(), controller.getBlockState(), controller.getBlockState(), 3);
                    }
                }
            }
            for (var key : net.ledok.arenas_ld.block.entity.DungeonControllerBlockEntity.getControllers()) {
                ServerLevel level = server.getLevel(key.dimension());
                if (level == null) continue;
                var be = level.getBlockEntity(key.pos());
                if (be instanceof net.ledok.arenas_ld.block.entity.DungeonControllerBlockEntity controller) {
                    if (controller.partyMembers.remove(player.getUUID())) {
                        controller.setChanged();
                        level.sendBlockUpdated(controller.getBlockPos(), controller.getBlockState(), controller.getBlockState(), 3);
                    }
                }
            }
        }
        if (ArenasLdMod.MOB_ARENA_MANAGER.isInArena(player)) {
            MobArenaManager.ArenaInfo arenaInfo = ArenasLdMod.MOB_ARENA_MANAGER.getArenaInfo(player);
            if (arenaInfo != null) {
                ServerLevel world = player.server.getLevel(arenaInfo.dimension());
                if (world != null && world.getBlockEntity(arenaInfo.pos()) instanceof MobArenaSpawnerBlockEntity spawner) {
                    spawner.sendLossMessage(player);
                    spawner.updateLeaderboardOnDisconnect(player);
                    spawner.removeParticipatingPlayer(player.getUUID());
                    if (spawner.getParticipatingPlayerCount() == 0) {
                        spawner.endArena();
                    }
                }
            }
        }
        ArenasLdMod.MOB_ARENA_MANAGER.onPlayerDisconnect(player);
    }
}
