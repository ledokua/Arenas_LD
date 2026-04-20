package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;

public class TierConfig {
    public Double healthMultiplierOverride;
    public Double damageMultiplierOverride;
    public String lootTableIdOverride = "";
    public String perPlayerLootTableIdOverride = "";
    public Boolean hardcoreOverride;

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        if (healthMultiplierOverride != null) {
            tag.putDouble("HealthMultiplierOverride", healthMultiplierOverride);
        }
        if (damageMultiplierOverride != null) {
            tag.putDouble("DamageMultiplierOverride", damageMultiplierOverride);
        }
        if (!lootTableIdOverride.isEmpty()) {
            tag.putString("LootTableIdOverride", lootTableIdOverride);
        }
        if (!perPlayerLootTableIdOverride.isEmpty()) {
            tag.putString("PerPlayerLootTableIdOverride", perPlayerLootTableIdOverride);
        }
        if (hardcoreOverride != null) {
            tag.putBoolean("HardcoreOverride", hardcoreOverride);
        }
        return tag;
    }

    public static TierConfig fromNbt(CompoundTag tag) {
        TierConfig config = new TierConfig();
        if (tag.contains("HealthMultiplierOverride")) {
            config.healthMultiplierOverride = tag.getDouble("HealthMultiplierOverride");
        }
        if (tag.contains("DamageMultiplierOverride")) {
            config.damageMultiplierOverride = tag.getDouble("DamageMultiplierOverride");
        }
        if (tag.contains("LootTableIdOverride")) {
            config.lootTableIdOverride = tag.getString("LootTableIdOverride");
        }
        if (tag.contains("PerPlayerLootTableIdOverride")) {
            config.perPlayerLootTableIdOverride = tag.getString("PerPlayerLootTableIdOverride");
        }
        if (tag.contains("HardcoreOverride")) {
            config.hardcoreOverride = tag.getBoolean("HardcoreOverride");
        }
        return config;
    }
}
