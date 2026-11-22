package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server → a player taken out of their lobby at {@code activity} by someone else (4.43.1): kicked by the owner. The
 * client shows a short notice and, in a hub-hosted view, closes it so the hub goes back to its lobby list (as after
 * «Leave»). A run start never sends it.
 */
public record LobbyRemovedPayload(ResourceLocation activity, UUID lobbyId, boolean kicked) implements CustomPacketPayload {
    public static final Type<LobbyRemovedPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "lobby_removed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LobbyRemovedPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeResourceLocation(p.activity());
            buf.writeUUID(p.lobbyId());
            buf.writeBoolean(p.kicked());
        },
        buf -> new LobbyRemovedPayload(buf.readResourceLocation(), buf.readUUID(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Tells {@code player} (if online) they were kicked from the lobby at {@code activity}. */
    public static void kicked(net.minecraft.server.MinecraftServer server, UUID player, @Nullable ResourceLocation activity,
                              UUID lobbyId) {
        ServerPlayer target = server.getPlayerList().getPlayer(player);
        if (target == null) {
            return;
        }
        if (activity != null) {
            net.ledok.arenas_ld.platform.ArenasNetwork.sendToPlayer(target, new LobbyRemovedPayload(activity, lobbyId, true));
        } else {
            // A controller without an activity id: no hub view to match, so the action bar says it.
            target.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.arenas_ld.lobby.removed.kicked"), true);
        }
    }
}
