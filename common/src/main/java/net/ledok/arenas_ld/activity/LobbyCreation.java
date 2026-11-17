package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.rewards.RunModifierCatalog;
import net.ledok.arenas_ld.rewards.RunModifierOption;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** {@code ArenasApi.createLobby / updateLobby / activityInfo / possibleDrops} (4.33.0, the hub's create panel). */
public final class LobbyCreation {
    /** Activities already logged as having no reward table (once each per server run). */
    private static final Set<String> NO_TABLES_LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private LobbyCreation() {
    }

    public static @Nullable Component create(ServerPlayer owner, ResourceLocation activity, ArenasApi.LobbySettings settings,
                                      boolean openScreen) {
        ActivityHost host = ActivityRegistry.resolve(owner.server, activity);
        if (host == null) {
            return Component.translatable("message.arenas_ld.open_lobbies.gone");
        }
        DifficultyTier tier;
        try {
            tier = tier(host, settings.difficulty());
        } catch (IllegalArgumentException e) {
            return Component.translatable("message.arenas_ld.lobby.settings.unknown_tier", settings.difficulty());
        }
        Set<String> modifiers = modifiers(settings);
        Lobby own = ownedForming(host, owner);
        if (own == null) {
            if (BusyStateCompat.isBusy(owner.getUUID())) {
                return Component.translatable("message.arenas_ld.already_in_party");
            }
            Component problem = LobbySettingsApply.problem(owner, host, null, tier, modifiers);
            if (problem != null) {
                return problem;
            }
            if (!host.createLobbyFor(owner)) {
                return Component.translatable("message.arenas_ld.lobby.settings.create_failed");
            }
        }
        Component refusal = host.applyLobbySettings(owner, tier, modifiers, visibility(settings.visibility()),
            Math.max(0, settings.minLevel()));
        host.syncViewers();
        if (refusal != null) {
            return refusal;
        }
        if (openScreen) {
            LobbyScreens.open(owner, host.asBlockEntity(), true);
        }
        return null;
    }

    public static @Nullable Component update(ServerPlayer owner, ArenasApi.LobbySettings settings) {
        LobbyMembership.Membership membership = LobbyMembership.find(owner.server, owner.getUUID());
        if (membership == null || membership.lobby().status() == LobbyStatus.IN_RUN) {
            return Component.translatable("message.arenas_ld.lobby.settings.no_lobby");
        }
        DifficultyTier tier;
        try {
            tier = tier(membership.host(), settings.difficulty());
        } catch (IllegalArgumentException e) {
            return Component.translatable("message.arenas_ld.lobby.settings.unknown_tier", settings.difficulty());
        }
        Component refusal = membership.host().applyLobbySettings(owner, tier, modifiers(settings),
            visibility(settings.visibility()), Math.max(0, settings.minLevel()));
        membership.host().syncViewers();
        return refusal;
    }

    public static Optional<ArenasApi.LobbySettings> settings(ServerPlayer owner) {
        LobbyMembership.Membership membership = LobbyMembership.find(owner.server, owner.getUUID());
        if (membership == null) {
            return Optional.empty();
        }
        Lobby lobby = membership.lobby();
        if (!lobby.isOwner(owner.getUUID()) || lobby.status() == LobbyStatus.IN_RUN || lobby.status() == LobbyStatus.DISBANDED) {
            return Optional.empty();
        }
        String difficulty = membership.host().enabledTiers().isEmpty() ? "" : ActivityHost.difficultyId(lobby.selectedTier());
        return Optional.of(new ArenasApi.LobbySettings(difficulty, lobby.hardcoreEnabled(), List.copyOf(lobby.modifiers()),
            ArenasApi.LobbyVisibility.valueOf(lobby.visibility().name()), lobby.minLevel()));
    }

    public static Optional<ArenasApi.ActivityInfo> info(MinecraftServer server, ResourceLocation activity) {
        ActivityHost host = ActivityRegistry.resolve(server, activity);
        if (host == null) {
            return Optional.empty();
        }
        List<String> tiers = new ArrayList<>();
        for (DifficultyTier tier : host.enabledTiers()) {
            tiers.add(ActivityHost.difficultyId(tier));
        }
        List<ArenasApi.ModifierInfo> modifiers = new ArrayList<>();
        for (RunModifierOption option : RunModifierOption.forType(LobbySettingsApply.type(host))) {
            modifiers.add(new ArenasApi.ModifierInfo(option.id(), option.name(), option.description(), option.rewards(),
                option.icon(), option.conflicts()));
        }
        return Optional.of(new ArenasApi.ActivityInfo(activity, host.activityType(),
            ActivityRegistry.displayName(host.activityType(), host.activityName()), List.copyOf(tiers),
            List.copyOf(modifiers), host.getMaxPartySize(), LobbyLevels.active()));
    }

    public static List<ItemStack> drops(MinecraftServer server, ResourceLocation activity, @Nullable String difficulty) {
        ActivityHost host = ActivityRegistry.resolve(server, activity);
        if (host == null) {
            return List.of();
        }
        DifficultyTier tier;
        try {
            tier = host.enabledTiers().isEmpty() ? null : tier(host, difficulty == null ? "" : difficulty);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        List<String> tables = host.rewardLootTables(tier);
        if (tables.isEmpty()) {
            if (NO_TABLES_LOGGED.add(activity + "/" + difficulty)) {
                net.ledok.arenas_ld.ArenasLdMod.LOGGER.info("[Arenas] Possible drops: {} ({}) has no reward loot table "
                    + "(per-player table or a 'loot give … loot <table>' reward command)", activity, difficulty);
            }
            return List.of();
        }
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : LootPreview.items(server, tables)) {
            out.add(stack.copy());
        }
        return out;
    }

    public static int dropsCount(MinecraftServer server, ResourceLocation activity, @Nullable String difficulty) {
        ActivityHost host = ActivityRegistry.resolve(server, activity);
        if (host == null) {
            return 0;
        }
        DifficultyTier tier;
        try {
            tier = host.enabledTiers().isEmpty() ? null : tier(host, difficulty == null ? "" : difficulty);
        } catch (IllegalArgumentException e) {
            return 0;
        }
        List<String> tables = host.rewardLootTables(tier);
        return tables.isEmpty() ? 0 : LootPreview.count(server, tables);
    }

    /** The tier for a difficulty id; null for blank (keep / arenas). */
    private static @Nullable DifficultyTier tier(ActivityHost host, @Nullable String difficulty) {
        if (difficulty == null || difficulty.isBlank() || host.enabledTiers().isEmpty()) {
            return null;
        }
        return DifficultyTier.valueOf(difficulty.trim().toUpperCase(Locale.ROOT));
    }

    private static Set<String> modifiers(ArenasApi.LobbySettings settings) {
        Set<String> out = new LinkedHashSet<>(settings.modifiers());
        if (settings.hardcore()) {
            out.add(RunModifierCatalog.HARDCORE);
        } else {
            out.remove(RunModifierCatalog.HARDCORE);
        }
        return out;
    }

    private static LobbyVisibility visibility(@Nullable ArenasApi.LobbyVisibility visibility) {
        return visibility == null ? LobbyVisibility.PUBLIC : LobbyVisibility.valueOf(visibility.name());
    }

    private static @Nullable Lobby ownedForming(ActivityHost host, ServerPlayer owner) {
        for (Lobby lobby : host.getLobbies()) {
            if (lobby.isOwner(owner.getUUID()) && lobby.status() != LobbyStatus.IN_RUN
                && lobby.status() != LobbyStatus.DISBANDED) {
                return lobby;
            }
        }
        return null;
    }
}
