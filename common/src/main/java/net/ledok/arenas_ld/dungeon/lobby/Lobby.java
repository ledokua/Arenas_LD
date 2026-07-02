package net.ledok.arenas_ld.dungeon.lobby;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
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
    long createdAtTick
) {
    private static final Codec<Set<UUID>> UUID_SET_CODEC = UUIDUtil.CODEC.listOf()
        .xmap(list -> (Set<UUID>) new HashSet<>(list), set -> new ArrayList<>(set));

    public static final Codec<Lobby> CODEC = RecordCodecBuilder.create(instance ->
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
            Codec.LONG.fieldOf("createdAtTick").forGetter(Lobby::createdAtTick)
        ).apply(instance, Lobby::new)
    );

    public Lobby withMemberAdded(UUID uuid, String name) {
        Set<UUID> newMembers = new HashSet<>(members);
        newMembers.add(uuid);
        Map<UUID, String> newMemberNames = new HashMap<>(memberNames);
        newMemberNames.put(uuid, name);
        return new Lobby(
            lobbyId, ownerUuid, ownerName, newMembers, newMemberNames, readyMembers, selectedTier,
            hardcoreEnabled, visibility, status, createdAtTick
        );
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
            hardcoreEnabled, visibility, status, createdAtTick
        );
    }

    public Lobby withReady(UUID uuid) {
        Set<UUID> newReadyMembers = new HashSet<>(readyMembers);
        newReadyMembers.add(uuid);
        return new Lobby(
            lobbyId, ownerUuid, ownerName, members, memberNames, newReadyMembers, selectedTier,
            hardcoreEnabled, visibility, status, createdAtTick
        );
    }

    public Lobby withUnready(UUID uuid) {
        Set<UUID> newReadyMembers = new HashSet<>(readyMembers);
        newReadyMembers.remove(uuid);
        return new Lobby(
            lobbyId, ownerUuid, ownerName, members, memberNames, newReadyMembers, selectedTier,
            hardcoreEnabled, visibility, status, createdAtTick
        );
    }

    public Lobby withOwner(UUID newOwner) {
        return new Lobby(
            lobbyId, newOwner, memberNames.getOrDefault(newOwner, ownerName), members, memberNames, readyMembers,
            selectedTier, hardcoreEnabled, visibility, status, createdAtTick
        );
    }

    public Lobby withStatus(LobbyStatus newStatus) {
        return new Lobby(
            lobbyId, ownerUuid, ownerName, members, memberNames, readyMembers, selectedTier,
            hardcoreEnabled, visibility, newStatus, createdAtTick
        );
    }

    public Lobby withTier(DifficultyTier tier) {
        return new Lobby(
            lobbyId, ownerUuid, ownerName, members, memberNames, readyMembers, tier,
            hardcoreEnabled, visibility, status, createdAtTick
        );
    }

    public Lobby withHardcore(boolean hardcore) {
        return new Lobby(
            lobbyId, ownerUuid, ownerName, members, memberNames, readyMembers, selectedTier,
            hardcore, visibility, status, createdAtTick
        );
    }

    public Lobby withVisibility(LobbyVisibility v) {
        return new Lobby(
            lobbyId, ownerUuid, ownerName, members, memberNames, readyMembers, selectedTier,
            hardcoreEnabled, v, status, createdAtTick
        );
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
