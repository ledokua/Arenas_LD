package net.ledok.arenas_ld.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Loader-neutral packet wiring. Common code declares payload types and registers receivers here;
 * each loader drains the declarations into its own API (Fabric's PayloadTypeRegistry and global
 * receivers at init, NeoForge's RegisterPayloadHandlersEvent). Receivers run on the main thread
 * on both loaders.
 */
public final class ArenasNetwork {
    private ArenasNetwork() {}

    public record PayloadDecl<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {}

    /** What a server-bound receiver sees — shaped like Fabric's context so handler bodies read the same. */
    public record ServerContext(ServerPlayer player) {
        public MinecraftServer server() {
            return player.server;
        }
    }

    @FunctionalInterface
    public interface ServerReceiver<T extends CustomPacketPayload> {
        void receive(T payload, ServerContext context);
    }

    private static final List<PayloadDecl<?>> C2S = new ArrayList<>();
    private static final List<PayloadDecl<?>> S2C = new ArrayList<>();
    private static final Map<CustomPacketPayload.Type<?>, ServerReceiver<?>> SERVER_RECEIVERS = new ConcurrentHashMap<>();
    private static final Map<CustomPacketPayload.Type<?>, Consumer<CustomPacketPayload>> CLIENT_RECEIVERS = new ConcurrentHashMap<>();

    public static <T extends CustomPacketPayload> void playC2S(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        C2S.add(new PayloadDecl<>(type, codec));
    }

    public static <T extends CustomPacketPayload> void playS2C(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        S2C.add(new PayloadDecl<>(type, codec));
    }

    public static <T extends CustomPacketPayload> void registerServerReceiver(
            CustomPacketPayload.Type<T> type, ServerReceiver<T> receiver) {
        SERVER_RECEIVERS.put(type, receiver);
    }

    /**
     * Client receivers are registered from client-only code (see ArenasClientNetwork), which wraps
     * the client context in; this map only ever holds plain consumers, so it is safe to reference
     * from a dedicated server.
     */
    public static void registerClientReceiver(CustomPacketPayload.Type<?> type, Consumer<CustomPacketPayload> receiver) {
        CLIENT_RECEIVERS.put(type, receiver);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ArenasPlatform.INSTANCE.sendToPlayer(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        ArenasPlatform.INSTANCE.sendToServer(payload);
    }

    // ── For the loader glue ────────────────────────────────────────────────

    public static List<PayloadDecl<?>> c2sPayloads() {
        return Collections.unmodifiableList(C2S);
    }

    public static List<PayloadDecl<?>> s2cPayloads() {
        return Collections.unmodifiableList(S2C);
    }

    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> void dispatchServer(T payload, ServerPlayer player) {
        ServerReceiver<T> receiver = (ServerReceiver<T>) SERVER_RECEIVERS.get(payload.type());
        if (receiver != null) {
            receiver.receive(payload, new ServerContext(player));
        }
    }

    public static void dispatchClient(CustomPacketPayload payload) {
        Consumer<CustomPacketPayload> receiver = CLIENT_RECEIVERS.get(payload.type());
        if (receiver != null) {
            receiver.accept(payload);
        }
    }
}
