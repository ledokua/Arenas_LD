package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Lobby screen → server: the owner switches a run modifier on or off (any controller type). The server
 * checks ownership, the lobby's state and that the modifier may be picked there.
 */
public record SetLobbyModifierPayload(BlockPos controllerPos, String modifier, boolean on) implements CustomPacketPayload {
    public static final Type<SetLobbyModifierPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "set_lobby_modifier"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetLobbyModifierPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.controllerPos());
            buf.writeUtf(p.modifier(), 64);
            buf.writeBoolean(p.on());
        },
        buf -> new SetLobbyModifierPayload(buf.readBlockPos(), buf.readUtf(64), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
