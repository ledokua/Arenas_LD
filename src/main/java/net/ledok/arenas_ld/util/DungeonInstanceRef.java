package net.ledok.arenas_ld.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record DungeonInstanceRef(BlockPos spawnerPos, ResourceKey<Level> dimension) {
}
