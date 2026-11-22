package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.activity.InstanceKey;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.DungeonRun;
import net.ledok.arenas_ld.packet.RunBreakableBlocksPayload;
import net.ledok.arenas_ld.platform.ArenasEvents;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.raid.run.RaidRun;
import net.ledok.arenas_ld.raid.run.RaidRunLifecycle;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Lets dungeon and raid players (Adventure mode) break the blocks their run allows, e.g. a boss's vine walls.
 * <ul>
 *   <li><b>What a run allows</b>: nothing unless its difficulty has {@code allowBlockBreaking}; then the global
 *       {@code run_breakable_blocks} plus the controller's own list, or only the controller's list in "replace"
 *       mode ({@link BreakableBlocks}).</li>
 *   <li><b>Client</b>: in Adventure the client won't even start breaking, so every {@value #SYNC_TICKS} ticks
 *       each player whose list changed (joined, left, run ended) gets it ({@link RunBreakableBlocksPayload}).</li>
 *   <li><b>Server</b>: checks the run again on the break itself and removes the block with no drops and no
 *       XP ({@link #breakInRun}). Nothing is restored afterwards.</li>
 * </ul>
 */
public final class RunBlockBreaking {
    private static final int SYNC_TICKS = 20;
    /** The action-bar line at most this often. */
    private static final long MESSAGE_COOLDOWN_MS = 3000;
    /** Only after the player keeps attacking the same block this long (a real break attempt, not a missed swing). */
    private static final long ATTEMPT_MS = 1000;
    /** Attempts this far apart are separate ones (holding attack repeats an attempt every tick). */
    private static final long ATTEMPT_GAP_MS = 250;

    /** What each online run player was last sent; a player not in a run has no entry. */
    private static Map<UUID, List<String>> sent = new HashMap<>();
    private static final Map<List<String>, Rule> COMPILED = new HashMap<>();

    /** The local player's run list (client side): null = not in a dungeon or raid run. */
    private static volatile @Nullable Rule clientRule;
    private static long lastMessageAt;
    private static long attemptStartedAt;
    private static long lastAttemptAt;
    private static @Nullable BlockPos attemptPos;

    private RunBlockBreaking() {
    }

    public static void register() {
        ArenasEvents.SERVER_TICK_END.add(RunBlockBreaking::sync);
        ArenasEvents.SERVER_STOPPED.add(server -> {
            sent = new HashMap<>();
            COMPILED.clear();
        });
    }

    // ── hooks ────────────────────────────────────────────────────────────────

    /**
     * {@code Player.blockActionRestricted} said no: true if it's an Adventure run player breaking a block their
     * run allows. On the client a refused block in a run shows the action-bar line.
     */
    public static boolean mayBreak(Player player, Level level, BlockPos pos, GameType gameType) {
        if (gameType != GameType.ADVENTURE) {
            return false;
        }
        Rule rule;
        if (level.isClientSide) {
            rule = clientRule;
        } else if (player instanceof ServerPlayer serverPlayer) {
            List<String> entries = entriesFor(serverPlayer);
            rule = entries == null ? null : compiled(entries);
        } else {
            return false;
        }
        if (rule == null) {
            return false;
        }
        if (rule.allows(level.getBlockState(pos))) {
            return true;
        }
        if (level.isClientSide) {
            // The client asks on every attack swing that hits a block (holding attack asks every tick), fights
            // included: tell the player only when they keep at the same block, and not too often.
            long now = System.currentTimeMillis();
            if (!pos.equals(attemptPos) || now - lastAttemptAt > ATTEMPT_GAP_MS) {
                attemptPos = pos.immutable();
                attemptStartedAt = now;
            }
            lastAttemptAt = now;
            if (now - attemptStartedAt >= ATTEMPT_MS && now - lastMessageAt > MESSAGE_COOLDOWN_MS) {
                lastMessageAt = now;
                player.displayClientMessage(Component.translatable("message.arenas_ld.run.block_unbreakable")
                    .withStyle(ChatFormatting.RED), true);
            }
        }
        return false;
    }

    /** {@code ServerPlayerGameMode.destroyBlock}: an allowed run break, without drops or XP. False = not one. */
    public static boolean breakInRun(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!mayBreak(player, level, pos, player.gameMode.getGameModeForPlayer())) {
            return false;
        }
        level.destroyBlock(pos, false, player);
        return true;
    }

    // ── client ───────────────────────────────────────────────────────────────

    public static void onClientPayload(RunBreakableBlocksPayload payload) {
        clientRule = payload.inRun() ? Rule.compile(payload.entries()) : null;
    }

    public static void clearClient() {
        clientRule = null;
    }

    // ── server ───────────────────────────────────────────────────────────────

    /** A known block id, or a {@code #tag} with a valid id (tags may be filled by a datapack later). */
    public static boolean isValidEntry(String entry) {
        String trimmed = entry == null ? "" : entry.trim();
        if (trimmed.startsWith("#")) {
            return ResourceLocation.tryParse(trimmed.substring(1)) != null;
        }
        ResourceLocation id = ResourceLocation.tryParse(trimmed);
        return id != null && BuiltInRegistries.BLOCK.containsKey(id);
    }

    /** The block entries the player's dungeon or raid run allows; null if they aren't in one. */
    public static @Nullable List<String> entriesFor(ServerPlayer player) {
        MinecraftServer server = player.server;
        DungeonRun dungeonRun = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(player.getUUID());
        if (dungeonRun != null) {
            DungeonControllerBlockEntity controller = ArenasLdMod.DUNGEON_MANAGER.findControllerForRun(server, dungeonRun);
            return entries(dungeonRun.resolvedTierConfig().allowBlockBreaking(), controller);
        }
        RaidBossSpawnerBlockEntity spawner = ArenasLdMod.RAID_BOSS_MANAGER.getSpawnerForPlayer(player);
        if (spawner != null && spawner.getLevel() instanceof ServerLevel spawnerLevel) {
            InstanceKey key = new InstanceKey(spawnerLevel.dimension(), spawner.getBlockPos());
            RaidControllerBlockEntity controller = RaidRunLifecycle.findOwningController(server, key);
            RaidRun raidRun = controller == null ? null : controller.getActiveRuns().get(key);
            return raidRun == null ? List.of() : entries(raidRun.resolvedTierConfig().allowBlockBreaking(), controller);
        }
        if (!RunLeftovers.isInRun(player.getUUID())) return null;
        for (var key : ArenaControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null || !level.isLoaded(key.pos())
                || !(level.getBlockEntity(key.pos()) instanceof ArenaControllerBlockEntity controller)) continue;
            for (var run : controller.getActiveRuns().values()) {
                var member = run.participants().get(player.getUUID());
                if (member == null || member.status() == RunParticipant.ParticipantStatus.REMOVED || run.survivalMode()) continue;
                ServerLevel arena = server.getLevel(run.spawnerDimension());
                boolean allowed = arena == player.level() && arena.isLoaded(run.spawnerPos())
                    && arena.getBlockEntity(run.spawnerPos()) instanceof ArenaSpawnerBlockEntity arenaSpawner
                    && arenaSpawner.allowsBlockBreaking();
                return entries(allowed, controller);
            }
        }
        return null;
    }

    private static List<String> entries(boolean allowed, @Nullable BreakableBlocks.Holder controller) {
        if (!allowed) {
            return List.of();
        }
        List<String> global = ArenasLdConfig.getInstance().run_breakable_blocks;
        return controller == null ? List.copyOf(global) : controller.getBreakableBlocks().effective(global);
    }

    private static void sync(MinecraftServer server) {
        if (server.getTickCount() % SYNC_TICKS != 0) {
            return;
        }
        Map<UUID, List<String>> now = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<String> entries = entriesFor(player);
            List<String> before = sent.get(player.getUUID());
            if (entries != null) {
                now.put(player.getUUID(), entries);
            }
            if (!Objects.equals(before, entries)) {
                ArenasNetwork.sendToPlayer(player, new RunBreakableBlocksPayload(entries != null,
                    entries == null ? List.of() : entries));
            }
        }
        sent = now;
    }

    private static Rule compiled(List<String> entries) {
        if (COMPILED.size() > 64) {
            COMPILED.clear(); // a few lists in use at a time; this only bounds a config being edited a lot
        }
        return COMPILED.computeIfAbsent(entries, Rule::compile);
    }

    /** A list of block ids and {@code #tags}, resolved. */
    private record Rule(Set<Block> blocks, List<TagKey<Block>> tags) {
        static Rule compile(List<String> entries) {
            Set<Block> blocks = new HashSet<>();
            List<TagKey<Block>> tags = new ArrayList<>();
            for (String raw : entries) {
                String entry = raw.trim();
                if (entry.startsWith("#")) {
                    ResourceLocation id = ResourceLocation.tryParse(entry.substring(1));
                    if (id != null) {
                        tags.add(TagKey.create(Registries.BLOCK, id));
                    }
                } else {
                    ResourceLocation id = ResourceLocation.tryParse(entry);
                    if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                        blocks.add(BuiltInRegistries.BLOCK.get(id));
                    }
                }
            }
            return new Rule(blocks, tags);
        }

        boolean allows(BlockState state) {
            if (blocks.contains(state.getBlock())) {
                return true;
            }
            for (TagKey<Block> tag : tags) {
                if (state.is(tag)) {
                    return true;
                }
            }
            return false;
        }
    }
}
