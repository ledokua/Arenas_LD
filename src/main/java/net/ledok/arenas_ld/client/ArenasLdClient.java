package net.ledok.arenas_ld.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.ledok.arenas_ld.dungeon.packet.DungeonBossSpawnerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.DungeonControllerAdminSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.DungeonControllerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.MobSpawnerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomControllerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.screen.RoomControllerScreen;
import net.ledok.arenas_ld.registry.BlockRegistry;
import net.ledok.arenas_ld.screen.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;

public class ArenasLdClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModScreenHandlers.BOSS_SPAWNER_SCREEN_HANDLER, BossSpawnerScreen::new);
        MenuScreens.register(ModScreenHandlers.MOB_ATTRIBUTES_SCREEN_HANDLER, MobAttributesScreen::new);
        MenuScreens.register(ModScreenHandlers.MOB_ARENA_SPAWNER_SCREEN_HANDLER, MobArenaSpawnerScreen::new);
        MenuScreens.register(ModScreenHandlers.MOB_ARENA_CONTROLLER_SCREEN_HANDLER, MobArenaControllerScreen::new);
        MenuScreens.register(ModScreenHandlers.RAID_CONTROLLER_SCREEN_HANDLER, RaidControllerScreen::new);
        MenuScreens.register(ModScreenHandlers.ROOM_CONTROLLER_SCREEN_HANDLER, RoomControllerScreen::new);
        MenuScreens.register(ModScreenHandlers.MOB_SPAWNER_SCREEN_HANDLER, net.ledok.arenas_ld.dungeon.screen.MobSpawnerScreen::new);
        MenuScreens.register(ModScreenHandlers.DUNGEON_BOSS_SPAWNER_SCREEN_HANDLER, net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerScreen::new);
        MenuScreens.register(ModScreenHandlers.DUNGEON_CONTROLLER_SCREEN_HANDLER, net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreen::new);
        MenuScreens.register(ModScreenHandlers.DUNGEON_CONTROLLER_ADMIN_SCREEN_HANDLER, net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminScreen::new);

        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.PHASE_BLOCK, RenderType.translucent());

        ConfiguratorOverlayRenderer.register();

        ClientPlayNetworking.registerGlobalReceiver(DungeonControllerSnapshotPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreen screen
                            && screen.matchesController(payload.data().blockPos())) {
                        screen.applyData(payload.data());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(RoomControllerSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.RoomControllerScreen screen
                    && screen.matchesController(payload.data().blockPos())) {
                    screen.applyData(payload.data());
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
    }
}
