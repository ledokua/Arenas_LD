package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.dungeon.run.PlayerReturnPoint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent, server-global record of where to send a player who was removed from a run while
 * offline (forfeited past the disconnect grace period, or still offline when the run ended).
 *
 * <p>Stored as world {@link SavedData} on the overworld's data storage so the eject survives a
 * server restart and can be looked up on login no matter which controller chunk is loaded. Shared
 * across dungeon/raid/arena — keyed only by player UUID and a {@link PlayerReturnPoint}.
 */
public class PendingRestoreStore extends SavedData {
    private static final String DATA_ID = "arenas_ld_pending_restores";
    private static final String KEY_LIST = "restores";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_RETURN_POINT = "returnPoint";

    private static final Factory<PendingRestoreStore> FACTORY =
        new Factory<>(PendingRestoreStore::new, PendingRestoreStore::load, null);

    private final Map<UUID, PlayerReturnPoint> restores = new HashMap<>();

    public static PendingRestoreStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
    }

    /** Records (or replaces) the eject point for a player. */
    public void put(UUID uuid, PlayerReturnPoint returnPoint) {
        restores.put(uuid, returnPoint);
        setDirty();
    }

    /** Returns and removes the pending eject point for a player, or {@code null} if none. */
    @Nullable
    public PlayerReturnPoint take(UUID uuid) {
        PlayerReturnPoint removed = restores.remove(uuid);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerReturnPoint> entry : restores.entrySet()) {
            CompoundTag element = new CompoundTag();
            element.putUUID(KEY_UUID, entry.getKey());
            PlayerReturnPoint.CODEC.encodeStart(ops, entry.getValue()).result()
                .ifPresent(encoded -> element.put(KEY_RETURN_POINT, encoded));
            list.add(element);
        }
        tag.put(KEY_LIST, list);
        return tag;
    }

    private static PendingRestoreStore load(CompoundTag tag, HolderLookup.Provider registries) {
        PendingRestoreStore store = new PendingRestoreStore();
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ListTag list = tag.getList(KEY_LIST, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag element = list.getCompound(i);
            if (!element.hasUUID(KEY_UUID) || !element.contains(KEY_RETURN_POINT)) {
                continue;
            }
            UUID uuid = element.getUUID(KEY_UUID);
            PlayerReturnPoint.CODEC.parse(ops, element.get(KEY_RETURN_POINT)).result()
                .ifPresent(rp -> store.restores.put(uuid, rp));
        }
        return store;
    }
}
