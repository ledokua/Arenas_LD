package net.ledok.arenas_ld.util;

import java.lang.reflect.Method;
import java.util.UUID;

public final class BusyStateCompat {
    private static final Method IS_BUSY_METHOD;
    private static final Method SET_BUSY_METHOD;
    private static final Method CLEAR_BUSY_METHOD;

    static {
        Method isBusy = null;
        Method setBusy = null;
        Method clearBusy = null;
        try {
            Class<?> busyStateClass = Class.forName("net.ledok.busylib.BusyState");
            isBusy = busyStateClass.getMethod("isBusy", UUID.class);
            setBusy = busyStateClass.getMethod("setBusy", UUID.class, String.class);
            clearBusy = busyStateClass.getMethod("clearBusy", UUID.class, String.class);
        } catch (Throwable ignored) {
        }
        IS_BUSY_METHOD = isBusy;
        SET_BUSY_METHOD = setBusy;
        CLEAR_BUSY_METHOD = clearBusy;
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
