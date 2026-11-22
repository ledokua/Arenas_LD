package net.ledok.arenas_ld.stats;

import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.packet.RunDetailsPayload;
import net.ledok.arenas_ld.packet.RunHudPayload;
import net.ledok.arenas_ld.platform.ArenasEvents;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.rewards.RewardMath;
import net.ledok.arenas_ld.rewards.RewardModifiers;
import net.ledok.arenas_ld.rewards.RunModifierCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The run details screen, server side: which runs are going ({@link #track}), who has the screen open, and what
 * they see, live or from a finished run's record. Nothing is built or sent while nobody has it open; a viewer gets a
 * running run at most once a second, and only when it changed.
 */
public final class RunDetails {
    private static final Set<RunStats> LIVE = new LinkedHashSet<>();
    private static final Map<UUID, Viewer> VIEWERS = new HashMap<>();
    /** Viewers of a finished run (the end chat line's [Details], staff history): its record, for the Timeline tab. */
    private static final Map<UUID, RunRecord> RECORD_VIEWERS = new HashMap<>();
    /** Events per Timeline page. */
    static final int TIMELINE_PAGE = 1000;

    private static final class Viewer {
        final RunStats stats;
        final boolean staff;
        @Nullable RunDetailsPayload last;

        Viewer(RunStats stats, boolean staff) {
            this.stats = stats;
            this.staff = staff;
        }
    }

    private RunDetails() {
    }

    public static void register() {
        SpellEngineHeals.register();
        RunRecords.register();
        ArenasEvents.SERVER_TICK_END.add(RunDetails::tick);
        ArenasEvents.SERVER_TICK_END.add(server -> SpellContext.reset());
        ArenasEvents.PLAYER_DISCONNECT.add((player, server) -> {
            VIEWERS.remove(player.getUUID());
            RECORD_VIEWERS.remove(player.getUUID());
        });
        ArenasEvents.SERVER_STOPPED.add(server -> {
            LIVE.clear();
            VIEWERS.clear();
            RECORD_VIEWERS.clear();
        });
    }

    static void track(RunStats stats) {
        LIVE.add(stats);
    }

    static void untrack(RunStats stats) {
        LIVE.remove(stats);
    }

    /** The run {@code player} is playing (not out of), or null. */
    public static @Nullable RunStats runOf(UUID player) {
        RunStats found = null; // the newest: an older run the player left may still be listed for a moment
        for (RunStats stats : LIVE) {
            if (stats.pvp) {
                continue; // a duel's combat log (CombatSessions) is no run
            }
            RunParticipant participant = stats.view.participants().get().get(player);
            if (participant != null && participant.status() != RunParticipant.ParticipantStatus.REMOVED) {
                found = stats;
            }
        }
        return found;
    }

    /** The going run {@code player} is or was in (out of it too: loot paid to them), or null. */
    public static @Nullable RunStats runOfAny(UUID player) {
        for (RunStats stats : LIVE) {
            if (!stats.pvp && stats.view.participants().get().containsKey(player)) {
                return stats;
            }
        }
        return null;
    }

    /** The run of {@code activity} at {@code instance} (any of its runs when null), or null. */
    public static @Nullable RunStats runAt(ResourceLocation activity, @Nullable ArenasApi.InstanceRef instance) {
        RunStats found = null; // the newest
        for (RunStats stats : LIVE) {
            if (stats.pvp) {
                continue;
            }
            RunDetailsSource source = stats.source();
            if (activity.equals(source.activityId()) && (instance == null || instance.equals(source.instance()))) {
                found = stats;
            }
        }
        return found;
    }

    /** Opens (or refreshes) the details screen of {@code stats} for {@code viewer}; staff also see the staff details. */
    public static void open(ServerPlayer viewer, RunStats stats, boolean staff) {
        Viewer entry = new Viewer(stats, staff);
        VIEWERS.put(viewer.getUUID(), entry);
        RECORD_VIEWERS.remove(viewer.getUUID());
        entry.last = build(viewer.server, stats, staff);
        ArenasNetwork.sendToPlayer(viewer, entry.last.opening(true));
    }

    public static void closed(ServerPlayer viewer) {
        VIEWERS.remove(viewer.getUUID());
        RECORD_VIEWERS.remove(viewer.getUUID());
    }

    /** {@code viewer} opened a finished run's details (the Timeline tab reads its record). */
    static void viewingRecord(ServerPlayer viewer, RunRecord record) {
        VIEWERS.remove(viewer.getUUID());
        RECORD_VIEWERS.put(viewer.getUUID(), record);
    }

    /**
     * The Timeline tab asked for {@code player}'s events from {@code from}: one page from the run this viewer has open
     * (live or finished). Nothing when they have none open or the player wasn't in it.
     */
    public static void timeline(ServerPlayer viewer, UUID player, int from) {
        List<List<Object>> events;
        int dropped;
        Viewer live = VIEWERS.get(viewer.getUUID());
        if (live != null) {
            PlayerCounters counters = live.stats.players().get(player);
            if (counters == null) {
                return;
            }
            events = counters.events;
            dropped = counters.eventsDropped;
        } else {
            RunRecord record = RECORD_VIEWERS.get(viewer.getUUID());
            RunRecord.Player p = record == null ? null
                : record.players.stream().filter(x -> x.uuid.equals(player.toString())).findFirst().orElse(null);
            if (p == null) {
                return;
            }
            events = p.events == null ? List.of() : p.events;
            dropped = p.eventsDropped;
        }
        int total = events.size();
        int start = Math.max(0, Math.min(from, total));
        int end = Math.min(total, start + TIMELINE_PAGE);
        List<List<String>> page = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            List<Object> event = events.get(i);
            List<String> fields = new ArrayList<>(event.size());
            for (Object field : event) {
                fields.add(text(field));
            }
            page.add(fields);
        }
        ArenasNetwork.sendToPlayer(viewer, new net.ledok.arenas_ld.packet.RunTimelinePayload(player, start, total, dropped, page));
    }

    private static String text(Object field) {
        if (field instanceof Float || field instanceof Double) {
            double d = ((Number) field).doubleValue();
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : String.format(Locale.ROOT, "%.1f", d);
        }
        return String.valueOf(field);
    }

    /**
     * Ends the runs that stopped ticking: a run its controller took away (ended between its own once-a-second checks,
     * abandoned, cleaned up) never saw itself over, and would stay listed and show instead of newer runs.
     */
    public static void endStopped(MinecraftServer server) {
        for (RunStats stats : new ArrayList<>(LIVE)) {
            if (stats.lastTick >= 0 && server.getTickCount() - stats.lastTick > 40) {
                stats.end(server);
            }
        }
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        endStopped(server);
        if (VIEWERS.isEmpty()) {
            return;
        }
        Map<RunStats, RunDetailsPayload> built = new HashMap<>();
        Map<RunStats, RunDetailsPayload> builtStaff = new HashMap<>();
        for (var iterator = VIEWERS.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<UUID, Viewer> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            Viewer viewer = entry.getValue();
            if (viewer.last != null && viewer.last.over()) {
                continue; // the run is over: its last numbers stay on screen
            }
            RunDetailsPayload payload = (viewer.staff ? builtStaff : built)
                .computeIfAbsent(viewer.stats, stats -> build(server, stats, viewer.staff));
            if (!payload.equals(viewer.last)) {
                viewer.last = payload;
                ArenasNetwork.sendToPlayer(player, payload);
            }
        }
    }

    /** A player's state word in a record: "alive", "downed", "out", "offline", "forfeited", "won", "lost". */
    static String state(MinecraftServer server, RunStats stats, UUID uuid, RunParticipant participant) {
        PlayerCounters counters = stats.players().get(uuid);
        if (counters != null && counters.forfeited) {
            return "forfeited"; // left with /exit before the run was over, even if it went on without them
        }
        if (participant.status() == RunParticipant.ParticipantStatus.REMOVED) {
            return "out";
        }
        if (stats.pvp) {
            if (stats.winnerSide != null && !stats.winnerSide.isEmpty() && counters != null) {
                return stats.winnerSide.equals(counters.side) ? "won" : "lost";
            }
            return server.getPlayerList().getPlayer(uuid) == null ? "offline" : "alive";
        }
        // Everyone still in it when it ended (downed and hardcore-out players too): its result.
        String result = stats.source().result();
        if ("win".equals(result)) {
            return "won";
        }
        if ("fail".equals(result) || "abandoned".equals(result)) {
            return "lost";
        }
        if (server.getPlayerList().getPlayer(uuid) == null) {
            return "offline";
        }
        return stats.view.downed().test(uuid) ? "downed" : "alive";
    }

    /** A going run as it is now: its record, plus HP, respawn timers, the HUD line and the rewards to come. */
    static RunDetailsPayload build(MinecraftServer server, RunStats stats, boolean staff) {
        RunRecord record = stats.snapshot(server);
        RunDetailsSource source = stats.source();
        long now = server.overworld().getGameTime();
        RunHudPayload hud = stats.lastHud;
        List<RunDetailsPayload.Row> rows = rows(record, staff, server.registryAccess(), p -> {
            UUID uuid = UUID.fromString(p.uuid);
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            boolean showHp = player != null && !"out".equals(p.state);
            int respawn = -1;
            if ("downed".equals(p.state)) {
                Long left = stats.view.respawnLeft().apply(now, uuid);
                respawn = left == null || left < 0 ? -1 : (int) ((left + 19) / 20);
            }
            return new float[]{showHp ? player.getHealth() : -1, showHp ? player.getMaxHealth() : -1, respawn};
        });
        return new RunDetailsPayload(source.activityName(), tier(record.difficulty), record.hardcore, modifiers(record),
            hud == null ? Component.empty() : hud.label(),
            hud == null ? 0 : hud.remainingTicks(), hud == null ? 0 : hud.totalTicks(), record.durationSeconds,
            hud == null ? RunHudPayload.NO_BOSS_HP : hud.bossHpPercent(),
            stats.pvp ? Component.empty() : rewards(source), Component.empty(), highlights(record), phases(record), rows,
            staff ? round(record.unattributed) : -1, stats.ended(), staff, false, false);
    }

    /** A finished run, from its record (the end screen, the staff history). */
    public static RunDetailsPayload fromRecord(RunRecord record, boolean staff, boolean end) {
        return fromRecord(record, staff, end, null);
    }

    /** {@link #fromRecord(RunRecord, boolean, boolean)} with the registries that read the class book stacks. */
    public static RunDetailsPayload fromRecord(RunRecord record, boolean staff, boolean end,
                                               @Nullable net.minecraft.core.HolderLookup.Provider registries) {
        List<RunDetailsPayload.Row> rows = rows(record, staff, registries, p -> new float[]{-1, -1, -1});
        return new RunDetailsPayload(Component.literal(record.activityName), tier(record.difficulty), record.hardcore,
            modifiers(record), Component.empty(), 0, 0, record.durationSeconds, RunHudPayload.NO_BOSS_HP,
            recordRewards(record), result(record), highlights(record), phases(record), rows,
            staff ? round(record.unattributed) : -1, true, staff, end, end);
    }

    /** The spell book stack from the record, or empty (no book, no registries, unreadable). */
    private static net.minecraft.world.item.ItemStack classStack(RunRecord.Player p,
                                                                @Nullable net.minecraft.core.HolderLookup.Provider registries) {
        if (registries == null || p.gear == null || p.gear.playerClassStack == null || p.gear.playerClassStack.isEmpty()) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        try {
            return net.minecraft.world.item.ItemStack.parse(registries,
                net.minecraft.nbt.TagParser.parseTag(p.gear.playerClassStack)).orElse(net.minecraft.world.item.ItemStack.EMPTY);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException e) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
    }

    private static List<RunDetailsPayload.Row> rows(RunRecord record, boolean staff,
                                                    @Nullable net.minecraft.core.HolderLookup.Provider registries,
                                                    Function<RunRecord.Player, float[]> live) {
        List<RunDetailsPayload.Row> rows = new ArrayList<>();
        for (RunRecord.Player p : record.players) {
            float[] now = live.apply(p);
            float blocked = p.blockedShield + p.blockedArmor + p.blockedResistance + p.blockedEnchant + p.blockedAbsorption;
            rows.add(new RunDetailsPayload.Row(p.name, state(p.state), p.deaths, (int) now[2], now[0], now[1],
                round(p.dealt), round(p.taken), round(blocked), round(p.healedSelf + p.healedOthers), p.kills,
                staff ? Component.literal(levelLine(record, p)).append(staffLine(p, "duel".equals(record.type))) : Component.empty(),
                uuidOf(p.uuid), p.gear == null || p.gear.playerClass == null ? "" : p.gear.playerClass,
                p.gear == null ? -1 : p.gear.playerLevel, round(p.healedOthers), rewardLines(p), classStack(p, registries)));
        }
        return rows;
    }

    private static UUID uuidOf(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (RuntimeException e) {
            return new UUID(0, 0);
        }
    }

    private static List<Component> rewardLines(RunRecord.Player p) {
        List<Component> out = new ArrayList<>();
        if (p.rewards != null) {
            for (String json : p.rewards) {
                try {
                    Component line = Component.Serializer.fromJson(json, net.minecraft.core.RegistryAccess.EMPTY);
                    if (line != null) {
                        out.add(line);
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
        return out;
    }

    private static RunDetailsPayload.State state(String word) {
        return switch (word) {
            case "downed" -> RunDetailsPayload.State.DOWNED;
            case "out" -> RunDetailsPayload.State.OUT;
            case "offline" -> RunDetailsPayload.State.OFFLINE;
            case "forfeited" -> RunDetailsPayload.State.FORFEITED;
            case "won" -> RunDetailsPayload.State.WON;
            case "lost" -> RunDetailsPayload.State.LOST;
            default -> RunDetailsPayload.State.ALIVE;
        };
    }

    /**
     * Staff: "Lv 69 (run 40, +29) · gear avg 42.5 max 50 · LB 3/3/-/2/5/- · top hit 6,012 spell:…:northerners_guillotine
     * on boss 1:23 · top sources spell:… 61%, item:… 30% · ". Plain text (no client lang needed).
     */
    static String levelLine(RunRecord record, RunRecord.Player p) {
        StringBuilder out = new StringBuilder();
        RunRecord.Gear gear = p.gear;
        if (gear != null) {
            out.append("Lv ").append(gear.playerLevel < 0 ? "?" : Integer.toString(gear.playerLevel));
            if (gear.playerClass != null && !gear.playerClass.isEmpty()) {
                // "wizards:arcane_spell_book" → "arcane_spell_book"; "skills:rogue" stays as it is.
                String shown = gear.playerClass.startsWith("skills:") ? gear.playerClass
                    : gear.playerClass.substring(gear.playerClass.indexOf(':') + 1);
                out.append(' ').append(shown);
            }
            if (p.loadoutId != null && !p.loadoutId.isEmpty()) {
                out.append(" RENTAL ").append(p.rentalClass == null || p.rentalClass.isEmpty() ? p.loadoutId
                    : p.rentalClass + " L" + p.rentalBand);
            }
            if (record.runLevel >= 0) {
                out.append(" (run ").append(record.runLevel);
                if (gear.playerLevel >= 0) {
                    out.append(String.format(Locale.ROOT, ", %+d", gear.playerLevel - record.runLevel));
                }
                out.append(')');
            }
            out.append(" · gear ").append(gear.powerAvg < 0 ? "none"
                : String.format(Locale.ROOT, "avg %s max %d", trim(gear.powerAvg), gear.powerMax));
            StringBuilder lb = new StringBuilder();
            boolean any = false;
            for (RunRecord.Slot slot : gear.slots) {
                lb.append(lb.isEmpty() ? "" : "/").append(slot.limitBreak < 0 ? "-" : Integer.toString(slot.limitBreak));
                any |= slot.limitBreak >= 0;
            }
            if (any) {
                out.append(" · LB ").append(lb);
            }
            out.append(" · ");
        } else if (record.runLevel >= 0) {
            out.append("run Lv ").append(record.runLevel).append(" · ");
        }
        if (p.bossActiveSeconds > 0) {
            out.append("on boss ").append(p.bossActiveSeconds).append("s, ")
                .append(number(p.bossDealt / p.bossActiveSeconds)).append("/s · ");
        }
        if (p.topHits != null && !p.topHits.isEmpty()) {
            RunRecord.Hit hit = p.topHits.get(0);
            out.append("top hit ").append(number(hit.amount)).append(' ').append(hit.source)
                .append(hit.boss ? " on boss " : " on " + hit.target + " ")
                .append(String.format(Locale.ROOT, "%d:%02d", hit.seconds / 60, hit.seconds % 60)).append(" · ");
        }
        if (p.damageBySource != null && !p.damageBySource.isEmpty() && p.dealt > 0) {
            out.append("top sources ");
            int shown = 0;
            for (Map.Entry<String, Float> source : p.damageBySource.entrySet()) {
                if (shown++ == 3) {
                    break;
                }
                out.append(shown > 1 ? ", " : "").append(source.getKey()).append(' ')
                    .append(Math.round(source.getValue() * 100 / p.dealt)).append('%');
            }
            out.append(" · ");
        }
        return out.toString();
    }

    private static String trim(float value) {
        return value == Math.floor(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static Component staffLine(RunRecord.Player p, boolean duel) {
        if (duel) { // no bosses, no loot (4.42.0)
            return Component.translatable("gui.arenas_ld.run_details.staff_line_duel",
                number(p.overkill), number(p.takenRaw), number(p.takenEnemies), number(p.takenEnvironment),
                number(p.blockedShield), number(p.blockedArmor), number(p.blockedResistance), number(p.blockedEnchant),
                number(p.blockedAbsorption), number(p.healedOthers), number(p.overheal), p.assists,
                String.format(Locale.ROOT, "%d:%02d", p.aliveSeconds / 60, p.aliveSeconds % 60));
        }
        return Component.translatable("gui.arenas_ld.run_details.staff_line",
            number(p.bossDealt), number(p.overkill), number(p.takenRaw), number(p.takenEnemies), number(p.takenEnvironment),
            number(p.blockedShield), number(p.blockedArmor), number(p.blockedResistance), number(p.blockedEnchant),
            number(p.blockedAbsorption), number(p.healedOthers), number(p.overheal), p.assists, p.bossKills,
            String.format(Locale.ROOT, "%d:%02d", p.aliveSeconds / 60, p.aliveSeconds % 60),
            p.loot == null || p.loot.isEmpty() ? "—" : String.join(", ", p.loot));
    }

    private static Component tier(String difficulty) {
        return difficulty.isEmpty() ? Component.empty() : Component.translatable("gui.arenas_ld.tier." + difficulty);
    }

    private static List<Component> modifiers(RunRecord record) {
        List<Component> out = new ArrayList<>();
        for (String id : RunModifierCatalog.withLegacyHardcore(record.modifiers, record.hardcore)) {
            out.add(RunModifierCatalog.displayName(id));
        }
        return out;
    }

    private static Component result(RunRecord record) {
        if ("duel".equals(record.type)) {
            if (record.winnerSide == null || record.winnerSide.isEmpty()) {
                return Component.translatable("gui.arenas_ld.run_details.result.draw");
            }
            List<String> names = new ArrayList<>();
            for (RunRecord.Player p : record.players) {
                if (record.winnerSide.equals(p.side)) {
                    names.add(p.name);
                }
            }
            return Component.translatable("gui.arenas_ld.run_details.result.duel_won", String.join(", ", names));
        }
        return Component.translatable("gui.arenas_ld.run_details.result." + (record.result.isEmpty() ? "fail" : record.result));
    }

    /** Top damage, most blocked, top healer (each only when someone did any). */
    static List<Component> highlights(RunRecord record) {
        List<Component> out = new ArrayList<>();
        highlight(out, record, "damage", p -> p.dealt);
        highlight(out, record, "blocked", p -> p.blockedShield + p.blockedArmor + p.blockedResistance + p.blockedEnchant + p.blockedAbsorption);
        highlight(out, record, "healer", p -> p.healedSelf + p.healedOthers);
        return out;
    }

    private static void highlight(List<Component> out, RunRecord record, String key, Function<RunRecord.Player, Float> value) {
        RunRecord.Player best = null;
        for (RunRecord.Player p : record.players) {
            if (value.apply(p) > 0 && (best == null || value.apply(p) > value.apply(best))) {
                best = p;
            }
        }
        if (best != null) {
            out.add(Component.translatable("gui.arenas_ld.run_details.highlight." + key, best.name, number(value.apply(best))));
        }
    }

    private static List<Component> phases(RunRecord record) {
        List<Component> out = new ArrayList<>();
        for (RunRecord.Phase phase : record.phases) {
            String time = String.format(Locale.ROOT, "%d:%02d", phase.seconds / 60, phase.seconds % 60);
            out.add(phase.percent == 0
                ? Component.translatable("gui.arenas_ld.run_details.phase.killed", phase.boss, time)
                : Component.translatable("gui.arenas_ld.run_details.phase.mark", phase.boss, phase.percent, time));
        }
        return out;
    }

    /** A finished run's base rolls (4.40.1, so the end screen shows them too); empty in records before 4.39.0. */
    private static Component recordRewards(RunRecord record) {
        return record.baseLootRolls < 0 ? Component.empty()
            : Component.translatable("gui.arenas_ld.lobby.base_loot_rolls", RewardMath.number(record.baseLootRolls));
    }

    /** "3 loot rolls · 500 currency · 1,000 XP — Picked: ×2 loot · +25% XP". */
    private static Component rewards(RunDetailsSource source) {
        MutableComponent line = Component.translatable("gui.arenas_ld.run_details.reward_base",
            RewardMath.number(source.lootRolls()),
            String.format("%,d", source.baseCurrency()), String.format("%,d", source.baseXp()));
        Component modifiers = RewardModifiers.lobbyLine(source.activityId(),
            RunModifierCatalog.withLegacyHardcore(source.modifiers(), source.hardcore()));
        if (!modifiers.getString().isEmpty()) {
            line.append(" — ").append(modifiers);
        }
        return line;
    }

    private static String number(float value) {
        return value >= 100 ? String.format(Locale.ROOT, "%,.0f", value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** One decimal: equal numbers compare equal, so an unchanged run isn't resent. */
    private static float round(float value) {
        return Math.round(value * 10) / 10f;
    }
}
