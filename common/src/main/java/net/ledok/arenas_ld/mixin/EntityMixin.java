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
}
