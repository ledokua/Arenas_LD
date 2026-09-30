package net.ledok.arenas_ld.neoforge;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.platform.ArenasEvents;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Drains the common event lists and packet declarations into NeoForge — the part the Forgified
 * Fabric API used to do for us.
 */
final class NeoForgeArenasWiring {
    private NeoForgeArenasWiring() {}

    static void register(IEventBus modBus) {
        modBus.addListener(NeoForgeArenasWiring::onRegisterPayloads);

        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
            ArenasEvents.fire(ArenasEvents.SERVER_TICK_END, event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
            ArenasEvents.fire(ArenasEvents.SERVER_STOPPING, event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) ->
            ArenasEvents.fire(ArenasEvents.SERVER_STOPPED, event.getServer()));

        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ArenasEvents.firePlayer(ArenasEvents.PLAYER_JOIN, player, player.server);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ArenasEvents.firePlayer(ArenasEvents.PLAYER_DISCONNECT, player, player.server);
            }
        });

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
            ArenasEvents.REGISTER_COMMANDS.forEach(listener -> listener.accept(event.getDispatcher())));

        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
            if (ArenasEvents.isUseBlocked(event.getEntity(), event.getHand())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (ArenasEvents.isUseBlocked(event.getEntity(), event.getHand())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> {
            if (ArenasEvents.isUseBlocked(event.getEntity(), event.getHand())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific event) -> {
            if (ArenasEvents.isUseBlocked(event.getEntity(), event.getHand())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
    }

    /** Fires after every mod constructor, so all of ArenasLdMod.initRuntime's declarations are in. */
    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ArenasLdMod.MOD_ID).versioned("1");
        for (ArenasNetwork.PayloadDecl<?> decl : ArenasNetwork.c2sPayloads()) {
            playToServer(registrar, decl);
        }
        for (ArenasNetwork.PayloadDecl<?> decl : ArenasNetwork.s2cPayloads()) {
            playToClient(registrar, decl);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void playToServer(PayloadRegistrar registrar,
                                                                    ArenasNetwork.PayloadDecl<T> decl) {
        registrar.playToServer(decl.type(), (StreamCodec<RegistryFriendlyByteBuf, T>) decl.codec(),
            (payload, context) -> ArenasNetwork.dispatchServer(payload, (ServerPlayer) context.player()));
    }

    /** The client receivers were registered by client-only code; on a dedicated server this never runs. */
    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void playToClient(PayloadRegistrar registrar,
                                                                    ArenasNetwork.PayloadDecl<T> decl) {
        registrar.playToClient(decl.type(), (StreamCodec<RegistryFriendlyByteBuf, T>) decl.codec(),
            (payload, context) -> ArenasNetwork.dispatchClient(payload));
    }
}
