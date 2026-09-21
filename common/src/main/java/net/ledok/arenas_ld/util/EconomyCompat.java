package net.ledok.arenas_ld.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.UUID;

public final class EconomyCompat {
    private static final Class<?> MANAGER_CLASS;
    private static final Method GET_INSTANCE;
    private static final Method DELIVER_CURRENCY;
    private static final Method DELIVER_ITEM;
    private static final Method GIVE;

    static {
        Class<?> managerClass = null;
        Method getInstance = null;
        Method currency = null;
        Method item = null;
        Method give = null;
        try {
            managerClass = Class.forName("net.ledok.economy_ld.manager.EconomyManager");
            getInstance = managerClass.getMethod("getInstance");
            currency = managerClass.getMethod("deliverCurrency", UUID.class, long.class, String.class);
            item = managerClass.getMethod("deliverItem", UUID.class, ItemStack.class, int.class, String.class);
            give = managerClass.getMethod("give", UUID.class, String.class, long.class);
        } catch (Throwable ignored) {
            managerClass = null;
            getInstance = null;
            currency = null;
            item = null;
            give = null;
        }
        MANAGER_CLASS = managerClass;
        GET_INSTANCE = getInstance;
        DELIVER_CURRENCY = currency;
        DELIVER_ITEM = item;
        GIVE = give;
    }

    private EconomyCompat() {
    }

    public static boolean isAvailable() {
        return MANAGER_CLASS != null && GET_INSTANCE != null && DELIVER_CURRENCY != null;
    }

    private static Object managerInstance() {
        if (GET_INSTANCE == null) {
            return null;
        }
        try {
            return GET_INSTANCE.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Deposits currency straight into the player's wallet balance (not the claimable inbox).
     * Use for online players so the reward lands the moment the popup shows it; falls back to
     * {@link #deliverCurrency} when the running Economy_LD build predates the wallet API.
     */
    public static void giveCurrency(UUID playerUuid, String username, long amount, String reason) {
        if (playerUuid == null || amount <= 0L) {
            return;
        }
        if (GIVE == null || username == null || username.isBlank()) {
            deliverCurrency(playerUuid, amount, reason);
            return;
        }
        Object instance = managerInstance();
        if (instance == null) {
            return;
        }
        try {
            GIVE.invoke(instance, playerUuid, username, amount);
        } catch (Throwable ignored) {
        }
    }

    public static void deliverCurrency(UUID playerUuid, long amount, String reason) {
        if (!isAvailable() || playerUuid == null || amount <= 0L) {
            return;
        }
        Object instance = managerInstance();
        if (instance == null) {
            return;
        }
        try {
            DELIVER_CURRENCY.invoke(instance, playerUuid, amount, reason == null ? "ARENA_REWARD" : reason);
        } catch (Throwable ignored) {
        }
    }

    public static void deliverCurrencyByName(MinecraftServer server, String username, long amount, String reason) {
        if (!isAvailable() || server == null || username == null || username.isBlank() || amount <= 0L) {
            return;
        }
        UUID uuid = resolveUuid(server, username);
        if (uuid != null) {
            deliverCurrency(uuid, amount, reason);
        }
    }

    public static void deliverItem(UUID playerUuid, ItemStack stack, int quantity, String reason) {
        if (DELIVER_ITEM == null || playerUuid == null) {
            return;
        }
        if (stack == null || stack.isEmpty() || quantity <= 0) {
            return;
        }
        Object instance = managerInstance();
        if (instance == null) {
            return;
        }
        try {
            DELIVER_ITEM.invoke(instance, playerUuid, stack, quantity, reason == null ? "ARENA_REWARD" : reason);
        } catch (Throwable ignored) {
        }
    }

    private static UUID resolveUuid(MinecraftServer server, String username) {
        var online = server.getPlayerList().getPlayerByName(username);
        if (online != null) {
            return online.getUUID();
        }
        var cache = server.getProfileCache();
        if (cache == null) {
            return null;
        }
        return cache.get(username).map(profile -> profile.getId()).orElse(null);
    }
}
