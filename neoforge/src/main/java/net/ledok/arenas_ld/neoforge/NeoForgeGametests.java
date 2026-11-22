package net.ledok.arenas_ld.neoforge;

import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.platform.ArenasPlatform;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.util.Set;

/**
 * Smoke test of the platform layer on NeoForge and the shared API tests ({@code ApiGameTests}),
 * mirroring the Fabric gametests. NeoForge picks it up from the holder annotation only when
 * GameTests are enabled (the gameTestServer dev run), so it never runs in production. The template
 * is run-gametest/gameteststructures/empty.snbt.
 */
@GameTestHolder("arenas_ld")
@PrefixGameTestTemplate(false)
public final class NeoForgeGametests {
    static {
        // Runs start at once in tests; apiRunPreparation turns the countdown on for itself (own batch).
        // Only in a game test run: this class is loaded on normal servers too (the loader reads test entrypoints),
        // and there it must not switch the start countdown off (4.29.x–4.33.x shipped that bug).
        if (System.getProperty("neoforge.enabledGameTestNamespaces") != null) {
            net.ledok.arenas_ld.util.RunPreparation.secondsOverride = 0;
        }
    }

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
    public static void runEventLog(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runEventLog(helper);
    }

    @GameTest(template = "empty")
    public static void runDetailsClassBook(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runDetailsClassBook(helper);
    }

    @GameTest(template = "empty")
    public static void runPlayerSafety(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runPlayerSafety(helper);
    }

    @GameTest(template = "empty")
    public static void rewardCommands(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.rewardCommands(helper);
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
    public static void apiRunModifiers(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runModifiers(helper);
    }

    @GameTest(template = "empty")
    public static void apiHardcoreRun(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.hardcoreRun(helper);
    }

    @GameTest(template = "empty")
    public static void apiRunModifierConflicts(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runModifierConflicts(helper);
    }

    @GameTest(template = "empty")
    public static void apiUnregisteredPlayerRemoval(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.unregisteredPlayerRemoval(helper);
    }

    @GameTest(template = "empty")
    public static void apiLobbyAutoLeave(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.lobbyAutoLeave(helper);
    }

    @GameTest(template = "empty")
    public static void apiReachZoneRoom(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.reachZoneRoom(helper);
    }

    @GameTest(template = "empty")
    public static void apiCombatLogDuel(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.combatLogDuel(helper);
    }

    @GameTest(template = "empty")
    public static void apiRunStatsCounting(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runStatsCounting(helper);
    }

    // Own batch: the rental tests swap the one global rental provider.
    @GameTest(template = "empty", batch = "rental_dungeon")
    public static void apiRentalDungeonPick(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.rentalDungeonPick(helper);
    }

    // Own batch: the rental tests swap the one global rental provider.
    @GameTest(template = "empty", batch = "rental_required")
    public static void apiRunSettings(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runSettings(helper);
    }

    // Own batch: the rental tests swap the one global rental provider.
    @GameTest(template = "empty", batch = "rental_builds")
    public static void apiRentalBuilds(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.rentalBuilds(helper);
    }

    @GameTest(template = "empty", batch = "run_preparation", timeoutTicks = 500)
    public static void apiDungeonPreparation(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.dungeonPreparation(helper);
    }

    @GameTest(template = "empty", batch = "run_preparation", timeoutTicks = 500)
    public static void apiRunPreparation(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runPreparation(helper, NeoForgeGametests::acceptModPayloads);
    }

    @GameTest(template = "empty")
    public static void apiSummonSplits(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.summonSplits(helper);
    }

    @GameTest(template = "empty", batch = "open_lobbies")
    public static void apiOpenLobbies(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.openLobbiesList(helper, NeoForgeGametests::acceptModPayloads);
    }

    @GameTest(template = "empty")
    public static void apiLobbySettingsChange(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.lobbySettingsChange(helper);
    }

    @GameTest(template = "empty", batch = "open_lobbies")
    public static void apiHubLobbyApi(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.hubLobbyApi(helper, NeoForgeGametests::acceptModPayloads);
    }

    @GameTest(template = "empty")
    public static void apiRunStatsSave(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runStatsSave(helper);
    }

    @GameTest(template = "empty")
    public static void apiRunStates(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runStates(helper);
    }

    @GameTest(template = "empty")
    public static void apiRespawnShield(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.respawnShield(helper);
    }

    @GameTest(template = "empty")
    public static void apiHardcoreOut(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.hardcoreOut(helper);
    }

    @GameTest(template = "empty")
    public static void apiRunBlockBreaking(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runBlockBreaking(helper);
    }

    @GameTest(template = "empty")
    public static void apiRunDetailsNewestRun(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runDetailsNewestRun(helper);
    }

    @GameTest(template = "empty")
    public static void apiResetCooldowns(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.resetCooldowns(helper);
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

    @GameTest(template = "empty")
    public static void doorGroups(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.doorGroups(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void controllerChunks(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.controllerChunks(helper);
    }

    // Own batch: it waits up to 200 ticks, and a neighbor test starting meanwhile clears its space (discarding these mobs).
    @GameTest(template = "empty", timeoutTicks = 400, batch = "run_minions")
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

    @GameTest(template = "empty")
    public static void apiAdminRename(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminRename(helper);
    }

    @GameTest(template = "empty")
    public static void apiAdminForget(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminForget(helper);
    }

    @GameTest(template = "empty")
    public static void apiAdminStatus(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminStatus(helper);
    }

    @GameTest(template = "empty")
    public static void apiAdminStatusUnloaded(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminStatusUnloaded(helper);
    }

    @GameTest(template = "empty")
    public static void apiAdminScreen(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminScreen(helper, NeoForgeGametests::acceptModPayloads);
    }

    @GameTest(template = "empty")
    public static void apiAdminTeleport(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminTeleport(helper);
    }

    @GameTest(template = "empty")
    public static void apiAdminForceEnd(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.adminForceEnd(helper, NeoForgeGametests::acceptModPayloads);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void arenaDisconnectGrace(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.arenaDisconnectGrace(helper, NeoForgeGametests::acceptModPayloads);
    }

    @GameTest(template = "empty")
    public static void controllerBrokenMidRun(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.controllerBrokenMidRun(helper, NeoForgeGametests::acceptModPayloads);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void arenaWaveTransitions(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.arenaWaveTransitions(helper, NeoForgeGametests::acceptModPayloads);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void practiceRoom(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.practiceRoom(helper, NeoForgeGametests::acceptModPayloads);
    }

    @GameTest(template = "empty")
    public static void arenaForfeit(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.arenaForfeit(helper, NeoForgeGametests::acceptModPayloads);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void downedSpectators(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.downedSpectators(helper, NeoForgeGametests::acceptModPayloads);
    }

    @GameTest(template = "empty")
    public static void runLeftovers(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runLeftovers(helper, NeoForgeGametests::acceptModPayloads);
    }

    @GameTest(template = "empty")
    public static void runLeftoversOnLoad(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.runLeftoversOnLoad(helper);
    }

    // Own batch: it waits up to 260 ticks for a fresh chunk to generate, which other tests running beside it slow down.
    @GameTest(template = "empty", timeoutTicks = 400, batch = "raid_boss_chunks")
    public static void raidBossChunks(GameTestHelper helper) {
        net.ledok.arenas_ld.gametest.ApiGameTests.raidBossChunks(helper, NeoForgeGametests::acceptModPayloads);
    }

    /**
     * NeoForge refuses to send a payload to a client that never registered it (it throws), and a
     * test player's connection negotiated nothing: let it take Arenas_LD's payloads and the
     * extended menu opening, as a modded client would.
     */
    private static void acceptModPayloads(ServerPlayer player) {
        Set<ResourceLocation> channels = ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection());
        channels.add(AdvancedOpenScreenPayload.TYPE.id());
        for (ArenasNetwork.PayloadDecl<?> payload : ArenasNetwork.s2cPayloads()) {
            channels.add(payload.type().id());
        }
    }
}
