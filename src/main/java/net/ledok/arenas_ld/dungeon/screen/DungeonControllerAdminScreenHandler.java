package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DungeonControllerAdminScreenHandler extends AbstractContainerMenu {
    private final BlockPos blockPos;
    private List<BlockPos> instances;
    private Set<BlockPos> activeRunInstances;
    private Map<BlockPos, Integer> instanceCooldownTimers;
    private Set<BlockPos> pendingRemovals;
    private int cooldownTicks;
    private int closeTimerSeconds;
    private int maxPartySize;
    private int inviteExpiryTicks;
    private Map<DifficultyTier, TierConfig> tierConfigs;
    private Map<BlockPos, DungeonControllerAdminData.InstanceRun> runningInstances;

    public DungeonControllerAdminScreenHandler(int syncId, Inventory inventory, DungeonControllerAdminData data) {
        super(ModScreenHandlers.DUNGEON_CONTROLLER_ADMIN_SCREEN_HANDLER, syncId);
        this.blockPos = data.blockPos();
        this.instances = List.copyOf(data.instances());
        this.activeRunInstances = Set.copyOf(data.activeRunInstances());
        this.instanceCooldownTimers = Map.copyOf(data.instanceCooldownTimers());
        this.pendingRemovals = Set.copyOf(data.pendingRemovals());
        this.cooldownTicks = data.cooldownTicks();
        this.closeTimerSeconds = data.closeTimerSeconds();
        this.maxPartySize = data.maxPartySize();
        this.inviteExpiryTicks = data.inviteExpiryTicks();
        this.tierConfigs = Map.copyOf(data.tierConfigs());
        this.runningInstances = Map.copyOf(data.runningInstances());
    }

    public DungeonControllerAdminScreenHandler(int syncId, Inventory inventory, DungeonControllerBlockEntity controller) {
        this(syncId, inventory, new DungeonControllerAdminData(
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
            Map.of()
        ));
    }

    public BlockPos getBlockPos() { return blockPos; }
    public List<BlockPos> getInstances() { return instances; }
    public Set<BlockPos> getActiveRunInstances() { return activeRunInstances; }
    public Map<BlockPos, Integer> getInstanceCooldownTimers() { return instanceCooldownTimers; }
    public Set<BlockPos> getPendingRemovals() { return pendingRemovals; }
    public int getCooldownTicks() { return cooldownTicks; }
    public int getCloseTimerSeconds() { return closeTimerSeconds; }
    public int getMaxPartySize() { return maxPartySize; }
    public int getInviteExpiryTicks() { return inviteExpiryTicks; }
    public Map<DifficultyTier, TierConfig> getTierConfigs() { return tierConfigs; }
    public Map<BlockPos, DungeonControllerAdminData.InstanceRun> getRunningInstances() { return runningInstances; }

    public void applyData(DungeonControllerAdminData data) {
        this.instances = List.copyOf(data.instances());
        this.activeRunInstances = Set.copyOf(data.activeRunInstances());
        this.instanceCooldownTimers = Map.copyOf(data.instanceCooldownTimers());
        this.pendingRemovals = Set.copyOf(data.pendingRemovals());
        this.cooldownTicks = data.cooldownTicks();
        this.closeTimerSeconds = data.closeTimerSeconds();
        this.maxPartySize = data.maxPartySize();
        this.inviteExpiryTicks = data.inviteExpiryTicks();
        this.tierConfigs = Map.copyOf(data.tierConfigs());
        this.runningInstances = Map.copyOf(data.runningInstances());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64;
    }
}
