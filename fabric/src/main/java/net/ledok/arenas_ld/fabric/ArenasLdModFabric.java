package net.ledok.arenas_ld.fabric;

import net.fabricmc.api.ModInitializer;
import net.ledok.arenas_ld.ArenasLdMod;

public final class ArenasLdModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ArenasLdMod.init();
        // After init: the common code has recorded its packets and event listeners by now.
        FabricArenasWiring.register();
        // Run event log sources from optional mods (4.37.0).
        net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        if (loader.isModLoaded("spell_engine_expansion_ld") && !net.ledok.arenas_ld.fabric.compat.SpellActivityLog.register()) {
            // SEE before 0.3.7 has no SpellActivityEvents: the log just has no casts/procs/dodges.
            net.ledok.arenas_ld.ArenasLdMod.LOGGER.info("[Arenas] Spell Engine Expansion without SpellActivityEvents (needs 0.3.7+): no spell events in the run log");
        }
        if (loader.isModLoaded("trinkets")) {
            net.ledok.arenas_ld.fabric.compat.TrinketSwapLog.register();
        }
    }
}
