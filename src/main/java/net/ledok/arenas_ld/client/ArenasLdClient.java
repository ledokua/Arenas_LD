package net.ledok.arenas_ld.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.client.screen.CreateLobbyScreen;
import net.ledok.arenas_ld.client.screen.DungeonLobbyScreen;
import net.ledok.arenas_ld.client.screen.JoinLobbyScreen;
import net.ledok.arenas_ld.client.screen.LobbyScreen;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.registry.BlockRegistry;
import net.ledok.arenas_ld.screen.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

public class ArenasLdClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register S2C packet types
        ModPackets.registerS2CPackets();

        // Register Screens
        MenuScreens.register(ModScreenHandlers.BOSS_SPAWNER_SCREEN_HANDLER, BossSpawnerScreen::new);
        MenuScreens.register(ModScreenHandlers.MOB_SPAWNER_SCREEN_HANDLER, MobSpawnerScreen::new);
        MenuScreens.register(ModScreenHandlers.MOB_ATTRIBUTES_SCREEN_HANDLER, MobAttributesScreen::new);
        MenuScreens.register(ModScreenHandlers.DUNGEON_BOSS_SPAWNER_SCREEN_HANDLER, DungeonBossSpawnerScreen::new);
        MenuScreens.register(ModScreenHandlers.LOBBY_SCREEN_HANDLER, LobbyScreen::new);
        MenuScreens.register(ModScreenHandlers.CREATE_LOBBY_SCREEN_HANDLER, CreateLobbyScreen::new);
        MenuScreens.register(ModScreenHandlers.DUNGEON_LOBBY_SCREEN_HANDLER, DungeonLobbyScreen::new);
        MenuScreens.register(ModScreenHandlers.JOIN_LOBBY_SCREEN_HANDLER, JoinLobbyScreen::new);

        // Other client setup
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.PHASE_BLOCK, RenderType.translucent());
        
        // Register S2C packet handlers
        registerS2CPacketHandlers();
    }
    
    private void registerS2CPacketHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.OpenDungeonLobbyScreenPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft.getInstance().setScreen(new DungeonLobbyScreen(
                        new DungeonLobbyScreenHandler(0, Minecraft.getInstance().player.getInventory(), new DungeonLobbyData(payload.lobbyId(), payload.ownerId(), payload.playerNames())),
                        Minecraft.getInstance().player.getInventory(),
                        Component.literal("Dungeon Lobby")
                ));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.UpdateLobbyStatePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Screen currentScreen = Minecraft.getInstance().screen;
                if (currentScreen instanceof DungeonLobbyScreen lobbyScreen) {
                    lobbyScreen.getMenu().setPlayerNames(payload.playerNames());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.CloseLobbyScreenPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Screen currentScreen = Minecraft.getInstance().screen;
                if (currentScreen instanceof DungeonLobbyScreen) {
                    Minecraft.getInstance().setScreen(null);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.LobbyListPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft.getInstance().setScreen(new JoinLobbyScreen(
                        new JoinLobbyScreenHandler(0, Minecraft.getInstance().player.getInventory(), new JoinLobbyData(payload.lobbies())),
                        Minecraft.getInstance().player.getInventory(),
                        Component.literal("Join Lobby")
                ));
            });
        });
    }
}
