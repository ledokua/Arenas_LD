package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** No-argument lobby action. {@code action} is one of: create, leave, ready, start. */
public record ArenaLobbyActionPayload(BlockPos controllerPos, String action) implements CustomPacketPayload {
    public static final Type<ArenaLobbyActionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_lobby_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaLobbyActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBlockPos(p.controllerPos()); buf.writeUtf(p.action()); },
        buf -> new ArenaLobbyActionPayload(buf.readBlockPos(), buf.readUtf()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
