package net.ledok.arenas_ld.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.ledok.arenas_ld.util.RunEffectsClientState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * "Frozen ground": the local player's walking uses ice friction. Movement is simulated by the client,
 * so only the client side does anything ({@link RunEffectsClientState}). Vanilla (Fabric) reads
 * {@code Block.getFriction()}, NeoForge its {@code BlockState.getFriction(level, pos, entity)}; each
 * injector is optional, so the one that matches the loader applies.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFrictionMixin {
    @Unique
    private static final float ARENAS_LD$ICE = 0.98F;

    @ModifyExpressionValue(method = "travel", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float arenasLd$frozenVanilla(float friction) {
        return arenasLd$frozen() ? ARENAS_LD$ICE : friction;
    }

    @ModifyExpressionValue(method = "travel", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F"))
    private float arenasLd$frozenNeoForge(float friction) {
        return arenasLd$frozen() ? ARENAS_LD$ICE : friction;
    }

    @Unique
    private boolean arenasLd$frozen() {
        return RunEffectsClientState.frozenGround && (Object) this instanceof Player player && player.level().isClientSide();
    }
}
