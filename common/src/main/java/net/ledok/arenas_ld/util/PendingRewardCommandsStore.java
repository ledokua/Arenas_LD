package net.ledok.arenas_ld.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent queue of per-player reward commands won by players who were offline at the win.
 * {@link RewardCommands} runs and removes a player's entry when they next join.
 *
 * <p>Stored as world {@link SavedData} on the overworld's data storage (like
 * {@link PendingRestoreStore}). Commands are kept raw, placeholders included, so they run with
 * the player's name at join time.
 */
public class PendingRewardCommandsStore extends SavedData {
    private static final String DATA_ID = "arenas_ld_pending_reward_commands";
    private static final String KEY_LIST = "players";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_COMMANDS = "commands";

    private static final Factory<PendingRewardCommandsStore> FACTORY =
        new Factory<>(PendingRewardCommandsStore::new, PendingRewardCommandsStore::load, null);

    private final Map<UUID, List<String>> pending = new HashMap<>();

    public static PendingRewardCommandsStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
    }

    /** Queues one command for a player's next join. */
    public void add(UUID uuid, String command) {
        pending.computeIfAbsent(uuid, k -> new ArrayList<>()).add(command);
        setDirty();
    }

    /** The commands queued for a player, in order (a copy). */
    public List<String> peek(UUID uuid) {
        return List.copyOf(pending.getOrDefault(uuid, List.of()));
    }

    /** Returns and removes the commands queued for a player; empty if none. */
    public List<String> take(UUID uuid) {
        List<String> removed = pending.remove(uuid);
        if (removed == null) {
            return List.of();
        }
        setDirty();
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, List<String>> entry : pending.entrySet()) {
            CompoundTag element = new CompoundTag();
            element.putUUID(KEY_UUID, entry.getKey());
            ListTag commands = new ListTag();
            for (String command : entry.getValue()) {
                commands.add(StringTag.valueOf(command));
            }
            element.put(KEY_COMMANDS, commands);
            list.add(element);
        }
        tag.put(KEY_LIST, list);
        return tag;
    }

    private static PendingRewardCommandsStore load(CompoundTag tag, HolderLookup.Provider registries) {
        PendingRewardCommandsStore store = new PendingRewardCommandsStore();
        ListTag list = tag.getList(KEY_LIST, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag element = list.getCompound(i);
            if (!element.hasUUID(KEY_UUID)) {
                continue;
            }
            ListTag commands = element.getList(KEY_COMMANDS, Tag.TAG_STRING);
            List<String> queued = new ArrayList<>();
            for (int j = 0; j < commands.size(); j++) {
                queued.add(commands.getString(j));
            }
            if (!queued.isEmpty()) {
                store.pending.put(element.getUUID(KEY_UUID), queued);
            }
        }
        return store;
    }
}
