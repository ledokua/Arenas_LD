package net.ledok.arenas_ld.client;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.registry.BlockRegistry;
import net.ledok.arenas_ld.screen.*;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class ArenasLdClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ArenasLdMod.LOGGER.info("ArenasLdClient: Client Setup");
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        ArenasLdMod.LOGGER.info("ArenasLdClient: Registering Screens");
        try {
            event.register(ModScreenHandlers.BOSS_SPAWNER_SCREEN_HANDLER.get(), BossSpawnerScreen::new);
            event.register(ModScreenHandlers.MOB_SPAWNER_SCREEN_HANDLER.get(), MobSpawnerScreen::new);
            event.register(ModScreenHandlers.MOB_ATTRIBUTES_SCREEN_HANDLER.get(), MobAttributesScreen::new);
            event.register(ModScreenHandlers.DUNGEON_BOSS_SPAWNER_SCREEN_HANDLER.get(), DungeonBossSpawnerScreen::new);
            event.register(ModScreenHandlers.EQUIPMENT_SCREEN_HANDLER.get(), EquipmentScreen::new);
        } catch (Exception e) {
            ArenasLdMod.LOGGER.error("Failed to register screens", e);
        }
    }
}
