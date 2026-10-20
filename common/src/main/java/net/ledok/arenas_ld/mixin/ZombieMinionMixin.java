package net.ledok.arenas_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ledok.arenas_ld.util.RunMinions;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Run minions: a zombie calls its reinforcements in its own {@code hurt}, after
 * {@code LivingEntity.hurt} returns, so that override acts for the zombie too.
 */
@Mixin(Zombie.class)
public abstract class ZombieMinionMixin {

    @WrapMethod(method = "hurt")
    private boolean arenas_ld$reinforcementsAreMinions(DamageSource source, float amount, Operation<Boolean> original) {
        RunMinions.Frame frame = RunMinions.pushHurt((Zombie) (Object) this);
        try {
            return original.call(source, amount);
        } finally {
            RunMinions.pop(frame);
        }
    }
}
