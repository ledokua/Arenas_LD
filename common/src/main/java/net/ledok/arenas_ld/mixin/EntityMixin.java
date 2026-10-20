package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.util.LivingEntityHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    /**
     * Explosions caused by run-spawned mobs never destroy blocks (see
     * LivingEntityHooks#suppressExplosionBlockDamage). Explosions with a source entity route
     * their per-block decision through the source's shouldBlockExplode, so vetoing here covers
     * creepers, ghast fireballs and wither skulls on both loaders.
     */
    @Inject(method = "shouldBlockExplode", at = @At("HEAD"), cancellable = true)
    private void shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state,
                                    float power, CallbackInfoReturnable<Boolean> cir) {
        if (LivingEntityHooks.suppressExplosionBlockDamage(explosion)) {
            cir.setReturnValue(false);
        }
    }

    /** A minion read back from disk is remembered with its summoner again (see RunMinions). */
    @Inject(method = "load", at = @At("TAIL"))
    private void arenas_ld$minionLoaded(net.minecraft.nbt.CompoundTag tag, CallbackInfo ci) {
        net.ledok.arenas_ld.util.RunMinions.onLoaded((Entity) (Object) this);
    }

    /** A run mob killed or discarded takes its minions with it (see RunMinions). */
    @Inject(method = "remove", at = @At("HEAD"))
    private void arenas_ld$minionsLeaveWithSummoner(Entity.RemovalReason reason, CallbackInfo ci) {
        net.ledok.arenas_ld.util.RunMinions.onRemoved((Entity) (Object) this, reason);
    }
}
