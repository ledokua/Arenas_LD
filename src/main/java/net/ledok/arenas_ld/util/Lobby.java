package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable lobby state container.
 *
 * Contract: every mutation to this object or its collections must be followed
 * by the owning controller's persistence/sync path (markDirtyAndSync or equivalent).
 */
public class Lobby {
    public UUID id = UUID.randomUUID();
    public UUID ownerUuid = new UUID(0L, 0L);
    public String ownerName = "";
    public List<UUID> members = new ArrayList<>();
    public Map<UUID, Long> pendingInvites = new java.util.HashMap<>();
    public LobbyVisibility visibility = LobbyVisibility.OPEN;
    public DifficultyTier selectedTier = DifficultyTier.NORMAL;
    public boolean hardcoreEnabled = false;
    public LobbyStatus status = LobbyStatus.OPEN;

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("OwnerUuid", ownerUuid);
        tag.putString("OwnerName", ownerName);
        tag.putString("Visibility", visibility.name());
        tag.putString("SelectedTier", selectedTier.name());
        tag.putBoolean("HardcoreEnabled", hardcoreEnabled);
        tag.putString("Status", status.name());

        ListTag membersList = new ListTag();
        for (UUID member : members) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("Uuid", member);
            membersList.add(memberTag);
        }
        tag.put("Members", membersList);

        ListTag invitesList = new ListTag();
        for (Map.Entry<UUID, Long> entry : pendingInvites.entrySet()) {
            CompoundTag inviteTag = new CompoundTag();
            inviteTag.putUUID("Uuid", entry.getKey());
            inviteTag.putLong("ExpireAt", entry.getValue());
            invitesList.add(inviteTag);
        }
        tag.put("PendingInvites", invitesList);
        return tag;
    }

    public static Lobby fromNbt(CompoundTag tag) {
        Lobby lobby = new Lobby();
        if (tag.hasUUID("Id")) {
            lobby.id = tag.getUUID("Id");
        }
        if (tag.hasUUID("OwnerUuid")) {
            lobby.ownerUuid = tag.getUUID("OwnerUuid");
        }
        lobby.ownerName = tag.getString("OwnerName");

        String visibilityName = tag.getString("Visibility");
        if (!visibilityName.isEmpty()) {
            try {
                lobby.visibility = LobbyVisibility.valueOf(visibilityName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                lobby.visibility = LobbyVisibility.OPEN;
            }
        }

        lobby.selectedTier = DifficultyTier.fromNameOrDefault(tag.getString("SelectedTier"), DifficultyTier.NORMAL);
        lobby.hardcoreEnabled = tag.getBoolean("HardcoreEnabled");

        String statusName = tag.getString("Status");
        if (!statusName.isEmpty()) {
            try {
                lobby.status = LobbyStatus.valueOf(statusName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                lobby.status = LobbyStatus.OPEN;
            }
        }

        lobby.members.clear();
        if (tag.contains("Members", Tag.TAG_LIST)) {
            ListTag membersList = tag.getList("Members", Tag.TAG_COMPOUND);
            for (Tag memberTag : membersList) {
                CompoundTag member = (CompoundTag) memberTag;
                if (member.hasUUID("Uuid")) {
                    lobby.members.add(member.getUUID("Uuid"));
                }
            }
        }

        lobby.pendingInvites.clear();
        if (tag.contains("PendingInvites", Tag.TAG_LIST)) {
            ListTag invitesList = tag.getList("PendingInvites", Tag.TAG_COMPOUND);
            for (Tag inviteTag : invitesList) {
                CompoundTag invite = (CompoundTag) inviteTag;
                if (invite.hasUUID("Uuid")) {
                    lobby.pendingInvites.put(invite.getUUID("Uuid"), invite.getLong("ExpireAt"));
                }
            }
        }

        return lobby;
    }
}
