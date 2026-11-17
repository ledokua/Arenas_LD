package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.util.RespawnShield;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A melee swing ends the respawn shield ({@link RespawnShield}), even one that misses. */
@Mixin(Player.class)
public abstract class PlayerAttackShieldMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void arenasLd$endShield(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            RespawnShield.attacked(player);
        }
    }
}
