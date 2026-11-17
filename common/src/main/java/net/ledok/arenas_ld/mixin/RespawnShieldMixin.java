package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.util.RespawnShield;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The short invulnerability after a run respawn ({@link RespawnShield}); players reach it through super.hurt. */
@Mixin(LivingEntity.class)
public abstract class RespawnShieldMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void arenasLd$respawnShield(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (RespawnShield.blocks((LivingEntity) (Object) this, source)) {
            cir.setReturnValue(false);
        }
    }
}
