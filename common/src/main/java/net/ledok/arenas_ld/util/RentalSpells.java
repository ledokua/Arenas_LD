package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Choosing the spells of a rental kit's spell book (LeDok 2026-11-17, rental round 2). A kit's book is a Spell Engine
 * {@code spell_container} with a {@code pool} (a spell tag) and up to {@code max_spell_count} {@code spell_ids}; in the
 * lobby preview the player picks which spells of the pool it holds, the pick is kept per player and preset, and at the
 * run start (and after each respawn or rejoin) the lent book's {@code spell_ids} become the pick.
 * <p>
 * Spell Engine is read by reflection (its own classes, not remapped), so Arenas runs without it: then no book has spells.
 */
public final class RentalSpells {
    /**
     * A kit book's spell choice.
     *
     * @param pool     the book's pool (spell tag id, no "#")
     * @param max      how many spells it holds
     * @param defaults the kit's own spell_ids
     * @param allowed  the pool's spells the kit level may pick, in tag order
     */
    public record Choice(String pool, int max, List<String> defaults, List<String> allowed) {
    }

    private static boolean looked;
    private static @Nullable DataComponentType<Object> component;
    private static @Nullable Method copyWith, poolOf, maxOf, idsOf, setDirty;
    private static @Nullable ResourceKey<? extends Registry<?>> spellRegistry;
    private static @Nullable Field tierField;

    private RentalSpells() {
    }

    // ── reading ──────────────────────────────────────────────────────────────

    /** The spell choice of the first spell book among {@code items} (a kit's preview), or null when it has none. */
    public static @Nullable Choice forKit(RegistryAccess access, List<ItemStack> items, int kitLevel) {
        for (ItemStack stack : items) {
            Choice choice = of(access, stack, kitLevel);
            if (choice != null) {
                return choice;
            }
        }
        return null;
    }

    /** The stack's spell choice: a container with a pool, room for spells and pool members; else null. */
    public static @Nullable Choice of(RegistryAccess access, ItemStack stack, int kitLevel) {
        Object container = container(stack);
        if (container == null) {
            return null;
        }
        try {
            String pool = clean((String) poolOf.invoke(container));
            int max = (int) maxOf.invoke(container);
            @SuppressWarnings("unchecked") List<String> ids = (List<String>) idsOf.invoke(container);
            if (pool.isEmpty() || max <= 0) {
                return null;
            }
            List<String> allowed = poolSpells(access, pool, maxTier(kitLevel));
            if (allowed.isEmpty()) {
                return null;
            }
            return new Choice(pool, max, ids == null ? List.of() : List.copyOf(ids), allowed);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The spell_ids of the stack's container (empty without one). */
    public static List<String> spellsOf(ItemStack stack) {
        Object container = container(stack);
        if (container == null) {
            return List.of();
        }
        try {
            @SuppressWarnings("unchecked") List<String> ids = (List<String>) idsOf.invoke(container);
            return ids == null ? List.of() : List.copyOf(ids);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    /** The pool of the stack's container ("" without one). */
    public static String poolOf(ItemStack stack) {
        Object container = container(stack);
        if (container == null) {
            return "";
        }
        try {
            return clean((String) poolOf.invoke(container));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    /**
     * What the picker starts with: the player's saved pick for this preset, else the spells of their own book when it
     * has the same pool, else the kit's own; always cut to what {@code choice} allows.
     */
    public static List<String> prefill(ServerPlayer player, String preset, Choice choice) {
        List<String> saved = Store.get(player.server).pick(player.getUUID(), preset);
        List<String> out = saved == null ? List.of() : validate(saved, choice);
        if (out.isEmpty()) {
            var own = net.ledok.arenas_ld.stats.SpellBooks.of(player);
            if (own != null && clean(own.pool()).equals(choice.pool())) {
                out = validate(spellsOf(own.stack()), choice);
            }
        }
        if (out.isEmpty()) {
            out = validate(choice.defaults(), choice);
        }
        return out;
    }

    /** {@code picked} cut to the allowed spells, without repeats, at most {@code max}. */
    public static List<String> validate(List<String> picked, Choice choice) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String id : picked) {
            if (id != null && choice.allowed().contains(id) && out.size() < choice.max()) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    // ── applying ─────────────────────────────────────────────────────────────

    /**
     * Puts {@code spells} into the player's equipped book of {@code pool} (the lent one). Returns whether a book was
     * changed; Spell Engine re-reads the player's containers.
     */
    public static boolean apply(ServerPlayer player, String pool, List<String> spells) {
        if (spells.isEmpty() || !lookup()) {
            return false;
        }
        var book = net.ledok.arenas_ld.stats.SpellBooks.of(player);
        if (book == null || !poolOf(book.stack()).equals(clean(pool))) {
            return false;
        }
        Object container = container(book.stack());
        if (container == null) {
            return false;
        }
        try {
            if (spellsOf(book.stack()).equals(spells)) {
                return true;
            }
            book.stack().set(component, copyWith.invoke(container, List.copyOf(spells)));
            if (setDirty != null) {
                setDirty.invoke(null, player);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ArenasLdMod.LOGGER.warn("[Arenas] Rental: couldn't set the spells of {}'s book", player.getScoreboardName(), e);
            return false;
        }
    }

    // ── Spell Engine ─────────────────────────────────────────────────────────

    private static @Nullable Object container(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !lookup()) {
            return null;
        }
        return stack.get(component);
    }

    /** The pool's spells (registry tag), those above {@code maxTier} left out (-1: all). */
    private static List<String> poolSpells(RegistryAccess access, String pool, int maxTier) {
        ResourceLocation id = ResourceLocation.tryParse(pool);
        if (id == null || spellRegistry == null) {
            return List.of();
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        var registry = access.registry((ResourceKey) spellRegistry);
        if (registry.isEmpty()) {
            return List.of();
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        Registry<Object> spells = (Registry<Object>) registry.get();
        var tag = spells.getTag(TagKey.create(spells.key(), id));
        if (tag.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Holder<Object> holder : tag.get()) {
            String spellId = holder.unwrapKey().map(k -> k.location().toString()).orElse(null);
            if (spellId != null && (maxTier < 0 || tierOf(holder.value()) <= maxTier)) {
                out.add(spellId);
            }
        }
        return out;
    }

    private static int tierOf(Object spell) {
        try {
            return tierField == null ? 0 : tierField.getInt(spell);
        } catch (IllegalAccessException | RuntimeException e) {
            return 0;
        }
    }

    /** The highest spell tier the kit level may pick, from {@code rental_spell_max_tier}; -1 = the whole pool. */
    public static int maxTierFor(int kitLevel) {
        return maxTier(kitLevel);
    }

    static int maxTier(int kitLevel) {
        Map<String, Integer> rule = ArenasLdConfig.getInstance().rental_spell_max_tier;
        if (rule == null || rule.isEmpty()) {
            return -1;
        }
        int bestLevel = Integer.MIN_VALUE;
        int tier = -1;
        for (Map.Entry<String, Integer> entry : rule.entrySet()) {
            try {
                int level = Integer.parseInt(entry.getKey().trim());
                if (level <= Math.max(0, kitLevel) && level > bestLevel && entry.getValue() != null) {
                    bestLevel = level;
                    tier = entry.getValue();
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return tier;
    }

    private static String clean(@Nullable String pool) {
        if (pool == null) {
            return "";
        }
        String out = pool.trim();
        return out.startsWith("#") ? out.substring(1) : out;
    }

    @SuppressWarnings("unchecked")
    private static boolean lookup() {
        if (looked) {
            return component != null;
        }
        looked = true;
        try {
            Class<?> components = Class.forName("net.spell_engine.api.spell.SpellDataComponents");
            Class<?> container = Class.forName("net.spell_engine.api.spell.container.SpellContainer");
            Object type = components.getField("SPELL_CONTAINER").get(null);
            copyWith = container.getMethod("copyWith", List.class);
            poolOf = container.getMethod("pool");
            maxOf = container.getMethod("max_spell_count");
            idsOf = container.getMethod("spell_ids");
            try {
                Class<?> registry = Class.forName("net.spell_engine.api.spell.registry.SpellRegistry");
                spellRegistry = (ResourceKey<? extends Registry<?>>) registry.getField("KEY").get(null);
                tierField = Class.forName("net.spell_engine.api.spell.Spell").getField("tier");
            } catch (ReflectiveOperationException | RuntimeException e) {
                ArenasLdMod.LOGGER.warn("[Arenas] Rental: couldn't read Spell Engine's spell registry; no spell choice", e);
            }
            try {
                setDirty = Class.forName("net.spell_engine.internals.container.SpellContainerSource")
                    .getMethod("setDirtyServerSide", net.minecraft.world.entity.player.Player.class);
            } catch (ReflectiveOperationException | RuntimeException e) {
                setDirty = null;
            }
            component = (DataComponentType<Object>) type;
        } catch (ClassNotFoundException e) {
            // No Spell Engine: no spell choice.
        } catch (ReflectiveOperationException | RuntimeException e) {
            ArenasLdMod.LOGGER.warn("[Arenas] Rental: couldn't read Spell Engine spell containers; no spell choice", e);
        }
        return component != null;
    }

    // ── saved picks ──────────────────────────────────────────────────────────

    /** Each player's last spell pick per preset name ("terra_quake"), so the next run starts with it. */
    public static final class Store extends SavedData {
        private static final String DATA_ID = "arenas_ld_rental_spells";
        private static final Factory<Store> FACTORY = new Factory<>(Store::new, Store::load, null);
        private final Map<UUID, Map<String, List<String>>> picks = new HashMap<>();

        public static Store get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
        }

        public @Nullable List<String> pick(UUID player, String preset) {
            Map<String, List<String>> own = picks.get(player);
            return own == null ? null : own.get(preset);
        }

        public void put(UUID player, String preset, List<String> spells) {
            picks.computeIfAbsent(player, u -> new LinkedHashMap<>()).put(preset, List.copyOf(spells));
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag players = new CompoundTag();
            picks.forEach((uuid, presets) -> {
                CompoundTag own = new CompoundTag();
                presets.forEach((preset, spells) -> {
                    ListTag list = new ListTag();
                    spells.forEach(s -> list.add(StringTag.valueOf(s)));
                    own.put(preset, list);
                });
                players.put(uuid.toString(), own);
            });
            tag.put("players", players);
            return tag;
        }

        private static Store load(CompoundTag tag, HolderLookup.Provider registries) {
            Store store = new Store();
            CompoundTag players = tag.getCompound("players");
            for (String key : players.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    CompoundTag own = players.getCompound(key);
                    Map<String, List<String>> presets = new LinkedHashMap<>();
                    for (String preset : own.getAllKeys()) {
                        ListTag list = own.getList(preset, Tag.TAG_STRING);
                        List<String> spells = new ArrayList<>();
                        for (int i = 0; i < list.size(); i++) {
                            spells.add(list.getString(i));
                        }
                        presets.put(preset, spells);
                    }
                    store.picks.put(uuid, presets);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return store;
        }
    }
}
