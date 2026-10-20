package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.ledok.arenas_ld.util.RunItemRestrictions;
import net.ledok.arenas_ld.util.SafeSpot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The API's admin calls ({@code ArenasApi.location} to {@code ArenasApi.forget}). {@link #status}
 * reads only the saved index and what a loaded controller holds in memory; the other calls are
 * one-off staff actions and may load the controller's chunk. A refusal is returned as the reason
 * to show; null means done. Server thread only.
 */
public final class ActivityAdmin {
    /** The longest display name; the admin screens cut names there too. */
    public static final int MAX_NAME_LENGTH = 48;

    private ActivityAdmin() {
    }

    public static Optional<ArenasApi.ControllerLocation> location(MinecraftServer server, ResourceLocation id) {
        ActivityIndex.Entry entry = entry(server, id);
        return entry == null ? Optional.empty()
            : Optional.of(new ArenasApi.ControllerLocation(entry.dimension(), entry.pos()));
    }

    public static @Nullable Component teleportToController(ServerPlayer player, ResourceLocation id) {
        MinecraftServer server = player.server;
        ActivityIndex.Entry entry = entry(server, id);
        if (entry == null) {
            return unknown(id);
        }
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null) {
            return Component.translatable("message.arenas_ld.admin.dimension_missing", id.toString(),
                entry.dimension().location().toString());
        }
        if (RunItemRestrictions.isPlayerInRun(player)) {
            return Component.translatable("message.arenas_ld.admin.teleport_in_run");
        }
        if (BusyStateCompat.isBusy(player.getUUID())) {
            Set<String> reasons = BusyStateCompat.reasons(player.getUUID());
            return reasons.isEmpty()
                ? Component.translatable("message.arenas_ld.admin.teleport_busy")
                : Component.translatable("message.arenas_ld.admin.teleport_busy_because", String.join(", ", reasons));
        }
        // Reads (and may load) the blocks around the controller: fine for a one-off admin action.
        Vec3 spot = SafeSpot.near(level, entry.pos());
        if (spot == null) {
            return Component.translatable("message.arenas_ld.admin.no_safe_spot", id.toString(), entry.location());
        }
        Vec3 controller = Vec3.atCenterOf(entry.pos());
        double dx = controller.x - spot.x;
        double dy = controller.y - (spot.y + player.getEyeHeight(Pose.STANDING));
        double dz = controller.z - spot.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        // Straight above it (standing on the controller) any heading faces it: keep the player's.
        float yaw = horizontal < 1.0E-4 ? player.getYRot()
            : Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
        float pitch = Mth.wrapDegrees((float) -(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG));
        // As /tp does: absolute position and rotation, any dimension, then no fall carried over.
        player.teleportTo(level, spot.x, spot.y, spot.z, EnumSet.noneOf(RelativeMovement.class), yaw, pitch);
        if (player.level() != level || player.position().distanceToSqr(spot) > 4.0) {
            // A dimension change can be refused (a mod, NeoForge's travel event) without teleportTo saying so.
            return Component.translatable("message.arenas_ld.admin.teleport_failed", id.toString());
        }
        if (!player.isFallFlying()) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.0, 1.0));
            player.setOnGround(true);
        }
        player.resetFallDistance();
        return null;
    }

    public static @Nullable Component rename(MinecraftServer server, ResourceLocation id, String name) {
        // strip(), not trim(): a name of Unicode spaces only is blank (the index reads it as unnamed).
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            return Component.translatable("message.arenas_ld.admin.name_blank");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return Component.translatable("message.arenas_ld.admin.name_too_long", MAX_NAME_LENGTH);
        }
        ActivityHost host = ActivityRegistry.resolve(server, id);
        if (host == null) {
            return unknown(id);
        }
        host.setActivityName(trimmed);
        // The setter refreshes the index only once the controller has ticked; an equal entry fires nothing.
        ActivityRegistry.refresh(host);
        host.syncViewers();
        return null;
    }

    public static @Nullable Component openAdmin(ServerPlayer player, ResourceLocation id) {
        if (!player.hasPermissions(2)) {
            return Component.translatable("message.arenas_ld.admin.no_permission");
        }
        ActivityHost host = ActivityRegistry.resolve(player.server, id);
        if (host == null) {
            return unknown(id);
        }
        return AdminScreens.open(player, host.asBlockEntity(), true);
    }

    /**
     * Polled every couple of seconds for every activity: reads the index, and the controller only
     * when its chunk is loaded. Never {@link ActivityRegistry#resolve}, never a chunk load.
     */
    public static Optional<ArenasApi.ActivityStatus> status(MinecraftServer server, ResourceLocation id) {
        ActivityIndex.Entry entry = entry(server, id);
        if (entry == null) {
            return Optional.empty();
        }
        EnumSet<ArenasApi.Warning> warnings = EnumSet.noneOf(ArenasApi.Warning.class);
        if (entry.generatedId()) {
            warnings.add(ArenasApi.Warning.GENERATED_ID);
        }
        if (entry.name().isBlank()) {
            warnings.add(ArenasApi.Warning.UNNAMED);
        }
        if (entry.type() != ArenasApi.ActivityType.ARENA && entry.difficulties().isEmpty()) {
            warnings.add(ArenasApi.Warning.NO_DIFFICULTIES); // arenas have no difficulties at all
        }
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null) {
            warnings.add(ArenasApi.Warning.CONTROLLER_MISSING);
            return Optional.of(withoutLiveState(id, warnings));
        }
        if (!level.isLoaded(entry.pos())) {
            // Reading the block would load the chunk. Controllers keep theirs loaded, so it's suspicious.
            warnings.add(ArenasApi.Warning.CONTROLLER_NOT_LOADED);
            return Optional.of(withoutLiveState(id, warnings));
        }
        if (!(level.getBlockEntity(entry.pos()) instanceof ActivityHost host) || !id.equals(host.activityId())) {
            warnings.add(ArenasApi.Warning.CONTROLLER_MISSING);
            return Optional.of(withoutLiveState(id, warnings));
        }
        ActivityHost.LiveStatus live = host.liveStatus();
        if (live.instances().isEmpty()) {
            warnings.add(ArenasApi.Warning.NO_INSTANCES);
        }
        if (live.instances().stream().anyMatch(ArenasApi.InstanceStatus::pendingRemoval)) {
            warnings.add(ArenasApi.Warning.PENDING_REMOVALS);
        }
        return Optional.of(new ArenasApi.ActivityStatus(id, live.instances(), live.runs(), live.openLobbies(),
            live.queuedLobbies(), live.waitingPlayers(), Collections.unmodifiableSet(warnings)));
    }

    private static ArenasApi.ActivityStatus withoutLiveState(ResourceLocation id, EnumSet<ArenasApi.Warning> warnings) {
        return new ArenasApi.ActivityStatus(id, List.of(), List.of(), 0, 0, 0, Collections.unmodifiableSet(warnings));
    }

    public static @Nullable Component forceEnd(MinecraftServer server, ResourceLocation id,
                                               @Nullable ArenasApi.InstanceRef instance) {
        ActivityHost host = ActivityRegistry.resolve(server, id);
        if (host == null) {
            return unknown(id);
        }
        int ended = host.forceEnd(instance);
        if (ended == 0) {
            return instance != null
                ? Component.translatable("message.arenas_ld.admin.no_run_at", id.toString(), describe(instance))
                : Component.translatable("message.arenas_ld.admin.nothing_running", id.toString());
        }
        ArenasLdMod.LOGGER.info("Staff ended {} run(s) of {}{}", ended, id,
            instance == null ? "" : " at " + describe(instance));
        return null;
    }

    public static @Nullable Component forget(MinecraftServer server, ResourceLocation id) {
        ActivityIndex.Entry entry = entry(server, id);
        if (entry == null) {
            return unknown(id);
        }
        ServerLevel level = server.getLevel(entry.dimension());
        // May load the chunk: an unloaded controller is still there, and taking its entry would make it
        // vanish from the API until its next first tick.
        if (level != null && level.getBlockEntity(entry.pos()) instanceof ActivityHost host && id.equals(host.activityId())) {
            return Component.translatable("message.arenas_ld.admin.controller_present", id.toString(), entry.location());
        }
        return ActivityRegistry.forget(server, id) ? null : unknown(id);
    }

    private static @Nullable ActivityIndex.Entry entry(MinecraftServer server, ResourceLocation id) {
        return server.overworld() == null ? null : ActivityIndex.get(server).get(id); // no worlds yet: nothing known
    }

    private static Component unknown(ResourceLocation id) {
        return Component.translatable("message.arenas_ld.activity.unknown", id.toString());
    }

    /** "10, 64, -3 (minecraft:overworld)", as the instance commands write a location. */
    private static String describe(ArenasApi.InstanceRef instance) {
        return instance.pos().toShortString() + " (" + instance.dimension().location() + ")";
    }
}
