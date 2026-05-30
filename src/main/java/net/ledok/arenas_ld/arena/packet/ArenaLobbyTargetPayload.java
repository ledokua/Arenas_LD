package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Lobby action targeting another id. {@code action} is one of: join, request, accept_invite,
 * decline_invite, accept_request, decline_request, invite, kick. {@code target} is a lobby id
 * (join/request/*_invite) or a player uuid (invite/kick/*_request).
 */
public record ArenaLobbyTargetPayload(BlockPos controllerPos, String action, UUID target) implements CustomPacketPayload {
    public static final Type<ArenaLobbyTargetPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_lobby_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaLobbyTargetPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBlockPos(p.controllerPos()); buf.writeUtf(p.action()); buf.writeUUID(p.target()); },
        buf -> new ArenaLobbyTargetPayload(buf.readBlockPos(), buf.readUtf(), buf.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
