package net.ledok.arenas_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ledok.arenas_ld.stats.RunStats;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Run stats, healing by source: an effect's tick (regeneration, modded heal-over-time) heals as "effect:<id>". Wraps the
 * call in {@code MobEffectInstance.tick}, since effects override {@code applyEffectTick} without calling super. For
 * anything but a run player the label isn't even built.
 */
@Mixin(MobEffectInstance.class)
public abstract class MobEffectHealContextMixin {
    @Shadow @Final private Holder<MobEffect> effect;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/effect/MobEffect;applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)Z"))
    private boolean arenasLd$healContext(MobEffect mobEffect, LivingEntity entity, int amplifier, Operation<Boolean> original) {
        String previous = RunStats.enterHealContext(entity, effect);
        try {
            return original.call(mobEffect, entity, amplifier);
        } finally {
            RunStats.exitHealContext(previous);
        }
    }
}
