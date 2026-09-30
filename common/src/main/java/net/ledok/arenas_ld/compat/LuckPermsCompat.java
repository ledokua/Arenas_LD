package net.ledok.arenas_ld.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Optional LuckPerms meta reads; everything answers "unset" without the mod. LuckPerms only has
 * online (or logging in/out) players loaded, so an offline player also reads as unset.
 */
public final class LuckPermsCompat {

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("luckperms");

    private LuckPermsCompat() {
    }

    /** An integer meta value (group inheritance resolved), e.g. {@code /lp group prime meta set <key> 2}. */
    public static OptionalInt metaInt(UUID player, String key) {
        return LOADED ? Hook.metaInt(player, key) : OptionalInt.empty();
    }

    /** A decimal meta value (group inheritance resolved), e.g. {@code /lp group prime meta set <key> 1.1}. */
    public static OptionalDouble metaDouble(UUID player, String key) {
        return LOADED ? Hook.metaDouble(player, key) : OptionalDouble.empty();
    }

    /** The only class touching LuckPerms types, so the mod loads without LuckPerms. */
    private static final class Hook {
        static OptionalInt metaInt(UUID player, String key) {
            String value = metaValue(player, key);
            if (value == null) {
                return OptionalInt.empty();
            }
            try {
                return OptionalInt.of(Integer.parseInt(value.trim()));
            } catch (NumberFormatException e) {
                return OptionalInt.empty();
            }
        }

        static OptionalDouble metaDouble(UUID player, String key) {
            String value = metaValue(player, key);
            if (value == null) {
                return OptionalDouble.empty();
            }
            try {
                double parsed = Double.parseDouble(value.trim());
                return Double.isFinite(parsed) ? OptionalDouble.of(parsed) : OptionalDouble.empty();
            } catch (NumberFormatException e) {
                return OptionalDouble.empty();
            }
        }

        private static String metaValue(UUID player, String key) {
            try {
                net.luckperms.api.model.user.User user = net.luckperms.api.LuckPermsProvider.get()
                        .getUserManager().getUser(player);
                return user == null ? null : user.getCachedData().getMetaData().getMetaValue(key);
            } catch (IllegalStateException e) {
                return null; // LuckPerms not enabled yet
            }
        }
    }
}
