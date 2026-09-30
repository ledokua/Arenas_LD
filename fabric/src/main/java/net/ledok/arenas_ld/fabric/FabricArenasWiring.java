package net.ledok.arenas_ld.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.arenas_ld.platform.ArenasEvents;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;

/** Drains the common event lists and packet declarations into Fabric API. */
final class FabricArenasWiring {
    private FabricArenasWiring() {}

    static void register() {
        registerPayloads();
        registerEvents();
    }

    private static void registerPayloads() {
        for (ArenasNetwork.PayloadDecl<?> decl : ArenasNetwork.s2cPayloads()) {
            registerS2C(decl);
        }
        for (ArenasNetwork.PayloadDecl<?> decl : ArenasNetwork.c2sPayloads()) {
            registerC2S(decl);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void registerS2C(ArenasNetwork.PayloadDecl<T> decl) {
        PayloadTypeRegistry.playS2C().register(decl.type(), (StreamCodec<RegistryFriendlyByteBuf, T>) decl.codec());
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void registerC2S(ArenasNetwork.PayloadDecl<T> decl) {
        PayloadTypeRegistry.playC2S().register(decl.type(), (StreamCodec<RegistryFriendlyByteBuf, T>) decl.codec());
        ServerPlayNetworking.registerGlobalReceiver(decl.type(),
            (payload, context) -> ArenasNetwork.dispatchServer(payload, context.player()));
    }

    private static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> ArenasEvents.fire(ArenasEvents.SERVER_TICK_END, server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ArenasEvents.fire(ArenasEvents.SERVER_STOPPING, server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ArenasEvents.fire(ArenasEvents.SERVER_STOPPED, server));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            ArenasEvents.firePlayer(ArenasEvents.PLAYER_JOIN, handler.player, server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            ArenasEvents.firePlayer(ArenasEvents.PLAYER_DISCONNECT, handler.player, server));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            ArenasEvents.REGISTER_COMMANDS.forEach(listener -> listener.accept(dispatcher)));

        UseItemCallback.EVENT.register((player, world, hand) -> ArenasEvents.isUseBlocked(player, hand)
            ? InteractionResultHolder.fail(player.getItemInHand(hand))
            : InteractionResultHolder.pass(player.getItemInHand(hand)));
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
            ArenasEvents.isUseBlocked(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
            ArenasEvents.isUseBlocked(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
    }
}
