package net.ledok.arenas_ld.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity attacker = source.getEntity();
        if (attacker != null) {
            LivingEntity victim = (LivingEntity) (Object) this;
            if (victim.getTeam() != null && victim.isAlliedTo(attacker)) {
                PlayerTeam team = (PlayerTeam) victim.getTeam();
                if (!team.isAllowFriendlyFire()) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
