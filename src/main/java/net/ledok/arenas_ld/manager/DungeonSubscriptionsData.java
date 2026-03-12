package net.ledok.arenas_ld.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DungeonSubscriptionsData extends SavedData {
    public static final String DATA_NAME = "arenas_ld_dungeon_subscriptions";

    public final Map<String, DungeonBossManager.DungeonKey> registeredDungeons = new HashMap<>();
    public final Map<UUID, Set<String>> subscriptions = new HashMap<>();

    public static DungeonSubscriptionsData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonSubscriptionsData data = new DungeonSubscriptionsData();

        if (tag.contains("Registered", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Registered", Tag.TAG_COMPOUND);
            for (Tag t : list) {
                CompoundTag ct = (CompoundTag) t;
                String name = ct.getString("Name");
                BlockPos pos = BlockPos.of(ct.getLong("Pos"));
                ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        ResourceLocation.parse(ct.getString("Dim")));
                data.registeredDungeons.put(name, new DungeonBossManager.DungeonKey(pos, dim));
            }
        }

        if (tag.contains("Subscriptions", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Subscriptions", Tag.TAG_COMPOUND);
            for (Tag t : list) {
                CompoundTag ct = (CompoundTag) t;
                UUID uuid = ct.getUUID("Player");
                Set<String> names = new HashSet<>();
                ListTag nameList = ct.getList("Names", Tag.TAG_STRING);
                for (Tag nameTag : nameList) {
                    names.add(nameTag.getAsString());
                }
                data.subscriptions.put(uuid, names);
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag registered = new ListTag();
        for (Map.Entry<String, DungeonBossManager.DungeonKey> entry : registeredDungeons.entrySet()) {
            CompoundTag ct = new CompoundTag();
            ct.putString("Name", entry.getKey());
            ct.putLong("Pos", entry.getValue().pos().asLong());
            ct.putString("Dim", entry.getValue().dimension().location().toString());
            registered.add(ct);
        }
        tag.put("Registered", registered);

        ListTag subs = new ListTag();
        for (Map.Entry<UUID, Set<String>> entry : subscriptions.entrySet()) {
            CompoundTag ct = new CompoundTag();
            ct.putUUID("Player", entry.getKey());
            ListTag names = new ListTag();
            for (String name : entry.getValue()) {
                names.add(net.minecraft.nbt.StringTag.valueOf(name));
            }
            ct.put("Names", names);
            subs.add(ct);
        }
        tag.put("Subscriptions", subs);

        return tag;
    }
}
