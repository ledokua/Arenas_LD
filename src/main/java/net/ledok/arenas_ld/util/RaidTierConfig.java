package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;

public record RaidTierConfig(
        double healthMultOverride,
        double damageMultOverride,
        String lootTableId,
        String perPlayerLootTableId
) {
    public static RaidTierConfig defaultFor(RaidDifficulty tier) {
        return new RaidTierConfig(-1, -1, "", "");
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("HealthMultOverride", healthMultOverride);
        tag.putDouble("DamageMultOverride", damageMultOverride);
        tag.putString("LootTableId", lootTableId == null ? "" : lootTableId);
        tag.putString("PerPlayerLootTableId", perPlayerLootTableId == null ? "" : perPlayerLootTableId);
        return tag;
    }

    public static RaidTierConfig fromNbt(CompoundTag tag) {
        return new RaidTierConfig(
                tag.contains("HealthMultOverride") ? tag.getDouble("HealthMultOverride") : -1,
                tag.contains("DamageMultOverride") ? tag.getDouble("DamageMultOverride") : -1,
                tag.contains("LootTableId") ? tag.getString("LootTableId") : "",
                tag.contains("PerPlayerLootTableId") ? tag.getString("PerPlayerLootTableId") : ""
        );
    }
}
