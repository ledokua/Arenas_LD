package net.ledok.arenas_ld.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ledok.arenas_ld.stats.RunStats;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * FloraFare's {@code regenMode "curve"} (the Fallen Eden server's) turns vanilla's natural heals in
 * {@code FoodData#tick} off and heals in {@code NaturalRegeneration#curveHeal} instead, so
 * {@link net.ledok.arenas_ld.mixin.FoodDataRunStatsMixin} never saw it and hunger regen counted as healing in run
 * stats. This marks that heal as natural regen too (4.44.0; found by Eden_LD, whose 1.3.129 hooks the same call).
 * Named as in production (intermediary, no refmap), so it only applies there; nothing without FloraFare.
 */
@Pseudo
@Mixin(targets = "net.tend1tnuy.florafare.food.NaturalRegeneration", remap = false)
abstract class FloraFareCurveRegenMixin {

    @WrapOperation(method = "curveHeal", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1657;method_6025(F)V", remap = false),
            remap = false, require = 0)
    private static void arenasLd$naturalRegen(Player player, float amount, Operation<Void> original) {
        RunStats.naturalRegen(true);
        try {
            original.call(player, amount);
        } finally {
            RunStats.naturalRegen(false);
        }
    }
}
