package net.ledok.arenas_ld.neoforge;

import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.platform.ArenasPlatform;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Smoke test of the platform layer on NeoForge, mirroring the Fabric gametest. NeoForge picks it
 * up from the holder annotation only when GameTests are enabled (the gameTestServer dev run), so it
 * never runs in production. The template is run-gametest/gameteststructures/empty.snbt.
 */
@GameTestHolder("arenas_ld")
@PrefixGameTestTemplate(false)
public final class NeoForgeGametests {
    private NeoForgeGametests() {}

    @GameTest(template = "empty")
    public static void platformLayerIsWired(GameTestHelper helper) {
        helper.assertTrue(ArenasPlatform.INSTANCE instanceof NeoForgeArenasPlatform, "NeoForge platform service resolves");
        helper.assertTrue(ArenasPlatform.INSTANCE.isModLoaded("arenas_ld"), "platform service resolves the mod");
        helper.assertTrue(!ArenasPlatform.INSTANCE.isModLoaded("fabric_api"), "no Fabric API present");
        helper.assertTrue(ArenasNetwork.c2sPayloads().size() > 50, "server-bound payloads declared");
        helper.assertTrue(!ArenasNetwork.s2cPayloads().isEmpty(), "client-bound payloads declared");
        helper.assertTrue(BuiltInRegistries.MENU.getKey(ModScreenHandlers.DUNGEON_CONTROLLER_SCREEN_HANDLER) != null,
            "extended menu types registered");
        var commands = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
        helper.assertTrue(commands.getChild("arenasld") != null, "/arenasld registered through ArenasEvents");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void apiActivityIndex(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.activityIndex(helper);
    }

    @GameTest(template = "empty")
    public static void apiEntryGate(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.entryGate(helper);
    }

    @GameTest(template = "empty")
    public static void apiCopiedController(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.copiedController(helper);
    }

    @GameTest(template = "empty")
    public static void runTeams(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runTeams(helper);
    }

    @GameTest(template = "empty")
    public static void instanceCheck(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.instanceCheck(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void controllerChunks(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.controllerChunks(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void runMinions(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runMinions(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void evokerMinions(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.evokerMinions(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 6200, skyAccess = true)
    public static void lichMinions(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.lichMinions(helper);
    }
}
