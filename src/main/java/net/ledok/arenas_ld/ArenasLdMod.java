package net.ledok.arenas_ld;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.ledok.arenas_ld.event.PlayerTickHandler;
import net.ledok.arenas_ld.manager.DungeonBossManager;
import net.ledok.arenas_ld.manager.MobArenaManager;
import net.ledok.arenas_ld.manager.PhaseBlockManager;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.registry.*;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.ledok.arenas_ld.util.BossDataComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArenasLdMod implements ModInitializer {
    public static final String MOD_ID = "arenas_ld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final PhaseBlockManager PHASE_BLOCK_MANAGER = new PhaseBlockManager();
    public static final DungeonBossManager DUNGEON_BOSS_MANAGER = new DungeonBossManager();
    public static final MobArenaManager MOB_ARENA_MANAGER = new MobArenaManager();
    public static final ArenasLdConfig CONFIG = ArenasLdConfig.load();

    @Override
    public void onInitialize() {
        LOGGER.info("Yggdrasil LD has been initialized!");
        ItemRegistry.initialize();
        BlockRegistry.initialize();
        BlockEntitiesRegistry.initialize();
        DataComponentRegistry.initialize();
        ModCreativeModeTabs.initialize();
        ModScreenHandlers.initialize();
        ModPackets.registerC2SPackets();
        ModPackets.registerS2CPackets();
        BossDataComponent.initialize();
        CommandRegistry.initialize();
        DUNGEON_BOSS_MANAGER.initialize();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PHASE_BLOCK_MANAGER.start();
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                PlayerTickHandler.onPlayerTick(player);
            }
            DUNGEON_BOSS_MANAGER.tick(server);
        });
    }
}
