package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.dungeon.run.DungeonInstanceRef;

public record InstanceState(
        DungeonInstanceRef ref,
        InstanceStatus status,
        int cooldownTicksRemaining
) {
}
