package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.util.LivingEntityHooks;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public abstract class EndermanTakeBlockGoalMixin {

    @Shadow @Final private EnderMan enderman;

    /** Run-spawned endermen never pick up the dungeon's blocks. */
    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void canUse(CallbackInfoReturnable<Boolean> cir) {
        if (LivingEntityHooks.suppressBlockGriefing(this.enderman)) {
            cir.setReturnValue(false);
        }
    }
}
