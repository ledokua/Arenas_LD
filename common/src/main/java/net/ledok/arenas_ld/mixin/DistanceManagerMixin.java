package net.ledok.arenas_ld.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removing a player from a chunk they aren't registered in throws (vanilla reads a missing set). In
 * {@code ChunkMap.move} that happens after the player's section was already updated, so every later move sees
 * "no change" and the player never gets chunk tickets or chunks again until they rejoin (seen on live after a
 * run ended and its players were sent home, and once without a run). Nothing to remove there: skip it, so the
 * move goes on to register the player where they are now.
 */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin {
    @Shadow @Final Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk;

    @Inject(method = "removePlayer", at = @At("HEAD"), cancellable = true)
    private void arenasLd$skipUnregistered(SectionPos section, ServerPlayer player, CallbackInfo ci) {
        if (playersPerChunk.get(section.chunk().toLong()) == null) {
            ArenasLdMod.LOGGER.warn("[Arenas] {} was not registered in chunk {} of {}; skipped the removal so their chunks reload",
                player.getScoreboardName(), section.chunk(), player.level().dimension().location());
            ci.cancel();
        }
    }
}
