package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.activity.ActivityHost;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.run.ArenaPhase;
import net.ledok.arenas_ld.arena.run.ArenaRun;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.DungeonPhase;
import net.ledok.arenas_ld.dungeon.run.DungeonRun;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.platform.ArenasEvents;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.raid.run.RaidPhase;
import net.ledok.arenas_ld.raid.run.RaidRun;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * What players leave behind in an instance: Spell Engine summons, projectiles, pets, thrown items.
 * Instances have no bounds to sweep (raid copies sit ~80 blocks apart), so Arenas_LD follows the
 * players instead.
 *
 * <p><b>Tracking.</b> An entity freshly added to a level whose owner chain ({@link OwnableEntity},
 * {@link TraceableEntity}: projectiles, thrown items, fangs, clouds…) leads to a player who is in a
 * run is tagged {@code arenas_ld_run_owned:<run id>:<owner uuid>} and remembered. The run id is a
 * short hash of the instance (dimension + spawner/boss spawner position) and the run's start tick,
 * so a leftover from an earlier run in the same instance never passes for this run's. Run mobs and
 * minions, players and fixtures (armor stands, item frames, paintings, leash knots, display /
 * marker / interaction entities, NPCs_LD NPCs) are never tracked.
 *
 * <p><b>Leaving.</b> When a player's run is over for them (the run closes, or they leave it alone:
 * {@code /exit}, hardcore death, disconnect expiry), after they were sent home, their tracked
 * entities that are loaded are handled: Spell Engine summons, projectiles and other non-living
 * leftovers are discarded; living ones (tamed wolves, Saint's Dragons dragons, horses) are never
 * deleted but teleported to their owner; thrown items, and a thrown trident (a non-stackable
 * projectile its owner may pick up), go back into the owner's inventory (or drop at their feet). Pets and items of an offline owner stay where they are and keep their tag; they are
 * tried again when the owner joins and whenever they load from disk.
 *
 * <p><b>Loading.</b> A tagged entity that loads from disk (its chunk wasn't loaded when the run ended)
 * is handled the same way at the end of that tick, unless its owner is still in that very run.
 *
 * <p><b>Run start.</b> Spell Engine summons of the party within {@value #START_SWEEP_RADIUS} blocks of
 * where each player landed that don't belong to the new run are discarded; real pets are kept.
 *
 * <p><b>Cost.</b> The fresh-entity hook runs for every entity on the server. It is a few
 * {@code instanceof} checks and, for entities with an owner, one {@link HashMap} lookup in the
 * participant index (player UUID → run). The index is kept up to date where players enter and leave
 * runs, and rebuilt from the controllers every {@value #REBUILD_INTERVAL} ticks and after a join as a
 * safety net (a restart, a missed path); the rebuild visits only loaded controllers and their runs.
 *
 * <p>Server thread only, except {@link #onLoaded}, which only queues.
 */
public final class RunLeftovers {
    public static final String TAG_PREFIX = "arenas_ld_run_owned:";
    static final String SPELL_SUMMON_CLASS = "net.spell_engine.entity.SummonedEntity";
    /** Run start: how far around a landing player their leftover spell summons are looked for. */
    public static final int START_SWEEP_RADIUS = 48;
    private static final int REBUILD_INTERVAL = 100;
    /** How many owner links are followed (a summon's arrow is its player's). */
    private static final int OWNER_DEPTH = 3;

    /** One run: its instance and start tick. */
    public record RunKey(ResourceKey<Level> dimension, BlockPos pos, long startTick) {
        public String id() {
            return runId(dimension.location().toString(), pos.getX(), pos.getY(), pos.getZ(), startTick);
        }

        public static RunKey of(DungeonRun run) {
            return new RunKey(run.dbsDimension(), run.dbsPos(), run.startTick());
        }

        public static RunKey of(RaidRun run) {
            return new RunKey(run.spawnerDimension() != null ? run.spawnerDimension() : Level.OVERWORLD,
                run.spawnerPos(), run.startTick());
        }

        public static RunKey of(ArenaRun run) {
            return new RunKey(run.spawnerDimension(), run.spawnerPos(), run.startTick());
        }
    }

    /** The run a player is in, with a label for the log. */
    private record Membership(RunKey key, String runId, String label) {
    }

    /** A tracked entity's run and owner. */
    private record Tracked(String runId, UUID owner) {
    }

    /** A parsed run-owned tag. */
    public record OwnedTag(String runId, UUID owner) {
    }

    /** What happens to a leftover. */
    public enum Kind {
        /** Spell Engine summons, projectiles, other non-living leftovers. */
        DISCARD,
        /** Living and owned: teleported to its owner, never deleted. */
        PET,
        /** A thrown item, or a thrown trident (any one-of-a-kind projectile its owner can pick up): back to its owner. */
        ITEM
    }

    /** Player UUID → the run they're in (non-REMOVED participant of a run that isn't DONE). */
    private static final Map<UUID, Membership> MEMBERS = new HashMap<>();
    /** Entity UUID → the run it was tagged for. Loaded entities, plus ones kept for an offline owner. */
    private static final Map<UUID, Tracked> TRACKED = new HashMap<>();
    /** Run id → log label, for runs that ended (a leftover loading later still gets a readable log). */
    private static final Map<String, String> LABELS = new HashMap<>();
    /** Tagged entities read from disk, handled at the end of the tick (never inside a chunk load). */
    private static final ConcurrentLinkedQueue<Entity> LOADED = new ConcurrentLinkedQueue<>();
    private static boolean rebuildRequested = true;

    private RunLeftovers() {
    }

    public static void register() {
        ArenasEvents.SERVER_TICK_END.add(RunLeftovers::endTick);
        ArenasEvents.SERVER_STOPPED.add(server -> clear());
        ArenasEvents.PLAYER_JOIN.add((player, server) -> {
            rebuildRequested = true;
            UUID uuid = player.getUUID();
            // Two ticks: after the run's own join handling (a pending eject sends them home first).
            ServerTaskScheduler.nextTick(s -> ServerTaskScheduler.nextTick(s2 -> retryFor(s2, uuid)));
        });
    }

    // ── pure helpers (unit tested) ───────────────────────────────────────────

    /** A short hash of an instance and a run start: 8 hex digits. */
    public static String runId(String dimension, int x, int y, int z, long startTick) {
        String text = dimension + "|" + x + "|" + y + "|" + z + "|" + startTick;
        long hash = 0xcbf29ce484222325L; // FNV-1a 64
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x100000001b3L;
        }
        return String.format(Locale.ROOT, "%08x", (int) (hash ^ (hash >>> 32)));
    }

    public static String tag(String runId, UUID owner) {
        return TAG_PREFIX + runId + ":" + owner;
    }

    /** The run and owner in a run-owned tag; null for any other tag or a damaged one. */
    public static @Nullable OwnedTag parseTag(String tag) {
        if (!tag.startsWith(TAG_PREFIX)) {
            return null;
        }
        String rest = tag.substring(TAG_PREFIX.length());
        int colon = rest.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        try {
            return new OwnedTag(rest.substring(0, colon), UUID.fromString(rest.substring(colon + 1)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Summons are discarded, other living things are pets, items go back, the rest is discarded.
     *
     * @param pickupOnlyOne a projectile its owner can pick up that isn't stackable (a trident): its
     *                      item goes back instead of being lost with it
     */
    public static Kind classify(boolean spellSummon, boolean item, boolean living, boolean pickupOnlyOne) {
        if (spellSummon) {
            return Kind.DISCARD;
        }
        if (item || pickupOnlyOne) {
            return Kind.ITEM;
        }
        return living ? Kind.PET : Kind.DISCARD;
    }

    // ── participant index ────────────────────────────────────────────────────

    /** A player entered a run (the run start, after the participant was added). */
    public static void enter(@Nullable ActivityHost host, RunKey key, UUID player) {
        String runId = key.id();
        String label = label(host, key);
        LABELS.put(runId, label);
        MEMBERS.put(player, new Membership(key, runId, label));
    }

    /** Whether the player is in a run (as the index has it): O(1). */
    public static boolean isInRun(UUID player) {
        return MEMBERS.containsKey(player);
    }

    private static String label(@Nullable ActivityHost host, RunKey key) {
        String activity = host == null ? "run"
            : host.activityId() != null ? host.activityId().toString()
            : host.activityType().name().toLowerCase(Locale.ROOT);
        BlockPos pos = key.pos();
        return activity + " " + key.dimension().location() + " " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /**
     * Rebuilds the index from the loaded controllers' runs. Adds players missing from it (after a
     * restart); drops players no longer in a run (their leftovers are handled when they load or the
     * owner joins, not here: an unloaded controller must not end anyone's run).
     */
    private static void rebuild(MinecraftServer server) {
        Map<UUID, Membership> fresh = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (BlockPos pos : ArenasLdMod.DUNGEON_MANAGER.getControllersIn(level.dimension())) {
                if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof DungeonControllerBlockEntity controller) {
                    for (DungeonRun run : controller.getActiveRuns().values()) {
                        if (run.phase() != DungeonPhase.DONE) {
                            addMembers(fresh, controller, RunKey.of(run),
                                run.participants());
                        }
                    }
                }
            }
        }
        for (RaidControllerBlockEntity.ControllerKey key : RaidControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level != null && level.isLoaded(key.pos())
                && level.getBlockEntity(key.pos()) instanceof RaidControllerBlockEntity controller) {
                for (RaidRun run : controller.getActiveRuns().values()) {
                    if (run.phase() != RaidPhase.DONE && run.spawnerDimension() != null) {
                        addMembers(fresh, controller, RunKey.of(run),
                            run.participants());
                    }
                }
            }
        }
        for (ArenaControllerBlockEntity.ControllerKey key : ArenaControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level != null && level.isLoaded(key.pos())
                && level.getBlockEntity(key.pos()) instanceof ArenaControllerBlockEntity controller) {
                for (ArenaRun run : controller.getActiveRuns().values()) {
                    if (run.phase() != ArenaPhase.DONE) {
                        addMembers(fresh, controller, RunKey.of(run),
                            run.participants());
                    }
                }
            }
        }
        for (Map.Entry<UUID, Membership> entry : MEMBERS.entrySet()) {
            Membership now = fresh.get(entry.getKey());
            if (now == null || !now.runId().equals(entry.getValue().runId())) {
                DebugLog.log("run leftovers: {} no longer in run {}", entry.getKey(), entry.getValue().label());
            }
        }
        MEMBERS.clear();
        MEMBERS.putAll(fresh);
        if (LABELS.size() > 1000) {
            Set<String> used = new HashSet<>();
            MEMBERS.values().forEach(m -> used.add(m.runId()));
            TRACKED.values().forEach(t -> used.add(t.runId()));
            LABELS.keySet().retainAll(used);
        }
    }

    private static void addMembers(Map<UUID, Membership> into, ActivityHost host, RunKey key,
                                   Map<UUID, RunParticipant> participants) {
        String runId = key.id();
        String label = LABELS.computeIfAbsent(runId, id -> label(host, key));
        Membership membership = new Membership(key, runId, label);
        for (RunParticipant participant : participants.values()) {
            if (participant.status() != RunParticipant.ParticipantStatus.REMOVED) {
                into.put(participant.playerUuid(), membership);
            }
        }
    }

    // ── tracking ─────────────────────────────────────────────────────────────

    /** {@code ServerLevel.addFreshEntity} added {@code entity}: tag it if a run player owns it. */
    public static void onFreshEntity(ServerLevel level, Entity entity) {
        if (MEMBERS.isEmpty() || entity instanceof Player || isRunMob(entity) || isFixture(entity)) {
            return;
        }
        UUID owner = runPlayerOwner(level, entity);
        if (owner == null) {
            return;
        }
        Membership membership = MEMBERS.get(owner);
        tagFor(entity, membership.runId(), owner);
    }

    private static void tagFor(Entity entity, String runId, UUID owner) {
        removeTags(entity);
        entity.addTag(tag(runId, owner));
        TRACKED.put(entity.getUUID(), new Tracked(runId, owner));
    }

    /** The run player at the end of the entity's owner chain, or null. */
    private static @Nullable UUID runPlayerOwner(ServerLevel level, Entity entity) {
        Entity current = entity;
        for (int depth = 0; depth < OWNER_DEPTH; depth++) {
            UUID ownerId;
            Entity ownerEntity = null;
            if (current instanceof OwnableEntity ownable) {
                ownerId = ownable.getOwnerUUID();
            } else if (current instanceof TraceableEntity traceable) {
                ownerEntity = traceable.getOwner();
                ownerId = ownerEntity == null ? null : ownerEntity.getUUID();
            } else {
                return null;
            }
            if (ownerId == null) {
                return null;
            }
            if (MEMBERS.containsKey(ownerId)) {
                return ownerId;
            }
            if (ownerEntity == null) {
                ownerEntity = level.getEntity(ownerId);
            }
            if (ownerEntity == null || ownerEntity instanceof Player || ownerEntity == current) {
                return null;
            }
            current = ownerEntity;
        }
        return null;
    }

    /** {@code Entity.load}: a tagged entity read back from disk is looked at the end of the tick. */
    public static void onLoaded(Entity entity) {
        if (entity instanceof Player || entity.level().isClientSide() || entity.getTags().isEmpty()) {
            return;
        }
        for (String tag : entity.getTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                LOADED.add(entity);
                return;
            }
        }
    }

    /** {@code Entity.remove}: a tracked entity that died or was discarded is forgotten. */
    public static void onRemoved(Entity entity, Entity.RemovalReason reason) {
        if (reason.shouldDestroy() && !TRACKED.isEmpty()) {
            TRACKED.remove(entity.getUUID());
        }
    }

    // ── leaving ──────────────────────────────────────────────────────────────

    /**
     * A player's run is over for them; call after they were sent home (or found offline). Handles
     * their loaded leftovers from that run. Safe to call more than once and for a player who already
     * entered another run (only this run's entities and membership are touched).
     */
    public static void left(MinecraftServer server, UUID player, RunKey key) {
        String runId = key.id();
        Membership membership = MEMBERS.get(player);
        if (membership != null && membership.runId().equals(runId)) {
            MEMBERS.remove(player);
        }
        if (TRACKED.isEmpty()) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(player);
        Report report = new Report();
        for (Map.Entry<UUID, Tracked> entry : List.copyOf(TRACKED.entrySet())) {
            Tracked tracked = entry.getValue();
            if (!tracked.runId().equals(runId) || !tracked.owner().equals(player)) {
                continue;
            }
            Entity entity = find(server, entry.getKey(), key.dimension());
            if (entity == null) {
                TRACKED.remove(entry.getKey()); // not loaded: handled when it loads
                continue;
            }
            if (handle(entity, owner, report)) {
                TRACKED.remove(entry.getKey());
            }
        }
        report.log(LABELS.getOrDefault(runId, runId), owner != null ? owner.getGameProfile().getName() : player + " (offline)");
    }

    /** The owner joined: their pets and items kept for them (owner was offline) come over now. */
    private static void retryFor(MinecraftServer server, UUID player) {
        ServerPlayer owner = server.getPlayerList().getPlayer(player);
        if (owner == null || TRACKED.isEmpty()) {
            return;
        }
        Membership membership = MEMBERS.get(player);
        Map<String, Report> reports = new HashMap<>();
        for (Map.Entry<UUID, Tracked> entry : List.copyOf(TRACKED.entrySet())) {
            Tracked tracked = entry.getValue();
            if (!tracked.owner().equals(player) || membership != null && membership.runId().equals(tracked.runId())) {
                continue;
            }
            Entity entity = find(server, entry.getKey(), null);
            if (entity == null) {
                TRACKED.remove(entry.getKey());
                continue;
            }
            if (handle(entity, owner, reports.computeIfAbsent(tracked.runId(), id -> new Report()))) {
                TRACKED.remove(entry.getKey());
            }
        }
        reports.forEach((runId, report) -> report.log(LABELS.getOrDefault(runId, runId), owner.getGameProfile().getName()));
    }

    /**
     * Handles one leftover. Returns false when it was kept for later (a pet or item of an offline
     * owner); it keeps its tag then.
     */
    private static boolean handle(Entity entity, @Nullable ServerPlayer owner, Report report) {
        if (entity.isRemoved()) {
            return true;
        }
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        switch (kindOf(entity)) {
            case DISCARD -> {
                removeTags(entity);
                entity.ejectPassengers();
                entity.discard();
                report.removed.merge(type, 1, Integer::sum);
                return true;
            }
            case ITEM -> {
                if (owner == null) {
                    report.keptItems++;
                    return false;
                }
                removeTags(entity);
                ItemStack stack = entity instanceof ItemEntity item ? item.getItem().copy()
                    : ((AbstractArrow) entity).getPickupItemStackOrigin().copy();
                entity.discard();
                if (!owner.getInventory().add(stack) && !stack.isEmpty()) {
                    ItemEntity dropped = owner.drop(stack, false);
                    if (dropped != null) {
                        dropped.setNoPickUpDelay();
                        dropped.setTarget(owner.getUUID());
                    }
                }
                report.items++;
                return true;
            }
            default -> {
                if (owner == null) {
                    report.keptPets++;
                    ArenasLdMod.LOGGER.info("[Arenas] {} ({}) stays where it is: its owner is offline.", type, entity.getUUID());
                    return false;
                }
                // Untagged first: a teleport to another dimension copies the entity with its tags.
                removeTags(entity);
                boolean moved = entity.teleportTo(owner.serverLevel(), owner.getX(), owner.getY(), owner.getZ(),
                    Set.of(), entity.getYRot(), entity.getXRot());
                if (moved) {
                    report.pets++;
                    // Its owner is in another run already: it belongs to that one now.
                    Membership membership = MEMBERS.get(owner.getUUID());
                    Entity arrived = owner.serverLevel().getEntity(entity.getUUID());
                    if (membership != null && arrived != null) {
                        tagFor(arrived, membership.runId(), owner.getUUID());
                    }
                } else {
                    ArenasLdMod.LOGGER.info("[Arenas] {} ({}) couldn't be returned to {}; it stays where it is.",
                        type, entity.getUUID(), owner.getGameProfile().getName());
                }
                return true;
            }
        }
    }

    private static Kind kindOf(Entity entity) {
        boolean pickupOnlyOne = entity instanceof AbstractArrow arrow && arrow.pickup == AbstractArrow.Pickup.ALLOWED
            && !arrow.getPickupItemStackOrigin().isEmpty() && arrow.getPickupItemStackOrigin().getMaxStackSize() == 1;
        return classify(isSpellSummon(entity), entity instanceof ItemEntity, entity instanceof LivingEntity, pickupOnlyOne);
    }

    private static @Nullable Entity find(MinecraftServer server, UUID uuid, @Nullable ResourceKey<Level> first) {
        ServerLevel firstLevel = first == null ? null : server.getLevel(first);
        if (firstLevel != null) {
            Entity entity = firstLevel.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level != firstLevel) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) {
                    return entity;
                }
            }
        }
        return null;
    }

    // ── run start ────────────────────────────────────────────────────────────

    /**
     * The party was teleported in: discards their Spell Engine summons near where they landed that
     * don't belong to this run (an earlier run's leftovers). Pets are kept.
     */
    public static void startSweep(RunKey key, List<ServerPlayer> players) {
        String runId = key.id();
        Map<String, Integer> removed = new TreeMap<>();
        Set<UUID> party = new HashSet<>();
        for (ServerPlayer player : players) {
            party.add(player.getUUID());
        }
        for (ServerPlayer player : players) {
            ServerLevel level = player.serverLevel();
            AABB box = player.getBoundingBox().inflate(START_SWEEP_RADIUS);
            for (Entity entity : level.getEntities((Entity) null, box, e -> isSpellSummon(e) && !e.isRemoved())) {
                UUID owner = ownerId(entity);
                OwnedTag tag = ownedTag(entity);
                if (owner == null || !party.contains(owner) || tag != null && tag.runId().equals(runId)) {
                    continue;
                }
                removed.merge(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), 1, Integer::sum);
                TRACKED.remove(entity.getUUID());
                entity.ejectPassengers();
                entity.discard();
            }
        }
        if (!removed.isEmpty()) {
            ArenasLdMod.LOGGER.info("[Arenas] Run start ({}): removed leftover summons {}",
                LABELS.getOrDefault(runId, runId), removed);
        }
    }

    private static @Nullable UUID ownerId(Entity entity) {
        if (entity instanceof OwnableEntity ownable) {
            return ownable.getOwnerUUID();
        }
        if (entity instanceof TraceableEntity traceable) {
            Entity owner = traceable.getOwner();
            return owner == null ? null : owner.getUUID();
        }
        OwnedTag tag = ownedTag(entity);
        return tag == null ? null : tag.owner();
    }

    // ── tick ─────────────────────────────────────────────────────────────────

    private static void endTick(MinecraftServer server) {
        if (rebuildRequested || server.getTickCount() % REBUILD_INTERVAL == 0) {
            rebuildRequested = false;
            rebuild(server);
        }
        if (LOADED.isEmpty()) {
            return;
        }
        // One log line per run and owner.
        Map<OwnedTag, Report> reports = new HashMap<>();
        Entity entity;
        while ((entity = LOADED.poll()) != null) {
            handleLoaded(server, entity, reports);
        }
        reports.forEach((key, report) -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(key.owner());
            report.log(LABELS.getOrDefault(key.runId(), key.runId()),
                owner != null ? owner.getGameProfile().getName() : key.owner() + " (offline)");
        });
    }

    /** A tagged entity that loaded from disk: still its owner's run's, or a leftover. */
    private static void handleLoaded(MinecraftServer server, Entity entity, Map<OwnedTag, Report> reports) {
        if (entity.isRemoved() || !(entity.level() instanceof ServerLevel level) || level.getEntity(entity.getUUID()) != entity) {
            return; // never made it into the world, or was copied elsewhere since
        }
        OwnedTag tag = ownedTag(entity);
        if (tag == null) {
            return;
        }
        Membership membership = MEMBERS.get(tag.owner());
        if (membership != null && membership.runId().equals(tag.runId())) {
            TRACKED.put(entity.getUUID(), new Tracked(tag.runId(), tag.owner())); // its run goes on
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(tag.owner());
        if (handle(entity, owner, reports.computeIfAbsent(tag, t -> new Report()))) {
            TRACKED.remove(entity.getUUID());
        } else {
            TRACKED.put(entity.getUUID(), new Tracked(tag.runId(), tag.owner()));
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    public static @Nullable OwnedTag ownedTag(Entity entity) {
        for (String tag : entity.getTags()) {
            OwnedTag parsed = parseTag(tag);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static void removeTags(Entity entity) {
        // getTags() may be a read-only copy (another mod in the pack does that): remove through removeTag
        for (String tag : List.copyOf(entity.getTags())) {
            if (tag.startsWith(TAG_PREFIX)) {
                entity.removeTag(tag);
            }
        }
    }

    private static boolean isRunMob(Entity entity) {
        return entity.getTags().contains(EntityEquipmentHelper.RUN_MOB_TAG) || RunMinions.isMinion(entity);
    }

    /** Things that belong to the build, never a player's leftovers. */
    private static boolean isFixture(Entity entity) {
        if (entity instanceof ArmorStand || entity instanceof BlockAttachedEntity || entity instanceof Display
            || entity instanceof Marker || entity instanceof Interaction) {
            return true;
        }
        return "npcs_ld".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace());
    }

    static boolean isSpellSummon(Entity entity) {
        for (Class<?> c = entity.getClass(); c != null; c = c.getSuperclass()) {
            if (SPELL_SUMMON_CLASS.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Server stopped: forget everything (singleplayer reopens a world). */
    public static void clear() {
        MEMBERS.clear();
        TRACKED.clear();
        LABELS.clear();
        LOADED.clear();
        rebuildRequested = true;
    }

    /** Counts for one cleanup's log line. */
    private static final class Report {
        final Map<String, Integer> removed = new TreeMap<>();
        int pets;
        int items;
        int keptPets;
        int keptItems;

        void log(String label, @Nullable String player) {
            if (removed.isEmpty() && pets == 0 && items == 0 && keptPets == 0 && keptItems == 0) {
                return;
            }
            StringBuilder line = new StringBuilder();
            line.append("removed ").append(removed.isEmpty() ? "nothing" : removed.toString())
                .append(", returned ").append(pets).append(" pet(s), returned ").append(items).append(" item stack(s)");
            if (keptPets > 0 || keptItems > 0) {
                line.append(", kept ").append(keptPets).append(" pet(s) and ").append(keptItems)
                    .append(" item stack(s) for an offline owner");
            }
            ArenasLdMod.LOGGER.info("[Arenas] Run cleanup ({}{}): {}", label, player == null ? "" : ", " + player, line);
        }
    }
}
