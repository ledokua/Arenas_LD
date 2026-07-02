package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent, server-global per-player run statistics across all three systems. Runs are counted
 * at run start; wins are credited to the participants still eligible at the win (a hardcore death
 * or forfeit forfeits the win credit). Losses are derivable as {@code runs - wins}.
 *
 * <p>Stored as world {@link SavedData} on the overworld's data storage, like
 * {@link PendingRestoreStore}, so stats survive restarts and are independent of any controller.
 */
public class PlayerStatsStore extends SavedData {
    private static final String DATA_ID = "arenas_ld_player_stats";
    private static final String KEY_LIST = "players";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_STATS = "stats";

    /** Which game system a run belongs to. */
    public enum Mode { DUNGEON, RAID, ARENA }

    public record Stats(
        int dungeonRuns, int dungeonWins,
        int raidRuns, int raidWins,
        int arenaRuns, int arenaWins,
        int bestArenaWave
    ) {
        public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0, 0, 0);

        public static final Codec<Stats> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.INT.optionalFieldOf("dungeonRuns", 0).forGetter(Stats::dungeonRuns),
                Codec.INT.optionalFieldOf("dungeonWins", 0).forGetter(Stats::dungeonWins),
                Codec.INT.optionalFieldOf("raidRuns", 0).forGetter(Stats::raidRuns),
                Codec.INT.optionalFieldOf("raidWins", 0).forGetter(Stats::raidWins),
                Codec.INT.optionalFieldOf("arenaRuns", 0).forGetter(Stats::arenaRuns),
                Codec.INT.optionalFieldOf("arenaWins", 0).forGetter(Stats::arenaWins),
                Codec.INT.optionalFieldOf("bestArenaWave", 0).forGetter(Stats::bestArenaWave)
            ).apply(instance, Stats::new)
        );

        public int runs(Mode mode) {
            return switch (mode) {
                case DUNGEON -> dungeonRuns;
                case RAID -> raidRuns;
                case ARENA -> arenaRuns;
            };
        }

        public int wins(Mode mode) {
            return switch (mode) {
                case DUNGEON -> dungeonWins;
                case RAID -> raidWins;
                case ARENA -> arenaWins;
            };
        }

        Stats withRun(Mode mode) {
            return switch (mode) {
                case DUNGEON -> new Stats(dungeonRuns + 1, dungeonWins, raidRuns, raidWins, arenaRuns, arenaWins, bestArenaWave);
                case RAID -> new Stats(dungeonRuns, dungeonWins, raidRuns + 1, raidWins, arenaRuns, arenaWins, bestArenaWave);
                case ARENA -> new Stats(dungeonRuns, dungeonWins, raidRuns, raidWins, arenaRuns + 1, arenaWins, bestArenaWave);
            };
        }

        Stats withWin(Mode mode) {
            return switch (mode) {
                case DUNGEON -> new Stats(dungeonRuns, dungeonWins + 1, raidRuns, raidWins, arenaRuns, arenaWins, bestArenaWave);
                case RAID -> new Stats(dungeonRuns, dungeonWins, raidRuns, raidWins + 1, arenaRuns, arenaWins, bestArenaWave);
                case ARENA -> new Stats(dungeonRuns, dungeonWins, raidRuns, raidWins, arenaRuns, arenaWins + 1, bestArenaWave);
            };
        }

        Stats withArenaWave(int wave) {
            return wave <= bestArenaWave ? this
                : new Stats(dungeonRuns, dungeonWins, raidRuns, raidWins, arenaRuns, arenaWins, wave);
        }
    }

    private static final Factory<PlayerStatsStore> FACTORY =
        new Factory<>(PlayerStatsStore::new, PlayerStatsStore::load, null);

    private final Map<UUID, Stats> stats = new HashMap<>();

    public static PlayerStatsStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
    }

    public Stats stats(UUID uuid) {
        return stats.getOrDefault(uuid, Stats.EMPTY);
    }

    public void recordRunStart(UUID uuid, Mode mode) {
        stats.merge(uuid, Stats.EMPTY.withRun(mode), (old, ignored) -> old.withRun(mode));
        setDirty();
    }

    public void recordWin(UUID uuid, Mode mode) {
        stats.merge(uuid, Stats.EMPTY.withWin(mode), (old, ignored) -> old.withWin(mode));
        setDirty();
    }

    public void recordArenaWave(UUID uuid, int wave) {
        stats.merge(uuid, Stats.EMPTY.withArenaWave(wave), (old, ignored) -> old.withArenaWave(wave));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Stats> entry : stats.entrySet()) {
            CompoundTag element = new CompoundTag();
            element.putUUID(KEY_UUID, entry.getKey());
            Stats.CODEC.encodeStart(NbtOps.INSTANCE, entry.getValue()).result()
                .ifPresent(encoded -> element.put(KEY_STATS, encoded));
            list.add(element);
        }
        tag.put(KEY_LIST, list);
        return tag;
    }

    private static PlayerStatsStore load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerStatsStore store = new PlayerStatsStore();
        ListTag list = tag.getList(KEY_LIST, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag element = list.getCompound(i);
            if (!element.hasUUID(KEY_UUID) || !element.contains(KEY_STATS)) {
                continue;
            }
            UUID uuid = element.getUUID(KEY_UUID);
            Stats.CODEC.parse(NbtOps.INSTANCE, element.get(KEY_STATS)).result()
                .ifPresent(s -> store.stats.put(uuid, s));
        }
        return store;
    }
}
