package net.ledok.arenas_ld.util;

public record InstanceState(
        DungeonInstanceRef ref,
        InstanceStatus status,
        int cooldownTicksRemaining
) {
}
