package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.event.PlayerDeathHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow protected abstract float getDamageAfterArmorAbsorb(DamageSource damageSource, float f);
    @Shadow protected abstract float getDamageAfterMagicAbsorb(DamageSource damageSource, float f);

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof ServerPlayer player) {
            if (isDamageFatal(player, source, amount)) {
                if (PlayerDeathHandler.onPlayerDeath(player, source)) {
                    cir.setReturnValue(false); // Cancel the damage
                }
            }
        }
    }

    private boolean isDamageFatal(LivingEntity entity, DamageSource source, float amount) {
        if (!entity.isInvulnerableTo(source)) {
            float f = amount;
            f = this.getDamageAfterArmorAbsorb(source, f);
            f = this.getDamageAfterMagicAbsorb(source, f);
            
            float g = Math.max(f - entity.getHealth(), 0.0F);
            return g > 0;
        }
        return false;
    }
}
