package net.ledok.arenas_ld.stats;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.packet.RunDetailsPayload;
import net.ledok.arenas_ld.platform.ArenasEvents;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Finished runs: one gzip JSON file per run ({@code <world>/arenas_ld/runs/<runId>.json.gz}, written once, off the
 * server thread), an index of them ({@code index.jsonl}, one line per run) for the staff history, kept 30 days and
 * pruned at server start. When a run ends its players get a chat summary and the end screen.
 */
public final class RunRecords {
    public static final int KEEP_DAYS = 30;
    private static final Gson GSON = new Gson();
    private static final int RECENT_MAX = 64;

    /** One line of the index: what the history list shows. Newest last. */
    public static final class Entry {
        public String runId = "";
        public String activityId = "";
        public String activityName = "";
        public String type = "";
        public String difficulty = "";
        public boolean hardcore;
        public List<String> modifiers = new ArrayList<>();
        public long startEpochMs;
        public int durationSeconds;
        public String result = "";
        public List<String> party = new ArrayList<>();
        public List<String> partyUuids = new ArrayList<>();
        /** The run's level, and per player (same order as {@link #party}) their level and average gear power; -1 unknown. */
        public int runLevel = -1;
        public List<Integer> playerLevels = new ArrayList<>();
        public List<Float> gearPower = new ArrayList<>();
        /** Per player: their class (spell book item id, or "skills:<branch>"; "" unknown). */
        public List<String> classes = new ArrayList<>();
        /** Per player: the rental loadout id ("" = own gear). */
        public List<String> loadouts = new ArrayList<>();
    }

    private static final List<Entry> INDEX = new ArrayList<>();
    private static final Map<String, RunRecord> RECENT = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, RunRecord> eldest) {
            return size() > RECENT_MAX;
        }
    };
    private static final Map<UUID, String> LAST = new HashMap<>();

    private RunRecords() {
    }

    public static void register() {
        ArenasEvents.SERVER_STARTED.add(RunRecords::loadIndex);
        ArenasEvents.SERVER_STOPPED.add(server -> {
            INDEX.clear();
            RECENT.clear();
            LAST.clear();
        });
    }

    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("arenas_ld").resolve("runs");
    }

    // ── index ────────────────────────────────────────────────────────────────

    /** Reads the index off the server thread, drops runs older than 30 days (and their files), then keeps it. */
    private static void loadIndex(MinecraftServer server) {
        Path dir = dir(server);
        CompletableFuture.supplyAsync(() -> {
            List<Entry> kept = new ArrayList<>();
            Path index = dir.resolve("index.jsonl");
            if (!Files.exists(index)) {
                return kept;
            }
            long cutoff = System.currentTimeMillis() - KEEP_DAYS * 86_400_000L;
            synchronized (RunRecords.class) {
                try {
                    boolean pruned = false;
                    for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
                        if (line.isBlank()) {
                            continue;
                        }
                        try {
                            Entry entry = GSON.fromJson(line, Entry.class);
                            if (entry.startEpochMs < cutoff) {
                                Files.deleteIfExists(dir.resolve(entry.runId + ".json.gz"));
                                pruned = true;
                            } else {
                                kept.add(entry);
                            }
                        } catch (JsonParseException e) {
                            pruned = true; // a damaged line goes
                        }
                    }
                    if (pruned) {
                        List<String> lines = new ArrayList<>(kept.size());
                        for (Entry entry : kept) {
                            lines.add(GSON.toJson(entry));
                        }
                        Files.write(index, lines, StandardCharsets.UTF_8);
                    }
                } catch (IOException e) {
                    ArenasLdMod.LOGGER.warn("[Arenas] Run stats: couldn't read the run history index {}", index, e);
                }
            }
            return kept;
        }, Util.ioPool()).thenAccept(kept -> server.execute(() -> {
            List<Entry> fresh = new ArrayList<>(INDEX); // runs finished while it loaded
            INDEX.clear();
            INDEX.addAll(kept);
            INDEX.addAll(fresh);
            for (Entry entry : INDEX) {
                for (String uuid : entry.partyUuids) {
                    try {
                        LAST.put(UUID.fromString(uuid), entry.runId);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }));
    }

    /** The finished runs, newest first, that match: one activity (null: any), one player (null: anyone). */
    public static List<Entry> history(@Nullable String activityId, @Nullable UUID player, int limit) {
        List<Entry> out = new ArrayList<>();
        String uuid = player == null ? null : player.toString();
        for (int i = INDEX.size() - 1; i >= 0 && out.size() < Math.max(1, limit); i--) {
            Entry entry = INDEX.get(i);
            if ((activityId == null || activityId.equals(entry.activityId)) && (uuid == null || entry.partyUuids.contains(uuid))) {
                out.add(entry);
            }
        }
        return out;
    }

    public static @Nullable String lastRunOf(UUID player) {
        return LAST.get(player);
    }

    public static boolean known(String runId) {
        if (RECENT.containsKey(runId)) {
            return true;
        }
        for (Entry entry : INDEX) {
            if (entry.runId.equals(runId)) {
                return true;
            }
        }
        return false;
    }

    // ── a run ends ───────────────────────────────────────────────────────────

    /** {@link RunStats#end}: keeps the record, writes its file, and shows it to its players. */
    static void finish(MinecraftServer server, RunStats stats, RunRecord record) {
        String path = record.activityId.isEmpty() ? "run" : record.activityId.substring(record.activityId.indexOf(':') + 1);
        record.runId = record.startEpochMs + "-" + path.replaceAll("[^a-z0-9_.-]", "_");
        Entry entry = new Entry();
        entry.runId = record.runId;
        entry.activityId = record.activityId;
        entry.activityName = record.activityName;
        entry.type = record.type;
        entry.difficulty = record.difficulty;
        entry.hardcore = record.hardcore;
        entry.modifiers = record.modifiers;
        entry.startEpochMs = record.startEpochMs;
        entry.durationSeconds = record.durationSeconds;
        entry.result = record.result;
        entry.runLevel = record.runLevel;
        for (RunRecord.Player p : record.players) {
            entry.party.add(p.name);
            entry.partyUuids.add(p.uuid);
            entry.playerLevels.add(p.gear == null ? -1 : p.gear.playerLevel);
            entry.gearPower.add(p.gear == null ? -1f : p.gear.powerAvg);
            entry.classes.add(p.gear == null || p.gear.playerClass == null ? "" : p.gear.playerClass);
            entry.loadouts.add(p.loadoutId == null ? "" : p.loadoutId);
            LAST.put(UUID.fromString(p.uuid), record.runId);
        }
        INDEX.add(entry);
        RECENT.put(record.runId, record);

        Path dir = dir(server);
        String json = GSON.toJson(record);
        String line = GSON.toJson(entry);
        CompletableFuture.runAsync(() -> {
            synchronized (RunRecords.class) {
                try {
                    Files.createDirectories(dir);
                    try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(
                        Files.newOutputStream(dir.resolve(record.runId + ".json.gz"))), StandardCharsets.UTF_8)) {
                        writer.write(json);
                    }
                    try (BufferedWriter writer = Files.newBufferedWriter(dir.resolve("index.jsonl"), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                        writer.write(line);
                        writer.newLine();
                    }
                } catch (IOException e) {
                    ArenasLdMod.LOGGER.warn("[Arenas] Run stats: couldn't write the run file {}", record.runId, e);
                }
            }
        }, Util.ioPool());

        // The chat line only; its [Details] opens the screen (LeDok 4.36.1: no auto-opened end screen).
        for (RunRecord.Player p : record.players) {
            ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(p.uuid));
            if (player != null) {
                player.sendSystemMessage(summary(record));
            }
        }
    }

    /** "Run over — Win in 12:34 · Top damage: Alice (1,234) · Top healer: Bob (567) [Details]". */
    private static Component summary(RunRecord record) {
        MutableComponent line = Component.translatable("message.arenas_ld.run_stats.summary",
            Component.translatable("gui.arenas_ld.run_details.result." + (record.result.isEmpty() ? "fail" : record.result)),
            String.format(Locale.ROOT, "%d:%02d", record.durationSeconds / 60, record.durationSeconds % 60))
            .withStyle(ChatFormatting.GOLD);
        for (Component highlight : RunDetails.highlights(record)) {
            line.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY)).append(highlight.copy().withStyle(ChatFormatting.YELLOW));
        }
        line.append(" ").append(Component.translatable("message.arenas_ld.run_stats.details").withStyle(style -> style
            .withColor(ChatFormatting.AQUA).withUnderlined(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/arenasld run last"))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.arenas_ld.run_stats.details.hover")))));
        return line;
    }

    // ── opening a finished run ───────────────────────────────────────────────

    /**
     * Shows a finished run's details to {@code viewer} (staff: with the staff details). Read from memory when it is
     * recent, else from its file off the server thread. False when the id is unknown.
     */
    public static boolean open(ServerPlayer viewer, String runId, boolean staff) {
        RunRecord recent = RECENT.get(runId);
        if (recent != null) {
            RunDetails.viewingRecord(viewer, recent);
            ArenasNetwork.sendToPlayer(viewer, RunDetails.fromRecord(recent, staff, false, viewer.server.registryAccess()).opening(true));
            return true;
        }
        if (!known(runId) || !runId.matches("[0-9]+-[a-z0-9_.-]+")) {
            return false;
        }
        MinecraftServer server = viewer.server;
        Path file = dir(server).resolve(runId + ".json.gz");
        UUID who = viewer.getUUID();
        CompletableFuture.supplyAsync(() -> {
            try (Reader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)),
                StandardCharsets.UTF_8))) {
                return GSON.fromJson(reader, RunRecord.class);
            } catch (IOException | JsonParseException e) {
                ArenasLdMod.LOGGER.warn("[Arenas] Run stats: couldn't read the run file {}", file, e);
                return null;
            }
        }, Util.ioPool()).thenAccept(record -> server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(who);
            if (player == null) {
                return;
            }
            if (record == null) {
                player.sendSystemMessage(Component.translatable("message.arenas_ld.run_stats.unreadable", runId)
                    .withStyle(ChatFormatting.RED));
                return;
            }
            RECENT.put(runId, record);
            RunDetails.viewingRecord(player, record);
            ArenasNetwork.sendToPlayer(player, RunDetails.fromRecord(record, staff, false, player.server.registryAccess()).opening(true));
        }));
        return true;
    }
}
