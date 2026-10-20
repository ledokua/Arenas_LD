package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.ledok.arenas_ld.platform.ExtendedMenuProvider;

public class DungeonControllerAdminMenuProvider implements ExtendedMenuProvider<DungeonControllerAdminData> {
    /** The registries {@link #lootTableIds} was read from: a /reload replaces them. Server thread only. */
    private static WeakReference<Object> lootTablesSource = new WeakReference<>(null);
    private static List<String> lootTableIds = List.of();

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
        return data(controller);
    }

    /** What an admin screen shows. The same for every viewer: build it once per send. */
    public static DungeonControllerAdminData data(DungeonControllerBlockEntity controller) {
        Map<BlockPos, DungeonControllerAdminData.InstanceRun> runningInstances = new HashMap<>();
        controller.getActiveRuns().forEach((pos, run) ->
            runningInstances.put(pos, new DungeonControllerAdminData.InstanceRun(run.tier(), run.ownerName())));
        return build(controller, runningInstances, knownLootTableIds(controller.getLevel()));
    }

    /** The server-side menu's copy: it only needs the controller's position and dimension. */
    static DungeonControllerAdminData menuData(DungeonControllerBlockEntity controller) {
        return build(controller, Map.of(), List.of());
    }

    private static DungeonControllerAdminData build(DungeonControllerBlockEntity controller,
                                                    Map<BlockPos, DungeonControllerAdminData.InstanceRun> runningInstances,
                                                    List<String> knownLootTableIds) {
        Level level = controller.getLevel();
        // Copies, not the controller's live views: one data object goes to several viewers, and a
        // singleplayer client gets it without encoding.
        return new DungeonControllerAdminData(
            controller.getBlockPos(),
            level != null ? level.dimension() : Level.OVERWORLD,
            List.copyOf(controller.getInstances()),
            Set.copyOf(controller.getActiveRuns().keySet()),
            Map.copyOf(controller.getInstanceCooldownTimers()),
            Set.copyOf(controller.getPendingInstanceRemovals()),
            controller.getCooldownTicks(),
            controller.getCloseTimerSeconds(),
            controller.getMaxPartySize(),
            controller.getInviteExpiryTicks(),
            controller.getRespawnTimeTicks(),
            controller.getDeathTimePenaltyTicks(),
            controller.getHpScalePerPlayer(),
            controller.getDungeonName(),
            Map.copyOf(controller.getTierConfigs()),
            runningInstances,
            knownLootTableIds,
            controller.activityId() == null ? "" : controller.activityId().toString()
        );
    }

    @Override
    public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, DungeonControllerAdminData> openingDataCodec() {
        return DungeonControllerAdminData.STREAM_CODEC;
    }

    /** Every loot table id, sorted; listed again only after a /reload. */
    private static List<String> knownLootTableIds(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return List.of();
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return List.of();
        Object source = server.reloadableRegistries();
        if (lootTablesSource.get() == source) {
            return lootTableIds;
        }
        List<String> ids = new ArrayList<>();
        try {
            server.reloadableRegistries().lookup()
                .lookup(net.minecraft.core.registries.Registries.LOOT_TABLE)
                .ifPresent(getter -> {
                    if (getter instanceof net.minecraft.core.HolderLookup.RegistryLookup<?> reg) {
                        reg.listElementIds().forEach(key -> ids.add(key.location().toString()));
                    }
                });
        } catch (Exception ignored) {}
        ids.sort(null);
        lootTableIds = List.copyOf(ids);
        lootTablesSource = new WeakReference<>(source);
        return lootTableIds;
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new DungeonControllerAdminScreenHandler(syncId, inv, controller);
    }
}
