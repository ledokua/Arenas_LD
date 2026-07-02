package net.ledok.arenas_ld.dungeon;

import net.ledok.arenas_ld.dungeon.run.DifficultyTier;

public interface DungeonContext {
    double getHealthMultiplier();
    double getDamageMultiplier();
    DifficultyTier getActiveTier();
}
