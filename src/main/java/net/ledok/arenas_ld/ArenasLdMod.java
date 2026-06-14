package net.ledok.arenas_ld;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.ledok.arenas_ld.dungeon.manager.DungeonManager;
import net.ledok.arenas_ld.dungeon.run.DungeonConnectionListener;
import net.ledok.arenas_ld.raid.manager.RaidBossManager;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.registry.*;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.ledok.arenas_ld.util.BossDataComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArenasLdMod implements ModInitializer {
    public static final String MOD_ID = "arenas_ld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final RaidBossManager RAID_BOSS_MANAGER = new RaidBossManager();
    public static final DungeonManager DUNGEON_MANAGER = new DungeonManager();
    public static final ArenasLdConfig CONFIG = ArenasLdConfig.load();

    @Override
    public void onInitialize() {
        LOGGER.info("Arenas_LD has been initialized!");
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
        RAID_BOSS_MANAGER.initialize();
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DUNGEON_MANAGER.clearForServerStop());
        DungeonConnectionListener.register();
        net.ledok.arenas_ld.util.RunItemRestrictions.register();
        net.ledok.arenas_ld.util.RunEffectRestrictions.register();
    }
}
