package net.ledok.arenas_ld.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.ledok.arenas_ld.client.ArenasLdClient;
import net.minecraft.client.gui.screens.MenuScreens;

public final class ArenasLdClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ArenasLdClient.registerScreens(MenuScreens::register);
        ArenasLdClient.init();
    }
}
