package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.activity.ActivityHost;
import net.ledok.arenas_ld.activity.LobbyMembership;
import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.packet.RentalLobbyPayload;
import net.ledok.arenas_ld.packet.RentalPreviewPayload;
import net.ledok.arenas_ld.platform.ArenasEvents;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.rewards.RewardModifier;
import net.ledok.arenas_ld.stats.PlayerCounters;
import net.ledok.arenas_ld.stats.RunDetails;
import net.ledok.arenas_ld.stats.RunGear;
import net.ledok.arenas_ld.stats.RunStats;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Rental builds in dungeon and raid runs (design: Balance/RENTAL-BUILDS.md). A lobby member may pick a class; when
 * a preset ({@code falleneden:rental/25/paladin_holy}: per class, the highest level folder at or below their level that
 * has it, offered by the provider); when
 * the run starts the {@link ArenasApi.RentalProvider} (Eden_LD) lends them that loadout,
 * and everything they earn in the run is halved. A "rental kits only" controller ({@link RunSettings#REQUIRED}) makes
 * every member pick one before the run can start, and pays its renters in full.
 * <ul>
 *   <li><b>Lobby</b>: a member's pick is kept with the lobby it was made in (a pick for another lobby is dropped);
 *       once a second each member of a waiting dungeon/raid lobby gets the lobby's picks when they change
 *       ({@link RentalLobbyPayload}).</li>
 *   <li><b>Start</b>: the run's stats tick ({@link #onRunPlayer}, once a second) begins the rental for each member
 *       who picked one: effects cleared, loadout handed out, the class/band/loadout noted in their run counters.
 *       After a restart the same tick begins it again (Eden ends rentals on login), a few ticks after the login.</li>
 *   <li><b>End</b>: once a second, a rental whose player is no longer in its running run (run over, left, removed)
 *       is ended.</li>
 * </ul>
 */
public final class RunRentals {
    /** The reward cut for a renter: everything they earn ×0.5. */
    public static final double REWARD_FACTOR = 0.5;
    private static final int SWEEP_TICKS = 20;
    /** A rejoining player is left alone this long, so Eden's login check runs first. */
    private static final int REJOIN_WAIT_TICKS = 5;
    /** A failing re-begin after a restart is tried this many times, this far apart. */
    private static final int REJOIN_TRIES = 3;
    private static final int REJOIN_RETRY_TICKS = 200;

    public static volatile @Nullable ArenasApi.RentalProvider provider;

    /** A lobby member's pick: the kit, and for its spell book the pool and the spells (empty: the kit's own). */
    private record Choice(UUID lobbyId, String kitId, String pool, List<String> spells) {
        Choice(UUID lobbyId, String kitId) {
            this(lobbyId, kitId, "", List.of());
        }
    }

    private static final Map<UUID, Choice> CHOICES = new HashMap<>();
    /** Player → the rental session Arenas began for them. */
    private static final Map<UUID, String> RENTING = new HashMap<>();
    private static Map<UUID, RentalLobbyPayload> sent = new HashMap<>();

    private RunRentals() {
    }

    public static void register() {
        ArenasEvents.SERVER_TICK_END.add(RunRentals::sweep);
        ArenasEvents.SERVER_STOPPED.add(server -> {
            CHOICES.clear();
            RENTING.clear();
            sent = new HashMap<>();
        });
    }

    /** A preset's level: its folder ("falleneden:rental/25/paladin_holy" → 25), or -1. */
    public static int kitLevel(@Nullable String kitId) {
        String[] parts = kitId == null ? new String[0] : kitId.split("/");
        if (parts.length < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[parts.length - 2]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** A preset's class: its name up to the first "_" ("…/25/paladin_holy" → "paladin"). */
    public static String classOf(String kitId) {
        String preset = presetName(kitId);
        int underscore = preset.indexOf('_');
        return underscore > 0 ? preset.substring(0, underscore) : preset;
    }

    /** The preset part of a kit id ("…/25/paladin_holy" → "paladin_holy"). */
    public static String presetName(String kitId) {
        return kitId.substring(Math.max(kitId.lastIndexOf('/'), kitId.lastIndexOf(':')) + 1);
    }

    /** The player's level for rentals (the {@code loot_stage} level; 0 without Loot Stage). */
    private static int level(ServerPlayer player) {
        return Math.max(0, RunGear.playerLevel(player));
    }

    /**
     * The highest kit level folder the player may rent: their level, never above {@code runLevel} (the run level of
     * the difficulty; -1 = no cap). LeDok 2026-11-17.
     */
    public static int capped(ServerPlayer player, int runLevel) {
        int level = level(player);
        return runLevel >= 0 ? Math.min(level, runLevel) : level;
    }

    /** {@link #capped} for the lobby's difficulty at {@code host}. */
    private static int capped(ServerPlayer player, ActivityHost host, Lobby lobby) {
        return capped(player, host.runLevel(lobby.selectedTier()));
    }

    /** The preset class recommended for the player: their skill-tree class, through {@code rental_class_presets}. */
    private static String recommendedClass(ServerPlayer player) {
        String branch = RunGear.skillBranch(player);
        if (branch.isEmpty()) {
            return "";
        }
        var map = net.ledok.arenas_ld.config.ArenasLdConfig.getInstance().rental_class_presets;
        String mapped = map == null ? null : map.get(branch);
        return (mapped == null || mapped.isBlank() ? branch : mapped).toLowerCase(java.util.Locale.ROOT);
    }

    // ── lobby ────────────────────────────────────────────────────────────────

    /** The player's waiting dungeon/raid lobby at {@code controller}, or null. */
    private static @Nullable LobbyMembership.Membership lobbyAt(ServerPlayer player, BlockPos controller) {
        LobbyMembership.Membership membership = LobbyMembership.find(player.server, player.getUUID());
        return membership == null || !rentalHost(membership.host())
            || !membership.host().asBlockEntity().getBlockPos().equals(controller) || !waiting(membership.lobby())
            ? null : membership;
    }

    /**
     * Every preset the player can rent with what it hands out, for the lobby's preview; null (refused, with a chat
     * line) when they aren't in a waiting lobby there or can't rent.
     */
    public static @Nullable RentalPreviewPayload preview(ServerPlayer player, BlockPos controller) {
        ArenasApi.RentalProvider p = provider;
        LobbyMembership.Membership membership = lobbyAt(player, controller);
        if (membership == null || p == null) {
            player.displayClientMessage(Component.translatable("message.arenas_ld.rental.not_in_lobby")
                .withStyle(ChatFormatting.RED), false);
            return null;
        }
        int level = capped(player, membership.host(), membership.lobby());
        String recommended = recommendedClass(player);
        Choice current = CHOICES.get(player.getUUID());
        List<RentalPreviewPayload.Kit> kits = new ArrayList<>();
        for (String kit : p.presets(level)) {
            List<net.minecraft.world.item.ItemStack> items = new ArrayList<>();
            for (var stack : p.preview(kit)) {
                if (stack != null && !stack.isEmpty() && encodes(player, stack)) {
                    items.add(stack.copy());
                }
            }
            boolean isRecommended = !recommended.isEmpty() && classOf(kit).equalsIgnoreCase(recommended);
            RentalSpells.Choice spells = RentalSpells.forKit(player.registryAccess(), items, kitLevel(kit));
            List<String> chosen = List.of();
            if (spells != null) {
                chosen = current != null && current.kitId().equals(kit) && !current.spells().isEmpty()
                    ? current.spells() : RentalSpells.prefill(player, presetName(kit), spells);
            }
            List<Integer> tiers = spells == null || !spells.perTier() ? List.of()
                : spells.allowed().stream().map(s -> spells.tiers().get(s)).toList();
            kits.add(new RentalPreviewPayload.Kit(kit, p.displayName(kit), kitLevel(kit), items, isRecommended,
                spells == null ? List.of() : spells.allowed(), spells == null ? 0 : spells.max(), chosen, tiers));
        }
        // The player's skill-tree class first (stable: the provider's order otherwise).
        kits.sort(java.util.Comparator.comparing(k -> !k.recommended()));
        if (kits.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.arenas_ld.rental.none_for_level")
                .withStyle(ChatFormatting.RED), false);
            return null;
        }
        // The header names the kits' level (4.40.2): the cap (min of player and run level) can sit between folders,
        // e.g. a level-6 player gets the level-5 kits.
        int shown = kits.stream().mapToInt(RentalPreviewPayload.Kit::level).max().orElse(level);
        return new RentalPreviewPayload(controller, shown, kits);
    }

    /**
     * Whether the stack can be sent: one that can't (an empty stack nested in a container component, e.g. a drained
     * shared rune pouch) is left out of the preview instead of breaking the player's connection.
     */
    private static boolean encodes(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), player.registryAccess());
        try {
            net.minecraft.world.item.ItemStack.STREAM_CODEC.encode(buf, stack);
            return true;
        } catch (RuntimeException e) {
            ArenasLdMod.LOGGER.warn("[Arenas] Rental preview: left out {} (can't be sent: {})", stack, e.getMessage());
            return false;
        } finally {
            buf.release();
        }
    }

    /** A lobby member picks a preset (kit id; "" = their own gear). Returns why it's refused, or null. */
    public static @Nullable Component choose(ServerPlayer player, BlockPos controller, String kitId) {
        return choose(player, controller, kitId, List.of());
    }

    /**
     * {@link #choose} with the spells picked for the kit's spell book (empty: the saved pick, their own book's, or the
     * kit's own). Spells outside what the kit allows are dropped; the pick is saved per player and preset.
     */
    public static @Nullable Component choose(ServerPlayer player, BlockPos controller, String kitId, List<String> spells) {
        LobbyMembership.Membership membership = lobbyAt(player, controller);
        if (membership == null) {
            return Component.translatable("message.arenas_ld.rental.not_in_lobby");
        }
        if (kitId.isEmpty()) {
            if (required(membership.host())) {
                return Component.translatable("message.arenas_ld.rental.required_no_own_gear");
            }
            CHOICES.remove(player.getUUID());
            return null;
        }
        ArenasApi.RentalProvider p = provider;
        if (p == null || !p.presets(capped(player, membership.host(), membership.lobby())).contains(kitId)) {
            return Component.translatable("message.arenas_ld.rental.no_loadout", presetName(kitId));
        }
        String pool = "";
        List<String> picked = List.of();
        RentalSpells.Choice book = RentalSpells.forKit(player.registryAccess(), p.preview(kitId), kitLevel(kitId));
        if (book != null) {
            pool = book.pool();
            // Nothing sent (an old client, a plain "Pick"): the prefill; else the pick, cut to the rule, and remembered.
            picked = spells == null || spells.isEmpty() ? List.of() : RentalSpells.validate(spells, book);
            if (picked.isEmpty()) {
                picked = RentalSpells.prefill(player, presetName(kitId), book);
            } else {
                RentalSpells.Store.get(player.server).put(player.getUUID(), presetName(kitId), picked);
            }
        }
        CHOICES.put(player.getUUID(), new Choice(membership.lobby().lobbyId(), kitId, pool, picked));
        return null;
    }

    /** Whether the controller runs "rental kits only" (every member must pick a rental build). */
    public static boolean required(@Nullable ActivityHost host) {
        return host != null && rentalHost(host) && RunSettings.of(host).rentalRequired();
    }

    /**
     * For a "rental kits only" controller: the members of {@code lobby} who haven't picked a build for it yet (names),
     * empty when everyone has or the controller doesn't require one. The run can't start until it's empty.
     */
    public static List<String> missingPicks(ActivityHost host, Lobby lobby) {
        if (!required(host)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (UUID member : lobby.members()) {
            Choice choice = CHOICES.get(member);
            if (choice == null || !choice.lobbyId().equals(lobby.lobbyId())) {
                out.add(lobby.memberNames().getOrDefault(member, member.toString()));
            }
        }
        return out;
    }

    /** Whether {@code player} has picked a rental build for {@code lobby}. */
    public static boolean hasPick(UUID player, Lobby lobby) {
        Choice choice = CHOICES.get(player);
        return choice != null && choice.lobbyId().equals(lobby.lobbyId());
    }

    private static boolean rentalHost(ActivityHost host) {
        return host.activityType() == ArenasApi.ActivityType.DUNGEON || host.activityType() == ArenasApi.ActivityType.RAID;
    }

    private static boolean waiting(Lobby lobby) {
        return lobby.status() == LobbyStatus.FORMING || lobby.status() == LobbyStatus.READY;
    }

    // ── run ──────────────────────────────────────────────────────────────────

    /**
     * The run's stats tick, for each online participant still in it: begins a picked rental (or again after a
     * restart).
     */
    public static void onRunPlayer(RunStats stats, PlayerCounters counters, ServerPlayer player) {
        ArenasApi.RentalProvider p = provider;
        if (p == null) {
            return;
        }
        UUID uuid = player.getUUID();
        String session = "arenas:" + stats.sessionKey();
        if (counters.loadoutId == null) {
            Choice choice = CHOICES.get(uuid);
            if (choice == null) {
                return;
            }
            // The pick is for this run unless its lobby is still waiting (then it is for that lobby's run). A dungeon
            // removes its lobby when the run starts and a raid keeps it IN_RUN, so the lobby itself can't be required.
            LobbyMembership.Membership membership = LobbyMembership.find(player.server, uuid);
            if (membership != null && membership.lobby().lobbyId().equals(choice.lobbyId()) && waiting(membership.lobby())) {
                return;
            }
            CHOICES.remove(uuid);
            String kit = choice.kitId();
            int band = kitLevel(kit);
            if (!p.presets(capped(player, stats.source().runLevel())).contains(kit)) { // their level changed since the pick, or the kit went
                player.sendSystemMessage(Component.translatable("message.arenas_ld.rental.failed").withStyle(ChatFormatting.RED));
                return;
            }
            player.removeAllEffects(); // food buffs, overdrives, relic stacks, potions: a clean start
            if (!p.isRenting(player) && !p.begin(player, session, kit)) {
                player.sendSystemMessage(Component.translatable("message.arenas_ld.rental.failed").withStyle(ChatFormatting.RED));
                return;
            }
            counters.loadoutId = kit;
            counters.rentalClass = classOf(kit);
            counters.rentalBand = band;
            counters.rentalPool = choice.pool();
            counters.rentalSpells = choice.spells();
            if (!choice.pool().isEmpty()) {
                // Checked again at the start (4.40.4): the rule (rental_spell_tiers, max tier) may have changed.
                RentalSpells.Choice book = RentalSpells.forKit(player.registryAccess(), p.preview(kit), band);
                counters.rentalSpells = book == null ? List.of() : RentalSpells.validate(choice.spells(), book);
            }
            applySpells(player, counters);
            RENTING.put(uuid, session);
            player.sendSystemMessage(Component.translatable("message.arenas_ld.rental.started", p.displayName(kit), band)
                .withStyle(ChatFormatting.GOLD));
            ArenasLdMod.LOGGER.info("[Arenas] {} rents {} for {}", player.getScoreboardName(), kit, session);
            return;
        }
        // Rented in this run: after a restart Eden gave their own items back on login; lend the loadout again. A
        // failing begin (the kit is gone) is tried at most REJOIN_TRIES times, REJOIN_RETRY_TICKS apart.
        if (!p.isRenting(player) && player.tickCount >= REJOIN_WAIT_TICKS && counters.rentalRejoinTries < REJOIN_TRIES
            && player.server.getTickCount() >= counters.rentalRetryTick) {
            if (p.begin(player, session, counters.loadoutId)) {
                counters.rentalRejoinTries = 0;
                counters.rentalSpellsTries = 0;
                applySpells(player, counters);
                ArenasLdMod.LOGGER.info("[Arenas] {} rents {} again for {} (rejoined)", player.getScoreboardName(),
                    counters.loadoutId, session);
            } else {
                counters.rentalRejoinTries++;
                counters.rentalRetryTick = player.server.getTickCount() + REJOIN_RETRY_TICKS;
                if (counters.rentalRejoinTries >= REJOIN_TRIES) {
                    ArenasLdMod.LOGGER.warn("[Arenas] {} couldn't rent {} again for {}; runs on with their own gear",
                        player.getScoreboardName(), counters.loadoutId, session);
                }
            }
        }
        if (!counters.rentalSpellsApplied && p.isRenting(player)) {
            applySpells(player, counters);
        }
        RENTING.putIfAbsent(uuid, session);
    }

    /** After a respawn in the run: the death emptied the inventory, so the loadout is handed out again. */
    public static void reequip(ServerPlayer player) {
        ArenasApi.RentalProvider p = provider;
        if (p != null && RENTING.containsKey(player.getUUID()) && p.isRenting(player)) {
            p.reequip(player);
            RunStats run = RunDetails.runOf(player.getUUID());
            PlayerCounters counters = run == null ? null : run.players().get(player.getUUID());
            if (counters != null) {
                counters.rentalSpellsTries = 0;
                applySpells(player, counters); // the respawn handed out the kit's own book again
            }
        }
    }

    /**
     * The picked spells into the lent book (nothing when none were picked or the kit has no book). The run's stats tick
     * tries again each second until it works (the provider may equip the book a tick later), and warns once after 10 s.
     */
    private static void applySpells(ServerPlayer player, PlayerCounters counters) {
        if (counters.rentalSpells.isEmpty() || counters.rentalPool.isEmpty()) {
            counters.rentalSpellsApplied = true;
            return;
        }
        counters.rentalSpellsApplied = RentalSpells.apply(player, counters.rentalPool, counters.rentalSpells);
        if (!counters.rentalSpellsApplied && ++counters.rentalSpellsTries == 10) {
            ArenasLdMod.LOGGER.warn("[Arenas] {}: no lent {} book to put the picked spells {} in",
                player.getScoreboardName(), counters.rentalPool, counters.rentalSpells);
        }
    }

    /**
     * The rental reward cut for {@code player} ("Rental ×0.5" on loot, XP and currency), or nothing. Read from their
     * run counters (saved with the run), so it holds for a renter who is offline after a restart too.
     */
    public static List<RewardModifier> rewardModifiers(UUID player) {
        RunStats run = RunDetails.runOfAny(player);
        PlayerCounters counters = run == null ? null : run.players().get(player);
        if (counters == null || counters.loadoutId == null || run.rentalRequired()) {
            return List.of(); // a "rental kits only" run pays in full (LeDok 2026-11-17)
        }
        List<RewardModifier> out = new ArrayList<>();
        for (RewardModifier.Stat stat : RewardModifier.Stat.values()) {
            out.add(RewardModifier.more(stat, REWARD_FACTOR, "rental", "message.arenas_ld.reward.source.rental"));
        }
        return out;
    }

    public static boolean isRenting(UUID player) {
        return RENTING.containsKey(player);
    }

    // ── sweep ────────────────────────────────────────────────────────────────

    private static void sweep(MinecraftServer server) {
        if (server.getTickCount() % SWEEP_TICKS != 0) {
            return;
        }
        endFinished(server);
        syncLobbies(server);
    }

    /** Ends rentals whose player is no longer in the running run they rent for. */
    private static void endFinished(MinecraftServer server) {
        if (RENTING.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, String> entry : new ArrayList<>(RENTING.entrySet())) {
            RunStats run = RunDetails.runOf(entry.getKey());
            if (run != null && entry.getValue().equals("arenas:" + run.sessionKey())) {
                continue;
            }
            RENTING.remove(entry.getKey());
            ArenasApi.RentalProvider p = provider;
            if (p != null) {
                p.end(server, entry.getKey());
            }
        }
    }

    /** Each member of a waiting dungeon/raid lobby gets its picks when they change; picks for other lobbies go. */
    private static void syncLobbies(MinecraftServer server) {
        ArenasApi.RentalProvider p = provider;
        boolean any = p != null;
        Map<UUID, LobbyMembership.Membership> all = LobbyMembership.all(server);
        // A pick goes when its player left its lobby, unless they're in a run now (a dungeon removes its lobby when the
        // run starts; the run takes the pick at its next stats tick).
        CHOICES.entrySet().removeIf(e -> {
            LobbyMembership.Membership m = all.get(e.getKey());
            boolean inLobby = m != null && m.lobby().lobbyId().equals(e.getValue().lobbyId());
            return !inLobby && RunDetails.runOf(e.getKey()) == null;
        });
        Map<UUID, RentalLobbyPayload> now = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            LobbyMembership.Membership m = all.get(player.getUUID());
            RentalLobbyPayload payload = null;
            List<String> presets = any && m != null && rentalHost(m.host()) && waiting(m.lobby())
                ? List.copyOf(p.presets(capped(player, m.host(), m.lobby()))) : List.of();
            Choice held = CHOICES.get(player.getUUID());
            if (held != null && m != null && held.lobbyId().equals(m.lobby().lobbyId()) && !presets.isEmpty()
                && !presets.contains(held.kitId())) {
                // The difficulty (or their level) changed: the kit is no longer allowed. Ask again.
                CHOICES.remove(player.getUUID());
                player.displayClientMessage(Component.translatable("message.arenas_ld.rental.pick_again",
                    p.displayName(held.kitId())).withStyle(ChatFormatting.GOLD), false);
                RentalPreviewPayload again = preview(player, m.host().asBlockEntity().getBlockPos());
                if (again != null) {
                    ArenasNetwork.sendToPlayer(player, again);
                }
            }
            if (!presets.isEmpty()) {
                Map<String, String> picks = new LinkedHashMap<>();
                for (UUID member : m.lobby().members()) {
                    Choice choice = CHOICES.get(member);
                    if (choice != null) {
                        picks.put(m.lobby().memberNames().getOrDefault(member, member.toString()), classOf(choice.kitId()));
                    }
                }
                Choice own = CHOICES.get(player.getUUID());
                payload = new RentalLobbyPayload(true, m.host().asBlockEntity().getBlockPos(), presets,
                    own == null ? -1 : kitLevel(own.kitId()), own == null ? "" : presetName(own.kitId()), picks,
                    required(m.host()));
                now.put(player.getUUID(), payload);
            }
            RentalLobbyPayload before = sent.get(player.getUUID());
            if (!Objects.equals(before, payload) && (payload != null || before != null)) {
                ArenasNetwork.sendToPlayer(player, payload != null ? payload : RentalLobbyPayload.NONE);
            }
        }
        sent = now;
    }
}
