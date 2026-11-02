package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.util.DownedSpectators;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The spectator menu's teleport for downed run players ({@link DownedSpectators}): only a living
 * teammate, who becomes their camera; anyone else is refused. Injected after
 * {@code PacketUtils.ensureRunningOnSameThread}, which throws on the netty thread, so this runs on
 * the server thread only.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplDownedMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleTeleportToEntityPacket", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
        shift = At.Shift.AFTER), cancellable = true)
    private void arenas_ld$downedTeleport(ServerboundTeleportToEntityPacket packet, CallbackInfo ci) {
        if (!player.isSpectator() || !DownedSpectators.isRestricted(player)) {
            return;
        }
        Entity target = null;
        for (ServerLevel level : player.server.getAllLevels()) {
            target = packet.getEntity(level);
            if (target != null) {
                break;
            }
        }
        DownedSpectators.spectateRequest(player, target);
        ci.cancel();
    }
}
