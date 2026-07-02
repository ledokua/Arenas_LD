package net.ledok.arenas_ld.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.ledok.arenas_ld.client.ArenasLdClient;

public final class ArenasLdClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ArenasLdClient.init();
    }
}
