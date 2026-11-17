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
    private static final List<ArenasApi.EntryRequirement> REQUIREMENTS = new CopyOnWriteArrayList<>();

    private EntryGates() {
    }

    public static void register(ArenasApi.EntryGate gate) {
        GATES.add(gate);
    }

    public static void registerRequirement(ArenasApi.EntryRequirement requirement) {
        REQUIREMENTS.add(requirement);
    }

    /** First requirement label of the registered requirements, or null. One that throws is logged and skipped. */
    public static @Nullable Component requirement(ServerPlayer player, ActivityHost host, @Nullable DifficultyTier tier) {
        ResourceLocation id = host.activityId();
        if (REQUIREMENTS.isEmpty() || id == null) {
            return null;
        }
        String difficulty = tier == null ? null : ActivityHost.difficultyId(tier);
        for (ArenasApi.EntryRequirement requirement : REQUIREMENTS) {
            try {
                Component label = requirement.describe(player, id, difficulty);
                if (label != null) {
                    return label;
                }
            } catch (Throwable t) {
                LOGGER.error("Entry requirement {} failed for {} ({} / {})", requirement,
                    player.getGameProfile().getName(), id, difficulty, t);
            }
        }
        return null;
    }

    /**
     * The requirement label of each enabled difficulty the viewer may enter (for the lobby screen,
     * shown in green; the {@link #lockedTiers} ones show their refusal instead).
     */
    public static Map<DifficultyTier, Component> metRequirements(ServerPlayer viewer, ActivityHost host,
                                                                 Map<DifficultyTier, Component> locked) {
        Map<DifficultyTier, Component> met = new EnumMap<>(DifficultyTier.class);
        if (REQUIREMENTS.isEmpty()) {
            return met;
        }
        for (DifficultyTier tier : host.enabledTiers()) {
            if (!locked.containsKey(tier)) {
                Component label = requirement(viewer, host, tier);
                if (label != null) {
                    met.put(tier, label);
                }
            }
        }
        return met;
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

    /** {@link net.ledok.arenas_ld.api.ArenasApi#setInviteFilter}; null: everyone may invite. */
    public static volatile net.ledok.arenas_ld.api.ArenasApi.@Nullable InviteFilter inviteFilter;

    /**
     * The invite filter's say on {@code inviter} inviting {@code invitee} to {@code host}: false (the inviter told why,
     * the invitee nothing) when it refuses. A filter that throws is logged and lets the invite through.
     */
    public static boolean inviteAllowed(ServerPlayer inviter, UUID invitee, ActivityHost host) {
        net.ledok.arenas_ld.api.ArenasApi.InviteFilter filter = inviteFilter;
        if (filter == null) {
            return true;
        }
        Component refusal;
        try {
            net.minecraft.resources.ResourceLocation id = host.activityId();
            refusal = filter.refuse(inviter, invitee, id == null ? "" : id.toString());
        } catch (RuntimeException e) {
            net.ledok.arenas_ld.ArenasLdMod.LOGGER.warn("[Arenas] The invite filter failed; letting the invite through", e);
            return true;
        }
        if (refusal == null) {
            return true;
        }
        inviter.sendSystemMessage(refusal);
        return false;
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
     * Every online member must pass for the lobby's run to start, its run modifiers must not conflict, and on a
     * "rental kits only" controller every member must have picked a rental build.
     * Returns the message listing who can't and why (or the conflict), or null when everyone may enter.
     */
    public static @Nullable Component partyRefusal(MinecraftServer server, Lobby lobby, ActivityHost host,
                                                   @Nullable DifficultyTier tier) {
        // Two conflicting run modifiers never start together (the config may have changed since they were picked).
        Component conflict = net.ledok.arenas_ld.rewards.RunModifierCatalog.conflictRefusal(lobby.modifiers());
        if (conflict != null) {
            return conflict;
        }
        // "Rental kits only": everyone picks a rental build first (LeDok 2026-11-17).
        List<String> missing = net.ledok.arenas_ld.util.RunRentals.missingPicks(host, lobby);
        if (!missing.isEmpty()) {
            return Component.translatable("message.arenas_ld.rental.start_needs_picks", String.join(", ", missing))
                .withStyle(ChatFormatting.RED);
        }
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
