package net.ledok.arenas_ld.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Projectiles don't hit teammates when their team has friendly fire off (run parties, Duels 2v2 partners): arrows,
 * tridents and the like pass through. Spell Engine projectiles are left alone (4.45.0, LeDok 2026-11-22): healing and
 * buff spells (Waterballs, Magical Ballad, Penance, …) must reach allies, and Spell Engine's own target check already
 * keeps their damage off them.
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    @Unique
    private static final ClassValue<Boolean> ARENAS_LD$SPELL_PROJECTILE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                if (c.getName().equals("net.spell_engine.entity.SpellProjectile")) {
                    return true;
                }
            }
            return false;
        }
    };

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void canHitEntity(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Projectile projectile = (Projectile) (Object) this;
        if (ARENAS_LD$SPELL_PROJECTILE.get(projectile.getClass())) {
            return;
        }
        Entity owner = projectile.getOwner();
        if (owner != null && target != null && owner.isAlliedTo(target)
                && owner.getTeam() instanceof PlayerTeam team && !team.isAllowFriendlyFire()) {
            cir.setReturnValue(false);
        }
    }
}
