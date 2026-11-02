package net.ledok.arenas_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ledok.arenas_ld.util.DownedSpectators;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Downed run players watch their party ({@link DownedSpectators}): sneaking switches to the next
 * teammate instead of leaving the camera, and clicking an entity only spectates a living teammate.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDownedMixin {

    /** {@code tick}: a spectator with another camera leaves it when sneaking. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;wantsToStopRiding()Z"))
    private boolean arenas_ld$downedKeepsCamera(ServerPlayer self, Operation<Boolean> original) {
        return DownedSpectators.wantsToLeaveCamera(self, original.call(self));
    }

    /** A spectator's attack (an entity click) makes that entity their camera. */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void arenas_ld$downedSpectate(Entity target, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.isSpectator() && DownedSpectators.spectateRequest(self, target)) {
            ci.cancel();
        }
    }
}
