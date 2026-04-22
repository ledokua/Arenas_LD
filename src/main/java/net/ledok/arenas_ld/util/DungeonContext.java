package net.ledok.arenas_ld.util;

public interface DungeonContext {
    double getHealthMultiplier();
    double getDamageMultiplier();
    DifficultyTier getActiveTier();
}
