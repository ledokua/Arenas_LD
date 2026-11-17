package net.ledok.arenas_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ledok.arenas_ld.stats.RunStats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Run stats, healing by source: vanilla instant health (potions, splash, clouds) heals as "effect:minecraft:instant_health". */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect") // package-private
public abstract class InstantHealContextMixin {
    @WrapMethod(method = "applyInstantenousEffect")
    private void arenasLd$healContext(Entity source, Entity indirect, LivingEntity entity, int amplifier, double health,
                                      Operation<Void> original) {
        String previous = RunStats.enterHealContext(entity,
            BuiltInRegistries.MOB_EFFECT.wrapAsHolder((MobEffect) (Object) this));
        try {
            original.call(source, indirect, entity, amplifier, health);
        } finally {
            RunStats.exitHealContext(previous);
        }
    }
}
