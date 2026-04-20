package net.ledok.arenas_ld.util;

public enum DifficultyTier {
    EASY(0.75, 0.75),
    NORMAL(1.0, 1.0),
    HARD(1.5, 1.5),
    NIGHTMARE(2.0, 2.0);

    private final double defaultHealthMultiplier;
    private final double defaultDamageMultiplier;

    DifficultyTier(double defaultHealthMultiplier, double defaultDamageMultiplier) {
        this.defaultHealthMultiplier = defaultHealthMultiplier;
        this.defaultDamageMultiplier = defaultDamageMultiplier;
    }

    public double defaultHealthMultiplier() {
        return defaultHealthMultiplier;
    }

    public double defaultDamageMultiplier() {
        return defaultDamageMultiplier;
    }

    public String translationKey() {
        return "gui.arenas_ld.tier." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static DifficultyTier fromNameOrDefault(String value, DifficultyTier fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return DifficultyTier.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
