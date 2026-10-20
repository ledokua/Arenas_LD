package net.ledok.arenas_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ledok.arenas_ld.util.RunMinions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Run minions ({@link RunMinions}): a run mob being hurt (or dying of it) acts for itself, so
 * minions it spawns from {@code LivingEntity.hurt} or its death are recognized; minions give no
 * experience. Wrapped, so a damage-cancelling listener can't leave the frame behind.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMinionMixin {

    @WrapMethod(method = "hurt")
    private boolean arenas_ld$hurtAsRunMob(DamageSource source, float amount, Operation<Boolean> original) {
        RunMinions.Frame frame = RunMinions.pushHurt((LivingEntity) (Object) this);
        try {
            return original.call(source, amount);
        } finally {
            RunMinions.pop(frame);
        }
    }

    @Inject(method = "getExperienceReward", at = @At("HEAD"), cancellable = true)
    private void arenas_ld$minionsGiveNoExperience(ServerLevel level, @Nullable Entity killer,
                                                   CallbackInfoReturnable<Integer> cir) {
        if (RunMinions.dropsNoExperience((LivingEntity) (Object) this)) {
            cir.setReturnValue(0);
        }
    }
}
