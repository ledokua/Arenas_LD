package net.ledok.arenas_ld.raid.screen;

import net.ledok.arenas_ld.activity.AdminMenuBase;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.raid.run.RaidTierConfig;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class RaidControllerAdminScreenHandler extends AdminMenuBase {
    private final BlockPos blockPos;
    private final ResourceKey<Level> dimension;
    private List<RaidControllerAdminData.InstanceEntry> instances = List.of();
    private Set<BlockPos> pendingRemovals = Set.of();
    private int cooldownTicks;
    private int closeTimerSeconds;
    private int maxPartySize;
    private int respawnTimeTicks;
    private int inviteExpiryTicks;
    private int deathTimePenaltyTicks;
    private String raidName = "";
    private Map<DifficultyTier, RaidTierConfig> tierConfigs = Map.of();
    private Map<BlockPos, RaidControllerAdminData.InstanceRun> runningInstances = Map.of();
    private List<String> knownLootTableIds = List.of();
    private String activityId = "";

    /** Client side, from the opening data; snapshots refresh it through {@link #applyData}. */
    public RaidControllerAdminScreenHandler(int syncId, Inventory inventory, RaidControllerAdminData data) {
        super(ModScreenHandlers.RAID_CONTROLLER_ADMIN_SCREEN_HANDLER, syncId);
        this.blockPos = data.blockPos();
        this.dimension = data.dimension();
        applyData(data);
    }

    /**
     * Server side: only which controller it edits. The screen's data goes out as opening data and
     * snapshots, so it is not built a second time for a menu that never reads it.
     */
    public RaidControllerAdminScreenHandler(int syncId, Inventory inventory, RaidControllerBlockEntity controller) {
        super(ModScreenHandlers.RAID_CONTROLLER_ADMIN_SCREEN_HANDLER, syncId);
        this.blockPos = controller.getBlockPos();
        Level level = controller.getLevel();
        this.dimension = level != null ? level.dimension() : Level.OVERWORLD;
    }

    public BlockPos getBlockPos() { return blockPos; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public List<RaidControllerAdminData.InstanceEntry> getInstances() { return instances; }
    public Set<BlockPos> getPendingRemovals() { return pendingRemovals; }
    public int getCooldownTicks() { return cooldownTicks; }
    public int getCloseTimerSeconds() { return closeTimerSeconds; }
    public int getMaxPartySize() { return maxPartySize; }
    public int getRespawnTimeTicks() { return respawnTimeTicks; }
    public int getInviteExpiryTicks() { return inviteExpiryTicks; }
    public int getDeathTimePenaltyTicks() { return deathTimePenaltyTicks; }
    public String getRaidName() { return raidName == null ? "" : raidName; }
    public Map<DifficultyTier, RaidTierConfig> getTierConfigs() { return tierConfigs; }
    public Map<BlockPos, RaidControllerAdminData.InstanceRun> getRunningInstances() { return runningInstances; }
    public List<String> getKnownLootTableIds() { return knownLootTableIds != null ? knownLootTableIds : List.of(); }
    public String getActivityId() { return activityId == null ? "" : activityId; }

    public void applyData(RaidControllerAdminData data) {
        this.instances = List.copyOf(data.instances());
        this.pendingRemovals = Set.copyOf(data.pendingRemovals());
        this.cooldownTicks = data.cooldownTicks();
        this.closeTimerSeconds = data.closeTimerSeconds();
        this.maxPartySize = data.maxPartySize();
        this.respawnTimeTicks = data.respawnTimeTicks();
        this.inviteExpiryTicks = data.inviteExpiryTicks();
        this.deathTimePenaltyTicks = data.deathTimePenaltyTicks();
        this.raidName = data.raidName();
        this.tierConfigs = Map.copyOf(data.tierConfigs());
        this.runningInstances = Map.copyOf(data.runningInstances());
        this.knownLootTableIds = List.copyOf(data.knownLootTableIds());
        this.activityId = data.activityId();
    }

    @Override
    public BlockPos controllerPos() {
        return blockPos;
    }

    @Override
    protected boolean isController(@Nullable BlockEntity be) {
        return be instanceof RaidControllerBlockEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
