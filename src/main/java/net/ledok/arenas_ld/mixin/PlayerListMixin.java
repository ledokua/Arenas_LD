package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void onPlayerDisconnect(ServerPlayer player, CallbackInfo ci) {
        if (ArenasLdMod.MOB_ARENA_MANAGER.isInArena(player)) {
            BlockPos arenaPos = ArenasLdMod.MOB_ARENA_MANAGER.getArenaPos(player);
            if (arenaPos != null) {
                Level world = player.level();
                if (world.getBlockEntity(arenaPos) instanceof MobArenaSpawnerBlockEntity spawner) {
                    player.setGameMode(GameType.SURVIVAL);
                    BlockPos exitPos = arenaPos.offset(spawner.exitPortalDestination);
                    player.setRespawnPosition(world.dimension(), exitPos, 0, true, false);
                }
            }
            ArenasLdMod.MOB_ARENA_MANAGER.removePlayer(player);
        }
    }
}
