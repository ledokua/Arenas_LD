package net.ledok.arenas_ld.client;

import net.ledok.arenas_ld.dungeon.packet.DungeonBossSpawnerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.DungeonControllerAdminSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.DungeonControllerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.MobSpawnerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomControllerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.screen.RoomControllerScreen;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.raid.packet.RaidControllerAdminSnapshotPayload;
import net.ledok.arenas_ld.raid.packet.RaidControllerSnapshotPayload;
import net.ledok.arenas_ld.raid.screen.RaidBossSpawnerScreen;
import net.ledok.arenas_ld.raid.screen.RaidControllerAdminScreen;
import net.ledok.arenas_ld.raid.screen.RaidControllerScreen;
import net.ledok.arenas_ld.registry.BlockRegistry;
import net.ledok.arenas_ld.screen.*;
import net.ledok.vectorlib.client.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public class ArenasLdClient {
    /**
     * Abstraction over menu-screen registration: Fabric passes
     * {@code MenuScreens::register}, NeoForge passes its
     * {@code RegisterMenuScreensEvent::register} (direct MenuScreens
     * registration is disallowed there).
     */
    @FunctionalInterface
    public interface ScreenRegistrar {
        <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void register(
                MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, S> constructor);
    }

    public static void registerScreens(ScreenRegistrar registrar) {
        registrar.register(ModScreenHandlers.RAID_BOSS_SPAWNER_SCREEN_HANDLER, RaidBossSpawnerScreen::new);
        registrar.register(ModScreenHandlers.MOB_ATTRIBUTES_SCREEN_HANDLER, MobAttributesScreen::new);
        registrar.register(ModScreenHandlers.EQUIPMENT_SCREEN_HANDLER, net.ledok.arenas_ld.screen.EquipmentScreen::new);
        registrar.register(ModScreenHandlers.RAID_CONTROLLER_SCREEN_HANDLER, RaidControllerScreen::new);
        registrar.register(ModScreenHandlers.ROOM_CONTROLLER_SCREEN_HANDLER, RoomControllerScreen::new);
        registrar.register(ModScreenHandlers.MOB_SPAWNER_SCREEN_HANDLER, net.ledok.arenas_ld.dungeon.screen.MobSpawnerScreen::new);
        registrar.register(ModScreenHandlers.DUNGEON_BOSS_SPAWNER_SCREEN_HANDLER, net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerScreen::new);
        registrar.register(ModScreenHandlers.DUNGEON_CONTROLLER_SCREEN_HANDLER, net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreen::new);
        registrar.register(ModScreenHandlers.DUNGEON_CONTROLLER_ADMIN_SCREEN_HANDLER, net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminScreen::new);
        registrar.register(ModScreenHandlers.RAID_CONTROLLER_ADMIN_SCREEN_HANDLER, RaidControllerAdminScreen::new);
        registrar.register(ModScreenHandlers.ARENA_CONTROLLER_SCREEN_HANDLER, net.ledok.arenas_ld.arena.screen.ArenaControllerScreen::new);
        registrar.register(ModScreenHandlers.ARENA_CONTROLLER_ADMIN_SCREEN_HANDLER, net.ledok.arenas_ld.arena.screen.ArenaControllerAdminScreen::new);
        registrar.register(ModScreenHandlers.ARENA_SPAWNER_SCREEN_HANDLER, net.ledok.arenas_ld.arena.screen.ArenaSpawnerScreen::new);
    }

    /** Block render layers: Fabric passes BlockRenderLayerMap, NeoForge ItemBlockRenderTypes. */
    public static void registerRenderLayers(java.util.function.BiConsumer<net.minecraft.world.level.block.Block, RenderType> sink) {
        sink.accept(BlockRegistry.PHASE_BLOCK, RenderType.translucent());
    }

    /** Opens the live details screen of the player's dungeon or raid run ({@code /arenasld run details}). */
    public static final net.minecraft.client.KeyMapping RUN_DETAILS_KEY = new net.minecraft.client.KeyMapping(
        "key.arenas_ld.run_details", org.lwjgl.glfw.GLFW.GLFW_KEY_SEMICOLON, "key.categories.arenas_ld");

    /** Every client tick (the loader glue calls it). */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        net.ledok.arenas_ld.screen.RunDetailsScreen.clientTick();
        DevRunDetails.tick();
        net.ledok.arenas_ld.screen.lobby.LobbyScreen.hostedTick();
        while (RUN_DETAILS_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.player.connection.sendCommand("arenasld run details");
            }
        }
    }

    public static void init() {
        SelectionOverlayRenderer.register();
        SpawnTelegraphRenderer.register();

        // RMB-in-air on the Dungeon Tool opens its client-side mode picker.
        net.ledok.arenas_ld.item.DungeonToolItem.clientModeScreenOpener = hand ->
            net.minecraft.client.Minecraft.getInstance().setScreen(
                new net.ledok.arenas_ld.screen.DungeonToolScreen(hand));
        // Telegraph expiry compares absolute game time, so stale positions would
        // render again in any world whose clock is behind the stored deadline.
        ClientEvents.DISCONNECTED.add(SpawnTelegraphStore::clear);
        ClientEvents.DISCONNECTED.add(() -> SpawnTelegraphRenderer.onRunHud(net.ledok.arenas_ld.packet.RunHudPayload.HIDDEN));

        // Run HUD overlays (timer bar + loot reveal): one OVERLAY listener drives their
        // per-frame animation; the drawing itself happens through CanvasOverlay handles.
        net.ledok.vectorlib.client.render.RenderEvents.onHudRender(
            net.ledok.vectorlib.client.render.HudRenderStage.OVERLAY, ctx -> {
                net.ledok.arenas_ld.client.hud.RunTimerHud.tick(ctx.partialTick());
                net.ledok.arenas_ld.client.hud.LootRevealHud.tick();
                net.ledok.arenas_ld.client.hud.AllyHealthHud.tick();
            });
        net.ledok.vectorlib.client.render.RenderEvents.onHudRender(
            net.ledok.vectorlib.client.render.HudRenderStage.OVERLAY,
            ctx -> net.ledok.arenas_ld.client.hud.LobbyBadgeHud.render(ctx.graphics(), ctx.width()));
        ClientEvents.DISCONNECTED.add(net.ledok.arenas_ld.client.hud.LobbyBadgeHud::clear);
        ClientEvents.DISCONNECTED.add(net.ledok.arenas_ld.screen.RunDetailsScreen::clear);
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.RunTimelinePayload.TYPE, (payload, context) ->
            context.client().execute(() -> net.ledok.arenas_ld.screen.run.TimelineView.onPayload(payload)));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.RunDetailsPayload.TYPE, (payload, context) ->
            context.client().execute(() -> net.ledok.arenas_ld.screen.RunDetailsScreen.onPayload(payload)));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.LobbyBadgePayload.TYPE, (payload, context) ->
            context.client().execute(() -> net.ledok.arenas_ld.client.hud.LobbyBadgeHud.onPayload(payload)));
        ClientEvents.DISCONNECTED.add(net.ledok.arenas_ld.client.hud.RunTimerHud::clear);
        ClientEvents.DISCONNECTED.add(net.ledok.arenas_ld.client.hud.LootRevealHud::clear);
        ClientEvents.DISCONNECTED.add(net.ledok.arenas_ld.client.hud.AllyHealthHud::clear);
        ClientEvents.DISCONNECTED.add(net.ledok.arenas_ld.screen.lobby.LobbyScreen::clearHosted);
        DevLobbyHost.registerIfEnabled();
        // Set at once (no execute): the menu's open-screen packet right behind it must find it.
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.LobbyOpeningPayload.TYPE, (payload, context) ->
            net.ledok.arenas_ld.screen.lobby.LobbyScreen.nextActivity(payload));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.AllyHealthPayload.TYPE, (payload, context) ->
            context.client().execute(() -> net.ledok.arenas_ld.client.hud.AllyHealthHud.onPayload(payload)));
        ClientEvents.DISCONNECTED.add(() -> net.ledok.arenas_ld.util.RunEffectsClientState.frozenGround = false);
        ClientEvents.DISCONNECTED.add(net.ledok.arenas_ld.util.RunBlockBreaking::clearClient);
        ClientEvents.DISCONNECTED.add(net.ledok.arenas_ld.screen.RentalPick::clear);
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.ToolSourceSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> net.ledok.arenas_ld.client.SelectionOverlayRenderer.onToolSourceSync(payload)));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.LobbyRemovedPayload.TYPE, (payload, context) ->
            context.client().execute(() -> net.ledok.arenas_ld.screen.lobby.LobbyScreen.onRemoved(payload)));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.RentalLobbyPayload.TYPE, (payload, context) ->
            context.client().execute(() -> net.ledok.arenas_ld.screen.RentalPick.onPayload(payload)));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.RentalPreviewPayload.TYPE, (payload, context) ->
            context.client().execute(() -> net.ledok.arenas_ld.screen.RentalPick.onPreview(payload)));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.InviteCandidatesPayload.TYPE, (payload, context) ->
            context.client().execute(() -> net.ledok.arenas_ld.screen.lobby.InvitePicker.onPayload(payload)));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.RunBreakableBlocksPayload.TYPE, (payload, context) ->
            context.client().execute(() -> net.ledok.arenas_ld.util.RunBlockBreaking.onClientPayload(payload)));

        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.RunFrozenGroundPayload.TYPE, (payload, context) ->
            net.ledok.arenas_ld.util.RunEffectsClientState.frozenGround = payload.frozen());
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.RunHudPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    net.ledok.arenas_ld.client.hud.RunTimerHud.onPayload(payload);
                    SpawnTelegraphRenderer.onRunHud(payload);
                }));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.LootRewardPayload.TYPE, (payload, context) ->
                context.client().execute(() -> net.ledok.arenas_ld.client.hud.LootRevealHud.onPayload(payload)));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.packet.LootHudClearPayload.TYPE, (payload, context) ->
                context.client().execute(net.ledok.arenas_ld.client.hud.LootRevealHud::clear));

        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.dungeon.packet.DungeonCloseScreenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (net.ledok.arenas_ld.screen.lobby.LobbyScreen.current() instanceof net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreen screen
                            && screen.matchesController(payload.controllerPos())) {
                        screen.onClose();
                    }
                }));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.raid.packet.RaidCloseScreenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (net.ledok.arenas_ld.screen.lobby.LobbyScreen.current() instanceof RaidControllerScreen screen
                            && screen.matchesController(payload.controllerPos())) {
                        screen.onClose();
                    }
                }));
        ArenasClientNetwork.registerReceiver(DungeonControllerSnapshotPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (net.ledok.arenas_ld.screen.lobby.LobbyScreen.current() instanceof net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreen screen
                            && screen.matchesController(payload.data().blockPos())) {
                        screen.applyData(payload.data());
                    }
                }));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.dungeon.packet.SpawnTelegraphPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (context.client().level != null) {
                    SpawnTelegraphStore.set(payload.positions(), payload.durationTicks(), context.client().level.getGameTime());
                }
            }));
        ArenasClientNetwork.registerReceiver(RoomControllerSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.RoomControllerScreen screen
                    && screen.matchesController(payload.data().blockPos())) {
                    screen.applyData(payload.data());
                } else if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.RoomRewardsScreen rewardsScreen
                    && rewardsScreen.matchesController(payload.data().blockPos())) {
                    rewardsScreen.applyData(payload.data());
                }
            }));
        ArenasClientNetwork.registerReceiver(MobSpawnerSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.MobSpawnerScreen screen
                    && screen.matchesSpawner(payload.data().blockPos())) {
                    screen.applyData(payload.data());
                }
            }));
        ArenasClientNetwork.registerReceiver(DungeonBossSpawnerSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerScreen screen
                    && screen.matchesSpawner(payload.data().blockPos())) {
                    screen.applyData(payload.data());
                }
            }));
        ArenasClientNetwork.registerReceiver(DungeonControllerAdminSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminScreen screen
                    && screen.matchesController(payload.data().blockPos(), payload.data().dimension())) {
                    screen.applyData(payload.data());
                }
            }));
        ArenasClientNetwork.registerReceiver(ModPackets.RaidControllerInfoPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (net.ledok.arenas_ld.screen.lobby.LobbyScreen.current() instanceof RaidControllerScreen screen
                        && payload.pos().equals(screen.getMenu().getPos())) {
                    screen.applyServerInfo(payload);
                }
            }));
        ArenasClientNetwork.registerReceiver(RaidControllerAdminSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof RaidControllerAdminScreen screen
                        && screen.matchesController(payload.data().blockPos(), payload.data().dimension())) {
                    screen.applyData(payload.data());
                }
            }));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.arena.packet.ArenaControllerAdminSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof net.ledok.arenas_ld.arena.screen.ArenaControllerAdminScreen screen
                        && screen.matchesController(payload.data().blockPos(), payload.data().dimension())) {
                    screen.applyData(payload.data());
                }
            }));
        ArenasClientNetwork.registerReceiver(net.ledok.arenas_ld.arena.packet.ArenaControllerSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (net.ledok.arenas_ld.screen.lobby.LobbyScreen.current() instanceof net.ledok.arenas_ld.arena.screen.ArenaControllerScreen screen
                        && screen.matchesController(payload.data().blockPos())) {
                    screen.applyData(payload.data());
                }
            }));
        ArenasClientNetwork.registerReceiver(RaidControllerSnapshotPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (net.ledok.arenas_ld.screen.lobby.LobbyScreen.current() instanceof RaidControllerScreen screen
                        && payload.data().blockPos().equals(screen.getMenu().getPos())) {
                    screen.applyData(payload.data());
                }
            }));
    }
}
