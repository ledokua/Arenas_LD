package net.ledok.arenas_ld.util;

public enum RaidDifficulty {
    EASY(0.75, 0.75),
    NORMAL(1.0, 1.0),
    HARD(1.5, 1.25),
    LEGENDARY(2.0, 1.5);

    public final double healthMult;
    public final double damageMult;

    RaidDifficulty(double healthMult, double damageMult) {
        this.healthMult = healthMult;
        this.damageMult = damageMult;
    }

    public String translationKey() {
        return "raid_difficulty.arenas_ld." + name().toLowerCase();
    }

    public static RaidDifficulty fromNameOrDefault(String name, RaidDifficulty def) {
        try {
            return valueOf(name.toUpperCase());
        } catch (Exception e) {
            return def;
        }
    }
}
