package net.ledok.arenas_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ledok.arenas_ld.util.RunSplits;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Split run slimes ({@link RunSplits}): while a run slime's {@code remove} runs, the parts it adds are recognized as
 * its parts. Wrapped (not HEAD/RETURN) so the mark always comes off, and so it works however the loader adds the
 * parts (NeoForge collects them for its split event first).
 */
@Mixin(Slime.class)
public abstract class SlimeSplitMixin {

    @WrapMethod(method = "remove")
    private void arenas_ld$splitAsRunMob(Entity.RemovalReason reason, Operation<Void> original) {
        Slime self = (Slime) (Object) this;
        RunSplits.enterRemove(self);
        try {
            original.call(reason);
        } finally {
            RunSplits.exitRemove(self);
        }
    }
}
