package net.ledok.arenas_ld.stats;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Levels for balance work (LeDok): a player's level and gear when they join a run, and the run's own level.
 * <ul>
 *   <li>Player level: the {@code loot_stage:loot_stage} attribute (Loot Stage).</li>
 *   <li>Gear: Dungeon Difficulty's power level ({@code dd.rsf} in {@code custom_data}; crafted gear has none) and
 *       Limit Breaker's {@code limit_break_level}, per armor slot and both hands.</li>
 *   <li>Class: the equipped Spell Engine spell book, else the {@code falleneden:classes} skill branch.</li>
 *   <li>Run level: the number in the difficulty's loot table ({@code falleneden:arenas/level_40} → 40).</li>
 * </ul>
 * Read once per player per run, never per hit.
 */
public final class RunGear {
    private static final ResourceLocation LOOT_STAGE = ResourceLocation.fromNamespaceAndPath("loot_stage", "loot_stage");
    private static final ResourceLocation CLASS_TREE = ResourceLocation.fromNamespaceAndPath("falleneden", "classes");
    private static final String POWER_KEY = "dd.rsf";
    private static final String LIMIT_BREAK_KEY = "limit_break_level";
    private static final Pattern LEVEL = Pattern.compile("level_(\\d+)$");
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private RunGear() {
    }

    public static RunRecord.Gear capture(ServerPlayer player) {
        RunRecord.Gear gear = new RunRecord.Gear();
        gear.playerLevel = playerLevel(player);
        int sum = 0;
        int count = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            RunRecord.Slot entry = new RunRecord.Slot();
            entry.slot = slot.getName();
            entry.item = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            entry.power = data.contains(POWER_KEY, Tag.TAG_ANY_NUMERIC) ? data.getInt(POWER_KEY) : -1;
            entry.limitBreak = data.contains(LIMIT_BREAK_KEY, Tag.TAG_ANY_NUMERIC) ? data.getInt(LIMIT_BREAK_KEY) : -1;
            if (entry.power >= 0) {
                sum += entry.power;
                count++;
                gear.powerMax = Math.max(gear.powerMax, entry.power);
            }
            gear.slots.add(entry);
        }
        gear.powerAvg = count == 0 ? -1 : Math.round(sum * 10f / count) / 10f;
        SpellBooks.Book book = SpellBooks.of(player);
        if (book != null) {
            gear.playerClass = book.item();
            try {
                gear.playerClassStack = book.stack().copyWithCount(1).save(player.registryAccess()).toString();
            } catch (RuntimeException e) {
                gear.playerClassStack = "";
            }
            gear.spellPool = book.pool();
        } else {
            String branch = skillBranch(player);
            gear.playerClass = branch.isEmpty() ? "" : "skills:" + branch;
        }
        return gear;
    }

    /** No spell book: the class skill-tree branch with most unlocked nodes ("" without Pufferfish skills). */
    public static String skillBranch(ServerPlayer player) {
        if (!net.ledok.arenas_ld.platform.ArenasPlatform.INSTANCE.isModLoaded("puffish_skills")) {
            return "";
        }
        try {
            return net.ledok.arenas_ld.compat.PuffishSkillsCompat.topBranch(player, CLASS_TREE);
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }

    /** The {@code loot_stage:loot_stage} level, or -1 without Loot Stage. */
    public static int playerLevel(ServerPlayer player) {
        Optional<Holder.Reference<Attribute>> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(LOOT_STAGE);
        if (attribute.isEmpty()) {
            return -1;
        }
        AttributeInstance instance = player.getAttribute(attribute.get());
        return instance == null ? -1 : (int) Math.round(instance.getValue());
    }

    /** Attributes always recorded (when the player has them). */
    private static final java.util.Set<String> STAT_IDS = java.util.Set.of(
        "minecraft:generic.attack_damage", "minecraft:generic.attack_speed", "minecraft:generic.max_health",
        "minecraft:generic.armor", "minecraft:generic.armor_toughness", "eden_ld:damage_reduction",
        "spell_engine:evasion_chance", "mrpg_lib:rage_modifier");
    /** Namespaces recorded when above 0: spell power per school and its crits, Critical Strike. */
    private static final java.util.Set<String> STAT_NAMESPACES = java.util.Set.of("spell_power", "critical_strike");

    /** A player's combat stats now (attributes and effects); a few hundred map lookups, a few times per run. */
    public static RunRecord.Stats stats(ServerPlayer player, int seconds) {
        RunRecord.Stats stats = new RunRecord.Stats();
        stats.seconds = seconds;
        BuiltInRegistries.ATTRIBUTE.holders().forEach(holder -> {
            ResourceLocation id = holder.key().location();
            boolean always = STAT_IDS.contains(id.toString());
            if (!always && !STAT_NAMESPACES.contains(id.getNamespace())) {
                return;
            }
            AttributeInstance instance = player.getAttribute(holder);
            if (instance == null) {
                return;
            }
            double value = instance.getValue();
            if (always || value > 0) {
                stats.attributes.put(id.toString(), Math.round(value * 1000) / 1000.0);
            }
        });
        for (var effect : player.getActiveEffects()) {
            effect.getEffect().unwrapKey().ifPresent(key ->
                stats.effects.add(key.location() + " " + effect.getAmplifier()));
        }
        return stats;
    }

    /** Every unlocked skill-tree node, "<category>/<node>" (empty without Pufferfish skills). */
    public static java.util.List<String> skills(ServerPlayer player) {
        if (!net.ledok.arenas_ld.platform.ArenasPlatform.INSTANCE.isModLoaded("puffish_skills")) {
            return new java.util.ArrayList<>();
        }
        try {
            return net.ledok.arenas_ld.compat.PuffishSkillsCompat.unlockedSkills(player);
        } catch (RuntimeException | LinkageError e) {
            return new java.util.ArrayList<>();
        }
    }

    /** The level in a loot table id ("falleneden:arenas/level_40" → 40), or -1. */
    public static int levelOf(@Nullable String lootTable) {
        if (lootTable == null) {
            return -1;
        }
        Matcher matcher = LEVEL.matcher(lootTable.trim());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }
}
