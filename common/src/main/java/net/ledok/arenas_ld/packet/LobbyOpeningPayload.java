package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent right before a lobby menu opens: its activity, for a hub that hosts the view (4.35.0); the activity's display
 * name for the hub's title ("" = the controller's own name) and whether to open on the Leaderboard tab (4.43.2).
 */
public record LobbyOpeningPayload(ResourceLocation activity, String name, boolean leaderboard) implements CustomPacketPayload {
    public static final Type<LobbyOpeningPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "lobby_opening"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LobbyOpeningPayload> STREAM_CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC, LobbyOpeningPayload::activity,
        ByteBufCodecs.STRING_UTF8, LobbyOpeningPayload::name,
        ByteBufCodecs.BOOL, LobbyOpeningPayload::leaderboard,
        LobbyOpeningPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
