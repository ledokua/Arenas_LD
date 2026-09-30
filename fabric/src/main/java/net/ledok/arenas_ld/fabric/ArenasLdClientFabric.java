package net.ledok.arenas_ld.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.ledok.arenas_ld.client.ArenasClientEvents;
import net.ledok.arenas_ld.client.ArenasLdClient;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ArenasLdClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ArenasLdClient.registerScreens(MenuScreens::register);
        ArenasLdClient.registerRenderLayers(BlockRenderLayerMap.INSTANCE::putBlock);
        ArenasLdClient.init();

        // Every client-bound type gets a receiver that dispatches to whatever the common client
        // code registered for it (types were declared during the main initializer).
        for (ArenasNetwork.PayloadDecl<?> decl : ArenasNetwork.s2cPayloads()) {
            registerReceiver(decl.type());
        }
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> ArenasClientEvents.fireAfterTranslucent(
            new ArenasClientEvents.WorldRenderContext(context.matrixStack(), context.camera())));
    }

    private static <T extends CustomPacketPayload> void registerReceiver(CustomPacketPayload.Type<T> type) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> ArenasNetwork.dispatchClient(payload));
    }
}
