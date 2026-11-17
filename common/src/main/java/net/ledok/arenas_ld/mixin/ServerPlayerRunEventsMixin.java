package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.stats.RunEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The run event log's hit window (4.37.0): from the outermost {@code hurt} of a run player to its return, so the hit's
 * row gets what fired inside it (Spell Engine passives such as Cheat Death, a dodge, a shield) and what it caused comes
 * after it. Players outside runs cost one null check.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRunEventsMixin {
    @Inject(method = "hurt", at = @At("HEAD"))
    private void arenasLd$hitStart(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        RunEvents.hurtStart((ServerPlayer) (Object) this, source, amount);
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void arenasLd$hitEnd(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        RunEvents.hurtEnd((ServerPlayer) (Object) this);
    }
}
