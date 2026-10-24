package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;

/**
 * A spawner whose run mobs pass their health and damage scaling on to their minions (see
 * {@link RunMinions}). Staff turn it off for bosses that already scale their own minions from the
 * boss's stats, so the scaling isn't applied twice. Off only skips the scaling: the minions are
 * still adopted (team, no loot, gone with the boss).
 */
public interface MinionScalingHost {
    /** Saved next to the spawner's state; absent (older saves) means on. */
    String NBT_KEY = "ScaleMinions";

    boolean scalesMinions();

    void setScalesMinions(boolean scale);

    static void save(CompoundTag nbt, boolean scale) {
        nbt.putBoolean(NBT_KEY, scale);
    }

    static boolean load(CompoundTag nbt) {
        return !nbt.contains(NBT_KEY) || nbt.getBoolean(NBT_KEY);
    }
}
