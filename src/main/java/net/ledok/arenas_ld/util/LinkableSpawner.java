package net.ledok.arenas_ld.util;

import net.minecraft.core.BlockPos;
import java.util.List;

public interface LinkableSpawner {
    void addLinkedSpawner(BlockPos pos);
    boolean removeLinkedSpawner(BlockPos pos);
    void clearLinkedSpawners();
    List<BlockPos> getLinkedSpawners();
    void forceReset();

    default void startForDungeon(DungeonContext ctx) {
        forceReset();
    }
}
