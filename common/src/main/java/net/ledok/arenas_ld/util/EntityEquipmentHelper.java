package net.ledok.arenas_ld.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class EntityEquipmentHelper {
    private static final String MAX_HEALTH_ID = "minecraft:generic.max_health";
    private static final String ATTACK_DAMAGE_ID = "minecraft:generic.attack_damage";

    /**
     * Scoreboard tag marking a spawned mob whose natural loot-table drops (bones, arrows,
     * rotten flesh, …) are suppressed. Read by {@code LivingEntityMixin#dropFromLootTable}.
     */
    public static final String NO_NATURAL_LOOT_TAG = "arenas_ld_no_loot";

    private EntityEquipmentHelper() {
    }

    /**
     * Equip a copy of {@code template} in {@code slot} if {@code chance}% rolls succeed. Configured
     * equipment is never dropped on death — its drop chance is forced to 0. Whether the mob drops
     * its natural loot is controlled separately via {@link #setNaturalLootEnabled}.
     */
    public static void applyEquipment(LivingEntity entity, EquipmentSlot slot, ItemStack template, int chance) {
        if (template == null || template.isEmpty()) {
            return;
        }
        if (chance < 100 && entity.getRandom().nextInt(100) >= chance) {
            return;
        }
        entity.setItemSlot(slot, template.copy());
        if (entity instanceof Mob mob) {
            mob.setDropChance(slot, 0.0F);
        }
    }

    /**
     * Apply every equipment slot from {@code equip} to {@code entity}, rolling each slot's spawn
     * chance. Equipment never drops; the {@code dropChance} flag instead toggles the mob's natural
     * loot-table drops.
     */
    public static void applyAllEquipment(LivingEntity entity, EquipmentData equip) {
        if (equip == null) {
            return;
        }
        for (int i = 0; i < EquipmentData.SLOT_COUNT; i++) {
            applyEquipment(entity, EquipmentData.SLOTS[i], equip.items[i], equip.chances[i]);
        }
        setNaturalLootEnabled(entity, equip.dropChance);
    }

    /**
     * Enable or suppress the mob's natural loot-table drops. When disabled, a marker tag is added
     * that {@code LivingEntityMixin} uses to cancel the loot-table roll on death.
     */
    public static void setNaturalLootEnabled(LivingEntity entity, boolean enabled) {
        if (enabled) {
            entity.removeTag(NO_NATURAL_LOOT_TAG);
        } else {
            entity.addTag(NO_NATURAL_LOOT_TAG);
        }
    }

    /**
     * Apply attribute base values, scaling max-health and attack-damage by the given multipliers.
     * Max-health is multiplied by {@code healthMultiplier * perPlayerMultiplier}; attack-damage by
     * {@code damageMultiplier}. Pass {@code 1.0} for any multiplier that should not scale.
     */
    public static void applyScaledAttributes(
            LivingEntity entity,
            List<AttributeData> attributes,
            RegistryAccess registryAccess,
            double healthMultiplier,
            double damageMultiplier,
            double perPlayerMultiplier
    ) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        var attributeRegistry = registryAccess.registryOrThrow(Registries.ATTRIBUTE);
        for (AttributeData attr : attributes) {
            ResourceLocation attrLocation = ResourceLocation.tryParse(attr.id());
            if (attrLocation == null) continue;
            ResourceKey<Attribute> key = ResourceKey.create(Registries.ATTRIBUTE, attrLocation);
            attributeRegistry.getHolder(key).ifPresent(holder -> {
                AttributeInstance instance = entity.getAttribute(holder);
                if (instance == null) return;
                double value = attr.value();
                if (MAX_HEALTH_ID.equals(attr.id())) {
                    value = value * healthMultiplier * perPlayerMultiplier;
                } else if (ATTACK_DAMAGE_ID.equals(attr.id())) {
                    value = value * damageMultiplier;
                }
                instance.setBaseValue(value);
            });
        }
    }

    /**
     * Resolve the world-space spawn position for a boss relative to its spawner. When no offset is
     * configured the boss spawns one block above the spawner; otherwise the first offset is used.
     * The returned position is block-centred on X/Z.
     */
    public static Vec3 resolveBossSpawnPos(BlockPos spawnerPos, List<BlockPos> offsets) {
        if (offsets == null || offsets.isEmpty()) {
            return new Vec3(spawnerPos.getX() + 0.5, spawnerPos.getY() + 1, spawnerPos.getZ() + 0.5);
        }
        BlockPos offset = offsets.get(0);
        return new Vec3(
            spawnerPos.getX() + offset.getX() + 0.5,
            spawnerPos.getY() + offset.getY(),
            spawnerPos.getZ() + offset.getZ() + 0.5
        );
    }
}
