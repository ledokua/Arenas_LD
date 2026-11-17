package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.config.ArenasLdConfig.Effects;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.platform.ArenasEvents;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.rewards.RunModifierCatalog;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The gameplay side of a run's picked run modifiers ({@link RunModifierCatalog}): damage taken and
 * dealt by run players, run mobs' health and speed, run players' speed, and frozen ground.
 * <ul>
 *   <li><b>Mobs</b>: every run mob passes {@code markRunMob} as it spawns. While a run ticks or starts,
 *       its modifiers are the "current run" ({@link #during}), so a mob spawned then gets their health and
 *       speed as attribute modifiers. Minions copy them from their summoner ({@link #copyMob}).</li>
 *   <li><b>Players</b>: each run refreshes its online participants every {@value #REFRESH_TICKS} ticks
 *       ({@link #refresh}); a player not refreshed for {@value #STALE_TICKS} ticks (left, run over, server
 *       restarted, controller unloaded) loses the effects again. Nothing to clean up by hand.</li>
 * </ul>
 * Server thread only.
 */
public final class RunEffects {
    public static final int REFRESH_TICKS = 10;
    private static final int STALE_TICKS = 40;
    private static final ResourceLocation MOB_HEALTH = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "run_modifier_health");
    private static final ResourceLocation MOB_SPEED = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "run_modifier_mob_speed");
    private static final ResourceLocation PLAYER_SPEED = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "run_modifier_player_speed");
    /** On a protect-room target: run modifiers leave it alone. */
    public static final String NOT_A_FOE_TAG = "arenas_ld_not_a_foe";

    private static final ThreadLocal<Effects> CURRENT = new ThreadLocal<>();

    private record PlayerState(Effects effects, long refreshedAt) {
    }

    private static final Map<UUID, PlayerState> PLAYERS = new HashMap<>();

    private RunEffects() {
    }

    public static void register() {
        ArenasEvents.SERVER_TICK_END.add(RunEffects::sweep);
        ArenasEvents.SERVER_STOPPED.add(server -> PLAYERS.clear());
    }

    // ── mobs ─────────────────────────────────────────────────────────────────

    /** Runs {@code body} with the run's modifiers as the ones new run mobs get. */
    public static void during(Collection<String> runModifiers, Runnable body) {
        during(runModifiers, () -> {
            body.run();
            return null;
        });
    }

    public static <T> T during(Collection<String> runModifiers, Supplier<T> body) {
        if (runModifiers.isEmpty()) {
            return body.get();
        }
        Effects previous = CURRENT.get();
        CURRENT.set(RunModifierCatalog.combined(runModifiers));
        try {
            return body.get();
        } finally {
            CURRENT.set(previous);
        }
    }

    /** {@code markRunMob}: a run mob spawned during a run with health or speed modifiers gets them. */
    public static void onRunMob(LivingEntity mob) {
        Effects effects = CURRENT.get();
        if (effects == null || mob instanceof Player || mob.getTags().contains(NOT_A_FOE_TAG)) {
            return;
        }
        applyMob(mob, effects.mob_health, effects.mob_speed);
    }

    /** A minion takes its summoner's run-modifier health and speed. */
    public static void copyMob(LivingEntity summoner, LivingEntity minion) {
        double health = factor(summoner, Attributes.MAX_HEALTH, MOB_HEALTH);
        double speed = factor(summoner, Attributes.MOVEMENT_SPEED, MOB_SPEED);
        applyMob(minion, health, speed);
    }

    private static void applyMob(LivingEntity mob, double health, double speed) {
        if (health != 1) {
            setFactor(mob, Attributes.MAX_HEALTH, MOB_HEALTH, health);
            mob.setHealth(mob.getMaxHealth());
        }
        if (speed != 1) {
            setFactor(mob, Attributes.MOVEMENT_SPEED, MOB_SPEED, speed);
        }
    }

    private static double factor(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        AttributeModifier modifier = instance == null ? null : instance.getModifier(id);
        return modifier == null ? 1 : 1 + modifier.amount();
    }

    private static void setFactor(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double factor) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (factor != 1) {
            instance.addPermanentModifier(new AttributeModifier(id, factor - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // ── players ──────────────────────────────────────────────────────────────

    /** A run's tick: its online, not-removed participants keep its effects for a while longer. */
    public static void refresh(MinecraftServer server, Collection<String> runModifiers,
                               Map<UUID, RunParticipant> participants) {
        if (runModifiers.isEmpty() || server.getTickCount() % REFRESH_TICKS != 0) {
            return;
        }
        Effects effects = RunModifierCatalog.combined(runModifiers);
        long now = server.getTickCount();
        for (RunParticipant participant : participants.values()) {
            if (participant.status() == RunParticipant.ParticipantStatus.REMOVED) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerUuid());
            if (player == null) {
                continue;
            }
            PlayerState before = PLAYERS.put(player.getUUID(), new PlayerState(effects, now));
            if (before == null || before.effects().player_speed != effects.player_speed) {
                setFactor(player, Attributes.MOVEMENT_SPEED, PLAYER_SPEED, effects.player_speed);
            }
            if (before == null || before.effects().frozen_ground != effects.frozen_ground) {
                ArenasNetwork.sendToPlayer(player, new net.ledok.arenas_ld.packet.RunFrozenGroundPayload(effects.frozen_ground));
            }
        }
    }

    /** Drops the effects of players no run refreshed lately. */
    private static void sweep(MinecraftServer server) {
        if (PLAYERS.isEmpty() || server.getTickCount() % REFRESH_TICKS != 0) {
            return;
        }
        long now = server.getTickCount();
        for (Map.Entry<UUID, PlayerState> entry : new ArrayList<>(PLAYERS.entrySet())) {
            if (now - entry.getValue().refreshedAt() <= STALE_TICKS) {
                continue;
            }
            PLAYERS.remove(entry.getKey());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                setFactor(player, Attributes.MOVEMENT_SPEED, PLAYER_SPEED, 1);
                if (entry.getValue().effects().frozen_ground) {
                    ArenasNetwork.sendToPlayer(player, new net.ledok.arenas_ld.packet.RunFrozenGroundPayload(false));
                }
            }
        }
    }

    /** Login: a speed modifier saved on the player from a run they are no longer in goes. */
    public static void onJoin(ServerPlayer player) {
        if (!PLAYERS.containsKey(player.getUUID())) {
            setFactor(player, Attributes.MOVEMENT_SPEED, PLAYER_SPEED, 1);
        }
    }

    private static @Nullable Effects effectsOf(@Nullable LivingEntity entity) {
        PlayerState state = entity instanceof ServerPlayer ? PLAYERS.get(entity.getUUID()) : null;
        return state == null ? null : state.effects();
    }

    /**
     * Damage {@code target} takes: ×damage_taken for a run player, ×damage_dealt when a run player (or
     * their projectile) hits anything but a player.
     */
    public static float modifyDamage(LivingEntity target, @Nullable DamageSource source, float amount) {
        if (PLAYERS.isEmpty()) {
            return amount;
        }
        Effects taken = effectsOf(target);
        if (taken != null && taken.damage_taken != 1) {
            amount *= (float) taken.damage_taken;
        }
        if (!(target instanceof Player) && source != null && source.getEntity() instanceof LivingEntity attacker) {
            Effects dealt = effectsOf(attacker);
            if (dealt != null && dealt.damage_dealt != 1) {
                amount *= (float) dealt.damage_dealt;
            }
        }
        return amount;
    }
}
