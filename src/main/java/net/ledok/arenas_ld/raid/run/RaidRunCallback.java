package net.ledok.arenas_ld.raid.run;

import net.minecraft.core.BlockPos;

import java.util.List;

public interface RaidRunCallback {
    void onRaidEnded(
            BlockPos spawnerPos,
            boolean wasWin,
            RaidDifficulty difficulty,
            List<String> playerNames,
            int timeSeconds
    );
}
