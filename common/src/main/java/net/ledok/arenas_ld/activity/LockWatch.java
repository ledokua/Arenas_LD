package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Remembers the entry-gate locks each viewer's lobby screen last got, so a controller can re-send
 * the screen when a verdict changes on its own (a player levels up with the screen open). One per
 * tiered controller; server thread only.
 */
public final class LockWatch {
    /** How often open screens are re-checked. */
    private static final int CHECK_INTERVAL_TICKS = 40;

    private final Map<UUID, Map<DifficultyTier, Component>> sent = new HashMap<>();

    /** The viewer's locks for a snapshot being built; remembered as what they now see. */
    public Map<DifficultyTier, Component> locksFor(ServerPlayer viewer, ActivityHost host) {
        Map<DifficultyTier, Component> locks = EntryGates.lockedTiers(viewer, host);
        sent.put(viewer.getUUID(), locks);
        return locks;
    }

    /** Every {@value #CHECK_INTERVAL_TICKS} ticks: the viewers whose locks differ from what they were sent. */
    public List<ServerPlayer> changedViewers(ActivityHost host, long gameTime) {
        List<ServerPlayer> changed = new ArrayList<>();
        if (gameTime % CHECK_INTERVAL_TICKS != 0 || !EntryGates.active()) {
            return changed;
        }
        List<ServerPlayer> viewers = LobbyScreens.viewers(host.asBlockEntity());
        Set<UUID> open = new HashSet<>();
        for (ServerPlayer viewer : viewers) {
            open.add(viewer.getUUID());
            if (!EntryGates.lockedTiers(viewer, host).equals(sent.get(viewer.getUUID()))) {
                changed.add(viewer);
            }
        }
        sent.keySet().retainAll(open);
        return changed;
    }
}
