package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Keeps the {@link ActivityIndex} in step with the controllers and answers the API's activity
 * queries. Controllers {@link #attach} on their first server tick, {@link #refresh} after an admin
 * edit, and {@link #detach} when their block is broken. Server thread only.
 */
public final class ActivityRegistry {
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static final int MAX_SLUG_LENGTH = 32;

    private ActivityRegistry() {
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    private static void fireChanged() {
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (Throwable t) {
                ArenasLdMod.LOGGER.error("Activities listener {} failed", listener, t);
            }
        }
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public static List<ArenasApi.Activity> activities(MinecraftServer server) {
        if (server.overworld() == null) {
            return List.of(); // before the worlds exist (e.g. SERVER_STARTING)
        }
        return ActivityIndex.get(server).entries().stream()
            .sorted(Comparator.comparing((ActivityIndex.Entry e) -> e.type().ordinal())
                .thenComparing(e -> e.id().toString()))
            .map(ActivityRegistry::toActivity)
            .toList();
    }

    /** Every controller the world knows of (it may be gone since); read straight from the saved index. */
    public static List<ActivityIndex.Entry> knownControllers(MinecraftServer server) {
        if (server.overworld() == null) {
            return List.of();
        }
        return List.copyOf(ActivityIndex.get(server).entries());
    }

    public static Optional<ArenasApi.Activity> activity(MinecraftServer server, ResourceLocation id) {
        if (server.overworld() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ActivityIndex.get(server).get(id)).map(ActivityRegistry::toActivity);
    }

    private static ArenasApi.Activity toActivity(ActivityIndex.Entry entry) {
        return new ArenasApi.Activity(entry.id(), entry.type(), displayName(entry.type(), entry.name()),
            entry.difficulties());
    }

    /** The admin name, or "Unnamed dungeon/raid/arena" when blank. */
    public static Component displayName(ArenasApi.ActivityType type, String name) {
        if (name != null && !name.isBlank()) {
            return Component.literal(name);
        }
        return Component.translatable("activity.arenas_ld.unnamed." + type.name().toLowerCase(Locale.ROOT));
    }

    /**
     * The live controller of an activity. Loads (and force-loads, as controllers keep themselves)
     * its chunk when needed. An entry whose controller is gone is dropped.
     */
    public static @Nullable ActivityHost resolve(MinecraftServer server, ResourceLocation id) {
        if (server.overworld() == null) {
            return null;
        }
        ActivityIndex index = ActivityIndex.get(server);
        ActivityIndex.Entry entry = index.get(id);
        if (entry == null) {
            return null;
        }
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(entry.pos());
        if (be instanceof ActivityHost host && id.equals(host.activityId())) {
            net.ledok.arenas_ld.util.ControllerChunks.hold(level, entry.pos());
            return host;
        }
        ArenasLdMod.LOGGER.warn("Dropping activity {}: no controller with that id at {} any more", id, entry.location());
        net.ledok.arenas_ld.util.ControllerChunks.releaseIfGone(server, entry);
        if (index.remove(id)) {
            fireChanged();
        }
        return null;
    }

    // ── Controller lifecycle ─────────────────────────────────────────────────

    /**
     * Registers a controller (once per session, from its first server tick). A controller without
     * an id, or whose id another live controller already has (a copied block), gets a generated one.
     */
    public static void attach(ActivityHost host) {
        BlockEntity be = host.asBlockEntity();
        if (!(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        ActivityIndex index = ActivityIndex.get(server);
        BlockPos pos = be.getBlockPos();
        ResourceLocation id = host.activityId();
        if (id == null) {
            id = generateId(index, host, level, pos);
            host.storeActivityId(id);
            if (host.loadedWithoutId()) {
                ArenasLdMod.LOGGER.warn("{} controller at {} {} had no activity id; assigned {} (change it in the admin screen)",
                    host.activityType(), level.dimension().location(), pos.toShortString(), id);
            } else {
                ArenasLdMod.LOGGER.info("New {} controller at {} {} got activity id {}",
                    host.activityType(), level.dimension().location(), pos.toShortString(), id);
            }
        } else {
            ActivityIndex.Entry existing = index.get(id);
            if (existing != null && !existing.isAt(level.dimension(), pos) && isLive(server, existing)) {
                // A copied block: it carries the original's id and its saved instances, lobbies and runs.
                ResourceLocation fresh = generateId(index, host, level, pos);
                ArenasLdMod.LOGGER.warn("The {} controller at {} {} is a copy of {} (the controller at {}): it is now {} and"
                        + " keeps its settings, but its copied instances, lobbies, runs and leaderboard were cleared."
                        + " Link its own instances.",
                    host.activityType(), level.dimension().location(), pos.toShortString(), id, existing.location(), fresh);
                host.storeActivityId(fresh);
                host.clearCopiedState(liveHost(server, existing));
                id = fresh;
            }
        }
        boolean changed = index.removeAt(level.dimension(), pos, id);
        changed |= index.put(entryFor(host, level, id));
        if (changed) {
            fireChanged();
        }
    }

    /** Copies the controller's current name and enabled difficulties into the index. */
    public static void refresh(ActivityHost host) {
        BlockEntity be = host.asBlockEntity();
        ResourceLocation id = host.activityId();
        if (id == null || !(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (ActivityIndex.get(level.getServer()).put(entryFor(host, level, id))) {
            fireChanged();
        }
    }

    /** The controller block at this position was removed: forget its activity. */
    public static void detach(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel
            && ActivityIndex.get(serverLevel.getServer()).removeAt(serverLevel.dimension(), pos, null)) {
            fireChanged();
        }
    }

    /** Drops an index entry by hand (for a controller removed by a tool that skipped block updates). */
    public static boolean forget(MinecraftServer server, ResourceLocation id) {
        ActivityIndex index = ActivityIndex.get(server);
        ActivityIndex.Entry entry = index.get(id);
        if (entry != null) {
            // A controller gone without a block break still has its chunk force-loaded.
            net.ledok.arenas_ld.util.ControllerChunks.releaseIfGone(server, entry);
        }
        if (index.remove(id)) {
            fireChanged();
            return true;
        }
        return false;
    }

    /**
     * Admin request to change a controller's id.
     *
     * @return null when applied (or unchanged), else the reason it was refused
     */
    public static @Nullable Component changeId(ActivityHost host, String raw) {
        BlockEntity be = host.asBlockEntity();
        if (!(be.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        ResourceLocation id = parseId(raw);
        if (id == null) {
            return Component.translatable("message.arenas_ld.activity.invalid_id", raw);
        }
        if (id.equals(host.activityId())) {
            return null;
        }
        MinecraftServer server = level.getServer();
        ActivityIndex index = ActivityIndex.get(server);
        ActivityIndex.Entry existing = index.get(id);
        if (existing != null && !existing.isAt(level.dimension(), be.getBlockPos()) && isLive(server, existing)) {
            return Component.translatable("message.arenas_ld.activity.id_taken", id.toString(), existing.location());
        }
        index.removeAt(level.dimension(), be.getBlockPos(), null);
        host.storeActivityId(id);
        index.put(entryFor(host, level, id));
        fireChanged();
        return null;
    }

    private static ActivityIndex.Entry entryFor(ActivityHost host, ServerLevel level, ResourceLocation id) {
        List<ArenasApi.Difficulty> difficulties = new ArrayList<>();
        for (DifficultyTier tier : host.enabledTiers()) {
            difficulties.add(new ArenasApi.Difficulty(ActivityHost.difficultyId(tier)));
        }
        return new ActivityIndex.Entry(id, host.activityType(), level.dimension(), host.asBlockEntity().getBlockPos(),
            host.activityName() == null ? "" : host.activityName(), List.copyOf(difficulties));
    }

    /**
     * Whether the entry's controller is still there. An unloaded location counts as live: the
     * controller may simply not be loaded, and taking its id would break whoever stored it.
     */
    private static boolean isLive(MinecraftServer server, ActivityIndex.Entry entry) {
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null) {
            return false;
        }
        if (!level.isLoaded(entry.pos())) {
            return true;
        }
        return level.getBlockEntity(entry.pos()) instanceof ActivityHost other && entry.id().equals(other.activityId());
    }

    /** The entry's controller when its chunk is loaded and the controller still has that id. */
    private static @Nullable ActivityHost liveHost(MinecraftServer server, ActivityIndex.Entry entry) {
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null || !level.isLoaded(entry.pos())) {
            return null;
        }
        return level.getBlockEntity(entry.pos()) instanceof ActivityHost other && entry.id().equals(other.activityId())
            ? other : null;
    }

    private static ResourceLocation generateId(ActivityIndex index, ActivityHost host, ServerLevel level, BlockPos pos) {
        String type = host.activityType().name().toLowerCase(Locale.ROOT);
        String slug = slug(host.activityName());
        String base = type + "_" + (slug.isEmpty() ? pos.getX() + "_" + pos.getY() + "_" + pos.getZ() : slug);
        return uniqueId(base, candidate -> {
            ActivityIndex.Entry taken = index.get(candidate);
            return taken != null && !taken.isAt(level.dimension(), pos);
        });
    }

    // ── Pure helpers (unit-tested) ───────────────────────────────────────────

    /**
     * Parses an admin-typed id: trimmed, lowercased, {@code arenas_ld:} when no namespace is given.
     * Null when it is not a valid resource location.
     */
    public static @Nullable ResourceLocation parseId(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return null;
        }
        int colon = text.indexOf(':');
        String namespace = colon < 0 ? ArenasLdMod.MOD_ID : text.substring(0, colon);
        String path = colon < 0 ? text : text.substring(colon + 1);
        if (namespace.isEmpty() || path.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryBuild(namespace, path);
    }

    /** Lowercase ASCII letters and digits joined by single underscores; empty for e.g. Cyrillic names. */
    static String slug(String name) {
        if (name == null) {
            return "";
        }
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH).replaceAll("_+$", "");
        }
        return slug;
    }

    /** {@code arenas_ld:<base>}, then {@code <base>_2}, {@code <base>_3}… until {@code taken} says no. */
    static ResourceLocation uniqueId(String base, Predicate<ResourceLocation> taken) {
        ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, base);
        for (int n = 2; taken.test(candidate); n++) {
            candidate = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, base + "_" + n);
        }
        return candidate;
    }
}
