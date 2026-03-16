package net.ledok.arenas_ld.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.ledok.arenas_ld.registry.BlockRegistry;
import net.ledok.arenas_ld.screen.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;

public class ArenasLdClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.DungeonControllerInfoPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof net.ledok.arenas_ld.screen.DungeonControllerScreen screen) {
                    screen.applyServerInfo(payload);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.MobArenaControllerInfoPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().screen instanceof net.ledok.arenas_ld.screen.MobArenaControllerScreen screen) {
                    screen.applyServerInfo(payload);
                }
            });
        });

        MenuScreens.register(ModScreenHandlers.BOSS_SPAWNER_SCREEN_HANDLER, BossSpawnerScreen::new);
        MenuScreens.register(ModScreenHandlers.MOB_SPAWNER_SCREEN_HANDLER, MobSpawnerScreen::new);
        MenuScreens.register(ModScreenHandlers.MOB_ATTRIBUTES_SCREEN_HANDLER, MobAttributesScreen::new);
        MenuScreens.register(ModScreenHandlers.DUNGEON_BOSS_SPAWNER_SCREEN_HANDLER, DungeonBossSpawnerScreen::new);
        MenuScreens.register(ModScreenHandlers.MOB_ARENA_SPAWNER_SCREEN_HANDLER, MobArenaSpawnerScreen::new);
        MenuScreens.register(ModScreenHandlers.MOB_ARENA_CONTROLLER_SCREEN_HANDLER, MobArenaControllerScreen::new);
        MenuScreens.register(ModScreenHandlers.DUNGEON_CONTROLLER_SCREEN_HANDLER, DungeonControllerScreen::new);

        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.PHASE_BLOCK, RenderType.translucent());
    }
}
