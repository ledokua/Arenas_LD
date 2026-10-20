package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.activity.AdminMenuBase;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
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

public class DungeonControllerAdminScreenHandler extends AdminMenuBase {
    private final BlockPos blockPos;
    /** The controller's dimension, from the opening data and each snapshot. */
    private ResourceKey<Level> dimension;
    private List<BlockPos> instances;
    private Set<BlockPos> activeRunInstances;
    private Map<BlockPos, Integer> instanceCooldownTimers;
    private Set<BlockPos> pendingRemovals;
    private int cooldownTicks;
    private int closeTimerSeconds;
    private int maxPartySize;
    private int inviteExpiryTicks;
    private int respawnTimeTicks;
    private int deathTimePenaltyTicks;
    private double hpScalePerPlayer;
    private String dungeonName;
    private Map<DifficultyTier, TierConfig> tierConfigs;
    private Map<BlockPos, DungeonControllerAdminData.InstanceRun> runningInstances;
    private List<String> knownLootTableIds;
    private String activityId;

    public DungeonControllerAdminScreenHandler(int syncId, Inventory inventory, DungeonControllerAdminData data) {
        super(ModScreenHandlers.DUNGEON_CONTROLLER_ADMIN_SCREEN_HANDLER, syncId);
        this.blockPos = data.blockPos();
        this.dimension = data.dimension();
        this.instances = List.copyOf(data.instances());
        this.activeRunInstances = Set.copyOf(data.activeRunInstances());
        this.instanceCooldownTimers = Map.copyOf(data.instanceCooldownTimers());
        this.pendingRemovals = Set.copyOf(data.pendingRemovals());
        this.cooldownTicks = data.cooldownTicks();
        this.closeTimerSeconds = data.closeTimerSeconds();
        this.maxPartySize = data.maxPartySize();
        this.inviteExpiryTicks = data.inviteExpiryTicks();
        this.respawnTimeTicks = data.respawnTimeTicks();
        this.deathTimePenaltyTicks = data.deathTimePenaltyTicks();
        this.hpScalePerPlayer = data.hpScalePerPlayer();
        this.dungeonName = data.dungeonName();
        this.tierConfigs = Map.copyOf(data.tierConfigs());
        this.runningInstances = Map.copyOf(data.runningInstances());
        this.knownLootTableIds = List.copyOf(data.knownLootTableIds());
        this.activityId = data.activityId();
    }

    /** Server side: the menu only needs to know which controller it edits. */
    public DungeonControllerAdminScreenHandler(int syncId, Inventory inventory, DungeonControllerBlockEntity controller) {
        this(syncId, inventory, DungeonControllerAdminMenuProvider.menuData(controller));
    }

    @Override
    public BlockPos controllerPos() {
        return blockPos;
    }

    @Override
    protected boolean isController(@Nullable BlockEntity be) {
        return be instanceof DungeonControllerBlockEntity;
    }

    public BlockPos getBlockPos() { return blockPos; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public List<BlockPos> getInstances() { return instances; }
    public Set<BlockPos> getActiveRunInstances() { return activeRunInstances; }
    public Map<BlockPos, Integer> getInstanceCooldownTimers() { return instanceCooldownTimers; }
    public Set<BlockPos> getPendingRemovals() { return pendingRemovals; }
    public int getCooldownTicks() { return cooldownTicks; }
    public int getCloseTimerSeconds() { return closeTimerSeconds; }
    public int getMaxPartySize() { return maxPartySize; }
    public int getInviteExpiryTicks() { return inviteExpiryTicks; }
    public int getRespawnTimeTicks() { return respawnTimeTicks; }
    public int getDeathTimePenaltyTicks() { return deathTimePenaltyTicks; }
    public double getHpScalePerPlayer() { return hpScalePerPlayer; }
    public String getDungeonName() { return dungeonName; }
    public Map<DifficultyTier, TierConfig> getTierConfigs() { return tierConfigs; }
    public Map<BlockPos, DungeonControllerAdminData.InstanceRun> getRunningInstances() { return runningInstances; }
    public List<String> getKnownLootTableIds() { return knownLootTableIds; }
    public String getActivityId() { return activityId == null ? "" : activityId; }

    public void applyData(DungeonControllerAdminData data) {
        this.dimension = data.dimension();
        this.instances = List.copyOf(data.instances());
        this.activeRunInstances = Set.copyOf(data.activeRunInstances());
        this.instanceCooldownTimers = Map.copyOf(data.instanceCooldownTimers());
        this.pendingRemovals = Set.copyOf(data.pendingRemovals());
        this.cooldownTicks = data.cooldownTicks();
        this.closeTimerSeconds = data.closeTimerSeconds();
        this.maxPartySize = data.maxPartySize();
        this.inviteExpiryTicks = data.inviteExpiryTicks();
        this.respawnTimeTicks = data.respawnTimeTicks();
        this.deathTimePenaltyTicks = data.deathTimePenaltyTicks();
        this.hpScalePerPlayer = data.hpScalePerPlayer();
        this.dungeonName = data.dungeonName();
        this.tierConfigs = Map.copyOf(data.tierConfigs());
        this.runningInstances = Map.copyOf(data.runningInstances());
        this.knownLootTableIds = List.copyOf(data.knownLootTableIds());
        this.activityId = data.activityId();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
