package net.ledok.arenas_ld.util;

/**
 * What the client knows of its own run's modifiers (no client classes here: the common friction mixin reads it).
 * Set by {@code RunFrozenGroundPayload}; cleared on disconnect.
 */
public final class RunEffectsClientState {
    /** The local player slides as on ice ("Frozen ground"). */
    public static volatile boolean frozenGround;

    private RunEffectsClientState() {
    }
}
