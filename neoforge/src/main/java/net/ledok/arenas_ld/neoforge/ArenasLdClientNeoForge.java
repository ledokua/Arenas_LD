package net.ledok.arenas_ld.neoforge;

import net.ledok.arenas_ld.client.ArenasLdClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-side NeoForge wiring; only touched when FMLEnvironment.dist is CLIENT. */
final class ArenasLdClientNeoForge {
    private ArenasLdClientNeoForge() {}

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent event) -> ArenasLdClient.registerScreens(event::register));
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(ArenasLdClient::init));
    }
}
