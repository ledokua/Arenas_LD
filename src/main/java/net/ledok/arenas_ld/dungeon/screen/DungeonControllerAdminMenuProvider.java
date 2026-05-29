package net.ledok.arenas_ld.dungeon.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

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
        Map<net.minecraft.core.BlockPos, DungeonControllerAdminData.InstanceRun> runningInstances = new java.util.HashMap<>();
        controller.getActiveRuns().forEach((pos, run) ->
            runningInstances.put(pos, new DungeonControllerAdminData.InstanceRun(run.tier(), run.ownerName())));
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
            controller.getRespawnTimeTicks(),
            controller.getDeathTimePenaltyTicks(),
            controller.isLootViaInbox(),
            controller.getTierConfigs(),
            runningInstances,
            enumerateLootTables(controller.getLevel())
        );
    }

    private static java.util.List<String> enumerateLootTables(net.minecraft.world.level.Level level) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return java.util.List.of();
        net.minecraft.server.MinecraftServer server = serverLevel.getServer();
        if (server == null) return java.util.List.of();
        java.util.List<String> ids = new java.util.ArrayList<>();
        try {
            server.reloadableRegistries().lookup()
                .lookup(net.minecraft.core.registries.Registries.LOOT_TABLE)
                .ifPresent(lookup -> lookup.listElementIds()
                    .forEach(key -> ids.add(key.location().toString())));
        } catch (Exception ignored) {}
        ids.sort(null);
        return ids;
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new DungeonControllerAdminScreenHandler(syncId, inv, controller);
    }
}
