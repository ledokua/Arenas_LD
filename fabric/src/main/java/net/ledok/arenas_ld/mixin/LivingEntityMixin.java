package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.util.LivingEntityHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric-only driver for {@link LivingEntityHooks}. On NeoForge the same hooks
 * are driven by events instead (see the neoforge module), because NeoForge
 * patches these LivingEntity methods heavily.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Unique
    private DamageSource arenasLd$currentDamageSource;

    @Inject(method = "hurt", at = @At("HEAD"))
    private void arenasLd$captureDamageSource(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        this.arenasLd$currentDamageSource = source;
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void arenasLd$clearDamageSource(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        this.arenasLd$currentDamageSource = null;
    }

    @ModifyArg(
            method = "hurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"),
            index = 1
    )
    private float arenasLd$applyDungeonTierDamageMultiplier(float amount) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return amount;
        }
        return LivingEntityHooks.modifyPlayerHurtAmount(player, this.arenasLd$currentDamageSource, amount);
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void onSetHealth(float health, CallbackInfo ci) {
        if (health <= 0.0F && (Object) this instanceof ServerPlayer player
                && LivingEntityHooks.handlePlayerLethal(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void onDie(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && LivingEntityHooks.handlePlayerLethal(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "dropFromLootTable", at = @At("HEAD"), cancellable = true)
    private void arenasLd$suppressNaturalLoot(DamageSource damageSource, boolean hitByPlayer, CallbackInfo ci) {
        if (LivingEntityHooks.suppressNaturalLoot((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
