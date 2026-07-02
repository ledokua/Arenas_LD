package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the configured run effect blacklist ({@code run_effect_blacklist}) into mob-effect
 * holders. Entries are effect ids (e.g. {@code minecraft:speed} or {@code galosphere:astral}).
 *
 * <p>Like {@link RunItemBlacklist}, the parsed form is cached and rebuilt only when the config's
 * backing list instance changes (which happens on a config reload).
 */
public final class RunEffectBlacklist {
    private RunEffectBlacklist() {}

    private static List<String> cachedSource;
    private static List<Holder<MobEffect>> blacklisted = List.of();

    /** Mob-effect holders that should be stripped from players who are in a run. */
    public static List<Holder<MobEffect>> effects() {
        rebuildIfNeeded();
        return blacklisted;
    }

    private static void rebuildIfNeeded() {
        List<String> source = ArenasLdConfig.getInstance().run_effect_blacklist;
        if (source == cachedSource) {
            return;
        }
        cachedSource = source;

        List<Holder<MobEffect>> resolved = new ArrayList<>();
        if (source != null) {
            for (String raw : source) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(raw.trim());
                if (id == null) {
                    ArenasLdMod.LOGGER.warn("[Arenas] Ignoring invalid effect id in run blacklist: {}", raw);
                    continue;
                }
                ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, id);
                BuiltInRegistries.MOB_EFFECT.getHolder(key).ifPresentOrElse(
                    resolved::add,
                    () -> ArenasLdMod.LOGGER.warn("[Arenas] Ignoring unknown effect in run blacklist: {}", id));
            }
        }
        blacklisted = List.copyOf(resolved);
    }
}
