package net.ledok.arenas_ld.neoforge;

import net.ledok.arenas_ld.client.ArenasClientEvents;
import net.ledok.arenas_ld.client.ArenasLdClient;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-side NeoForge wiring; only touched when FMLEnvironment.dist is CLIENT. */
final class ArenasLdClientNeoForge {
    private ArenasLdClientNeoForge() {}

    @SuppressWarnings("deprecation") // setRenderLayer: the code path matching Fabric's BlockRenderLayerMap
    static void register(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent event) -> ArenasLdClient.registerScreens(event::register));
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            ArenasLdClient.registerRenderLayers(ItemBlockRenderTypes::setRenderLayer);
            ArenasLdClient.init();
        }));
        // Same world state as Fabric's AFTER_TRANSLUCENT: the level's pose stack, with the view
        // rotation already in RenderSystem's model-view matrix.
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                ArenasClientEvents.fireAfterTranslucent(
                    new ArenasClientEvents.WorldRenderContext(event.getPoseStack(), event.getCamera()));
            }
        });
    }
}
