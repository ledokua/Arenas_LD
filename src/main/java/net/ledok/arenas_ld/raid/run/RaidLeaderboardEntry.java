package net.ledok.arenas_ld.raid.run;

import net.minecraft.nbt.CompoundTag;

public record RaidLeaderboardEntry(String playerName, int timeSeconds) {
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("PlayerName", playerName);
        tag.putInt("TimeSeconds", timeSeconds);
        return tag;
    }

    public static RaidLeaderboardEntry fromNbt(CompoundTag tag) {
        return new RaidLeaderboardEntry(
                tag.getString("PlayerName"),
                tag.getInt("TimeSeconds")
        );
    }
}
