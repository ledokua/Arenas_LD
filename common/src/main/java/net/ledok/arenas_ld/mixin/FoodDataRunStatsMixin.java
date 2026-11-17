package net.ledok.arenas_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ledok.arenas_ld.stats.RunStats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Regeneration from a full hunger bar isn't counted as healing in run stats (LeDok). */
@Mixin(FoodData.class)
public abstract class FoodDataRunStatsMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void arenasLd$naturalRegen(Player player, float amount, Operation<Void> original) {
        RunStats.naturalRegen(true);
        try {
            original.call(player, amount);
        } finally {
            RunStats.naturalRegen(false);
        }
    }
}
