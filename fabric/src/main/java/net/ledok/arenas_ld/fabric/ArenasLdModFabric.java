package net.ledok.arenas_ld.fabric;

import net.fabricmc.api.ModInitializer;
import net.ledok.arenas_ld.ArenasLdMod;

public final class ArenasLdModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ArenasLdMod.init();
    }
}
