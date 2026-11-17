package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Possible drops" for the hub's create panel: every item a set of loot tables can give, read from the loaded tables
 * (item entries, nested and inline tables, item tags, alternatives / groups / sequences). No rolls, no conditions or
 * functions (an enchanted book shows as a book). Modded entry types are skipped. Cached per data reload. Server
 * thread only.
 */
public final class LootPreview {
    /** At most this many items per activity (a mod pack's "everything" table would otherwise flood the panel). */
    private static final int MAX_ITEMS = 64;
    private static final int MAX_DEPTH = 8;

    private static final Map<List<String>, Preview> CACHE = new HashMap<>();
    private static Object cachedFor;

    private LootPreview() {
    }

    private static final java.util.regex.Pattern LOOT_COMMAND =
        java.util.regex.Pattern.compile("(?:^|\\s)loot\\s.*?\\sloot\\s+([a-z0-9_.-]+:[a-z0-9_/.-]+)");

    /** Loot tables a reward command hands out ({@code loot give @p loot <table>}, any target). */
    public static List<String> tablesInCommands(List<String> commands) {
        List<String> out = new ArrayList<>();
        for (String command : commands) {
            var matcher = LOOT_COMMAND.matcher(command.startsWith("/") ? command.substring(1) : command);
            if (matcher.find()) {
                out.add(matcher.group(1));
            }
        }
        return out;
    }

    /** The items (capped) and how many distinct items there are in all. */
    private record Preview(List<ItemStack> items, int total) {
    }

    public static List<ItemStack> items(MinecraftServer server, List<String> tableIds) {
        return preview(server, tableIds).items();
    }

    /** How many distinct items the tables can give, before the {@value #MAX_ITEMS} cap. */
    public static int count(MinecraftServer server, List<String> tableIds) {
        return preview(server, tableIds).total();
    }

    private static Preview preview(MinecraftServer server, List<String> tableIds) {
        Object registries = server.reloadableRegistries();
        if (registries != cachedFor) {
            CACHE.clear();
            cachedFor = registries;
        }
        List<String> key = List.copyOf(tableIds);
        return CACHE.computeIfAbsent(key, ids -> {
            Set<Item> found = new LinkedHashSet<>();
            for (String id : ids) {
                table(server, id, 0, found, new HashSet<>());
            }
            found.remove(Items.AIR);
            List<ItemStack> out = new ArrayList<>();
            for (Item item : found) {
                if (out.size() < MAX_ITEMS) {
                    out.add(new ItemStack(item));
                }
            }
            if (out.isEmpty()) {
                ArenasLdMod.LOGGER.info("[Arenas] Possible drops: no items found in loot tables {}; what was seen: {}",
                    ids, diagnose(server, ids));
            }
            return new Preview(List.copyOf(out), found.size());
        });
    }

    private static void table(MinecraftServer server, String id, int depth, Set<Item> out, Set<String> seen) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || depth > MAX_DEPTH || !seen.add(id)) {
            return;
        }
        LootTable table = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, location));
        if (table != LootTable.EMPTY) {
            table(server, table, depth, out, seen);
        }
    }

    /**
     * Walks the loaded table itself, not its JSON: writing a table out fails when a mod's loot function can't be
     * encoded (Fallen Eden's dungeon_difficulty:local_scaling threw and hid every gear table). Fields are found by
     * their types, so it works under any mappings.
     */
    private static void table(MinecraftServer server, LootTable table, int depth, Set<Item> out, Set<String> seen) {
        for (Object pool : listOf(table, POOLS)) {
            for (Object entry : listOf(pool, ENTRIES)) {
                entry(server, entry, depth, out, seen);
            }
        }
    }

    private static void entry(MinecraftServer server, Object entry, int depth, Set<Item> out, Set<String> seen) {
        try {
            if (entry instanceof net.minecraft.world.level.storage.loot.entries.LootItem) {
                Object holder = field(entry.getClass(), net.minecraft.core.Holder.class, null).get(entry);
                if (holder instanceof net.minecraft.core.Holder<?> h && h.value() instanceof Item item) {
                    out.add(item);
                }
            } else if (entry instanceof net.minecraft.world.level.storage.loot.entries.TagEntry) {
                Object tag = field(entry.getClass(), TagKey.class, null).get(entry);
                if (tag instanceof TagKey<?> key) {
                    @SuppressWarnings("unchecked")
                    TagKey<Item> items = (TagKey<Item>) key;
                    BuiltInRegistries.ITEM.getTag(items).ifPresent(set -> set.forEach(holder -> out.add(holder.value())));
                }
            } else if (entry instanceof net.minecraft.world.level.storage.loot.entries.NestedLootTable) {
                Object contents = field(entry.getClass(), com.mojang.datafixers.util.Either.class, null).get(entry);
                if (contents instanceof com.mojang.datafixers.util.Either<?, ?> either) {
                    either.ifLeft(key -> {
                        if (key instanceof ResourceKey<?> resourceKey) {
                            table(server, resourceKey.location().toString(), depth + 1, out, seen);
                        }
                    }).ifRight(inline -> {
                        if (inline instanceof LootTable lootTable && depth < MAX_DEPTH) {
                            table(server, lootTable, depth + 1, out, seen);
                        }
                    });
                }
            } else if (entry instanceof net.minecraft.world.level.storage.loot.entries.CompositeEntryBase && depth < MAX_DEPTH) {
                for (Object child : listOf(entry, CHILDREN)) {
                    entry(server, child, depth + 1, out, seen);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LAST_ERROR = e.toString();
            ArenasLdMod.LOGGER.debug("[Arenas] Loot preview skipped an entry: {}", e.toString());
        }
    }

    private static volatile String LAST_ERROR = "";

    /** For the log when nothing was found: per table, whether it exists, its class, pools and entry classes. */
    private static String diagnose(MinecraftServer server, List<String> ids) {
        StringBuilder out = new StringBuilder();
        for (String id : ids) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            LootTable table = location == null ? LootTable.EMPTY
                : server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, location));
            out.append(id).append(": ");
            if (table == LootTable.EMPTY) {
                out.append("MISSING; ");
                continue;
            }
            out.append(table.getClass().getName());
            try {
                List<?> pools = listOf(table, POOLS);
                out.append(" pools=").append(pools.size());
                List<String> kinds = new ArrayList<>();
                for (Object pool : pools) {
                    for (Object entry : listOf(pool, ENTRIES)) {
                        kinds.add(entry.getClass().getName());
                    }
                }
                out.append(" entries=").append(kinds.size()).append(' ').append(kinds.stream().distinct().limit(4).toList());
            } catch (RuntimeException e) {
                out.append(" error=").append(e);
            }
            out.append("; ");
        }
        if (!LAST_ERROR.isEmpty()) {
            out.append("last entry error: ").append(LAST_ERROR);
        }
        return out.toString();
    }

    // ── fields by type ───────────────────────────────────────────────────────

    private static final Class<?> POOLS = net.minecraft.world.level.storage.loot.LootPool.class;
    private static final Class<?> ENTRIES = net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer.class;
    private static final Class<?> CHILDREN = net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer.class;
    private static final Map<String, java.lang.reflect.Field> FIELDS = new java.util.concurrent.ConcurrentHashMap<>();

    /** The {@code List<element>} field of {@code owner} (walking up its classes), as a list; empty when none. */
    private static List<?> listOf(Object owner, Class<?> element) {
        try {
            Object value = field(owner.getClass(), List.class, element).get(owner);
            return value instanceof List<?> list ? list : List.of();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    /** The field of {@code type} (a {@code List} of {@code element} when given) declared by {@code owner} or a superclass. */
    private static java.lang.reflect.Field field(Class<?> owner, Class<?> type, @org.jetbrains.annotations.Nullable Class<?> element)
            throws NoSuchFieldException {
        String key = owner.getName() + "|" + type.getName() + "|" + (element == null ? "" : element.getName());
        java.lang.reflect.Field cached = FIELDS.get(key);
        if (cached != null) {
            return cached;
        }
        for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType() != type) {
                    continue;
                }
                if (element != null) {
                    if (!(f.getGenericType() instanceof java.lang.reflect.ParameterizedType pt)
                        || pt.getActualTypeArguments().length != 1 || pt.getActualTypeArguments()[0] != element) {
                        continue;
                    }
                }
                f.setAccessible(true);
                FIELDS.put(key, f);
                return f;
            }
        }
        throw new NoSuchFieldException(owner.getName() + " has no " + type.getSimpleName()
            + (element == null ? "" : "<" + element.getSimpleName() + ">"));
    }
}
