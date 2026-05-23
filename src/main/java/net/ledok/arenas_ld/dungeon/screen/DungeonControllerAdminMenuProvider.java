package net.ledok.arenas_ld.dungeon.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class DungeonControllerAdminMenuProvider implements ExtendedScreenHandlerFactory<DungeonControllerAdminData> {
    private final DungeonControllerBlockEntity controller;

    public DungeonControllerAdminMenuProvider(DungeonControllerBlockEntity controller) {
        this.controller = controller;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.arenas_ld.dungeon_controller_admin.title");
    }

    @Override
    public DungeonControllerAdminData getScreenOpeningData(ServerPlayer player) {
        Map<DifficultyTier, List<LeaderboardEntry>> topLeaderboards = new EnumMap<>(DifficultyTier.class);
        for (DifficultyTier tier : DifficultyTier.values()) {
            List<LeaderboardEntry> top10 = controller.getLeaderboards().getOrDefault(tier, List.of()).stream()
                .sorted(Comparator.comparingInt(LeaderboardEntry::timeSeconds))
                .limit(10)
                .toList();
            topLeaderboards.put(tier, top10);
        }
        return new DungeonControllerAdminData(
            controller.getBlockPos(),
            controller.getInstances(),
            controller.getActiveRuns().keySet(),
            controller.getInstanceCooldownTimers(),
            controller.getPendingInstanceRemovals(),
            controller.getCooldownTicks(),
            controller.getCloseTimerSeconds(),
            controller.getMaxPartySize(),
            controller.getInviteExpiryTicks(),
            controller.getTierConfigs(),
            topLeaderboards
        );
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new DungeonControllerAdminScreenHandler(syncId, inv, controller);
    }
}
