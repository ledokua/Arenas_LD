package net.ledok.arenas_ld.util;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class BusyStateCompat {
    private static final Method IS_BUSY_METHOD;
    private static final Method SET_BUSY_METHOD;
    private static final Method CLEAR_BUSY_METHOD;
    private static final Method GET_REASONS_METHOD;

    static {
        Method isBusy = null;
        Method setBusy = null;
        Method clearBusy = null;
        Method getReasons = null;
        try {
            Class<?> busyStateClass = Class.forName("net.ledok.busylib.BusyState");
            isBusy = busyStateClass.getMethod("isBusy", UUID.class);
            setBusy = busyStateClass.getMethod("setBusy", UUID.class, String.class);
            clearBusy = busyStateClass.getMethod("clearBusy", UUID.class, String.class);
            try {
                getReasons = busyStateClass.getMethod("getReasons", UUID.class);
            } catch (Throwable ignored) {
                // An older BusyLib: busy still works, the reasons just can't be named.
            }
        } catch (Throwable ignored) {
        }
        IS_BUSY_METHOD = isBusy;
        SET_BUSY_METHOD = setBusy;
        CLEAR_BUSY_METHOD = clearBusy;
        GET_REASONS_METHOD = getReasons;
    }

    private BusyStateCompat() {}

    public static boolean isBusy(UUID playerId) {
        if (IS_BUSY_METHOD == null) {
            return false;
        }
        try {
            Object result = IS_BUSY_METHOD.invoke(null, playerId);
            return result instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Why the player is busy, sorted (e.g. {@code arenas_ld:dungeon}, {@code duels_ld:duel}); empty
     * when they aren't, or when BusyLib is absent or too old to say.
     */
    public static Set<String> reasons(UUID playerId) {
        if (GET_REASONS_METHOD == null) {
            return Set.of();
        }
        try {
            if (GET_REASONS_METHOD.invoke(null, playerId) instanceof Collection<?> reasons && !reasons.isEmpty()) {
                Set<String> sorted = new TreeSet<>();
                for (Object reason : reasons) {
                    if (reason != null) {
                        sorted.add(reason.toString());
                    }
                }
                return Collections.unmodifiableSet(sorted);
            }
        } catch (Throwable ignored) {
        }
        return Set.of();
    }

    public static void setBusy(UUID playerId, String reason) {
        if (SET_BUSY_METHOD == null) {
            return;
        }
        try {
            SET_BUSY_METHOD.invoke(null, playerId, reason);
        } catch (Throwable ignored) {
        }
    }

    public static void clearBusy(UUID playerId, String reason) {
        if (CLEAR_BUSY_METHOD == null) {
            return;
        }
        try {
            CLEAR_BUSY_METHOD.invoke(null, playerId, reason);
        } catch (Throwable ignored) {
        }
    }
}
