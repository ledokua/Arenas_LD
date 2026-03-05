package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;

public class MobArenaRewardData {
    public boolean perPlayer = false;
    public String lootTableId = "";
    public int weight = 10;
    public int rolls = 1;
    public int minWave = 1;
    public int maxWave = 100;
    public int waveFrequency = 1;

    public MobArenaRewardData() {}

    public MobArenaRewardData(boolean perPlayer, String lootTableId, int weight, int rolls, int minWave, int maxWave, int waveFrequency) {
        this.perPlayer = perPlayer;
        this.lootTableId = lootTableId;
        this.weight = weight;
        this.rolls = rolls;
        this.minWave = minWave;
        this.maxWave = maxWave;
        this.waveFrequency = waveFrequency;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("PerPlayer", perPlayer);
        tag.putString("LootTableId", lootTableId);
        tag.putInt("Weight", weight);
        tag.putInt("Rolls", rolls);
        tag.putInt("MinWave", minWave);
        tag.putInt("MaxWave", maxWave);
        tag.putInt("WaveFrequency", waveFrequency);
        return tag;
    }

    public static MobArenaRewardData fromNbt(CompoundTag tag) {
        MobArenaRewardData data = new MobArenaRewardData();
        data.perPlayer = tag.getBoolean("PerPlayer");
        data.lootTableId = tag.getString("LootTableId");
        data.weight = tag.getInt("Weight");
        data.rolls = tag.getInt("Rolls");
        data.minWave = tag.getInt("MinWave");
        data.maxWave = tag.getInt("MaxWave");
        data.waveFrequency = tag.getInt("WaveFrequency");
        return data;
    }
}
