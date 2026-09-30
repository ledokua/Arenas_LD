package net.ledok.arenas_ld.client;

import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/** Client-bound packet receivers; client-only. Shaped like Fabric's receiver so handler bodies read the same. */
public final class ArenasClientNetwork {
    private ArenasClientNetwork() {}

    public record ClientContext(Minecraft client) {
        @Nullable
        public LocalPlayer player() {
            return client.player;
        }
    }

    @FunctionalInterface
    public interface ClientReceiver<T extends CustomPacketPayload> {
        void receive(T payload, ClientContext context);
    }

    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> void registerReceiver(CustomPacketPayload.Type<T> type,
                                                                        ClientReceiver<T> receiver) {
        ArenasNetwork.registerClientReceiver(type,
            payload -> receiver.receive((T) payload, new ClientContext(Minecraft.getInstance())));
    }
}
