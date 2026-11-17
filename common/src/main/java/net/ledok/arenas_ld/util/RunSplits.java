package net.ledok.arenas_ld.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Summon extra data and splitting run mobs (4.30.0, LeDok).
 * <ul>
 *   <li><b>Extra data</b>: a summon's SNBT ({@code nbt}, e.g. {@code {Size:3}}) is merged into the mob as it is
 *       created, before Arenas' attributes, equipment and scaling, which still apply on top. Position, motion,
 *       rotation and UUID in it are ignored.</li>
 *   <li><b>Split parts</b>: a run slime or magma cube (any {@link Slime}) that splits when it dies gets parts that stay
 *       run mobs: same team, tags, run effects and run stats; they hold up their room, wave or raid until they die,
 *       and go with the run's cleanup. Each part's max health is the parent's ×(part size / parent size)² and its
 *       attack the parent's ×(part size / parent size), both × the summon's split strength (percent, default 100).
 *       Other base attributes are copied from the parent, except speed and armor, which vanilla sets by size.</li>
 *   <li><b>One bar</b>: the summon and all its parts are one group. Its HP for the run HUD is the whole tree: each
 *       part's health plus every part it could still split into (vanilla splits into 2–4, counted as 4), over the
 *       tree's full total at spawn. It only goes down and ends at 0 with the last part.</li>
 * </ul>
 * A slime that is a minion (spawned by a boss) splits as before: its parts are minions of the same boss.
 * Server thread only.
 */
public final class RunSplits {
    /** "arenas_ld_split:&lt;percent&gt;" on a run mob whose parts stay in the run. */
    public static final String SPLIT_TAG_PREFIX = "arenas_ld_split:";
    /** "arenas_ld_split_group:&lt;root uuid&gt;:&lt;tree max&gt;" on the root and all its parts. */
    public static final String GROUP_TAG_PREFIX = "arenas_ld_split_group:";
    /** On parts (not the root). */
    public static final String PART_TAG = "arenas_ld_split_part";
    /** Vanilla splits into 2–4 parts; the tree counts the most. */
    private static final int MAX_PARTS = 4;
    private static final Set<String> IGNORED_KEYS = Set.of("Pos", "Motion", "Rotation", "UUID", "id", "Passengers",
        "Dimension");

    /** A part that made it into the world: the run that owned the parent takes it. */
    @FunctionalInterface
    public interface PartListener {
        void onPart(ServerLevel level, UUID parent, LivingEntity part);
    }

    public static final List<PartListener> LISTENERS = new CopyOnWriteArrayList<>();

    /** Run slimes in their {@code remove} (splitting), innermost first. */
    private static final ArrayDeque<Slime> SPLITTING = new ArrayDeque<>();

    private RunSplits() {
    }

    // ── extra data ───────────────────────────────────────────────────────────

    /** Why this SNBT can't be used, or null when it can (blank included). */
    public static @Nullable String nbtProblem(@Nullable String snbt) {
        if (snbt == null || snbt.isBlank()) {
            return null;
        }
        try {
            TagParser.parseTag(snbt.trim());
            return null;
        } catch (CommandSyntaxException e) {
            return e.getMessage();
        }
    }

    /** Merges the summon's extra data into a mob that isn't in the world yet. Bad SNBT is logged and skipped. */
    public static void applyNbt(Entity mob, @Nullable String snbt) {
        if (snbt == null || snbt.isBlank()) {
            return;
        }
        CompoundTag extra;
        try {
            extra = TagParser.parseTag(snbt.trim());
        } catch (CommandSyntaxException e) {
            ArenasLdMod.LOGGER.warn("[Arenas] Summon data for {} isn't valid SNBT, skipped: {}", mob.getType(), e.getMessage());
            return;
        }
        for (String key : IGNORED_KEYS) {
            extra.remove(key);
        }
        CompoundTag current = mob.saveWithoutId(new CompoundTag());
        current.merge(extra);
        try {
            mob.load(current);
        } catch (RuntimeException e) {
            ArenasLdMod.LOGGER.warn("[Arenas] Summon data for {} couldn't be applied: {}", mob.getType(), e.toString());
        }
    }

    // ── roots ────────────────────────────────────────────────────────────────

    /**
     * A summon just made (attributes, scaling and full health set): if it can split, it becomes its group's root and
     * its parts will stay in the run. Call before it is added to the world.
     */
    public static void markRoot(LivingEntity mob, int strengthPercent) {
        if (!(mob instanceof Slime slime)) {
            return;
        }
        int strength = Math.max(1, Math.min(1000, strengthPercent));
        mob.addTag(SPLIT_TAG_PREFIX + strength);
        double tree = treeMax(mob.getMaxHealth(), slime.getSize(), strength / 100.0);
        mob.addTag(GROUP_TAG_PREFIX + mob.getUUID() + ":" + String.format(Locale.ROOT, "%.2f", tree));
    }

    /** A part's max health from its parent's. */
    static double partMax(double parentMax, int parentSize, double strength) {
        double ratio = (double) (parentSize / 2) / parentSize;
        return parentMax * ratio * ratio * strength;
    }

    /** One mob's max health plus every part it could split into, at most. */
    static double treeMax(double max, int size, double strength) {
        if (size <= 1) {
            return max;
        }
        return max + MAX_PARTS * treeMax(partMax(max, size, strength), size / 2, strength);
    }

    private static int strengthOf(Entity mob) {
        for (String tag : mob.getTags()) {
            if (tag.startsWith(SPLIT_TAG_PREFIX)) {
                try {
                    return Integer.parseInt(tag.substring(SPLIT_TAG_PREFIX.length()));
                } catch (NumberFormatException e) {
                    return 100;
                }
            }
        }
        return -1;
    }

    /** Whether this run mob's parts stay in the run. */
    public static boolean splits(Entity mob) {
        return mob instanceof Slime && strengthOf(mob) > 0;
    }

    /**
     * A run mob that died and hasn't split yet (a slime's parts come when its death animation ends): its room, wave
     * or raid isn't over.
     */
    public static boolean pendingSplit(Entity mob) {
        return mob instanceof Slime slime && slime.getSize() > 1 && !slime.isRemoved() && strengthOf(mob) > 0;
    }

    // ── splitting ────────────────────────────────────────────────────────────

    /** {@code Slime.remove} starts. */
    public static void enterRemove(Slime slime) {
        if (!slime.level().isClientSide && slime.getSize() > 1 && slime.isDeadOrDying() && strengthOf(slime) > 0) {
            SPLITTING.push(slime);
        }
    }

    /** {@code Slime.remove} returns. */
    public static void exitRemove(Slime slime) {
        if (SPLITTING.peek() == slime) {
            SPLITTING.pop();
        }
    }

    /**
     * {@code ServerLevel.addFreshEntity}, before anything else: a part of a splitting run slime is made a run mob
     * (so {@link RunMinions} leaves it alone). True when it was.
     */
    public static boolean onAdd(Entity entity) {
        Slime parent = SPLITTING.peek();
        if (parent == null || !(entity instanceof Slime part) || part.getType() != parent.getType()
            || part.getTags().contains(PART_TAG)) {
            return false;
        }
        double strength = strengthOf(parent) / 100.0;
        double ratio = (double) part.getSize() / parent.getSize();
        Set<Holder<Attribute>> bySize = Set.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED,
            Attributes.ARMOR);
        for (Holder.Reference<Attribute> holder : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            if (bySize.contains(holder)) {
                continue;
            }
            AttributeInstance from = parent.getAttribute(holder);
            AttributeInstance to = part.getAttribute(holder);
            if (from != null && to != null) {
                to.setBaseValue(from.getBaseValue());
            }
        }
        setBase(part, Attributes.MAX_HEALTH, base(parent, Attributes.MAX_HEALTH) * ratio * ratio * strength);
        setBase(part, Attributes.ATTACK_DAMAGE, base(parent, Attributes.ATTACK_DAMAGE) * ratio * strength);
        // Run mob, scale for its own minions, run stats key, boss mark, group and split strength.
        for (String tag : List.copyOf(parent.getTags())) {
            part.addTag(tag);
        }
        part.addTag(PART_TAG);
        RunEffects.copyMob(parent, part);
        net.ledok.arenas_ld.stats.RunStats.copyMob(parent, part);
        if (part.getTags().contains(net.ledok.arenas_ld.stats.RunStats.BOSS_TAG)) {
            net.ledok.arenas_ld.stats.RunStats.markBoss(part);
        }
        part.setHealth(part.getMaxHealth());
        return true;
    }

    /** {@code ServerLevel.addFreshEntity} returned: a part that made it in goes to its parent's run. */
    public static void afterAdd(ServerLevel level, Entity entity, boolean added) {
        Slime parent = SPLITTING.peek();
        if (!added || parent == null || !(entity instanceof LivingEntity part) || !part.getTags().contains(PART_TAG)) {
            return;
        }
        DebugLog.log("split: {} part {} (size {}) of {}", part.getType(), part.getUUID(),
            ((Slime) part).getSize(), parent.getUUID());
        for (PartListener listener : LISTENERS) {
            listener.onPart(level, parent.getUUID(), part);
        }
    }

    private static double base(LivingEntity mob, Holder<Attribute> attribute) {
        AttributeInstance instance = mob.getAttribute(attribute);
        return instance != null ? instance.getBaseValue() : 0;
    }

    private static void setBase(LivingEntity mob, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(Math.max(attribute == Attributes.MAX_HEALTH ? 1 : 0, value));
        }
    }

    // ── one bar ──────────────────────────────────────────────────────────────

    /** The group tag's root uuid and tree max, or null for a mob in no group. */
    private static @Nullable Map.Entry<String, Double> groupOf(Entity mob) {
        for (String tag : mob.getTags()) {
            if (tag.startsWith(GROUP_TAG_PREFIX)) {
                String rest = tag.substring(GROUP_TAG_PREFIX.length());
                int colon = rest.lastIndexOf(':');
                if (colon > 0) {
                    try {
                        return Map.entry(rest.substring(0, colon), Double.parseDouble(rest.substring(colon + 1)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return null;
    }

    /** What's left of a group member: its health and every part it could still split into. */
    private static double remaining(LivingEntity mob) {
        if (mob.isRemoved()) {
            return 0; // its parts are counted themselves
        }
        double left = Math.max(0, mob.getHealth());
        if (mob instanceof Slime slime && slime.getSize() > 1) {
            double strength = Math.max(1, strengthOf(mob)) / 100.0;
            left += MAX_PARTS * treeMax(partMax(mob.getMaxHealth(), slime.getSize(), strength), slime.getSize() / 2, strength);
        }
        return left;
    }

    /**
     * Combined health of these boss mobs as {current, max}: a mob in a split group counts its whole tree (its group's
     * max once), any other mob its health over its max health. Null entries and non-living ones are skipped.
     */
    public static double[] health(Collection<? extends Entity> mobs) {
        double current = 0;
        double max = 0;
        Map<String, Double> groups = new HashMap<>();
        for (Entity entity : mobs) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            Map.Entry<String, Double> group = groupOf(living);
            if (group == null) {
                current += Math.max(0, living.getHealth());
                max += living.getMaxHealth();
            } else {
                groups.putIfAbsent(group.getKey(), group.getValue());
                current += remaining(living);
            }
        }
        for (double groupMax : groups.values()) {
            max += groupMax;
        }
        return new double[]{Math.min(current, max), max};
    }

    public static void clear() {
        SPLITTING.clear();
    }
}
