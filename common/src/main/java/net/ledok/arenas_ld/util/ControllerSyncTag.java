package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The copy of a dungeon/raid/arena controller sent to clients in chunk data and block updates.
 *
 * <p>Controllers save everything into one {@code State} compound, and vanilla's default update tag
 * is that full save — so every client tracking the controller's (force-loaded) chunk received each
 * active run with its participants' return points (where they were standing, i.e. their bases),
 * lobby rosters, invites and the tier configs with their reward commands. Clients never read any of
 * it from the block entity: lobby and admin screens get their data from menu payloads, and the
 * Dungeon Tool overlay only needs the instance list. The redacted keys keep their tag type with no
 * content, so required codec fields still parse on the client.
 */
public final class ControllerSyncTag {
    private static final String[] SERVER_ONLY = {
        "activeRuns", "lobbies", "pendingInvites", "pendingJoinRequests", "queuedLobbyIds",
        "lobbyOfflineSinceTicks", "tierConfigs"
    };

    private ControllerSyncTag() {
    }

    /** Empties the server-only entries of {@code fullSave}'s State compound, in place; returns it. */
    public static CompoundTag redact(CompoundTag fullSave) {
        if (!(fullSave.get("State") instanceof CompoundTag state)) {
            return fullSave;
        }
        for (String key : SERVER_ONLY) {
            Tag value = state.get(key);
            if (value instanceof ListTag) {
                state.put(key, new ListTag());
            } else if (value instanceof CompoundTag) {
                state.put(key, new CompoundTag());
            }
        }
        return fullSave;
    }
}
