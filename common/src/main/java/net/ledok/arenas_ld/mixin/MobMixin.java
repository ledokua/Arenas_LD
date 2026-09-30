package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.util.LivingEntityHooks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobMixin {

    /**
     * Cancel vanilla conversions for run-spawned mobs (see LivingEntityHooks#blockMobConversion).
     * convertTo is @Nullable and every vanilla caller handles the null return, so cancelling here
     * simply leaves the original mob as it is.
     */
    @Inject(method = "convertTo", at = @At("HEAD"), cancellable = true)
    private void convertTo(EntityType<?> type, boolean keepEquipment, CallbackInfoReturnable<Mob> cir) {
        if (LivingEntityHooks.blockMobConversion((Mob) (Object) this)) {
            cir.setReturnValue(null);
        }
    }
}
