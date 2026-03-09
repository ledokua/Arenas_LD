package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;

public class LeaderboardEntry {
    public final String playerName;
    public final int wave;

    public LeaderboardEntry(String playerName, int wave) {
        this.playerName = playerName;
        this.wave = wave;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("PlayerName", playerName);
        tag.putInt("Wave", wave);
        return tag;
    }

    public static LeaderboardEntry fromNbt(CompoundTag tag) {
        return new LeaderboardEntry(tag.getString("PlayerName"), tag.getInt("Wave"));
    }
}
