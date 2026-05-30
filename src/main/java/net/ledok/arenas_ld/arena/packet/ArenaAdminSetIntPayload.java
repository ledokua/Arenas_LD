package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Admin int setting. {@code key}: maxPartySize, respawnTime, cooldown, closeTimer, inviteExpiry, deathPenalty, maxWave. */
public record ArenaAdminSetIntPayload(BlockPos controllerPos, String key, int value) implements CustomPacketPayload {
    public static final Type<ArenaAdminSetIntPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_admin_set_int"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaAdminSetIntPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBlockPos(p.controllerPos()); buf.writeUtf(p.key()); buf.writeVarInt(p.value()); },
        buf -> new ArenaAdminSetIntPayload(buf.readBlockPos(), buf.readUtf(), buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
