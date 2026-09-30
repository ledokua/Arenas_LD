package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.util.LivingEntityHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExplosionDamageCalculator.class)
public abstract class ExplosionDamageCalculatorMixin {

    /**
     * No friendly fire from explosions (see LivingEntityHooks#suppressExplosionFriendlyFire):
     * a creeper on a no-friendly-fire team never damages its allies, mirroring ProjectileMixin
     * for arrows. Creeper and TNT explosions use this base implementation — only the breeze's
     * wind-charge calculator overrides it, and that one damages nothing anyway.
     */
    @Inject(method = "shouldDamageEntity", at = @At("HEAD"), cancellable = true)
    private void shouldDamageEntity(Explosion explosion, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (LivingEntityHooks.suppressExplosionFriendlyFire(explosion, entity)) {
            cir.setReturnValue(false);
        }
    }
}
