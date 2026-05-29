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

import java.util.ArrayList;
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
    private int respawnTimeTicks;
    private int deathTimePenaltyTicks;
    private boolean lootViaInbox;
    private Map<DifficultyTier, TierConfig> tierConfigs;
    private Map<BlockPos, DungeonControllerAdminData.InstanceRun> runningInstances;
    private List<String> knownLootTableIds;

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
        this.respawnTimeTicks = data.respawnTimeTicks();
        this.deathTimePenaltyTicks = data.deathTimePenaltyTicks();
        this.lootViaInbox = data.lootViaInbox();
        this.tierConfigs = Map.copyOf(data.tierConfigs());
        this.runningInstances = Map.copyOf(data.runningInstances());
        this.knownLootTableIds = List.copyOf(data.knownLootTableIds());
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
            controller.getRespawnTimeTicks(),
            controller.getDeathTimePenaltyTicks(),
            controller.isLootViaInbox(),
            controller.getTierConfigs(),
            Map.of(),
            enumerateLootTables(controller.getLevel())
        ));
    }

    private static List<String> enumerateLootTables(net.minecraft.world.level.Level level) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return List.of();
        net.minecraft.server.MinecraftServer server = serverLevel.getServer();
        if (server == null) return List.of();
        List<String> ids = new ArrayList<>();
        try {
            server.reloadableRegistries().lookup()
                .lookup(net.minecraft.core.registries.Registries.LOOT_TABLE)
                .ifPresent(lookup -> lookup.listElementIds()
                    .forEach(key -> ids.add(key.location().toString())));
        } catch (Exception ignored) {}
        ids.sort(null);
        return ids;
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
    public int getRespawnTimeTicks() { return respawnTimeTicks; }
    public int getDeathTimePenaltyTicks() { return deathTimePenaltyTicks; }
    public boolean isLootViaInbox() { return lootViaInbox; }
    public Map<DifficultyTier, TierConfig> getTierConfigs() { return tierConfigs; }
    public Map<BlockPos, DungeonControllerAdminData.InstanceRun> getRunningInstances() { return runningInstances; }
    public List<String> getKnownLootTableIds() { return knownLootTableIds; }

    public void applyData(DungeonControllerAdminData data) {
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
        this.lootViaInbox = data.lootViaInbox();
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
