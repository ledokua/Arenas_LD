package net.ledok.arenas_ld.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void canHitEntity(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Projectile projectile = (Projectile) (Object) this;
        Entity owner = projectile.getOwner();

        if (owner != null && target != null) {
            if (owner.isAlliedTo(target)) {
                if (owner.getTeam() instanceof PlayerTeam team && !team.isAllowFriendlyFire()) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
