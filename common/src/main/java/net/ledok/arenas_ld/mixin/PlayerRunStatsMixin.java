package net.ledok.arenas_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ledok.arenas_ld.stats.RunStatsHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Players override {@code actuallyHurt} without calling LivingEntity's: the run stats hooks for their HP lost. */
@Mixin(Player.class)
public abstract class PlayerRunStatsMixin {
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void arenasLd$beforeHurt(DamageSource source, float amount, CallbackInfo ci) {
        ((RunStatsHolder) this).arenasLd$hurtStart(amount);
    }

    /** Run event log (4.37.1): what the hit really takes off health, after every mod's reductions. */
    @WrapOperation(method = "actuallyHurt", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
    private void arenasLd$applied(Player self, float health, Operation<Void> original) {
        if (((RunStatsHolder) self).arenasLd$runStats() != null) {
            net.ledok.arenas_ld.stats.RunEvents.applied(self, self.getHealth() - health);
        }
        original.call(self, health);
    }

    // RETURN, not TAIL: a hit fully soaked by absorption returns early.
    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void arenasLd$afterHurt(DamageSource source, float amount, CallbackInfo ci) {
        ((RunStatsHolder) this).arenasLd$hurtEnd(source);
    }
}
