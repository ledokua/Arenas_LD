package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → a player: the lobby they are in (activity name, members of the max, how many are ready), for the
 * HUD badge; {@link #NONE} when they are in none, or their lobby's run has started.
 */
public record LobbyBadgePayload(boolean inLobby, Component activity, int members, int maxMembers, int ready)
    implements CustomPacketPayload {
    public static final LobbyBadgePayload NONE = new LobbyBadgePayload(false, Component.empty(), 0, 0, 0);

    public static final Type<LobbyBadgePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "lobby_badge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LobbyBadgePayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBoolean(p.inLobby());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.activity());
            buf.writeVarInt(p.members());
            buf.writeVarInt(p.maxMembers());
            buf.writeVarInt(p.ready());
        },
        buf -> new LobbyBadgePayload(buf.readBoolean(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
