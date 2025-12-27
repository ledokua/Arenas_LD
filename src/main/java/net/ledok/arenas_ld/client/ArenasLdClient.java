package net.ledok.arenas_ld.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.ledok.arenas_ld.registry.BlockRegistry;
import net.ledok.arenas_ld.screen.*;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;

public class ArenasLdClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModScreenHandlers.BOSS_SPAWNER_SCREEN_HANDLER, BossSpawnerScreen::new);
        MenuScreens.register(ModScreenHandlers.MOB_SPAWNER_SCREEN_HANDLER, MobSpawnerScreen::new);
        MenuScreens.register(ModScreenHandlers.MOB_ATTRIBUTES_SCREEN_HANDLER, MobAttributesScreen::new);
        MenuScreens.register(ModScreenHandlers.DUNGEON_BOSS_SPAWNER_SCREEN_HANDLER, DungeonBossSpawnerScreen::new);


        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.PHASE_BLOCK, RenderType.translucent());
    }
}
