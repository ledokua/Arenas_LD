package net.ledok.arenas_ld.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Loader-neutral server event lists. Common code adds listeners; each loader's glue fires them
 * from its own events (Fabric API callbacks, the NeoForge event bus).
 */
public final class ArenasEvents {
    private ArenasEvents() {}

    public static final List<Consumer<MinecraftServer>> SERVER_TICK_END = new CopyOnWriteArrayList<>();
    public static final List<Consumer<MinecraftServer>> SERVER_STOPPING = new CopyOnWriteArrayList<>();
    public static final List<Consumer<MinecraftServer>> SERVER_STOPPED = new CopyOnWriteArrayList<>();

    /** A player finished joining (still inside the join handshake — defer teleports a tick). */
    public static final List<BiConsumer<ServerPlayer, MinecraftServer>> PLAYER_JOIN = new CopyOnWriteArrayList<>();
    /** A player is disconnecting; still in the player list. */
    public static final List<BiConsumer<ServerPlayer, MinecraftServer>> PLAYER_DISCONNECT = new CopyOnWriteArrayList<>();

    public static final List<Consumer<CommandDispatcher<CommandSourceStack>>> REGISTER_COMMANDS = new CopyOnWriteArrayList<>();

    /**
     * Returns true to cancel a right-click with {@code hand} — using the item in the air, on a
     * block, or on an entity. Fired on both sides; check for a server player yourself.
     */
    public static final List<BiPredicate<Player, InteractionHand>> USE_BLOCKED = new CopyOnWriteArrayList<>();

    public static boolean isUseBlocked(Player player, InteractionHand hand) {
        for (BiPredicate<Player, InteractionHand> check : USE_BLOCKED) {
            if (check.test(player, hand)) {
                return true;
            }
        }
        return false;
    }

    public static void fire(List<Consumer<MinecraftServer>> event, MinecraftServer server) {
        for (Consumer<MinecraftServer> listener : event) {
            listener.accept(server);
        }
    }

    public static void firePlayer(List<BiConsumer<ServerPlayer, MinecraftServer>> event, ServerPlayer player,
                                  MinecraftServer server) {
        for (BiConsumer<ServerPlayer, MinecraftServer> listener : event) {
            listener.accept(player, server);
        }
    }
}
