package net.ledok.arenas_ld;

import net.ledok.arenas_ld.client.ArenasLdClient;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.ledok.arenas_ld.manager.DungeonBossManager;
import net.ledok.arenas_ld.manager.PhaseBlockManager;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.registry.BlockRegistry;
import net.ledok.arenas_ld.registry.CommandRegistry;
import net.ledok.arenas_ld.registry.ItemRegistry;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.ledok.arenas_ld.util.BossDataComponent;
import net.ledok.arenas_ld.util.LinkerDataComponent;
import net.ledok.arenas_ld.util.LinkerModeDataComponent;
import net.ledok.arenas_ld.util.LootBundleDataComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ArenasLdMod.MOD_ID)
public class ArenasLdMod {
    public static final String MOD_ID = "arenas_ld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final PhaseBlockManager PHASE_BLOCK_MANAGER = new PhaseBlockManager();
    public static final DungeonBossManager DUNGEON_BOSS_MANAGER = new DungeonBossManager();
    public static final ArenasLdConfig CONFIG = ArenasLdConfig.load();

    public ArenasLdMod(IEventBus modEventBus) {
        LOGGER.info("Yggdrasil LD has been initialized!");
        ItemRegistry.initialize(modEventBus);
        BlockRegistry.initialize(modEventBus);
        BlockEntitiesRegistry.initialize(modEventBus);
        ModScreenHandlers.initialize(modEventBus);
        ModPackets.registerC2SPackets(modEventBus);
        BossDataComponent.initialize(modEventBus);
        LinkerDataComponent.initialize(modEventBus);
        LinkerModeDataComponent.initialize(modEventBus);
        LootBundleDataComponent.initialize(modEventBus);
        CommandRegistry.initialize();
        DUNGEON_BOSS_MANAGER.initialize();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.register(ArenasLdClient.class);
        }

        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void onServerStarted(ServerStartedEvent event) {
        PHASE_BLOCK_MANAGER.start();
    }
}
