package net.ledok.arenas_ld.screen.lobby;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.ledok.arenas_ld.rewards.RunModifierOption;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What {@link LobbyView} needs from a dungeon, raid or arena controller screen: its menu data, mapped onto one shape,
 * and its C2S payloads as actions. Each controller screen implements this (UI rework, D6).
 */
public interface LobbyHost {
    enum Kind { DUNGEON, RAID, ARENA }

    enum InviteStatus { ONLINE, IN_LOBBY, IN_RUN, BUSY }

    /** One instance as of the last snapshot ({@link #snapshotTick}). */
    record Instance(InstanceStatus status, int cooldownTicks) {
    }

    Kind kind();

    BlockPos controllerPos();

    /** The screen's title (the controller's name). */
    Component title();

    /** Extra header facts (run modifiers on this activity, arena max wave and requirement); may be empty. */
    List<Component> meta();

    List<Lobby> visibleLobbies();

    Optional<Lobby> ownLobby();

    List<PendingInvite> invites();

    List<PendingJoinRequest> joinRequests();

    int maxPartySize();

    /** 1-based place in the queue of the own lobby, 0 when not queued. */
    int queuePosition();

    /** Seconds until an instance is free (0: unknown or none). */
    int etaSeconds();

    /** The server tick now, estimated from the last snapshot. */
    long serverTick();

    /** The tick the instances were sent at (their cooldowns count down from it). */
    long snapshotTick();

    /** The instances for the strip; null for none (dungeons). */
    @Nullable List<Instance> instances();

    /** Whether the activity has difficulty tiers (dungeon, raid). */
    boolean hasTiers();

    boolean tierEnabled(DifficultyTier tier);

    /** The entry gate's reason the player can't pick {@code tier}, or null. */
    @Nullable Component tierLock(DifficultyTier tier);

    /** The requirement the player meets for {@code tier} (shown in green), or null. */
    @Nullable Component tierMet(DifficultyTier tier);

    List<RunModifierOption> modifierOptions();

    /** The "Modifiers: …" line of the own lobby (may be empty). */
    Component runModifiersLine();

    /** The top runs for {@code tier} (null for arenas, which rank by wave). */
    List<LeaderboardEntry> leaderboard(@Nullable DifficultyTier tier);

    /** Busy players (dungeon, raid); empty for arenas, which use {@link #inviteStatus} instead. */
    default Set<UUID> busyPlayers() {
        return Set.of();
    }

    /** What an invite candidate is doing. */
    default InviteStatus inviteStatus(UUID player) {
        if (busyPlayers().contains(player)) {
            return InviteStatus.BUSY;
        }
        for (Lobby lobby : visibleLobbies()) {
            if (lobby.members().contains(player)) {
                return lobby.status() == LobbyStatus.IN_RUN ? InviteStatus.IN_RUN : InviteStatus.IN_LOBBY;
            }
        }
        return InviteStatus.ONLINE;
    }

    /** Raid: the queue banner offers the owner Start again. */
    default boolean queueStartButton() {
        return false;
    }

    /** Raid and arena: a queued lobby can't press the footer's Start. */
    default boolean startNeedsNoQueue() {
        return kind() != Kind.DUNGEON;
    }

    // ── actions (the controller's own payloads) ──────────────────────────────

    void create();

    void join(UUID lobbyId);

    void request(UUID lobbyId);

    void acceptInvite(UUID lobbyId);

    void declineInvite(UUID lobbyId);

    void acceptRequest(UUID requester);

    void declineRequest(UUID requester);

    void invite(UUID player);

    void kick(UUID member);

    void toggleReady();

    void leave();

    void start();

    void setTier(DifficultyTier tier);

    void setVisibility(LobbyVisibility visibility);

    void close();
}
