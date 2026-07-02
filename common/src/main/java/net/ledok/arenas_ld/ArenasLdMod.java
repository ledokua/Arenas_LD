package net.ledok.arenas_ld;

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

public class ArenasLdMod {
    public static final String MOD_ID = "arenas_ld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final RaidBossManager RAID_BOSS_MANAGER = new RaidBossManager();
    public static final DungeonManager DUNGEON_MANAGER = new DungeonManager();
    public static final ArenasLdConfig CONFIG = ArenasLdConfig.load();

    /** Full Fabric-style init: content registration plus runtime wiring. */
    public static void init() {
        registerContent();
        initRuntime();
    }

    /**
     * Class-loads the content classes, running their registrations through
     * {@link RegistryBridge}. On NeoForge this must NOT run during mod
     * construction: vanilla Item/Block constructors create intrusive registry
     * holders, which are only legal while registries are unfrozen — i.e. during
     * the RegisterEvent phase.
     */
    public static void registerContent() {
        ItemRegistry.initialize();
        BlockRegistry.initialize();
        BlockEntitiesRegistry.initialize();
        DataComponentRegistry.initialize();
        ModCreativeModeTabs.initialize();
        ModScreenHandlers.initialize();
        BossDataComponent.initialize();
    }

    /** Packet, command, and event wiring; safe to run during mod construction. */
    public static void initRuntime() {
        LOGGER.info("Arenas_LD has been initialized!");
        ModPackets.registerC2SPackets();
        ModPackets.registerS2CPackets();
        CommandRegistry.initialize();
        RAID_BOSS_MANAGER.initialize();
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DUNGEON_MANAGER.clearForServerStop());
        DungeonConnectionListener.register();
        net.ledok.arenas_ld.arena.run.ArenaConnectionListener.register();
        net.ledok.arenas_ld.util.RunItemRestrictions.register();
        net.ledok.arenas_ld.util.RunEffectRestrictions.register();
    }
}
