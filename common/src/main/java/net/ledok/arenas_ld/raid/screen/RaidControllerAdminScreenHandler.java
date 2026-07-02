package net.ledok.arenas_ld.raid.screen;

import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.raid.run.RaidTierConfig;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class RaidControllerAdminScreenHandler extends AbstractContainerMenu {
    private final BlockPos blockPos;
    private List<RaidControllerAdminData.InstanceEntry> instances;
    private Set<BlockPos> pendingRemovals;
    private int cooldownTicks;
    private int closeTimerSeconds;
    private int maxPartySize;
    private int respawnTimeTicks;
    private int inviteExpiryTicks;
    private int deathTimePenaltyTicks;
    private boolean lootViaInbox;
    private String raidName;
    private Map<DifficultyTier, RaidTierConfig> tierConfigs;
    private Map<BlockPos, RaidControllerAdminData.InstanceRun> runningInstances;
    private List<String> knownLootTableIds;

    public RaidControllerAdminScreenHandler(int syncId, Inventory inventory, RaidControllerAdminData data) {
        super(ModScreenHandlers.RAID_CONTROLLER_ADMIN_SCREEN_HANDLER, syncId);
        this.blockPos = data.blockPos();
        applyData(data);
    }

    public RaidControllerAdminScreenHandler(int syncId, Inventory inventory, RaidControllerBlockEntity controller) {
        this(syncId, inventory, controller.buildAdminData());
    }

    public BlockPos getBlockPos() { return blockPos; }
    public List<RaidControllerAdminData.InstanceEntry> getInstances() { return instances; }
    public Set<BlockPos> getPendingRemovals() { return pendingRemovals; }
    public int getCooldownTicks() { return cooldownTicks; }
    public int getCloseTimerSeconds() { return closeTimerSeconds; }
    public int getMaxPartySize() { return maxPartySize; }
    public int getRespawnTimeTicks() { return respawnTimeTicks; }
    public int getInviteExpiryTicks() { return inviteExpiryTicks; }
    public int getDeathTimePenaltyTicks() { return deathTimePenaltyTicks; }
    public boolean isLootViaInbox() { return lootViaInbox; }
    public String getRaidName() { return raidName == null ? "" : raidName; }
    public Map<DifficultyTier, RaidTierConfig> getTierConfigs() { return tierConfigs; }
    public Map<BlockPos, RaidControllerAdminData.InstanceRun> getRunningInstances() { return runningInstances; }
    public List<String> getKnownLootTableIds() { return knownLootTableIds != null ? knownLootTableIds : List.of(); }

    public void applyData(RaidControllerAdminData data) {
        this.instances = List.copyOf(data.instances());
        this.pendingRemovals = Set.copyOf(data.pendingRemovals());
        this.cooldownTicks = data.cooldownTicks();
        this.closeTimerSeconds = data.closeTimerSeconds();
        this.maxPartySize = data.maxPartySize();
        this.respawnTimeTicks = data.respawnTimeTicks();
        this.inviteExpiryTicks = data.inviteExpiryTicks();
        this.deathTimePenaltyTicks = data.deathTimePenaltyTicks();
        this.lootViaInbox = data.lootViaInbox();
        this.raidName = data.raidName();
        this.tierConfigs = Map.copyOf(data.tierConfigs());
        this.runningInstances = Map.copyOf(data.runningInstances());
        this.knownLootTableIds = List.copyOf(data.knownLootTableIds());
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
