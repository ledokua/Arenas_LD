package net.ledok.arenas_ld.dungeon.lobby;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.rewards.RunModifierCatalog;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable player lobby state.
 */
public record Lobby(
    UUID lobbyId,
    UUID ownerUuid,
    String ownerName,
    Set<UUID> members,
    Map<UUID, String> memberNames,
    Set<UUID> readyMembers,
    DifficultyTier selectedTier,
    boolean hardcoreEnabled,
    LobbyVisibility visibility,
    LobbyStatus status,
    long createdAtTick,
    /** The run modifiers the owner picked (ids from the config's run_modifiers). */
    Set<String> modifiers,
    /** Minimum level to join or ask to join (0: none); see {@code ArenasApi.setLevelProvider}. Invites skip it. */
    int minLevel
) {
    /** A lobby with no minimum level (most code paths). */
    public Lobby(UUID lobbyId, UUID ownerUuid, String ownerName, Set<UUID> members, Map<UUID, String> memberNames,
                 Set<UUID> readyMembers, DifficultyTier selectedTier, boolean hardcoreEnabled, LobbyVisibility visibility,
                 LobbyStatus status, long createdAtTick, Set<String> modifiers) {
        this(lobbyId, ownerUuid, ownerName, members, memberNames, readyMembers, selectedTier, hardcoreEnabled,
            visibility, status, createdAtTick, modifiers, 0);
    }

    public Lobby(UUID lobbyId, UUID ownerUuid, String ownerName, Set<UUID> members, Map<UUID, String> memberNames,
                 Set<UUID> readyMembers, DifficultyTier selectedTier, boolean hardcoreEnabled, LobbyVisibility visibility,
                 LobbyStatus status, long createdAtTick) {
        this(lobbyId, ownerUuid, ownerName, members, memberNames, readyMembers, selectedTier, hardcoreEnabled,
            visibility, status, createdAtTick, Set.of());
    }

    private static final Codec<Set<UUID>> UUID_SET_CODEC = UUIDUtil.CODEC.listOf()
        .xmap(list -> (Set<UUID>) new HashSet<>(list), set -> new ArrayList<>(set));

    private static final Codec<Lobby> RAW_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("lobbyId").forGetter(Lobby::lobbyId),
            UUIDUtil.CODEC.fieldOf("ownerUuid").forGetter(Lobby::ownerUuid),
            Codec.STRING.fieldOf("ownerName").forGetter(Lobby::ownerName),
            UUID_SET_CODEC.fieldOf("members").forGetter(Lobby::members),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING)
                .fieldOf("memberNames").forGetter(Lobby::memberNames),
            UUID_SET_CODEC.fieldOf("readyMembers").forGetter(Lobby::readyMembers),
            DifficultyTier.CODEC.fieldOf("selectedTier").forGetter(Lobby::selectedTier),
            Codec.BOOL.fieldOf("hardcoreEnabled").forGetter(Lobby::hardcoreEnabled),
            LobbyVisibility.CODEC.fieldOf("visibility").forGetter(Lobby::visibility),
            LobbyStatus.CODEC.fieldOf("status").forGetter(Lobby::status),
            Codec.LONG.fieldOf("createdAtTick").forGetter(Lobby::createdAtTick),
            Codec.STRING.listOf().<Set<String>>xmap(java.util.LinkedHashSet::new, ArrayList::new)
                .optionalFieldOf("modifiers", Set.of()).forGetter(Lobby::modifiers),
            Codec.INT.optionalFieldOf("minLevel", 0).forGetter(Lobby::minLevel)
        ).apply(instance, Lobby::new)
    );

    /** A lobby saved before hardcore became a run modifier (only its flag set) loads with {@code hardcore_run} picked. */
    public static final Codec<Lobby> CODEC = RAW_CODEC.xmap(lobby -> lobby.hardcoreEnabled()
            && !RunModifierCatalog.oneLife(lobby.modifiers())
            ? new Lobby(lobby.lobbyId, lobby.ownerUuid, lobby.ownerName, lobby.members, lobby.memberNames,
                lobby.readyMembers, lobby.selectedTier, true, lobby.visibility, lobby.status, lobby.createdAtTick,
                java.util.Collections.unmodifiableSet(RunModifierCatalog.withLegacyHardcore(lobby.modifiers(), true)), lobby.minLevel)
            : lobby,
        lobby -> lobby);

    public Lobby withMemberAdded(UUID uuid, String name) {
        Set<UUID> newMembers = new HashSet<>(members);
        newMembers.add(uuid);
        Map<UUID, String> newMemberNames = new HashMap<>(memberNames);
        newMemberNames.put(uuid, name);
        return new Lobby(
            lobbyId, ownerUuid, ownerName, newMembers, newMemberNames, readyMembers, selectedTier,
            hardcoreEnabled, visibility, status, createdAtTick, modifiers, minLevel);
    }

    public Lobby withMemberRemoved(UUID uuid) {
        Set<UUID> newMembers = new HashSet<>(members);
        newMembers.remove(uuid);
        Map<UUID, String> newMemberNames = new HashMap<>(memberNames);
        newMemberNames.remove(uuid);
        Set<UUID> newReadyMembers = new HashSet<>(readyMembers);
        newReadyMembers.remove(uuid);
        UUID newOwner = ownerUuid.equals(uuid) ? newMembers.stream().findFirst().orElse(ownerUuid) : ownerUuid;
        String newOwnerName = newOwner.equals(ownerUuid) ? ownerName : newMemberNames.getOrDefault(newOwner, ownerName);
        return new Lobby(
            lobbyId, newOwner, newOwnerName, newMembers, newMemberNames, newReadyMembers, selectedTier,
            hardcoreEnabled, visibility, status, createdAtTick, modifiers, minLevel);
    }

    public Lobby withReady(UUID uuid) {
        Set<UUID> newReadyMembers = new HashSet<>(readyMembers);
        newReadyMembers.add(uuid);
        return new Lobby(
            lobbyId, ownerUuid, ownerName, members, memberNames, newReadyMembers, selectedTier,
            hardcoreEnabled, visibility, status, createdAtTick, modifiers, minLevel);
    }

    public Lobby withUnready(UUID uuid) {
        Set<UUID> newReadyMembers = new HashSet<>(readyMembers);
        newReadyMembers.remove(uuid);
        return new Lobby(
            lobbyId, ownerUuid, ownerName, members, memberNames, newReadyMembers, selectedTier,
            hardcoreEnabled, visibility, status, createdAtTick, modifiers, minLevel);
    }

    public Lobby withOwner(UUID newOwner) {
        return new Lobby(
            lobbyId, newOwner, memberNames.getOrDefault(newOwner, ownerName), members, memberNames, readyMembers,
            selectedTier, hardcoreEnabled, visibility, status, createdAtTick, modifiers, minLevel);
    }

    public Lobby withStatus(LobbyStatus newStatus) {
        return new Lobby(
            lobbyId, ownerUuid, ownerName, members, memberNames, readyMembers, selectedTier,
            hardcoreEnabled, visibility, newStatus, createdAtTick, modifiers, minLevel);
    }

    public Lobby withTier(DifficultyTier tier) {
        return new Lobby(
            lobbyId, ownerUuid, ownerName, members, memberNames, readyMembers, tier,
            hardcoreEnabled, visibility, status, createdAtTick, modifiers, minLevel);
    }

    /** Hardcore on or off: the {@code hardcore_run} modifier (the old hardcore switch and API). */
    public Lobby withHardcore(boolean hardcore) {
        return withModifier(RunModifierCatalog.HARDCORE, hardcore, RunModifierCatalog.sameGroup(RunModifierCatalog.HARDCORE));
    }

    /**
     * With run modifier {@code id} switched on or off; the ones in its exclusive {@code group} go off. The lobby
     * is hardcore while a picked modifier has one life ({@code hardcore_run}).
     */
    public Lobby withModifier(String id, boolean on, java.util.Collection<String> sameGroup) {
        Set<String> next = new java.util.LinkedHashSet<>(modifiers);
        if (on) {
            next.removeAll(sameGroup);
            next.add(id);
        } else {
            next.remove(id);
        }
        return new Lobby(
            lobbyId, ownerUuid, ownerName, members, memberNames, readyMembers, selectedTier,
            RunModifierCatalog.oneLife(next), visibility, status, createdAtTick, java.util.Collections.unmodifiableSet(next), minLevel);
    }

    public Lobby withMinLevel(int level) {
        return new Lobby(lobbyId, ownerUuid, ownerName, members, memberNames, readyMembers, selectedTier,
            hardcoreEnabled, visibility, status, createdAtTick, modifiers, Math.max(0, level));
    }

    /** With exactly these run modifiers (hardcore follows a one-life modifier). */
    public Lobby withModifiers(java.util.Collection<String> ids) {
        Set<String> next = java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(ids));
        return new Lobby(lobbyId, ownerUuid, ownerName, members, memberNames, readyMembers, selectedTier,
            RunModifierCatalog.oneLife(next), visibility, status, createdAtTick, next, minLevel);
    }

    public Lobby withVisibility(LobbyVisibility v) {
        return new Lobby(
            lobbyId, ownerUuid, ownerName, members, memberNames, readyMembers, selectedTier,
            hardcoreEnabled, v, status, createdAtTick, modifiers, minLevel);
    }

    public boolean isFull(int maxSize) {
        return members.size() >= maxSize;
    }

    public boolean isOwner(UUID uuid) {
        return ownerUuid.equals(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean allReady() {
        return !members.isEmpty() && readyMembers.containsAll(members);
    }
}
