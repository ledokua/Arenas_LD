package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;

public class DungeonLeaderboardEntry {
    public final String playerName;
    public final int timeSeconds;

    public DungeonLeaderboardEntry(String playerName, int timeSeconds) {
        this.playerName = playerName;
        this.timeSeconds = timeSeconds;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("PlayerName", playerName);
        tag.putInt("TimeSeconds", timeSeconds);
        return tag;
    }

    public static DungeonLeaderboardEntry fromNbt(CompoundTag tag) {
        return new DungeonLeaderboardEntry(tag.getString("PlayerName"), tag.getInt("TimeSeconds"));
    }
}
