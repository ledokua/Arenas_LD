package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Minions: mobs a run mob (a boss, a wave mob) spawns by itself, from a mod's or the vanilla game's
 * own code. Without this they would be strangers to the run: on no team, unscaled, dropping loot,
 * and left behind in the instance when the run ends.
 *
 * <p>A mob counts as a minion of a run mob when it is added to the world while that run mob is
 * acting: its tick (scheduled events it runs from its tick included), {@code LivingEntity.hurt}
 * (a zombie's whole {@code hurt}, for its reinforcements), or the tick of a projectile it shot. It
 * also counts when its owner ({@link OwnableEntity}, {@link TraceableEntity}) is a run mob. A
 * subclass's own {@code hurt} code outside {@code super.hurt} is not covered; such bosses spawn
 * from their tick, set themselves as the owner, or use {@code ArenasApi.spawnAsMinions}. A minion
 * of a minion belongs to the same top summoner. A minion:
 * <ul>
 *   <li>is a run mob too (no despawning, conversions or block griefing), drops no loot or XP;</li>
 *   <li>joins its summoner's team and gets its health and damage scaling;</li>
 *   <li>never holds up a room, wave or raid;</li>
 *   <li>vanishes when its summoner dies or is removed (room and run cleanup remove summoners).</li>
 * </ul>
 * While the top summoner is dead or dying nothing is spawned for it (a slime's split, a boss's
 * death summons): those minions would vanish with it at once. A dying minion's spawns are minions
 * of the top summoner as usual.
 *
 * <p>Server thread only.
 */
public final class RunMinions {
    public static final String MINION_TAG = "arenas_ld_minion";
    private static final String SUMMONER_TAG_PREFIX = "arenas_ld_summoner:";
    /** "arenas_ld_scale:&lt;health&gt;:&lt;damage&gt;", set on run mobs where they are spawned. */
    private static final String SCALE_TAG_PREFIX = "arenas_ld_scale:";

    /** How often (ticks) a minion checks on its summoner. */
    private static final int CHECK_INTERVAL = 10;
    /** A summoner not loaded for this long (its chunk unloaded, say) counts as gone. */
    private static final int MISSING_TICKS = 100;
    /** How long a removed summoner is remembered, for minions that tick again later. */
    private static final int GONE_TICKS = 1200;

    /**
     * The run mob the server thread acts for, with the entity whose tick or hurt pushed it. A null
     * {@code acting} is a barrier: inside it nothing is adopted (a player being hurt, a conversion,
     * {@code spawnAsMinions} for a summoner that is not a run mob).
     */
    public record Frame(Entity pushedBy, @Nullable LivingEntity acting) {
    }

    private static final ThreadLocal<ArrayDeque<Frame>> ACTING = ThreadLocal.withInitial(ArrayDeque::new);
    /** Summoners killed or discarded (not unloaded), by UUID, with the game time. */
    private static final Map<UUID, Long> GONE = new HashMap<>();
    /** Minions whose summoner isn't loaded: {first noticed, last checked} game times. */
    private static final Map<UUID, long[]> MISSING = new HashMap<>();
    /** Top summoner → its minions (added, or loaded from disk), so they can go with it at once. */
    private static final Map<UUID, Set<UUID>> MINIONS = new HashMap<>();

    private RunMinions() {
    }

    // ── scaling ──────────────────────────────────────────────────────────────

    /** Notes the health and damage scaling a run mob was spawned with, for its minions. */
    public static void recordScale(LivingEntity runMob, double healthMultiplier, double damageMultiplier) {
        // getTags() may be a read-only copy (another mod in the pack does that): remove through removeTag
        for (String tag : List.copyOf(runMob.getTags())) {
            if (tag.startsWith(SCALE_TAG_PREFIX)) {
                runMob.removeTag(tag);
            }
        }
        runMob.addTag(SCALE_TAG_PREFIX + format(healthMultiplier) + ":" + format(damageMultiplier));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static double[] scaleOf(Entity runMob) {
        for (String tag : runMob.getTags()) {
            if (tag.startsWith(SCALE_TAG_PREFIX)) {
                String[] parts = tag.substring(SCALE_TAG_PREFIX.length()).split(":");
                try {
                    return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
                } catch (RuntimeException e) {
                    break;
                }
            }
        }
        return new double[]{1.0, 1.0};
    }

    // ── who is acting ────────────────────────────────────────────────────────

    private static boolean isRunMob(@Nullable Entity entity) {
        return entity != null && entity.getTags().contains(EntityEquipmentHelper.RUN_MOB_TAG);
    }

    /** The run mob an entity acts for: itself, or the run mob that shot it or owns it. */
    private static @Nullable LivingEntity actingFor(Entity entity) {
        if (entity instanceof LivingEntity living && isRunMob(living)) {
            return living;
        }
        if (entity instanceof Projectile || entity instanceof TraceableEntity) {
            Entity owner = entity instanceof Projectile projectile ? projectile.getOwner()
                : ((TraceableEntity) entity).getOwner();
            if (owner instanceof LivingEntity living && isRunMob(living)) {
                return living;
            }
        }
        return null;
    }

    /**
     * Before an entity ticks: act for its run mob, if it has one. Returns the frame to give to
     * {@link #pop} in a {@code finally} (null when nothing was pushed).
     */
    public static @Nullable Frame push(Entity entity) {
        LivingEntity acting = actingFor(entity);
        return acting != null ? pushFrame(new Frame(entity, acting)) : null;
    }

    /**
     * Before a living entity is hurt: a run mob acts for itself; anything else hurt inside a run
     * mob's turn (a player, a pet) gets a barrier, so what its own hurt triggers isn't adopted.
     */
    public static @Nullable Frame pushHurt(LivingEntity entity) {
        if (isRunMob(entity)) {
            return pushFrame(new Frame(entity, entity));
        }
        return ACTING.get().isEmpty() ? null : pushFrame(new Frame(entity, null));
    }

    /** A barrier: nothing added inside it is adopted (used around conversions). */
    public static @Nullable Frame pushBarrier(Entity entity) {
        return ACTING.get().isEmpty() ? null : pushFrame(new Frame(entity, null));
    }

    private static Frame pushFrame(Frame frame) {
        ACTING.get().push(frame);
        return frame;
    }

    /** Removes exactly {@code frame} (and anything left above it), if it is still there. */
    public static void pop(@Nullable Frame frame) {
        if (frame == null) {
            return;
        }
        ArrayDeque<Frame> stack = ACTING.get();
        boolean present = false;
        for (Frame f : stack) {
            if (f == frame) {
                present = true;
                break;
            }
        }
        while (present && !stack.isEmpty()) {
            if (stack.pop() == frame) {
                break;
            }
        }
    }

    /**
     * Runs {@code body} acting for {@code summoner}: mobs it adds become its minions when the
     * summoner is a run mob. Otherwise it is a barrier and {@code body} just runs.
     */
    public static <T> T actingAs(LivingEntity summoner, Supplier<T> body) {
        Frame frame = pushFrame(new Frame(summoner, isRunMob(summoner) ? summoner : null));
        try {
            return body.get();
        } finally {
            pop(frame);
        }
    }

    // ── adoption ─────────────────────────────────────────────────────────────

    /**
     * {@code ServerLevel.addFreshEntity}: adopts the new mob if a run mob spawned it.
     *
     * @return false to cancel the spawn (a dying run mob's death spawn)
     */
    public static boolean onAdd(ServerLevel level, Entity entity) {
        if (!(entity instanceof Mob mob) || isRunMob(mob)) {
            return true; // not a mob, or one of the run's own (spawners mark them before adding)
        }
        LivingEntity summoner = null;
        if (hasOwner(mob)) {
            Entity owner = ownerOf(level, mob);
            if (!(owner instanceof LivingEntity living) || !isRunMob(living)) {
                return true; // someone else's: a player's pet or summon, another mod's mob
            }
            summoner = living;
        } else {
            Frame frame = ACTING.get().peek();
            if (frame != null && frame.acting() != null && isRunMob(frame.acting())) {
                summoner = frame.acting();
            }
        }
        if (summoner == null) {
            return true;
        }
        LivingEntity root = rootOf(level, summoner);
        if (!root.isAlive()) {
            DebugLog.log("minion: {} not spawned, its summoner {} is dying", mob.getType(), root.getUUID());
            return false;
        }
        adopt(level, mob, root);
        return true;
    }

    /**
     * The owner, whatever kind of entity it is. {@code OwnableEntity.getOwner} only looks among
     * players by default, so a mob owner is resolved by its UUID here.
     */
    private static @Nullable Entity ownerOf(ServerLevel level, Mob mob) {
        if (mob instanceof OwnableEntity ownable) {
            UUID owner = ownable.getOwnerUUID();
            return owner == null ? null : level.getEntity(owner);
        }
        return ((TraceableEntity) mob).getOwner();
    }

    /** Owned by anyone, loaded or not (a tamed mob's owner may be offline). */
    private static boolean hasOwner(Mob mob) {
        if (mob instanceof OwnableEntity ownable) {
            return ownable.getOwnerUUID() != null;
        }
        return mob instanceof TraceableEntity traceable && traceable.getOwner() != null;
    }

    /** A minion's summoner stands for its own summoner: minions of minions share the top one. */
    private static LivingEntity rootOf(ServerLevel level, LivingEntity summoner) {
        UUID root = summonerOf(summoner);
        if (root != null && level.getEntity(root) instanceof LivingEntity living) {
            return living;
        }
        return summoner;
    }

    private static void adopt(ServerLevel level, Mob mob, LivingEntity root) {
        EntityEquipmentHelper.markRunMob(mob);
        mob.addTag(EntityEquipmentHelper.NO_NATURAL_LOOT_TAG);
        mob.addTag(MINION_TAG);
        mob.addTag(SUMMONER_TAG_PREFIX + root.getUUID());

        double[] scale = scaleOf(root);
        multiplyBase(mob, Attributes.MAX_HEALTH, scale[0]);
        multiplyBase(mob, Attributes.ATTACK_DAMAGE, scale[1]);
        mob.setHealth(mob.getMaxHealth());
        DebugLog.log("minion: {} adopted by {} ({}), scale {}x health {}x damage", mob.getType(), root.getUUID(),
            root.getType(), scale[0], scale[1]);
    }

    /**
     * {@code ServerLevel.addFreshEntity} returned: a minion that made it into the world joins its
     * summoner's team and is remembered with it.
     */
    public static void afterAdd(ServerLevel level, Entity entity, boolean added) {
        if (!added || !isMinion(entity)) {
            return;
        }
        UUID root = summonerOf(entity);
        if (root == null) {
            return;
        }
        MINIONS.computeIfAbsent(root, id -> new HashSet<>()).add(entity.getUUID());
        if (level.getEntity(root) instanceof LivingEntity summoner && summoner.getTeam() instanceof PlayerTeam team) {
            level.getScoreboard().addPlayerToTeam(entity.getScoreboardName(), team);
        }
    }

    /** {@code Entity.load}: a minion read back from disk is remembered with its summoner again. */
    public static void onLoaded(Entity entity) {
        UUID root = summonerOf(entity);
        if (root != null) {
            MINIONS.computeIfAbsent(root, id -> new HashSet<>()).add(entity.getUUID());
        }
    }

    private static void multiplyBase(Mob mob, Holder<Attribute> attribute, double factor) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null && factor != 1.0) {
            instance.setBaseValue(instance.getBaseValue() * factor);
        }
    }

    private static @Nullable UUID summonerOf(Entity minion) {
        if (!minion.getTags().contains(MINION_TAG)) {
            return null;
        }
        for (String tag : minion.getTags()) {
            if (tag.startsWith(SUMMONER_TAG_PREFIX)) {
                try {
                    return UUID.fromString(tag.substring(SUMMONER_TAG_PREFIX.length()));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    public static boolean isMinion(Entity entity) {
        return entity.getTags().contains(MINION_TAG);
    }

    /** Whether {@code entity} is a minion of the run mob with this UUID. */
    public static boolean isMinionOf(Entity entity, UUID summoner) {
        return summoner.equals(summonerOf(entity));
    }

    // ── vanishing ────────────────────────────────────────────────────────────

    /**
     * Before an entity ticks: a minion whose summoner died or is gone vanishes.
     *
     * @return true when the minion was removed (skip its tick)
     */
    public static boolean vanishIfOrphaned(ServerLevel level, Entity entity) {
        if (entity.tickCount % CHECK_INTERVAL != 0 || !isMinion(entity)) {
            return false;
        }
        UUID root = summonerOf(entity);
        if (root == null) {
            return false;
        }
        long now = level.getGameTime();
        boolean orphaned;
        if (GONE.containsKey(root)) {
            orphaned = true;
        } else {
            Entity summoner = level.getEntity(root);
            if (summoner == null) {
                // Not loaded: its chunk may load after ours (a restart). Gone if it stays away
                // while we tick; a gap in our own checks (we weren't ticking) starts the wait over.
                long[] seen = MISSING.computeIfAbsent(entity.getUUID(), id -> new long[]{now, now});
                if (now - seen[1] > CHECK_INTERVAL * 3L) {
                    seen[0] = now;
                }
                seen[1] = now;
                orphaned = now - seen[0] >= MISSING_TICKS;
            } else {
                MISSING.remove(entity.getUUID());
                orphaned = !summoner.isAlive();
            }
        }
        if (orphaned) {
            MISSING.remove(entity.getUUID());
            DebugLog.log("minion: {} vanishes with its summoner {}", entity.getType(), root);
            entity.discard();
        }
        return orphaned;
    }

    /**
     * {@code Entity.remove}: a run mob killed or discarded (not unloaded) takes its loaded minions
     * with it right away. This matters at a run's end, where the summoners are discarded in the same
     * tick the players leave and the chunks are let go: the minions would never tick again.
     */
    public static void onRemoved(Entity entity, Entity.RemovalReason reason) {
        if (!reason.shouldDestroy() || !isRunMob(entity) || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        UUID id = entity.getUUID();
        UUID root = summonerOf(entity);
        if (root != null) {
            Set<UUID> siblings = MINIONS.get(root);
            if (siblings != null) {
                siblings.remove(id);
            }
            return; // a minion: its own minions belong to the same top summoner
        }
        GONE.put(id, level.getGameTime());
        Set<UUID> minions = MINIONS.remove(id);
        if (minions == null) {
            return;
        }
        for (UUID minionId : List.copyOf(minions)) {
            Entity minion = level.getEntity(minionId);
            if (minion != null && !minion.isRemoved()) {
                DebugLog.log("minion: {} vanishes with its summoner {}", minion.getType(), id);
                minion.discard();
            }
        }
    }

    /** Minions drop no experience. */
    public static boolean dropsNoExperience(LivingEntity entity) {
        return isMinion(entity);
    }

    /** Server tick end: forgets old removals and checks the acting stack is empty. */
    public static void endTick(MinecraftServer server) {
        ArrayDeque<Frame> stack = ACTING.get();
        if (!stack.isEmpty()) {
            ArenasLdMod.LOGGER.warn("{} run mob frame(s) left at the end of a tick; cleared", stack.size());
            stack.clear();
        }
        if (server.getTickCount() % 200 == 0) {
            long now = server.overworld() != null ? server.overworld().getGameTime() : 0;
            GONE.values().removeIf(at -> now - at > GONE_TICKS);
            MISSING.values().removeIf(seen -> now - seen[1] > GONE_TICKS);
        }
    }

    /** Server stopped: forget everything (singleplayer reopens a world). */
    public static void clear() {
        GONE.clear();
        MISSING.clear();
        MINIONS.clear();
    }

}
