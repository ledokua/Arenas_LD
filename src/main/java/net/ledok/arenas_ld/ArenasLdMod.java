package net.ledok.arenas_ld;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.ledok.arenas_ld.manager.PhaseBlockManager;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.registry.BlockRegistry;
import net.ledok.arenas_ld.registry.ItemRegistry;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.ledok.arenas_ld.util.BossDataComponent;
import net.ledok.arenas_ld.util.LinkerDataComponent;
import net.ledok.arenas_ld.util.LootBundleDataComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArenasLdMod implements ModInitializer {
    public static final String MOD_ID = "arenas_ld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final PhaseBlockManager PHASE_BLOCK_MANAGER = new PhaseBlockManager();

    @Override
    public void onInitialize() {
        LOGGER.info("Yggdrasil LD has been initialized!");
        ItemRegistry.initialize();
        BlockRegistry.initialize();
        BlockEntitiesRegistry.initialize();
        ModScreenHandlers.initialize();
        ModPackets.registerC2SPackets();
        BossDataComponent.initialize();
        LinkerDataComponent.initialize();
        LootBundleDataComponent.initialize();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PHASE_BLOCK_MANAGER.start();
        });

    }


}
