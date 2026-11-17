package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.util.RunBlockBreaking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adventure mode refuses every block; a run player may still break what their run allows (both sides). */
@Mixin(Player.class)
public abstract class RunBlockBreakingMixin {
    @Inject(method = "blockActionRestricted", at = @At("RETURN"), cancellable = true)
    private void arenasLd$runBreakable(Level level, BlockPos pos, GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && RunBlockBreaking.mayBreak((Player) (Object) this, level, pos, gameType)) {
            cir.setReturnValue(false);
        }
    }
}
