package net.ledok.arenas_ld.client;

import net.minecraft.core.BlockPos;

import java.util.List;

/** Client-side store for the current spawn-telegraph highlight; expires by game time. */
public final class SpawnTelegraphStore {
    private static List<BlockPos> positions = List.of();
    private static long expiresAt = 0L;

    private SpawnTelegraphStore() {
    }

    public static void set(List<BlockPos> newPositions, int durationTicks, long nowGameTime) {
        positions = List.copyOf(newPositions);
        expiresAt = nowGameTime + durationTicks;
    }

    public static List<BlockPos> active(long nowGameTime) {
        return nowGameTime >= expiresAt ? List.of() : positions;
    }

    public static void clear() {
        positions = List.of();
        expiresAt = 0L;
    }
}
