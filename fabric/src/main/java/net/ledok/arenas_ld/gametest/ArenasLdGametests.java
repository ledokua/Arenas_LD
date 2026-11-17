package net.ledok.arenas_ld.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.platform.ArenasPlatform;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Gametest entry point: the platform layer smoke test and the public API ({@code ApiGameTests}). */
public final class ArenasLdGametests implements FabricGameTest {
    static {
        // Runs start at once in tests; apiRunPreparation turns the countdown on for itself (own batch).
        // Only in a game test run: this class is loaded on normal servers too (the loader reads test entrypoints),
        // and there it must not switch the start countdown off (4.29.x–4.33.x shipped that bug).
        if (System.getProperty("fabric-api.gametest") != null) {
            net.ledok.arenas_ld.util.RunPreparation.secondsOverride = 0;
        }
    }


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

    @GameTest(template = EMPTY_STRUCTURE)
    public void runEventLog(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runEventLog(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void runDetailsClassBook(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runDetailsClassBook(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void runPlayerSafety(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runPlayerSafety(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void rewardCommands(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.rewardCommands(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiActivityIndex(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.activityIndex(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiEntryGate(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.entryGate(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiRunModifiers(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runModifiers(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiHardcoreRun(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.hardcoreRun(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiRunModifierConflicts(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runModifierConflicts(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiUnregisteredPlayerRemoval(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.unregisteredPlayerRemoval(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiLobbyAutoLeave(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.lobbyAutoLeave(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiRunStatsCounting(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runStatsCounting(helper);
    }

    // Own batch: the rental tests swap the one global rental provider.
    @GameTest(template = EMPTY_STRUCTURE, batch = "rental_dungeon")
    public void apiRentalDungeonPick(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.rentalDungeonPick(helper);
    }

    // Own batch: the rental tests swap the one global rental provider.
    @GameTest(template = EMPTY_STRUCTURE, batch = "rental_required")
    public void apiRunSettings(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runSettings(helper);
    }

    // Own batch: the rental tests swap the one global rental provider.
    @GameTest(template = EMPTY_STRUCTURE, batch = "rental_builds")
    public void apiRentalBuilds(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.rentalBuilds(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "run_preparation", timeoutTicks = 500)
    public void apiDungeonPreparation(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.dungeonPreparation(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "run_preparation", timeoutTicks = 500)
    public void apiRunPreparation(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runPreparation(helper, player -> {
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiSummonSplits(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.summonSplits(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "open_lobbies")
    public void apiOpenLobbies(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.openLobbiesList(helper, player -> {
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiLobbySettingsChange(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.lobbySettingsChange(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "open_lobbies")
    public void apiHubLobbyApi(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.hubLobbyApi(helper, player -> {
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiRunStatsSave(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runStatsSave(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiRunStates(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runStates(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiRespawnShield(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.respawnShield(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiHardcoreOut(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.hardcoreOut(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiRunBlockBreaking(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runBlockBreaking(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiRunDetailsNewestRun(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runDetailsNewestRun(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiResetCooldowns(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.resetCooldowns(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiCopiedController(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.copiedController(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void runTeams(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runTeams(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void instanceCheck(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.instanceCheck(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void doorGroups(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.doorGroups(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200)
    public void controllerChunks(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.controllerChunks(helper);
    }

    // Own batch: it waits up to 200 ticks, and a neighbor test starting meanwhile clears its space (discarding these mobs).
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "run_minions")
    public void runMinions(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runMinions(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 2000)
    public void evokerMinions(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.evokerMinions(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 6200, skyAccess = true)
    public void lichMinions(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.lichMinions(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiAdminRename(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminRename(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiAdminForget(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminForget(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiAdminStatus(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminStatus(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiAdminStatusUnloaded(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminStatusUnloaded(helper);
    }

    /** Fabric sends any payload to a test player's connection, so nothing to set up for it. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void apiAdminScreen(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminScreen(helper, player -> {
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiAdminTeleport(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminTeleport(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void apiAdminForceEnd(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminForceEnd(helper, player -> {
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void arenaDisconnectGrace(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.arenaDisconnectGrace(helper, player -> {
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void controllerBrokenMidRun(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.controllerBrokenMidRun(helper, player -> {
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void arenaForfeit(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.arenaForfeit(helper, player -> {
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void downedSpectators(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.downedSpectators(helper, player -> {
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void runLeftovers(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runLeftovers(helper, player -> {
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void runLeftoversOnLoad(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runLeftoversOnLoad(helper);
    }

    // Own batch: it waits up to 260 ticks for a fresh chunk to generate, which other tests running beside it slow down.
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "raid_boss_chunks")
    public void raidBossChunks(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.raidBossChunks(helper, player -> {
        });
    }
}
