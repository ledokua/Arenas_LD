package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.rewards.RunModifierCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * All of a lobby's settings at once (difficulty, run modifiers, visibility, minimum level), for the hub's create
 * panel and {@code ArenasApi.updateLobby}: checked together, stored with one notice ({@link LobbySettingsChange}).
 */
public final class LobbySettingsApply {
    private LobbySettingsApply() {
    }

    /** The modifier type of an activity ("dungeon", "raid", "arena"). */
    public static String type(ActivityHost host) {
        return host.activityType().name().toLowerCase(Locale.ROOT);
    }

    /**
     * Why these settings can't be used for {@code owner} at {@code host} (lobby or not yet), or null.
     * {@code tier} null = keep / none (arenas have no tiers).
     */
    public static @Nullable Component problem(ServerPlayer owner, ActivityHost host, @Nullable Lobby lobby,
                                              @Nullable DifficultyTier tier, Set<String> modifiers) {
        if (lobby != null) {
            if (!lobby.isOwner(owner.getUUID())) {
                return Component.translatable("message.arenas_ld.lobby.settings.not_owner");
            }
            if (lobby.status() == LobbyStatus.IN_RUN || lobby.status() == LobbyStatus.DISBANDED) {
                return Component.translatable("message.arenas_ld.lobby.settings.running");
            }
        }
        if (tier != null && !host.enabledTiers().isEmpty()) {
            if (!host.enabledTiers().contains(tier)) {
                return Component.translatable("message.arenas_ld.lobby.tier_disabled");
            }
            Component gate = EntryGates.check(owner, host, tier);
            if (gate != null) {
                return gate;
            }
        }
        var selectable = RunModifierCatalog.selectable(type(host));
        List<String> picked = new ArrayList<>(modifiers);
        for (int i = 0; i < picked.size(); i++) {
            String id = picked.get(i);
            if (!selectable.containsKey(id)) {
                return Component.translatable("message.arenas_ld.lobby.settings.unknown_modifier", id);
            }
            for (int j = i + 1; j < picked.size(); j++) {
                String other = picked.get(j);
                if (RunModifierCatalog.conflicts(id).contains(other) || RunModifierCatalog.sameGroup(id).contains(other)) {
                    return Component.translatable("message.arenas_ld.lobby.settings.conflict",
                        RunModifierCatalog.displayName(id), RunModifierCatalog.displayName(other));
                }
            }
        }
        return null;
    }

    /** {@code lobby} with the new settings ({@code tier} null keeps its tier). */
    public static Lobby build(Lobby lobby, @Nullable DifficultyTier tier, Set<String> modifiers,
                              LobbyVisibility visibility, int minLevel) {
        Lobby next = lobby.withModifiers(modifiers).withVisibility(visibility).withMinLevel(minLevel);
        return tier != null ? next.withTier(tier) : next;
    }
}
