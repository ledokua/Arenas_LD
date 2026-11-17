package net.ledok.arenas_ld.stats;

import org.jetbrains.annotations.Nullable;

/**
 * The Spell Engine spell whose impact is running right now (server thread), so a hit it deals is put down to that
 * spell ({@code SpellImpactsMixin}). Impacts nest (an area impact runs the impacts of its targets), hence the stack.
 */
public final class SpellContext {
    private static final Object[] STACK = new Object[16];
    private static int depth;

    private SpellContext() {
    }

    /** The innermost running spell (its registry holder), or null outside a spell impact. */
    public static @Nullable Object current() {
        return depth == 0 ? null : STACK[Math.min(depth, STACK.length) - 1];
    }

    public static void push(Object spell) {
        if (depth < STACK.length) {
            STACK[depth] = spell;
        }
        depth++;
    }

    /** Once a tick: an impact that threw never popped its spell; nothing carries over to the next tick. */
    public static void reset() {
        while (depth > 0) {
            pop();
        }
    }

    public static void pop() {
        if (depth > 0) {
            depth--;
            if (depth < STACK.length) {
                STACK[depth] = null;
            }
        }
    }
}
