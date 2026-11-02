package net.ledok.arenas_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ledok.arenas_ld.util.RunLeftovers;
import net.ledok.arenas_ld.util.RunMinions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Run minions ({@link RunMinions}): an entity ticks acting for its run mob, so the mobs it spawns
 * are recognized; a new mob is adopted when a run mob spawned it; an orphaned minion vanishes.
 * The ticks are wrapped (not HEAD/RETURN injects) so the frame is always taken off again, even
 * when another mod cancels the tick (Immersive Optimization skips far entities' ticks) or it throws.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMinionMixin {

    @WrapMethod(method = "tickNonPassenger")
    private void arenas_ld$tickAsRunMob(Entity entity, Operation<Void> original) {
        if (RunMinions.vanishIfOrphaned((ServerLevel) (Object) this, entity)) {
            return;
        }
        RunMinions.Frame frame = RunMinions.push(entity);
        try {
            original.call(entity);
        } finally {
            RunMinions.pop(frame);
        }
    }

    @WrapMethod(method = "tickPassenger")
    private void arenas_ld$tickPassengerAsRunMob(Entity vehicle, Entity passenger, Operation<Void> original) {
        if (RunMinions.vanishIfOrphaned((ServerLevel) (Object) this, passenger)) {
            return;
        }
        RunMinions.Frame frame = RunMinions.push(passenger);
        try {
            original.call(vehicle, passenger);
        } finally {
            RunMinions.pop(frame);
        }
    }

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void arenas_ld$adoptMinion(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!RunMinions.onAdd((ServerLevel) (Object) this, entity)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * A fresh entity made it into the world: a minion joins its summoner's team, and something a
     * run player owns (a summon, projectile, pet, thrown item) is tracked for their run's cleanup
     * ({@link RunLeftovers}). Never called for entities loading from disk with their chunk.
     */
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void arenas_ld$minionAdded(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        boolean added = cir.getReturnValueZ();
        RunMinions.afterAdd(level, entity, added);
        if (added) {
            RunLeftovers.onFreshEntity(level, entity);
        }
    }
}
