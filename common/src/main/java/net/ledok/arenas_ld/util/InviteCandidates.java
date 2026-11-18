package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.activity.ActivityHost;
import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.packet.InviteCandidatesPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The lobby invite picker's list (4.40.3, LeDok): every other online player, with their relation to the owner (from
 * {@link ArenasApi#setPlayerRelations}), whether they're in the party or already invited, and the invite filter's
 * refusal. The client adds the busy states it already knows (in a lobby, in a run).
 */
public final class InviteCandidates {
    /** {@link ArenasApi#setPlayerRelations}; null: nobody is related. */
    public static volatile ArenasApi.@Nullable PlayerRelations relations;

    private InviteCandidates() {
    }

    /** The list for {@code viewer}, owner of a lobby at {@code controller}; null when they own none there. */
    public static @Nullable InviteCandidatesPayload build(ServerPlayer viewer, BlockPos controller) {
        if (!viewer.serverLevel().isLoaded(controller)
            || !(viewer.serverLevel().getBlockEntity(controller) instanceof ActivityHost host)) {
            return null;
        }
        Lobby lobby = null;
        for (Lobby candidate : host.getLobbies()) {
            if (candidate.ownerUuid().equals(viewer.getUUID())) {
                lobby = candidate;
                break;
            }
        }
        if (lobby == null) {
            return null;
        }
        long now = viewer.serverLevel().getGameTime();
        Set<UUID> invited = new HashSet<>();
        for (PendingInvite invite : host.pendingInvites()) {
            if (invite.lobbyId().equals(lobby.lobbyId()) && invite.expiresAtTick() > now) {
                invited.add(invite.invitedUuid());
            }
        }
        ResolvedFilter filter = new ResolvedFilter(host);
        List<InviteCandidatesPayload.Entry> entries = new ArrayList<>();
        for (ServerPlayer other : viewer.server.getPlayerList().getPlayers()) {
            UUID id = other.getUUID();
            if (id.equals(viewer.getUUID())) {
                continue;
            }
            boolean member = lobby.members().contains(id);
            entries.add(new InviteCandidatesPayload.Entry(id, other.getGameProfile().getName(),
                relation(viewer, id).ordinal(), member, invited.contains(id),
                member ? Optional.empty() : Optional.ofNullable(filter.refusal(viewer, id))));
            if (entries.size() >= 256) {
                break;
            }
        }
        return new InviteCandidatesPayload(controller, entries);
    }

    public static ArenasApi.PlayerRelation relation(ServerPlayer viewer, UUID other) {
        ArenasApi.PlayerRelations provider = relations;
        if (provider == null) {
            return ArenasApi.PlayerRelation.NONE;
        }
        try {
            ArenasApi.PlayerRelation relation = provider.relation(viewer, other);
            return relation == null ? ArenasApi.PlayerRelation.NONE : relation;
        } catch (RuntimeException e) {
            ArenasLdMod.LOGGER.warn("[Arenas] The player relations provider failed", e);
            relations = null; // once: don't flood the log for every player
            return ArenasApi.PlayerRelation.NONE;
        }
    }

    /** The invite filter asked without telling anyone (the real invite still asks it and tells the inviter). */
    private record ResolvedFilter(ActivityHost host) {
        @Nullable Component refusal(ServerPlayer viewer, UUID other) {
            ArenasApi.InviteFilter filter = net.ledok.arenas_ld.activity.EntryGates.inviteFilter;
            if (filter == null) {
                return null;
            }
            try {
                var id = host.activityId();
                return filter.refuse(viewer, other, id == null ? "" : id.toString());
            } catch (RuntimeException e) {
                return null;
            }
        }
    }
}
