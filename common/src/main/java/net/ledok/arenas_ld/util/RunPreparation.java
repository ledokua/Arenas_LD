package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The few seconds between "start" and the teleport of a dungeon, raid or arena run, to load the arena first.
 * <ul>
 *   <li><b>Preload</b>: temporary tickets on the instance's chunks (its run area and a square around the entrance)
 *       at the "loaded, not ticking" level, so they load from disk in the background (C2ME loads async) while the
 *       party waits, instead of all at once when everyone arrives. Nothing ticks in them.</li>
 *   <li><b>Countdown</b>: a title to the party every second ("Preparing… 10"). The run always starts when it ends,
 *       never earlier, so everyone has time to finish what they're doing (LeDok).</li>
 *   <li><b>Cancel</b>: the controller's check fails (the party changed, someone went offline or unready); its
 *       reservation is let go and the party is told why.</li>
 *   <li><b>Release</b>: {@value #RELEASE_DELAY_TICKS} ticks after the start (by then the players and the run hold
 *       what they need), or right away on a cancel. Never saved: a restart drops a preparation, and the owner presses
 *       start again.</li>
 * </ul>
 * Off when {@code run_prepare_seconds} is 0 (the run starts at once, as before 4.29.0).
 */
public final class RunPreparation {
    private static final int RELEASE_DELAY_TICKS = 200;
    /** Bound on the entrance square, so a mistyped radius can't load thousands of chunks. */
    private static final int MAX_RADIUS = 16;

    private static final TicketType<UUID> TYPE = TicketType.create("arenas_ld_prepare", Comparator.<UUID>naturalOrder());

    /** What the controller does with its own preparation. All calls come on the server thread. */
    public interface Hooks {
        /** Who sees the countdown (the lobby's members now). */
        Collection<UUID> members();

        /** Null while the start can still go ahead; otherwise why not (the preparation is then cancelled). */
        @Nullable Component problem(MinecraftServer server);

        /** The countdown is over: start the run (checking again whatever it must). */
        void launch(MinecraftServer server);

        /** The preparation was cancelled with this reason: let go of any reservation. The party is told already. */
        void cancelled(MinecraftServer server, Component reason);

        /** At the start: close the members' lobby screens, so the countdown title isn't hidden behind them. */
        default void closeScreens(MinecraftServer server) {
        }
    }

    private record Prep(Set<HeldChunk> chunks, Hooks hooks, long startTick, int maxTicks) {
    }

    /** One chunk in the level that holds it. */
    public record HeldChunk(ResourceKey<Level> level, ChunkPos chunk) {
    }

    private static final Map<UUID, Prep> ACTIVE = new LinkedHashMap<>();
    /** Tickets kept a little after their run started: lobby id → (chunks, tick to let go). */
    private static final Map<UUID, Map.Entry<Set<HeldChunk>, Long>> RELEASING = new HashMap<>();

    private RunPreparation() {
    }

    /** Game tests: seconds to use instead of the config (null: the config). Survives config reloads. */
    public static volatile @Nullable Integer secondsOverride;

    /** Seconds of preparation from the config (0: off). */
    public static int seconds() {
        Integer override = secondsOverride;
        return Math.max(0, override != null ? override : ArenasLdConfig.getInstance().run_prepare_seconds);
    }

    public static boolean enabled() {
        return seconds() > 0;
    }

    public static int radius() {
        return Math.max(0, Math.min(MAX_RADIUS, ArenasLdConfig.getInstance().run_prepare_radius_chunks));
    }

    public static boolean isPreparing(UUID lobbyId) {
        return ACTIVE.containsKey(lobbyId);
    }

    /** Adds every chunk within {@code radius} chunks of {@code pos} in {@code level}. */
    public static void square(Set<HeldChunk> into, ResourceKey<Level> level, BlockPos pos, int radius) {
        ChunkPos center = new ChunkPos(pos);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                into.add(new HeldChunk(level, new ChunkPos(center.x + dx, center.z + dz)));
            }
        }
    }

    /**
     * Starts preparing {@code lobbyId}'s run: tickets on {@code chunks} and the countdown. False (nothing done) when
     * preparation is off or this lobby is already preparing.
     */
    public static boolean begin(MinecraftServer server, UUID lobbyId, Set<HeldChunk> chunks, Hooks hooks) {
        int seconds = seconds();
        if (seconds <= 0 || ACTIVE.containsKey(lobbyId)) {
            return false;
        }
        // A quick restart of the same lobby: its old tickets go first.
        Map.Entry<Set<HeldChunk>, Long> old = RELEASING.remove(lobbyId);
        if (old != null) {
            release(server, lobbyId, old.getKey());
        }
        Set<HeldChunk> held = new HashSet<>(chunks);
        for (HeldChunk chunk : held) {
            ServerLevel level = server.getLevel(chunk.level());
            if (level != null) {
                // Distance 0 is ticket level 33: the chunk is loaded in full but nothing in it ticks.
                level.getChunkSource().addRegionTicket(TYPE, chunk.chunk(), 0, lobbyId);
            }
        }
        Prep prep = new Prep(held, hooks, server.getTickCount(), seconds * 20);
        ACTIVE.put(lobbyId, prep);
        DebugLog.log("prepare: lobby {} loads {} chunks for {} s", lobbyId, held.size(), seconds);
        hooks.closeScreens(server);
        // Every screen closes (the hub too), held and crafting items go back into the inventory (4.36.1).
        RunPlayerSafety.secure(server, hooks.members());
        Component line = Component.translatableWithFallback("message.arenas_ld.run.preparing",
            "Preparing the run: it starts in %s s. Finish what you're doing.", seconds).withStyle(ChatFormatting.GOLD);
        for (UUID member : hooks.members()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(line);
            }
        }
        countdown(server, prep, seconds);
        return true;
    }

    /** Cancels {@code lobbyId}'s preparation (if any): tickets let go, the party told, the controller's hook run. */
    public static void cancel(MinecraftServer server, UUID lobbyId, Component reason) {
        Prep prep = ACTIVE.remove(lobbyId);
        if (prep == null) {
            return;
        }
        release(server, lobbyId, prep.chunks());
        Component line = Component.translatableWithFallback("message.arenas_ld.run.prepare_cancelled",
            "Start cancelled: %s", reason).withStyle(ChatFormatting.RED);
        for (UUID member : prep.hooks().members()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(line);
            }
        }
        prep.hooks().cancelled(server, reason);
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (!RELEASING.isEmpty()) {
            Iterator<Map.Entry<UUID, Map.Entry<Set<HeldChunk>, Long>>> it = RELEASING.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Map.Entry<Set<HeldChunk>, Long>> entry = it.next();
                if (now >= entry.getValue().getValue()) {
                    release(server, entry.getKey(), entry.getValue().getKey());
                    it.remove();
                }
            }
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        // Copy: a hook may start or cancel another preparation.
        for (Map.Entry<UUID, Prep> entry : new ArrayList<>(ACTIVE.entrySet())) {
            UUID lobbyId = entry.getKey();
            Prep prep = entry.getValue();
            if (ACTIVE.get(lobbyId) != prep) {
                continue;
            }
            Component problem = prep.hooks().problem(server);
            if (problem != null) {
                cancel(server, lobbyId, problem);
                continue;
            }
            long elapsed = now - prep.startTick();
            if (elapsed >= prep.maxTicks()) {
                ACTIVE.remove(lobbyId);
                RELEASING.put(lobbyId, Map.entry(prep.chunks(), now + RELEASE_DELAY_TICKS));
                DebugLog.log("prepare: lobby {} starts after {} ticks, area loaded: {}", lobbyId, elapsed,
                    loaded(server, prep.chunks()));
                prep.hooks().launch(server);
            } else if (elapsed > 0 && elapsed % 20 == 0) {
                countdown(server, prep, (int) ((prep.maxTicks() - elapsed + 19) / 20));
            }
        }
    }

    /**
     * The usual reasons a waiting start can't go ahead: the lobby is gone or already running, its members changed
     * from {@code party}, one is offline, or (with {@code needReady}) one isn't ready any more. Null: none.
     */
    public static @Nullable Component partyProblem(MinecraftServer server,
                                                   net.ledok.arenas_ld.dungeon.lobby.@Nullable Lobby lobby,
                                                   Set<UUID> party, boolean needReady) {
        if (lobby == null || lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN
            || lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.DISBANDED) {
            return Component.translatableWithFallback("message.arenas_ld.run.prepare.gone", "the lobby is gone");
        }
        if (!new HashSet<>(lobby.members()).equals(party)) {
            return Component.translatableWithFallback("message.arenas_ld.run.prepare.party_changed", "the party changed");
        }
        for (UUID member : party) {
            String name = lobby.memberNames().getOrDefault(member, member.toString());
            if (server.getPlayerList().getPlayer(member) == null) {
                return Component.translatableWithFallback("message.arenas_ld.run.prepare.offline", "%s went offline", name);
            }
            if (needReady && !lobby.readyMembers().contains(member)) {
                return Component.translatableWithFallback("message.arenas_ld.run.prepare.unready", "%s isn't ready", name);
            }
        }
        return null;
    }

    /** Whether every chunk is loaded in full. */
    static boolean loaded(MinecraftServer server, Set<HeldChunk> chunks) {
        for (HeldChunk chunk : chunks) {
            ServerLevel level = server.getLevel(chunk.level());
            if (level != null && !level.getChunkSource().hasChunk(chunk.chunk().x, chunk.chunk().z)) {
                return false;
            }
        }
        return true;
    }

    private static void countdown(MinecraftServer server, Prep prep, int secondsLeft) {
        Component title = Component.translatableWithFallback("title.arenas_ld.run.preparing", "Preparing… %s",
            secondsLeft).withStyle(ChatFormatting.GOLD);
        for (UUID member : prep.hooks().members()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 5));
                player.connection.send(new ClientboundSetTitleTextPacket(title));
            }
        }
    }

    private static void release(MinecraftServer server, UUID lobbyId, Set<HeldChunk> chunks) {
        for (HeldChunk chunk : chunks) {
            ServerLevel level = server.getLevel(chunk.level());
            if (level != null) {
                level.getChunkSource().removeRegionTicket(TYPE, chunk.chunk(), 0, lobbyId);
            }
        }
    }

    /** Chunks held for {@code lobbyId} now: preparing, or kept a little after its start (tests, debugging). */
    public static Set<HeldChunk> held(UUID lobbyId) {
        Prep prep = ACTIVE.get(lobbyId);
        if (prep != null) {
            return prep.chunks();
        }
        Map.Entry<Set<HeldChunk>, Long> releasing = RELEASING.get(lobbyId);
        return releasing != null ? releasing.getKey() : Set.of();
    }

    /** Server stopped: tickets go with the levels. */
    public static void clear() {
        ACTIVE.clear();
        RELEASING.clear();
    }

    /** For tests: the lobbies preparing now. */
    public static List<UUID> preparing() {
        return new ArrayList<>(ACTIVE.keySet());
    }
}
