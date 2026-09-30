package net.ledok.arenas_ld.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.platform.ArenasPlatform;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Gametest entry point. Individual gametests will be added in later phases as
 * features land (PC-3 gametests, PE-* gametests, etc.).
 */
public final class ArenasLdGametests implements FabricGameTest {

    /** The loader platform layer is wired in a real server: service, packets, menus, commands. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void platformLayerIsWired(GameTestHelper helper) {
        helper.assertTrue(ArenasPlatform.INSTANCE.isModLoaded("arenas_ld"), "platform service resolves the mod");
        helper.assertTrue(ArenasPlatform.INSTANCE.configDir() != null, "platform service has a config dir");
        helper.assertTrue(ArenasNetwork.c2sPayloads().size() > 50, "server-bound payloads declared");
        helper.assertTrue(!ArenasNetwork.s2cPayloads().isEmpty(), "client-bound payloads declared");
        helper.assertTrue(BuiltInRegistries.MENU.getKey(ModScreenHandlers.DUNGEON_CONTROLLER_SCREEN_HANDLER) != null,
            "extended menu types registered");
        var commands = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
        helper.assertTrue(commands.getChild("arenasld") != null, "/arenasld registered through ArenasEvents");
        helper.succeed();
    }
}
