package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The registered {@link ArenasApi.EntryGate}s and the checks the lobby code runs through them.
 * With no gate registered every check is a no-op.
 */
public final class EntryGates {
    /** Own logger: these run third-party callbacks and must not need the mod bootstrapped. */
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ArenasLdMod.MOD_ID);
    private static final List<ArenasApi.EntryGate> GATES = new CopyOnWriteArrayList<>();

    private EntryGates() {
    }

    public static void register(ArenasApi.EntryGate gate) {
        GATES.add(gate);
    }

    /** Whether any gate is registered (every check is a no-op otherwise). */
    public static boolean active() {
        return !GATES.isEmpty();
    }

    /** First refusal of the registered gates, or null. A gate that throws is logged and skipped. */
    public static @Nullable Component check(ServerPlayer player, ResourceLocation activity, @Nullable String difficulty) {
        for (ArenasApi.EntryGate gate : GATES) {
            try {
                Component refusal = gate.check(player, activity, difficulty);
                if (refusal != null) {
                    return refusal;
                }
            } catch (Throwable t) {
                LOGGER.error("Entry gate {} failed for {} ({} / {})", gate, player.getGameProfile().getName(),
                    activity, difficulty, t);
            }
        }
        return null;
    }

    /**
     * Gate verdict for a controller; {@code tier} null = the activity itself (and arenas). A tier is
     * only open when the activity is too, so the activity-level question is asked first — entering
     * never depends on having opened the lobby screen.
     */
    public static @Nullable Component check(ServerPlayer player, ActivityHost host, @Nullable DifficultyTier tier) {
        ResourceLocation id = host.activityId();
        if (GATES.isEmpty() || id == null) {
            return null;
        }
        Component activityRefusal = check(player, id, null);
        if (activityRefusal != null || tier == null) {
            return activityRefusal;
        }
        return check(player, id, ActivityHost.difficultyId(tier));
    }

    /** Checks and, on refusal, tells the player why. */
    public static boolean allow(ServerPlayer player, ActivityHost host, @Nullable DifficultyTier tier) {
        Component refusal = check(player, host, tier);
        if (refusal != null) {
            player.sendSystemMessage(refusal);
            return false;
        }
        return true;
    }

    /**
     * Checks another player on behalf of {@code actor} (an owner accepting a request, an inviter):
     * on refusal the actor hears "name can't enter: reason", and the target hears the reason when
     * {@code tellTarget} (a refused requester should know; an invitee never asked for anything).
     * An offline target is not checked here — the run start checks everyone.
     */
    public static boolean allowOther(MinecraftServer server, ServerPlayer actor, UUID target, ActivityHost host,
                                     @Nullable DifficultyTier tier, boolean tellTarget) {
        ServerPlayer player = server.getPlayerList().getPlayer(target);
        if (player == null) {
            return true;
        }
        Component refusal = check(player, host, tier);
        if (refusal == null) {
            return true;
        }
        Component line = Component.translatable("message.arenas_ld.gate.player_refused",
            player.getGameProfile().getName(), refusal).withStyle(ChatFormatting.RED);
        actor.sendSystemMessage(line);
        if (tellTarget && player != actor) {
            player.sendSystemMessage(refusal);
        }
        return false;
    }

    /**
     * Every online member must pass for the lobby's run to start. Returns the message listing who
     * can't and why, or null when everyone may enter.
     */
    public static @Nullable Component partyRefusal(MinecraftServer server, Lobby lobby, ActivityHost host,
                                                   @Nullable DifficultyTier tier) {
        if (GATES.isEmpty()) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        for (UUID member : lobby.members()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player == null) {
                continue;
            }
            Component refusal = check(player, host, tier);
            if (refusal != null) {
                lines.add(Component.translatable("message.arenas_ld.gate.player_refused",
                    player.getGameProfile().getName(), refusal));
            }
        }
        if (lines.isEmpty()) {
            return null;
        }
        MutableComponent message = Component.translatable("message.arenas_ld.gate.start_refused")
            .withStyle(ChatFormatting.RED);
        for (Component line : lines) {
            message.append("\n").append(line);
        }
        return message;
    }

    /** The viewer's refusal for each enabled difficulty it may not pick (for the lobby screen). */
    public static Map<DifficultyTier, Component> lockedTiers(ServerPlayer viewer, ActivityHost host) {
        Map<DifficultyTier, Component> locked = new EnumMap<>(DifficultyTier.class);
        if (GATES.isEmpty()) {
            return locked;
        }
        for (DifficultyTier tier : host.enabledTiers()) {
            Component refusal = check(viewer, host, tier);
            if (refusal != null) {
                locked.put(tier, refusal);
            }
        }
        return locked;
    }
}
