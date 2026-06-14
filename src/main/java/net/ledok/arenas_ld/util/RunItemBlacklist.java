package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the configured run item blacklist ({@code run_item_blacklist}) into item ids and item
 * tags, and tests stacks against it. Entries are plain item ids (e.g. {@code minecraft:ender_pearl})
 * or tag references prefixed with {@code #} (e.g. {@code #minecraft:beds}).
 *
 * <p>The parsed form is cached and rebuilt only when the config's backing list instance changes
 * (which happens on a config reload, since that swaps in a fresh config object).
 */
public final class RunItemBlacklist {
    private RunItemBlacklist() {}

    private static List<String> cachedSource;
    private static Set<ResourceLocation> blacklistedIds = Set.of();
    private static List<TagKey<Item>> blacklistedTags = List.of();

    public static boolean isBlacklisted(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        rebuildIfNeeded();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (blacklistedIds.contains(id)) {
            return true;
        }
        for (TagKey<Item> tag : blacklistedTags) {
            if (stack.is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static void rebuildIfNeeded() {
        List<String> source = ArenasLdConfig.getInstance().run_item_blacklist;
        if (source == cachedSource) {
            return;
        }
        cachedSource = source;

        Set<ResourceLocation> ids = new HashSet<>();
        List<TagKey<Item>> tags = new ArrayList<>();
        if (source != null) {
            for (String raw : source) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String entry = raw.trim();
                if (entry.startsWith("#")) {
                    ResourceLocation tagId = ResourceLocation.tryParse(entry.substring(1));
                    if (tagId == null) {
                        ArenasLdMod.LOGGER.warn("[Arenas] Ignoring invalid item tag in run blacklist: {}", entry);
                        continue;
                    }
                    tags.add(TagKey.create(Registries.ITEM, tagId));
                } else {
                    ResourceLocation itemId = ResourceLocation.tryParse(entry);
                    if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                        ArenasLdMod.LOGGER.warn("[Arenas] Ignoring unknown item in run blacklist: {}", entry);
                        continue;
                    }
                    ids.add(itemId);
                }
            }
        }
        blacklistedIds = ids;
        blacklistedTags = tags;
    }
}
