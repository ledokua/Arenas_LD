package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.stats.SpellContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * Run stats: while a Spell Engine impact runs on a target, its spell is the source of the damage it deals
 * ({@link SpellContext}). Optional: nothing happens without Spell Engine or if its method changes.
 */
@Pseudo
@Mixin(targets = "net.spell_engine.internals.impact.SpellImpacts", remap = false)
public abstract class SpellImpactsMixin {
    @Inject(method = "performImpact", at = @At("HEAD"), require = 0, remap = false)
    private static void arenasLd$spellStart(Level world, LivingEntity caster, Entity target, Holder<?> spell,
                                            @Coerce Object impact, @Coerce Object context, Collection<?> trackers,
                                            CallbackInfoReturnable<Boolean> cir) {
        SpellContext.push(spell);
    }

    @Inject(method = "performImpact", at = @At("RETURN"), require = 0, remap = false)
    private static void arenasLd$spellEnd(Level world, LivingEntity caster, Entity target, Holder<?> spell,
                                          @Coerce Object impact, @Coerce Object context, Collection<?> trackers,
                                          CallbackInfoReturnable<Boolean> cir) {
        SpellContext.pop();
    }
}
