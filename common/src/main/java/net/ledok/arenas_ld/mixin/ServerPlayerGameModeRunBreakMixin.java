package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.util.RunBlockBreaking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A block a run player may break goes without drops or XP ({@link RunBlockBreaking#breakInRun}). */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeRunBreakMixin {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;
    @Shadow private GameType gameModeForPlayer;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void arenasLd$runBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (gameModeForPlayer == GameType.ADVENTURE && RunBlockBreaking.breakInRun(player, level, pos)) {
            cir.setReturnValue(true);
        }
    }
}
