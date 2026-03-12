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
        MobArenaManager.ArenaInfo arenaInfo = ArenasLdMod.MOB_ARENA_MANAGER.getDisconnectedArenaInfo(player.getUUID());
        if (arenaInfo != null) {
            ServerLevel world = player.server.getLevel(arenaInfo.dimension());
            if (world != null && world.getBlockEntity(arenaInfo.pos()) instanceof MobArenaSpawnerBlockEntity spawner) {
                player.setGameMode(GameType.SURVIVAL);
                BlockPos exitPos = arenaInfo.pos().offset(spawner.exitPosition);
                ServerLevel exitLevel = player.server.getLevel(spawner.exitDimension);
                if (exitLevel != null) {
                    player.teleportTo(exitLevel, exitPos.getX() + 0.5, exitPos.getY(), exitPos.getZ() + 0.5, player.getYRot(), player.getXRot());
                }
            }
            ArenasLdMod.MOB_ARENA_MANAGER.removeDisconnectedPlayer(player.getUUID());
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onPlayerDisconnect(ServerPlayer player, CallbackInfo ci) {
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
