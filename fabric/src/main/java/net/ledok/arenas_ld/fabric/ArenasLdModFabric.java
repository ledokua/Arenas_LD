package net.ledok.arenas_ld.fabric;

import net.fabricmc.api.ModInitializer;
import net.ledok.arenas_ld.ArenasLdMod;

public final class ArenasLdModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ArenasLdMod.init();
        // After init: the common code has recorded its packets and event listeners by now.
        FabricArenasWiring.register();
    }
}
