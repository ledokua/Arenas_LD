package net.ledok.arenas_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ledok.arenas_ld.util.LivingEntityHooks;
import net.ledok.arenas_ld.util.RunMinions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Mob.class)
public abstract class MobMixin {

    /**
     * Cancel vanilla conversions for run-spawned mobs (see LivingEntityHooks#blockMobConversion).
     * convertTo is @Nullable and every vanilla caller handles the null return, so cancelling here
     * simply leaves the original mob as it is. Any other mob converting during a run mob's turn (a
     * villager a run zombie killed) converts behind a barrier, so the result isn't taken as a minion.
     */
    @WrapMethod(method = "convertTo")
    private <T extends Mob> T convertTo(EntityType<T> type, boolean keepEquipment, Operation<T> original) {
        if (LivingEntityHooks.blockMobConversion((Mob) (Object) this)) {
            return null;
        }
        RunMinions.Frame frame = RunMinions.pushBarrier((Mob) (Object) this);
        try {
            return original.call(type, keepEquipment);
        } finally {
            RunMinions.pop(frame);
        }
    }
}
