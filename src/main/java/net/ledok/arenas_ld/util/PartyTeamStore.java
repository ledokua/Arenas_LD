package net.ledok.arenas_ld.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent record of each player's team membership <em>before</em> they were placed into a
 * temporary no-PvP run team. Lets us restore their original team when the run ends — even across a
 * server restart — without persisting team data inside the run codec.
 *
 * <p>Keyed by player scoreboard name → prior team name. An empty value means "had no team". A
 * missing key means "not currently in a run team".
 */
public class PartyTeamStore extends SavedData {
    private static final String DATA_ID = "arenas_ld_party_teams";

    private static final Factory<PartyTeamStore> FACTORY =
        new Factory<>(PartyTeamStore::new, PartyTeamStore::load, null);

    private final Map<String, String> priorTeamByPlayer = new HashMap<>();

    public static PartyTeamStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
    }

    /** Records the player's prior team name ("" if none) before they join a run team. */
    public void put(String playerName, String priorTeamName) {
        priorTeamByPlayer.put(playerName, priorTeamName == null ? "" : priorTeamName);
        setDirty();
    }

    /**
     * Returns and removes the player's stored prior team name. Returns {@code null} if no record
     * exists, or an empty string if they had no prior team.
     */
    @Nullable
    public String take(String playerName) {
        String removed = priorTeamByPlayer.remove(playerName);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        for (Map.Entry<String, String> entry : priorTeamByPlayer.entrySet()) {
            entries.putString(entry.getKey(), entry.getValue());
        }
        tag.put("priorTeams", entries);
        return tag;
    }

    private static PartyTeamStore load(CompoundTag tag, HolderLookup.Provider registries) {
        PartyTeamStore store = new PartyTeamStore();
        CompoundTag entries = tag.getCompound("priorTeams");
        for (String name : entries.getAllKeys()) {
            store.priorTeamByPlayer.put(name, entries.getString(name));
        }
        return store;
    }
}
