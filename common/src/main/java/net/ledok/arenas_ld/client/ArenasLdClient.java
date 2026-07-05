package net.ledok.arenas_ld.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.ledok.arenas_ld.dungeon.packet.DungeonBossSpawnerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.DungeonControllerAdminSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.DungeonControllerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.MobSpawnerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomControllerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.screen.RoomControllerScreen;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.raid.packet.RaidControllerAdminSnapshotPayload;
import net.ledok.arenas_ld.raid.packet.RaidControllerSnapshotPayload;
import net.ledok.arenas_ld.raid.screen.RaidBossSpawnerScreen;
import net.ledok.arenas_ld.raid.screen.RaidControllerAdminScreen;
import net.ledok.arenas_ld.raid.screen.RaidControllerScreen;
import net.ledok.arenas_ld.registry.BlockRegistry;
import net.ledok.arenas_ld.screen.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public class ArenasLdClient {
    /**
     * Abstraction over menu-screen registration: Fabric passes
     * {@code MenuScreens::register}, NeoForge passes its
     * {@code RegisterMenuScreensEvent::register} (direct MenuScreens
     * registration is disallowed there).
     */
    @FunctionalInterface
    public interface ScreenRegistrar {
        <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void register(
                MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, S> constructor);
    }

    public static void registerScreens(ScreenRegistrar registrar) {
        registrar.register(ModScreenHandlers.RAID_BOSS_SPAWNER_SCREEN_HANDLER, RaidBossSpawnerScreen::new);
        registrar.register(ModScreenHandlers.MOB_ATTRIBUTES_SCREEN_HANDLER, MobAttributesScreen::new);
        registrar.register(ModScreenHandlers.EQUIPMENT_SCREEN_HANDLER, net.ledok.arenas_ld.screen.EquipmentScreen::new);
        registrar.register(ModScreenHandlers.RAID_CONTROLLER_SCREEN_HANDLER, RaidControllerScreen::new);
        registrar.register(ModScreenHandlers.ROOM_CONTROLLER_SCREEN_HANDLER, RoomControllerScreen::new);
        registrar.register(ModScreenHandlers.MOB_SPAWNER_SCREEN_HANDLER, net.ledok.arenas_ld.dungeon.screen.MobSpawnerScreen::new);
        registrar.register(ModScreenHandlers.DUNGEON_BOSS_SPAWNER_SCREEN_HANDLER, net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerScreen::new);
        registrar.register(ModScreenHandlers.DUNGEON_CONTROLLER_SCREEN_HANDLER, net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreen::new);
        registrar.register(ModScreenHandlers.DUNGEON_CONTROLLER_ADMIN_SCREEN_HANDLER, net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminScreen::new);
        registrar.register(ModScreenHandlers.RAID_CONTROLLER_ADMIN_SCREEN_HANDLER, RaidControllerAdminScreen::new);
        registrar.register(ModScreenHandlers.ARENA_CONTROLLER_SCREEN_HANDLER, net.ledok.arenas_ld.arena.screen.ArenaControllerScreen::new);
        registrar.register(ModScreenHandlers.ARENA_CONTROLLER_ADMIN_SCREEN_HANDLER, net.ledok.arenas_ld.arena.screen.ArenaControllerAdminScreen::new);
        registrar.register(ModScreenHandlers.ARENA_SPAWNER_SCREEN_HANDLER, net.ledok.arenas_ld.arena.screen.ArenaSpawnerScreen::new);
    }

    public static void init() {
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.PHASE_BLOCK, RenderType.translucent());

        SelectionOverlayRenderer.register();
        SpawnTelegraphRenderer.register();

        ClientPlayNetworking.registerGlobalReceiver(net.ledok.arenas_ld.dungeon.packet.DungeonCloseScreenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreen screen
                            && screen.matchesController(payload.controllerPos())) {
                        screen.onClose();
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(net.ledok.arenas_ld.raid.packet.RaidCloseScreenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (Minecraft.getInstance().screen instanceof RaidControllerScreen screen
                            && screen.matchesController(payload.controllerPos())) {
                        screen.onClose();
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(DungeonControllerSnapshotPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreen screen
                            && screen.matchesController(payload.data().blockPos())) {
                        screen.applyData(payload.data());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(net.ledok.arenas_ld.dungeon.packet.SpawnTelegraphPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (context.client().level != null) {
                    SpawnTelegraphStore.set(payload.positions(), payload.durationTicks(), context.client().level.getGameTime());
                }
            }));
        ClientPlayNetworking.registerGlobalReceiver(RoomControllerSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.RoomControllerScreen screen
                    && screen.matchesController(payload.data().blockPos())) {
                    screen.applyData(payload.data());
                } else if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.RoomRewardsScreen rewardsScreen
                    && rewardsScreen.matchesController(payload.data().blockPos())) {
                    rewardsScreen.applyData(payload.data());
                }
            }));
        ClientPlayNetworking.registerGlobalReceiver(MobSpawnerSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.MobSpawnerScreen screen
                    && screen.matchesSpawner(payload.data().blockPos())) {
                    screen.applyData(payload.data());
                }
            }));
        ClientPlayNetworking.registerGlobalReceiver(DungeonBossSpawnerSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerScreen screen
                    && screen.matchesSpawner(payload.data().blockPos())) {
                    screen.applyData(payload.data());
                }
            }));
        ClientPlayNetworking.registerGlobalReceiver(DungeonControllerAdminSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminScreen screen
                    && screen.matchesController(payload.data().blockPos())) {
                    screen.applyData(payload.data());
                }
            }));
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.RaidControllerInfoPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof RaidControllerScreen screen
                        && payload.pos().equals(screen.getMenu().getPos())) {
                    screen.applyServerInfo(payload);
                }
            }));
        ClientPlayNetworking.registerGlobalReceiver(RaidControllerAdminSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof RaidControllerAdminScreen screen
                        && screen.matchesController(payload.data().blockPos())) {
                    screen.applyData(payload.data());
                }
            }));
        ClientPlayNetworking.registerGlobalReceiver(RaidControllerSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof RaidControllerScreen screen
                        && payload.data().blockPos().equals(screen.getMenu().getPos())) {
                    screen.applyData(payload.data());
                }
            }));
    }
}
