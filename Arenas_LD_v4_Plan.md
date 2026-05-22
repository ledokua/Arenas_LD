# Arenas_LD v4.0 — Dungeon System Rewrite Plan

**Branch:** `4.0`
**Target version:** `4.0.0`
**Minecraft / Loader:** 1.21.1 / Fabric (unchanged from 3.3.0)
**Compatibility:** no migration from 3.3.0 worlds — old dungeon blocks/classes will be removed entirely at the end of Phase H.

---

## Architecture summary (the contract)

The dungeon system is being rebuilt around one principle: **the controller is the brain, spawners are workers.**

### The blocks

| Block | Role |
|---|---|
| `DungeonController` | Owns the run. Holds tier configs, lobbies, leaderboard, cooldown, party. Drives the run state machine each tick. |
| `DungeonBossSpawner` (DBS) | A worker. Holds the boss entity definition (type + attributes + equipment), the dungeon's entrance position, and an ordered list of `RoomController` references. The DBS itself acts as the spawner of the *final* (boss) room. |
| `RoomController` (NEW) | A worker. Owns a list of mob spawner positions (or one DBS position, for the boss room) and an optional door (phase block position). Has `activate(tier)`, `reset()`, `openDoor()`, `isCleared()`. |
| `MobSpawner` | A worker. Holds one entity definition (type + attributes + equipment). Stripped of loot, cooldown, trigger zones, wave counts. One mob per spawner. |
| `PhaseBlock` | A door, owned by exactly one `RoomController`. Toggled by its room on clear. |

### Connection topology

```
DungeonController
  └─ instances: List<DungeonBossSpawner position>
        └─ DBS
            ├─ entrancePos + dimension
            └─ rooms: ordered List<RoomController position>
                  └─ RoomController
                      ├─ spawners: List<MobSpawner pos>  (or [DBS pos] for boss room)
                      └─ door: Optional<PhaseBlock pos>
```

The boss room is just `rooms[last]` and its `spawners` list contains the DBS itself.

### The run loop

```
controller.startRun(instance, party, tier, hardcore):
  resolve TierConfig
  capture PlayerReturnPoint per player
  load chunks: DBS + every room + every spawner + every door
  for each room: room.reset()
  teleport party to DBS.entrancePos
  create DungeonRun, store in active runs
  phase = RUNNING, currentRoomIndex = 0
  rooms[0].activate(tier)

each server tick (per active run):
  decrement dungeonTimerTicks → if expired → loss(TIMEOUT)
  if run.players empty → loss(ABANDONED)
  currentRoom.refreshAliveMobs()
  if currentRoom.isCleared():
    currentRoom.openDoor()
    if last room → handleWin()
    else: currentRoomIndex++, rooms[currentRoomIndex].activate(tier)
  tick downed players
  update boss bars

handleWin:
  upsert leaderboard
  give per-player loot bundles (tier.perPlayerLootTable)
  phase = CLOSING, closeTimerTicks = controller.closeTimer
  on close: teleport each player to their PlayerReturnPoint, finalize

handleLoss(reason):
  despawn boss if alive, teleport players to return points, finalize

finalize:
  remove run, unload forced chunks, instance enters cooldown
```

### The tier model

`TierConfig` (one per tier, owned by the controller):
- `healthMultiplier` — applied to every spawn's max_health at spawn time
- `damageMultiplier` — applied to incoming player damage via the existing `LivingEntityMixin`
- `perPlayerLootTable` — rolled per player at win
- `dungeonTimeSeconds` — total time for the run at this tier
- `hardcoreDefault` — whether hardcore is on by default at this tier

Damage source filter: `dungeon_damage_source_filter` stays in the global config, **defaulting to `ALL`** (Hard mode scales fall/lava/drowning damage too — high-HP players make this fair).

### What goes away from 3.3.0

The following are removed entirely and **must not appear in any v4.0 code**:

- `triggerRadius` / `battleRadius` on the DBS
- `exitPositionCoords` / `exitPositionDimension` on the DBS (replaced by per-player `PlayerReturnPoint` on the run)
- Loot tables on the DBS (the world-drop `lootTableId`) — only per-player loot remains
- `respawnTime` / `respawnCooldown` on spawners (the controller owns cooldowns)
- `linkedSpawners` on the DBS (replaced by `rooms` list)
- Per-tier `lootTableIdOverride` on the DBS (loot is controller-owned)
- `groupId` on the DBS (lobby/party teaming lives on the controller)
- `skillExperiencePerWin` on the DBS (moves to controller; one value per dungeon)
- `regeneration` on the DBS (boss self-heal — drop for now; can be re-added later as a per-DBS attribute)

---

## Working agreement

- **Branch**: all work on `4.0`. `master` stays at 3.3.0 untouched.
- **PRs**: one PR per phase. Each phase leaves the branch in a buildable state.
- **Task IDs**: `P{letter}-{n}` (e.g. `PA-1`, `PB-3`). When asking for review, say "review PB-3" and tell me the commit SHA.
- **Review loop**: I write task spec → you feed Codex → Codex implements + commits to `4.0` → you say "PB-3 done, latest commit" → I fetch the branch and review against the task's acceptance criteria.
- **Old code coexistence**: 3.3.0 dungeon code stays in-place through Phases A–G alongside the new code. Phase H deletes it. The build must compile throughout.

### Task spec format

Every task in this doc has:

1. **Goal** — one sentence, what it produces.
2. **Files** — files to create or modify.
3. **Signatures** — Java class/method signatures with Javadoc-style comments explaining what each does. Codex implements method bodies against these.
4. **NBT schema** — if the class persists.
5. **Acceptance criteria** — concrete, testable assertions.
6. **Don'ts** — things Codex tends to over-engineer; explicit constraints.
7. **References** — existing files Codex should read for context, with line numbers when helpful.

---

## Phase A — Foundations (1 PR)

Goal of the phase: establish the new package layout, CI, and test harness. **No gameplay changes.** The build passes; the old dungeon system still works exactly as before.

### PA-1: New package layout

**Goal**: create the directory structure for the new dungeon code. Empty packages with `package-info.java` files describing intent.

**Files to create:**
```
src/main/java/net/ledok/arenas_ld/dungeon/package-info.java
src/main/java/net/ledok/arenas_ld/dungeon/block/package-info.java
src/main/java/net/ledok/arenas_ld/dungeon/blockentity/package-info.java
src/main/java/net/ledok/arenas_ld/dungeon/run/package-info.java
src/main/java/net/ledok/arenas_ld/dungeon/room/package-info.java
src/main/java/net/ledok/arenas_ld/dungeon/lobby/package-info.java
src/main/java/net/ledok/arenas_ld/dungeon/screen/package-info.java
src/main/java/net/ledok/arenas_ld/dungeon/packet/package-info.java
src/main/java/net/ledok/arenas_ld/dungeon/command/package-info.java
src/main/java/net/ledok/arenas_ld/dungeon/manager/package-info.java
```

Each `package-info.java` contains a one-paragraph Javadoc explaining what lives there.

**Acceptance:**
- All 10 files exist with Javadoc.
- `./gradlew build` passes.

**Don'ts:** don't move any existing files yet. Don't add classes beyond `package-info.java`. Don't touch other packages.

---

### PA-2: Test harness setup

**Goal**: enable JUnit 5 for unit tests and Fabric Gametest for in-world tests.

**Files to modify:**
- `build.gradle` — add JUnit 5 and `fabric-gametest-api-v1` dependencies, configure `test` task.

**Files to create:**
- `src/test/java/net/ledok/arenas_ld/SanityTest.java` — single test asserting `2 + 2 == 4`. Proves the test runner works.
- `src/main/java/net/ledok/arenas_ld/gametest/ArenasLdGametests.java` — empty class annotated as a gametest registry, no tests yet.
- `src/main/resources/fabric-gametest.json` if needed by the Fabric gametest API.

**Acceptance:**
- `./gradlew test` runs `SanityTest` and passes.
- `./gradlew runGametest` (if available via fabric-gametest) launches without errors.

**Don'ts:** don't write actual dungeon tests yet. Don't add Mockito or any other test library beyond JUnit 5 + fabric-gametest. Don't add testFixtures.

**References:**
- Existing `build.gradle` for dependency style.
- Fabric API docs for `fabric-gametest-api-v1`.

---

### PA-3: GitHub Actions CI

**Goal**: every PR and push runs `./gradlew build test`.

**Files to create:**
- `.github/workflows/build.yml`

**Spec:**
- Triggers: `push` (any branch), `pull_request`.
- Java 21, Temurin distribution.
- Cache Gradle.
- Runs `./gradlew build test --no-daemon`.

**Acceptance:**
- The workflow runs on the next push.
- Sanity test from PA-2 passes in CI.

**Don'ts:** don't add deploy/release steps. Don't add Codecov, SonarCloud, or anything beyond build+test. No matrix builds.

---

### PA-4: Fix the startup log message

**Goal**: trivial cleanup, build the habit of small reviewed PRs early.

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/ArenasLdMod.java` line 28.

**Spec:** change `"Yggdrasil LD has been initialized!"` to `"Arenas_LD has been initialized!"`.

**Acceptance:** log message changed. Build passes.

**Don'ts:** don't change anything else in `ArenasLdMod`.

---

## Phase B — Core domain classes (1 PR)

Goal of the phase: build the data classes the new system needs. These are pure logic, fully unit-tested, with zero Minecraft world dependencies where possible. No blocks yet.

These classes use Mojang `Codec` for serialization (NBT and network), not hand-written `toNbt`/`fromNbt`. The old code's pattern of hand-written NBT is deliberately abandoned here.

### PB-1: `DifficultyTier` enum (new, in dungeon package)

**Goal**: re-create the tier enum in the new package without the legacy `defaultHealthMultiplier()` / `defaultDamageMultiplier()` methods. Defaults now live in `TierConfig`.

**File:** `src/main/java/net/ledok/arenas_ld/dungeon/run/DifficultyTier.java`

**Spec:**
```java
/**
 * Difficulty tiers for a dungeon run. Each tier's effects (HP/damage multipliers,
 * loot, time) are defined in TierConfig on the controller, not on the enum itself.
 */
public enum DifficultyTier {
    NORMAL,
    HARD,
    HELL;

    public static final Codec<DifficultyTier> CODEC = Codec.STRING.xmap(
        s -> {
            try { return DifficultyTier.valueOf(s); }
            catch (IllegalArgumentException e) { return NORMAL; }
        },
        Enum::name
    );

    public static final StreamCodec<ByteBuf, DifficultyTier> STREAM_CODEC =
        ByteBufCodecs.STRING_UTF8.map(
            s -> { try { return valueOf(s); } catch (Exception e) { return NORMAL; } },
            Enum::name
        );
}
```

**Acceptance:**
- Three values exist.
- `CODEC` and `STREAM_CODEC` round-trip cleanly.
- Unknown string deserializes to `NORMAL` rather than throwing.

**Don'ts:** don't import or reference the old `net.ledok.arenas_ld.util.DifficultyTier`. Don't add `defaultHealthMultiplier()` — tiers don't carry defaults anymore.

**Unit tests** (`src/test/java/...`):
- Serialize each value, deserialize, assert equality.
- Serialize "garbage" string, assert returns NORMAL.

---

### PB-2: `TierConfig` record

**Goal**: per-tier configuration owned by the controller. Replaces the legacy `util.TierConfig`.

**File:** `src/main/java/net/ledok/arenas_ld/dungeon/run/TierConfig.java`

**Spec:**
```java
/**
 * Per-tier dungeon configuration. Owned by DungeonController, applied at run start.
 *
 * @param healthMultiplier scales max_health of every spawned mob (including boss). Must be > 0.
 * @param damageMultiplier scales incoming damage to players in this run. Must be > 0.
 * @param perPlayerLootTable loot table ID rolled per player on win. Empty string = no loot.
 * @param dungeonTimeSeconds total time for the run at this tier. Must be > 0.
 * @param hardcoreDefault whether hardcore is enabled by default at this tier.
 */
public record TierConfig(
    double healthMultiplier,
    double damageMultiplier,
    String perPlayerLootTable,
    int dungeonTimeSeconds,
    boolean hardcoreDefault
) {
    public static final TierConfig NORMAL_DEFAULT = new TierConfig(1.0, 1.0, "", 600, false);
    public static final TierConfig HARD_DEFAULT   = new TierConfig(1.5, 1.5, "", 600, false);
    public static final TierConfig HELL_DEFAULT   = new TierConfig(2.5, 2.5, "", 600, true);

    public static final Codec<TierConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.DOUBLE.fieldOf("healthMultiplier").forGetter(TierConfig::healthMultiplier),
        Codec.DOUBLE.fieldOf("damageMultiplier").forGetter(TierConfig::damageMultiplier),
        Codec.STRING.fieldOf("perPlayerLootTable").forGetter(TierConfig::perPlayerLootTable),
        Codec.INT.fieldOf("dungeonTimeSeconds").forGetter(TierConfig::dungeonTimeSeconds),
        Codec.BOOL.fieldOf("hardcoreDefault").forGetter(TierConfig::hardcoreDefault)
    ).apply(i, TierConfig::new));

    public static TierConfig defaultFor(DifficultyTier tier) {
        return switch (tier) {
            case NORMAL -> NORMAL_DEFAULT;
            case HARD -> HARD_DEFAULT;
            case HELL -> HELL_DEFAULT;
        };
    }
}
```

**Acceptance:**
- Codec round-trips cleanly.
- `defaultFor(tier)` returns the matching constant.

**Don'ts:** don't add validation in the constructor — defer to admin GUI to clamp invalid values. Don't add Optional fields; empty string is fine for "no loot." Don't add a builder.

**Unit tests:**
- Codec round-trip for a non-default value.
- `defaultFor` returns expected constants.

---

### PB-3: `PlayerReturnPoint` record

**Goal**: where the controller teleports a player back to when the run ends.

**File:** `src/main/java/net/ledok/arenas_ld/dungeon/run/PlayerReturnPoint.java`

**Spec:**
```java
/**
 * Captured at run start. Where to send a player when the run ends (win, loss, or quit).
 * Uses Vec3 (not BlockPos) to preserve sub-block position; preserves yaw/pitch.
 */
public record PlayerReturnPoint(
    ResourceKey<Level> dimension,
    Vec3 pos,
    float yaw,
    float pitch
) {
    public static final Codec<PlayerReturnPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
        ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(PlayerReturnPoint::dimension),
        Vec3.CODEC.fieldOf("pos").forGetter(PlayerReturnPoint::pos),
        Codec.FLOAT.fieldOf("yaw").forGetter(PlayerReturnPoint::yaw),
        Codec.FLOAT.fieldOf("pitch").forGetter(PlayerReturnPoint::pitch)
    ).apply(i, PlayerReturnPoint::new));

    /** Capture a player's current position+rotation+dimension. */
    public static PlayerReturnPoint capture(ServerPlayer player) {
        return new PlayerReturnPoint(
            player.level().dimension(),
            player.position(),
            player.getYRot(),
            player.getXRot()
        );
    }
}
```

**Acceptance:**
- Codec round-trips.
- `capture(player)` returns a record with the player's exact position/rotation/dimension.

**Don'ts:** don't add a `teleport(player)` method — that belongs on the consumer (the run), not the data record. Don't include velocity. Don't include game mode or other state.

**Unit tests:**
- Codec round-trip with arbitrary values.

---

### PB-4: `DungeonPhase` enum

**Goal**: the explicit run state machine that replaces the old three-boolean mess.

**File:** `src/main/java/net/ledok/arenas_ld/dungeon/run/DungeonPhase.java`

**Spec:**
```java
/**
 * Explicit phase of a dungeon run. Owned by DungeonRun. Transitions are unidirectional:
 *   STARTING -> RUNNING -> CLOSING -> DONE
 * with DONE terminal. Loss paths jump straight to CLOSING (no separate FAILED state —
 * the win/loss outcome is stored separately).
 */
public enum DungeonPhase {
    /** Setup: chunks loading, rooms resetting, players being teleported in. */
    STARTING,
    /** Run in progress: rooms activating in order, dungeon timer ticking. */
    RUNNING,
    /** Run ended (win or loss): close timer counting down, players still in dungeon. */
    CLOSING,
    /** Run fully finalized: players teleported out, cooldown started. Terminal. */
    DONE;

    public static final Codec<DungeonPhase> CODEC = Codec.STRING.xmap(
        s -> { try { return valueOf(s); } catch (Exception e) { return DONE; } },
        Enum::name
    );
}
```

**Acceptance:** four values, codec round-trips, unknown deserializes to DONE.

**Don'ts:** no `next()` method — transitions live in `DungeonRun`. No "FAILED" phase — outcome is a separate field.

---

### PB-5: `DungeonOutcome` enum

**Goal**: did the run end in a win or a loss, and if loss, why?

**File:** `src/main/java/net/ledok/arenas_ld/dungeon/run/DungeonOutcome.java`

**Spec:**
```java
public enum DungeonOutcome {
    IN_PROGRESS,    // run hasn't ended yet
    WIN,
    LOSS_TIMEOUT,
    LOSS_ABANDONED, // all players left or disconnected
    LOSS_FORCED;    // admin /arenasld debug endDungeon

    public static final Codec<DungeonOutcome> CODEC = Codec.STRING.xmap(
        s -> { try { return valueOf(s); } catch (Exception e) { return IN_PROGRESS; } },
        Enum::name
    );

    public boolean isLoss() {
        return this == LOSS_TIMEOUT || this == LOSS_ABANDONED || this == LOSS_FORCED;
    }
}
```

**Acceptance:** five values, codec round-trips, `isLoss()` correct for each.

**Don'ts:** don't add a "loss reason" string field — the enum value is the reason. Don't combine WIN with a "perfect/imperfect" qualifier — not in scope.

---

### PB-6: `DownedPlayer` record

**Goal**: per-player downed state. Tracks how long until auto-respawn at entrance.

**File:** `src/main/java/net/ledok/arenas_ld/dungeon/run/DownedPlayer.java`

**Spec:**
```java
/**
 * State of a downed (but not eliminated) player. The player is in spectator with reduced HP
 * until ticksRemaining hits 0, at which point they respawn at the dungeon entrance.
 */
public record DownedPlayer(UUID playerUuid, int ticksRemaining) {

    public static final Codec<DownedPlayer> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("uuid").forGetter(DownedPlayer::playerUuid),
        Codec.INT.fieldOf("ticksRemaining").forGetter(DownedPlayer::ticksRemaining)
    ).apply(i, DownedPlayer::new));

    public DownedPlayer tick() {
        return new DownedPlayer(playerUuid, Math.max(0, ticksRemaining - 1));
    }

    public boolean isReadyToRespawn() {
        return ticksRemaining <= 0;
    }
}
```

**Acceptance:** codec round-trips; `tick()` decrements; `isReadyToRespawn()` true at 0 or below.

**Don'ts:** don't store position — the player will be teleported to the *room's spawn* (entrance) on respawn, not to wherever they died. Don't store dimension — same reason.

---

### PB-7: `RunParticipant` record + `ParticipantStatus` enum

**Goal**: model a player's status within a run. Determines loot eligibility at win.

**File:** `src/main/java/net/ledok/arenas_ld/dungeon/run/RunParticipant.java`

**Spec:**
```java
public enum ParticipantStatus {
    ACTIVE,        // alive, in the dungeon, surviving
    DOWNED,        // in spectator, will respawn
    DISCONNECTED,  // logged out, may rejoin within grace period
    REMOVED;       // hardcore death or explicit leave — no loot, won't rejoin

    public static final Codec<ParticipantStatus> CODEC = Codec.STRING.xmap(
        s -> { try { return valueOf(s); } catch (Exception e) { return REMOVED; } },
        Enum::name
    );
}

/**
 * A player's participation in a run. Loot eligibility = status != REMOVED at win.
 */
public record RunParticipant(
    UUID playerUuid,
    String playerName,         // cached for leaderboards, in case player is offline at win
    ParticipantStatus status,
    long lastSeenTick          // for disconnect grace period
) {
    public static final Codec<RunParticipant> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("uuid").forGetter(RunParticipant::playerUuid),
        Codec.STRING.fieldOf("name").forGetter(RunParticipant::playerName),
        ParticipantStatus.CODEC.fieldOf("status").forGetter(RunParticipant::status),
        Codec.LONG.fieldOf("lastSeenTick").forGetter(RunParticipant::lastSeenTick)
    ).apply(i, RunParticipant::new));

    public RunParticipant withStatus(ParticipantStatus newStatus, long tick) {
        return new RunParticipant(playerUuid, playerName, newStatus, tick);
    }

    public boolean isEligibleForLoot() {
        return status != ParticipantStatus.REMOVED;
    }
}
```

**Acceptance:**
- Codec round-trips both types.
- `withStatus` returns a new record (immutable).
- `isEligibleForLoot()` returns false only for REMOVED.

**Don'ts:** don't track inventory snapshot, score, damage-dealt, etc. — out of scope for v4.0 core. Don't make `playerName` mutable.

---

### PB-8: `LeaderboardEntry` record (new version)

**Goal**: replace `util.DungeonLeaderboardEntry` with a codec-serialized version in the new package.

**File:** `src/main/java/net/ledok/arenas_ld/dungeon/run/LeaderboardEntry.java`

**Spec:**
```java
public record LeaderboardEntry(
    String playerName,
    int timeSeconds,
    long recordedAtEpochMillis
) {
    public static final Codec<LeaderboardEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("name").forGetter(LeaderboardEntry::playerName),
        Codec.INT.fieldOf("timeSeconds").forGetter(LeaderboardEntry::timeSeconds),
        Codec.LONG.fieldOf("recordedAt").forGetter(LeaderboardEntry::recordedAtEpochMillis)
    ).apply(i, LeaderboardEntry::new));
}
```

**Acceptance:** codec round-trip; record immutable.

**Don'ts:** don't sort or rank inside the record — that's the consumer's job. Don't store partyId or other contextual data — keep it minimal.

---

### PB-9: Unit tests for codec round-trips

**Goal**: one consolidated test class proving all PB-* codecs work.

**File:** `src/test/java/net/ledok/arenas_ld/dungeon/run/CodecRoundTripTests.java`

**Spec:** one test method per codec, each:
- Constructs an instance with non-default values.
- Encodes to a `Tag` (via `NbtOps.INSTANCE`).
- Decodes.
- Asserts equality with the original.

**Acceptance:** all codec round-trip tests pass; total test count ≥ 8 (one per PB-1..8).

**Don'ts:** don't add property-based testing libraries. Don't test `StreamCodec` here (separate concern, smaller risk). Don't test edge cases like nulls — codecs reject nulls and that's correct.

---

### PB-10: `DungeonRun` class (skeleton only)

**Goal**: the centerpiece. Owns all run-level state. **This task implements the data class + state field accessors only.** Lifecycle methods (`tick`, `handleWin`, `handleLoss`) come in Phase E after the controller exists.

**File:** `src/main/java/net/ledok/arenas_ld/dungeon/run/DungeonRun.java`

**Spec:**
```java
/**
 * The runtime state of an active (or just-ended) dungeon run. Owned by DungeonController.
 *
 * This class deliberately does NOT have tick/handleWin/handleLoss methods yet — those
 * are implemented in Phase E (PE-*) where the controller exists to invoke them.
 *
 * Mutable; persisted via NBT so runs survive server restarts.
 */
public final class DungeonRun {
    private DungeonPhase phase;
    private DungeonOutcome outcome;
    private final DifficultyTier tier;
    private final TierConfig resolvedTierConfig;   // snapshotted at run start; admin edits during run don't affect this run
    private final boolean hardcoreEnabled;
    private final BlockPos dbsPos;                 // instance ref (DBS position)
    private final ResourceKey<Level> dbsDimension;
    private int currentRoomIndex;
    private int dungeonTimerTicks;
    private int closeTimerTicks;
    private final long startTick;
    private final Map<UUID, RunParticipant> participants;       // keyed by uuid
    private final Map<UUID, PlayerReturnPoint> returnPoints;
    private final Map<UUID, DownedPlayer> downedPlayers;

    public DungeonRun(
        DifficultyTier tier,
        TierConfig resolvedTierConfig,
        boolean hardcoreEnabled,
        BlockPos dbsPos,
        ResourceKey<Level> dbsDimension,
        long startTick
    ) { ... initialize fields; phase=STARTING, outcome=IN_PROGRESS, currentRoomIndex=0,
            dungeonTimerTicks = tierConfig.dungeonTimeSeconds * 20, closeTimerTicks=0,
            empty maps ... }

    // --- Getters for every field (immutable view for maps) ---
    public DungeonPhase phase() { ... }
    public DungeonOutcome outcome() { ... }
    public DifficultyTier tier() { ... }
    public TierConfig resolvedTierConfig() { ... }
    public boolean hardcoreEnabled() { ... }
    public BlockPos dbsPos() { ... }
    public ResourceKey<Level> dbsDimension() { ... }
    public int currentRoomIndex() { ... }
    public int dungeonTimerTicks() { ... }
    public int closeTimerTicks() { ... }
    public long startTick() { ... }
    public Map<UUID, RunParticipant> participants() { ... return unmodifiableMap ... }
    public Map<UUID, PlayerReturnPoint> returnPoints() { ... return unmodifiableMap ... }
    public Map<UUID, DownedPlayer> downedPlayers() { ... return unmodifiableMap ... }

    // --- Mutators (used by lifecycle methods in Phase E) ---
    void setPhase(DungeonPhase phase) { ... }
    void setOutcome(DungeonOutcome outcome) { ... }
    void setCurrentRoomIndex(int index) { ... }
    void setDungeonTimerTicks(int ticks) { ... }
    void setCloseTimerTicks(int ticks) { ... }
    void addParticipant(RunParticipant p) { ... }
    void updateParticipant(RunParticipant p) { ... }  // replaces by uuid
    void removeParticipant(UUID uuid) { ... }
    void setReturnPoint(UUID uuid, PlayerReturnPoint rp) { ... }
    void setDowned(DownedPlayer dp) { ... }
    void clearDowned(UUID uuid) { ... }

    // --- Convenience predicates ---
    public Set<UUID> activeParticipantUuids() { ...filter by status == ACTIVE... }
    public Set<UUID> lootEligibleUuids() { ...filter by isEligibleForLoot()... }
    public boolean isFinished() { return phase == DungeonPhase.DONE; }

    // --- Serialization ---
    public static final Codec<DungeonRun> CODEC = ...; // RecordCodecBuilder, see acceptance
}
```

**NBT schema** (via the codec):
```
{
  "phase": "RUNNING",
  "outcome": "IN_PROGRESS",
  "tier": "HARD",
  "tierConfig": { ... TierConfig codec ... },
  "hardcore": true,
  "dbsPos": [x, y, z] (BlockPos.CODEC),
  "dbsDim": "minecraft:overworld",
  "currentRoomIndex": 2,
  "dungeonTimerTicks": 8400,
  "closeTimerTicks": 0,
  "startTick": 12345678,
  "participants": [ {RunParticipant}, ... ],
  "returnPoints": [ {uuid, rp}, ... ],
  "downedPlayers": [ {DownedPlayer}, ... ]
}
```

**Acceptance:**
- All getters return the field; map getters return unmodifiable views.
- Package-private mutators modify state.
- Codec round-trips a populated instance with: 3 participants (one ACTIVE, one DOWNED, one DISCONNECTED), 3 return points, 1 downed player, phase=RUNNING, outcome=IN_PROGRESS.
- `activeParticipantUuids()` returns only ACTIVE participants.
- `lootEligibleUuids()` returns all non-REMOVED.
- `isFinished()` true only when phase=DONE.

**Don'ts:**
- Do not implement `tick()`, `handleWin()`, `handleLoss()`, `handleStartingPhase()`, or any lifecycle method. Those are Phase E.
- Do not reference any block entity, server level, or player object in this class. Pure data + state mutation only.
- Do not make mutators public — they're package-private so only classes in the same package (the lifecycle code in Phase E) can call them.
- Do not add a builder. The constructor + mutators are enough.
- Do not synchronize fields. Block entities tick on the server thread only.

**References:**
- Read the old `DungeonBossSpawnerBlockEntity` fields (lines 64–106) to understand what state was tracked. Most of it moves here.

**Unit tests** (`src/test/java/...`):
- Construct a run, assert initial state (phase=STARTING, outcome=IN_PROGRESS, empty maps, correct timer from tier).
- Add 3 participants, assert `activeParticipantUuids()` returns only ACTIVE ones.
- Mark one REMOVED, assert `lootEligibleUuids()` excludes them.
- Codec round-trip on a populated run.

---
## Phase C — The Room block (1 PR)

**Goal of the phase:** introduce the brand-new `RoomController` block. It does not interact with the v4.0 controller or v4.0 DBS yet — those don't exist. The room's `activate(tier)` and `reset()` work against the world directly, spawning/despawning entities by talking to the **legacy** `MobSpawnerBlockEntity` and `DungeonBossSpawnerBlockEntity`. The new V2 spawners replace these in Phase D.

This phase is bigger than Phase B in lines of code, but the structure is well-defined. It contains: the new block + block entity, runtime methods that drive a room's lifecycle, additive methods on legacy spawners so the new room can use them, the admin GUI, and a smoke gametest proving the whole thing works in-world.

Tasks in this phase (8 total):
- **PC-1** — Register the new block + block entity in the registries, with placeholder texture and basic placement.
- **PC-2** — Block entity data model (fields, getters, admin operations, NBT via codec).
- **PC-3** — Block entity runtime methods (`activate`, `reset`, `openDoor`, `refreshAliveMobs`).
- **PC-3-old** — Additive methods on legacy `MobSpawnerBlockEntity` and `DungeonBossSpawnerBlockEntity` so the room can call them.
- **PC-4** — Admin GUI (screen + handler + packets).
- **PC-5** — Loom run config for gametests.
- **PC-6** — Smoke gametest validating spawn/clear/reset/door cycle.
- **PC-7** — Item registration + lang strings + creative tab entry.

After Phase C, the RoomController exists as a fully usable block in-world: admins can place it, configure its spawner list and door via the Linker (Linker support deferred to Phase F, but the data model and methods are ready). It does not yet *do* anything during a run because the v4.0 controller doesn't exist; Phase E wires it up.

---

### PC-1 — Register the RoomController block + block entity

**Goal**: get a placeable block on the registry with an empty block entity. No NBT, no logic, no GUI yet. This task is small on purpose — proves the registration plumbing works end-to-end before we put any data behind it.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/block/RoomControllerBlock.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/blockentity/RoomControllerBlockEntity.java`
- `src/main/resources/assets/arenas_ld/blockstates/room_controller.json`
- `src/main/resources/assets/arenas_ld/models/block/room_controller.json`
- `src/main/resources/assets/arenas_ld/models/item/room_controller.json`
- `src/main/resources/assets/arenas_ld/textures/block/room_controller.png` — placeholder, a solid magenta `#FF00FF` 16x16 PNG is fine. The texture exists only so the block doesn't render as the missing-texture purple-checkerboard. Real art comes later.

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/registry/BlockRegistry.java` — register the block.
- `src/main/java/net/ledok/arenas_ld/registry/BlockEntitiesRegistry.java` — register the block entity type.

#### Block spec

```java
package net.ledok.arenas_ld.dungeon.block;

import com.mojang.serialization.MapCodec;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class RoomControllerBlock extends BaseEntityBlock {

    public static final MapCodec<RoomControllerBlock> CODEC = simpleCodec(RoomControllerBlock::new);

    public RoomControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RoomControllerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
```

Note: no `useWithoutItem` yet — opening the GUI is PC-4. No ticker — the room doesn't tick on its own; the controller (Phase E) will drive it. No `getTicker` override.

#### Block entity spec (PC-1 stub only)

Just enough to compile. PC-2 fills in fields and persistence.

```java
package net.ledok.arenas_ld.dungeon.blockentity;

import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RoomControllerBlockEntity extends BlockEntity {

    public RoomControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.ROOM_CONTROLLER_BLOCK_ENTITY, pos, state);
    }
}
```

#### Block registration

In `BlockRegistry.java`, add (alongside existing entries, alphabetical order doesn't matter):

```java
public static final Block ROOM_CONTROLLER_BLOCK = registerBlock("room_controller",
        new RoomControllerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(-1.0f, 3600000.0f)));
```

In `BlockEntitiesRegistry.java`, add:

```java
public static final BlockEntityType<RoomControllerBlockEntity> ROOM_CONTROLLER_BLOCK_ENTITY =
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                ResourceLocation.parse(ArenasLdMod.MOD_ID + ":room_controller_be"),
                BlockEntityType.Builder.of(RoomControllerBlockEntity::new, BlockRegistry.ROOM_CONTROLLER_BLOCK).build(null));
```

Update the import in `BlockEntitiesRegistry.java`:

```java
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
```

And in `BlockRegistry.java`:

```java
import net.ledok.arenas_ld.dungeon.block.RoomControllerBlock;
```

#### Block assets

`blockstates/room_controller.json`:
```json
{
  "variants": {
    "": { "model": "arenas_ld:block/room_controller" }
  }
}
```

`models/block/room_controller.json`:
```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "arenas_ld:block/room_controller"
  }
}
```

`models/item/room_controller.json`:
```json
{
  "parent": "arenas_ld:block/room_controller"
}
```

`textures/block/room_controller.png`: a 16x16 magenta placeholder image.

#### Acceptance

- `./gradlew build` passes.
- Block exists on the registry under `arenas_ld:room_controller`.
- Block entity type registered as `arenas_ld:room_controller_be`.
- Block can be obtained via `/give @s arenas_ld:room_controller` and placed in-world.
- Block renders (even as solid magenta).
- Right-clicking the block does nothing yet (no GUI — that's PC-4).
- No NBT persistence yet — block entity exists but is empty.

#### Don'ts

- No GUI / screen / packets in this task. PC-4.
- No tick logic in this task. PC-3.
- No fields on the block entity beyond what the constructor inherits. PC-2.
- No creative tab entry yet. PC-7 adds lang + creative tab in one go.
- No tooltip on the item.
- Do not name the texture file differently or put it in a subfolder. Path must be `assets/arenas_ld/textures/block/room_controller.png`.

#### References

- `BlockRegistry.java` — registration patterns are visible at lines 15–43.
- `BlockEntitiesRegistry.java` — entity type registration patterns at lines 13–56.
- `MobSpawnerBlock.java` — comparable simple block, useful template (the v4.0 RoomControllerBlock is simpler).
- `PhaseBlock.java` — useful template for a block with a custom block state, **but** the RoomController has no block state, so we use the simpler form.

---

### PC-2 — RoomController data model

**Goal**: add the data fields, getters, admin operations, and NBT persistence. Pure data layer; runtime methods (`activate`, `reset`, `openDoor`) come in PC-3.

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/dungeon/blockentity/RoomControllerBlockEntity.java`

#### Field design

The room owns:
- `spawnerPositions: List<BlockPos>` — block positions of spawners this room owns. The spawner can be a legacy `MobSpawnerBlockEntity` (regular mob room) or legacy `DungeonBossSpawnerBlockEntity` (boss room). PC-3 resolves the type at runtime via `instanceof`. There's no need to track the type here.
- `doorPos: Optional<BlockPos>` — the phase block this room opens on clear. `Optional.empty()` for rooms with no door (e.g. the spawn room or the final/boss room).
- `aliveMobs: Set<UUID>` — runtime list of mob UUIDs spawned by this room. Populated by `activate()`, drained by `refreshAliveMobs()` as mobs die.
- `activated: boolean` — has the room been activated (its spawners told to spawn) in the current run? Prevents double-activation.
- `cleared: boolean` — have all spawned mobs died? Latches true; reset clears it.

#### Class structure

```java
package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RoomControllerBlockEntity extends BlockEntity {

    // ---- Fields ----

    private final List<BlockPos> spawnerPositions = new ArrayList<>();
    @Nullable private BlockPos doorPos = null;
    private final Set<UUID> aliveMobs = new HashSet<>();
    private boolean activated = false;
    private boolean cleared = false;

    // ---- Construction ----

    public RoomControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.ROOM_CONTROLLER_BLOCK_ENTITY, pos, state);
    }

    // ---- Getters ----

    public List<BlockPos> getSpawnerPositions() {
        return Collections.unmodifiableList(spawnerPositions);
    }

    @Nullable
    public BlockPos getDoorPos() {
        return doorPos;
    }

    public Set<UUID> getAliveMobs() {
        return Collections.unmodifiableSet(aliveMobs);
    }

    public boolean isActivated() {
        return activated;
    }

    public boolean isCleared() {
        return cleared;
    }

    // ---- Admin / Linker operations ----

    /** Returns true if the spawner was added (false if already present). */
    public boolean addSpawner(BlockPos pos) {
        if (spawnerPositions.contains(pos)) {
            return false;
        }
        spawnerPositions.add(pos);
        setChanged();
        return true;
    }

    /** Returns true if the spawner was removed (false if not present). */
    public boolean removeSpawner(BlockPos pos) {
        boolean removed = spawnerPositions.remove(pos);
        if (removed) setChanged();
        return removed;
    }

    public void clearSpawners() {
        if (!spawnerPositions.isEmpty()) {
            spawnerPositions.clear();
            setChanged();
        }
    }

    public void setDoorPos(@Nullable BlockPos pos) {
        if (!Objects.equals(doorPos, pos)) {
            this.doorPos = pos;
            setChanged();
        }
    }

    // ---- Runtime operations (called by PC-3 methods + Phase E controller) ----
    // Package-private so only same-package code can flip activated/cleared flags.

    void markActivated() {
        activated = true;
        setChanged();
    }

    void markCleared() {
        cleared = true;
        setChanged();
    }

    void clearRuntimeState() {
        activated = false;
        cleared = false;
        aliveMobs.clear();
        setChanged();
    }

    void trackSpawnedMob(UUID uuid) {
        aliveMobs.add(uuid);
        setChanged();
    }

    void untrackSpawnedMob(UUID uuid) {
        aliveMobs.remove(uuid);
        setChanged();
    }

    // ---- NBT ----

    // Internal serialization record; never exposed outside the class.
    private record State(
        List<BlockPos> spawners,
        Optional<BlockPos> door,
        Set<UUID> aliveMobs,
        boolean activated,
        boolean cleared
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().fieldOf("spawners").forGetter(State::spawners),
            BlockPos.CODEC.optionalFieldOf("door").forGetter(State::door),
            UUIDUtil.CODEC.listOf()
                .xmap((List<UUID> list) -> (Set<UUID>) new HashSet<>(list),
                      (Set<UUID> set) -> new ArrayList<>(set))
                .fieldOf("aliveMobs").forGetter(State::aliveMobs),
            Codec.BOOL.fieldOf("activated").forGetter(State::activated),
            Codec.BOOL.fieldOf("cleared").forGetter(State::cleared)
        ).apply(i, State::new));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        State state = new State(spawnerPositions, Optional.ofNullable(doorPos), aliveMobs, activated, cleared);
        State.CODEC.encodeStart(NbtOps.INSTANCE, state)
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                "Failed to save RoomController at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        if (nbt.contains("State")) {
            State.CODEC.parse(NbtOps.INSTANCE, nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                    "Failed to load RoomController at {}: {}", worldPosition, err))
                .ifPresent(state -> {
                    spawnerPositions.clear();
                    spawnerPositions.addAll(state.spawners());
                    doorPos = state.door().orElse(null);
                    aliveMobs.clear();
                    aliveMobs.addAll(state.aliveMobs());
                    activated = state.activated();
                    cleared = state.cleared();
                });
        }
    }
}
```

#### NBT schema

```
{
  "State": {
    "spawners": [<long array>, ...],   // BlockPos.CODEC list form
    "door": <long array>,              // optional; absent when no door
    "aliveMobs": [<int array>, ...],   // UUIDUtil.CODEC list form
    "activated": false,
    "cleared": false
  }
}
```

#### Acceptance

- Block entity has the 5 fields described, in the visibility shown.
- All 5 getters return what's documented; collection getters return unmodifiable views.
- `addSpawner` deduplicates: returns false if already present, doesn't add a duplicate.
- `removeSpawner` returns true only if it was present and got removed.
- `clearSpawners` is a no-op if already empty (no `setChanged()`).
- `setDoorPos` is a no-op if the value is unchanged (no `setChanged()`).
- Runtime mutators (`markActivated`, etc.) are package-private.
- Save/load round-trip preserves all fields (verified by gametest in PC-6).

#### Don'ts

- Do not call `setChanged()` on getter access.
- Do not validate spawner positions point to actual spawners — admins can typo; runtime methods will skip invalid entries.
- Do not auto-mark `cleared` when `aliveMobs` becomes empty — that's PC-3's `refreshAliveMobs()` logic.
- Do not store the parent DBS reference here. Ownership is top-down (DBS knows its rooms); rooms don't reach up.
- Do not add hand-written `toNbt`/`fromNbt` methods. NBT goes through the codec.
- Do not add equals/hashCode/toString.
- Do not synchronize. Server thread only.
- Do not split `State` into its own top-level class — it's a private serialization helper.

#### References

- PB-* tasks for codec patterns.
- Legacy `DungeonBossSpawnerBlockEntity.linkedSpawners` (lines 86, 405–431) — the room replaces that pattern, but the data shape is similar.

---

### PC-3-old — Add `spawnSingleMobScaled` to legacy spawners

**Important: this task comes BEFORE PC-3.** PC-3's `activate()` calls these new methods. We add them to legacy classes first so PC-3 has working callees.

**Goal**: add additive methods to legacy `MobSpawnerBlockEntity` and `DungeonBossSpawnerBlockEntity` that spawn one entity using the spawner's configured attributes/equipment, scaled by a health multiplier. Old behavior is not modified.

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/block/entity/MobSpawnerBlockEntity.java`
- `src/main/java/net/ledok/arenas_ld/block/entity/DungeonBossSpawnerBlockEntity.java`

#### Method spec

On `MobSpawnerBlockEntity`:

```java
/**
 * Spawn a single mob using this spawner's mobId + attributes + equipment, scaled by
 * the given health multiplier. Used by the v4.0 {@code RoomControllerBlockEntity}.
 *
 * <p>This method does not interact with the legacy wave/loot/trigger system on this spawner.
 * It is purely a "make me one configured mob at this spawner's location" helper for the
 * new architecture.
 *
 * <p>Spawn position: 1 block above the spawner. No spread, no multi-attempt safe-spawn
 * scan — the new architecture trusts the admin to place spawners in valid spots.
 *
 * @param world             the level (must be the spawner's level)
 * @param healthMultiplier  multiplier applied to max_health when scaling attributes
 * @return the spawned entity, or null on failure (invalid mobId, level.addFreshEntity returned false)
 */
@Nullable
public LivingEntity spawnSingleScaled(ServerLevel world, double healthMultiplier) {
    Optional<EntityType<?>> entityTypeOpt = EntityType.byString(this.mobId);
    if (entityTypeOpt.isEmpty()) {
        ArenasLdMod.LOGGER.warn("RoomController-driven spawn: invalid mob ID {} at {}", mobId, worldPosition);
        return null;
    }

    Entity mob = entityTypeOpt.get().create(world);
    if (!(mob instanceof LivingEntity living)) {
        ArenasLdMod.LOGGER.warn("RoomController-driven spawn: not a LivingEntity: {}", mobId);
        return null;
    }

    // Attributes
    for (AttributeData attr : this.attributes) {
        ResourceLocation attrLoc = ResourceLocation.tryParse(attr.id());
        if (attrLoc == null) continue;
        var attrRegistry = world.registryAccess().registryOrThrow(Registries.ATTRIBUTE);
        ResourceKey<Attribute> key = ResourceKey.create(Registries.ATTRIBUTE, attrLoc);
        attrRegistry.getHolder(key).ifPresent(holder -> {
            AttributeInstance inst = living.getAttribute(holder);
            if (inst != null) {
                double value = attr.value();
                if ("minecraft:generic.max_health".equals(attr.id())) {
                    value *= healthMultiplier;
                }
                inst.setBaseValue(value);
            }
        });
    }

    // Equipment (use existing EntityEquipmentHelper if available; otherwise inline)
    // Legacy MobSpawnerBlockEntity's applyEquipment method exists; we reuse it.
    applyEquipment(living, EquipmentSlot.HEAD, equipment.head);
    applyEquipment(living, EquipmentSlot.CHEST, equipment.chest);
    applyEquipment(living, EquipmentSlot.LEGS, equipment.legs);
    applyEquipment(living, EquipmentSlot.FEET, equipment.feet);
    applyEquipment(living, EquipmentSlot.MAINHAND, equipment.mainHand);
    applyEquipment(living, EquipmentSlot.OFFHAND, equipment.offHand);

    living.heal(living.getMaxHealth());

    // Position
    living.moveTo(
        worldPosition.getX() + 0.5,
        worldPosition.getY() + 1,
        worldPosition.getZ() + 0.5,
        world.random.nextFloat() * 360.0F,
        0.0F
    );

    if (!world.addFreshEntity(living)) {
        return null;
    }
    return living;
}
```

On `DungeonBossSpawnerBlockEntity`, same method signature, same body — except:
- Uses `EntityEquipmentHelper.applyEquipment(living, slot, itemId, equipment.dropChance)` (the DBS has the drop-chance variant; the regular `MobSpawnerBlockEntity` doesn't and uses its own simpler `applyEquipment`).
- That's the only difference.

```java
@Nullable
public LivingEntity spawnSingleScaled(ServerLevel world, double healthMultiplier) {
    // ... same as above but ...
    EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.HEAD, equipment.head, equipment.dropChance);
    EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.CHEST, equipment.chest, equipment.dropChance);
    EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.LEGS, equipment.legs, equipment.dropChance);
    EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.FEET, equipment.feet, equipment.dropChance);
    EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.MAINHAND, equipment.mainHand, equipment.dropChance);
    EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.OFFHAND, equipment.offHand, equipment.dropChance);
    // ... rest identical ...
}
```

#### Acceptance

- Method added to both legacy classes with the signature shown.
- Returns null on invalid mobId (logged at WARN, not ERROR — these are admin typos, not bugs).
- Returns null on non-LivingEntity (logged at WARN).
- Spawned entity has its `max_health` scaled by `healthMultiplier`.
- Other attributes (attack_damage, movement_speed, etc.) are set to their raw configured values, **not** scaled. Tier scaling only touches HP at this layer; damage scaling lives in the mixin.
- Spawned entity is healed to full immediately after attribute application.
- Existing methods/behavior of both classes are unchanged.

#### Don'ts

- Do not add the new method via an interface. Direct method on each class. We could share the impl via a shared helper later (Phase D when we build V2 spawners) but not now.
- Do not modify any existing method.
- Do not modify any existing field.
- Do not change the log level on existing error paths.
- Do not add the team-assignment code (`scoreboard.addPlayerToTeam(...)`). The new architecture defers team logic to the controller (Phase E) — for now, mobs spawned via this method are teamless.
- Do not invoke this method from anywhere yet. PC-3 calls it.

#### References

- Legacy `MobSpawnerBlockEntity.startBattle()` lines 269–360 — most of the code in `spawnSingleScaled` is a stripped version of that.
- Legacy `DungeonBossSpawnerBlockEntity.startBattle()` lines 851–910 — same for the boss.
- `EntityEquipmentHelper.applyEquipment` — referenced by the DBS variant.

---

### PC-3 — RoomController runtime methods

**Goal**: implement `activate(tier)`, `reset()`, `openDoor()`, `closeDoor()`, `refreshAliveMobs()` on `RoomControllerBlockEntity`.

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/dungeon/blockentity/RoomControllerBlockEntity.java`

#### Method specs

```java
/**
 * Spawn this room's mobs, applying the tier's health multiplier.
 *
 * <p>For each {@code BlockPos} in {@link #spawnerPositions}:
 * <ul>
 *   <li>If the block entity at that position is a legacy {@code MobSpawnerBlockEntity},
 *       call {@code spawnSingleScaled(world, tier.healthMultiplier())} on it.</li>
 *   <li>If it's a legacy {@code DungeonBossSpawnerBlockEntity}, same call on that type.</li>
 *   <li>Otherwise, log a warning and skip that position.</li>
 * </ul>
 *
 * <p>Sets {@code activated=true} regardless of how many mobs actually spawned.
 *
 * <p>If {@code activated} is already true, this is a no-op and returns 0 (defensive against
 * double-activation in case of a controller bug).
 *
 * @return the count of mobs that were spawned and tracked
 */
public int activate(ServerLevel world, TierConfig tier) {
    if (activated) return 0;

    int spawned = 0;
    for (BlockPos pos : spawnerPositions) {
        BlockEntity be = world.getBlockEntity(pos);
        LivingEntity entity = null;
        if (be instanceof MobSpawnerBlockEntity mobSpawner) {
            entity = mobSpawner.spawnSingleScaled(world, tier.healthMultiplier());
        } else if (be instanceof DungeonBossSpawnerBlockEntity bossSpawner) {
            entity = bossSpawner.spawnSingleScaled(world, tier.healthMultiplier());
        } else {
            ArenasLdMod.LOGGER.warn(
                "RoomController at {}: linked position {} is not a spawner (got {})",
                worldPosition, pos, be == null ? "null" : be.getClass().getSimpleName());
            continue;
        }
        if (entity != null) {
            trackSpawnedMob(entity.getUUID());
            spawned++;
        }
    }
    markActivated();
    return spawned;
}

/**
 * Despawn any alive mobs this room spawned, clear runtime state, close the door.
 * Idempotent: safe to call on a not-yet-activated or already-reset room.
 */
public void reset(ServerLevel world) {
    // Despawn alive mobs
    for (UUID uuid : new ArrayList<>(aliveMobs)) {
        Entity entity = world.getEntity(uuid);
        if (entity != null && entity.isAlive()) {
            entity.discard();
        }
    }
    clearRuntimeState();
    closeDoor(world);
}

/**
 * Open the room's door by setting the phase block's SOLID property to false.
 * No-op if no door is set, the door position isn't loaded, or the block at that position
 * isn't a PhaseBlock.
 */
public void openDoor(ServerLevel world) {
    setDoorSolid(world, false);
}

/**
 * Close the room's door by setting the phase block's SOLID property to true.
 * Same no-op conditions as {@link #openDoor(ServerLevel)}.
 */
public void closeDoor(ServerLevel world) {
    setDoorSolid(world, true);
}

private void setDoorSolid(ServerLevel world, boolean solid) {
    if (doorPos == null) return;
    if (!world.isLoaded(doorPos)) return;
    BlockState state = world.getBlockState(doorPos);
    if (!(state.getBlock() instanceof PhaseBlock)) {
        ArenasLdMod.LOGGER.warn(
            "RoomController at {}: door position {} is not a PhaseBlock (got {})",
            worldPosition, doorPos, state.getBlock());
        return;
    }
    if (state.getValue(PhaseBlock.SOLID) != solid) {
        world.setBlock(doorPos, state.setValue(PhaseBlock.SOLID, solid), 3);
    }
}

/**
 * Drop any aliveMobs UUIDs whose entities are dead, removed, in another dimension, or unloaded.
 * Updates {@link #cleared} to true if this leaves {@code aliveMobs} empty (and the room was activated).
 *
 * <p>This is intended to be called once per tick by the controller (Phase E) during a run.
 * It does NOT open the door — that's the controller's job after observing {@code isCleared()}.
 */
public void refreshAliveMobs(ServerLevel world) {
    Iterator<UUID> it = aliveMobs.iterator();
    boolean changed = false;
    while (it.hasNext()) {
        UUID uuid = it.next();
        Entity entity = world.getEntity(uuid);
        if (entity == null || !entity.isAlive() || entity.isRemoved() || entity.level() != world) {
            it.remove();
            changed = true;
        }
    }
    if (activated && !cleared && aliveMobs.isEmpty()) {
        markCleared();
        return; // setChanged is called by markCleared
    }
    if (changed) setChanged();
}
```

#### Imports to add

```java
import net.ledok.arenas_ld.block.PhaseBlock;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
```

#### Acceptance

- All five public methods present with the documented signatures.
- `activate` returns 0 if already activated (no double-spawn).
- `activate` accepts mixed legacy spawner types correctly.
- `activate` logs a warning and skips for any position whose block isn't a known spawner type.
- `reset` discards all tracked entities and clears runtime state. Idempotent.
- `openDoor` / `closeDoor` set the PhaseBlock.SOLID property correctly. No-op if no door / not loaded / wrong block type.
- `refreshAliveMobs` removes dead/missing/cross-dimension UUIDs.
- `refreshAliveMobs` sets `cleared = true` once `aliveMobs` becomes empty post-activation, but **does not** open the door.

#### Don'ts

- Do not auto-open the door in `refreshAliveMobs`. The controller is the orchestrator.
- Do not retry failed spawns. Logged, skipped, move on.
- Do not synchronize. Server thread only.
- Do not chunk-force at this layer. Chunk loading is a controller-level concern (Phase E) — by the time the controller calls `activate`, chunks should already be loaded. If they're not (`world.getBlockEntity` returns null), we log and skip; we don't load on demand.
- Do not propagate the open/close state to adjacent phase blocks (chain doors). Single-block doors only in v4.0 core. Multi-block door chains are deferred.
- Do not return the spawned entities from `activate` — the caller only needs to know "how many." If the controller ever needs the entities themselves, it asks via `getAliveMobs()`.

#### References

- PC-3-old's `spawnSingleScaled` (just landed).
- `PhaseBlock.SOLID` — `BooleanProperty.create("solid")`.
- The existing `PhaseBlockEntity.propagateState` (line 91) is the chain-door mechanism. We deliberately don't use it; rooms set the state directly.

---

### PC-4 — Admin GUI

**Goal**: a simple ops-only screen where admins can see the room's spawner list, remove entries, see/clear the door, and trigger a manual `reset()`. **Adding** spawners/door is via Linker (Phase F); the GUI is a viewer + remover.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/RoomControllerScreen.java` (client-side screen)
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/RoomControllerScreenHandler.java` (server menu)
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/RoomControllerData.java` (the ExtendedScreenHandlerFactory data record sent on open)
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/RoomRemoveSpawnerPayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/RoomClearSpawnersPayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/RoomClearDoorPayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/RoomResetPayload.java`

**Files to modify:**
- `RoomControllerBlockEntity.java` — implement `ExtendedScreenHandlerFactory<RoomControllerData>`.
- `RoomControllerBlock.java` — `useWithoutItem` opens the screen for ops.
- `ModScreenHandlers.java` — register the new screen handler.
- `ModPackets.java` or `ArenasLdMod.java` (wherever the existing payload registration happens) — register the 4 new C2S payloads.
- Existing client init class — register the screen factory for the new screen handler type.

#### Data shape

```java
public record RoomControllerData(
    BlockPos blockPos,
    List<BlockPos> spawnerPositions,
    Optional<BlockPos> doorPos
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, RoomControllerData> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RoomControllerData::blockPos,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), RoomControllerData::spawnerPositions,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), RoomControllerData::doorPos,
            RoomControllerData::new
        );
}
```

#### Screen layout

Plain text-list screen, similar to the existing `DungeonControllerScreen` style. Width ~250px, height ~200px.

```
+-----------------------------------------------+
| Room Controller                          [X]  |
+-----------------------------------------------+
| Spawners (use Linker to add):                 |
|   • [100, 64, 200]              [Remove]     |
|   • [102, 64, 201]              [Remove]     |
|   • [104, 65, 198]              [Remove]     |
|                                  [Clear All]  |
+-----------------------------------------------+
| Door: [110, 64, 200]            [Clear Door]  |
|   (use Linker to set)                         |
+-----------------------------------------------+
|                              [Reset Room]     |
+-----------------------------------------------+
```

Buttons:
- **Remove** (per row): sends `RoomRemoveSpawnerPayload(pos)` and locally removes the row. Server applies `removeSpawner(pos)`.
- **Clear All**: confirmation dialog → sends `RoomClearSpawnersPayload`.
- **Clear Door**: sends `RoomClearDoorPayload`. Door is then `null`.
- **Reset Room**: sends `RoomResetPayload`. Server calls `reset(world)`. Useful for admins testing dungeons mid-design.

No "Add" UI — Linker only.

#### Permissions

`useWithoutItem` in `RoomControllerBlock`:

```java
@Override
public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
    if (player.getMainHandItem().getItem() instanceof LinkerItem || player.getOffhandItem().getItem() instanceof LinkerItem) {
        return InteractionResult.PASS;
    }
    if (!world.isClientSide) {
        if (!player.isCreative() && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("You don't have permission to configure this block.")
                .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof RoomControllerBlockEntity room) {
            player.openMenu(room);
            return InteractionResult.CONSUME;
        }
    }
    return InteractionResult.SUCCESS;
}
```

#### Packet handlers

Each packet has a handler that:
1. Verifies the sender is a server player.
2. Verifies the player has op permission level ≥ 2.
3. Loads the block entity at the packet's `blockPos`.
4. Verifies it's a `RoomControllerBlockEntity`.
5. Calls the appropriate method.

The exact handler registration mirrors the existing `DungeonControllerPacketHandlers` pattern — read that file before writing this one.

#### Acceptance

- Op opens the block, sees the GUI listing current spawners and door.
- Non-op gets a chat message and no GUI.
- Holding a Linker, right-click passes through (returns PASS) — so the Linker can still interact.
- Each button sends its payload, server applies the change, BE is `setChanged()`'d.
- Removing the last spawner shows an empty list (not a crash, not a "no spawners" placeholder).
- Reset Room actually calls `reset(world)` and despawns mobs if any were active.

#### Don'ts

- Do not allow inline adding from the GUI. Linker is the only path.
- Do not show mob preview, attribute editing, equipment editing — none of that belongs on the Room; it's on the spawners.
- Do not auto-refresh the GUI live as the room's state changes. Click "close, reopen" if data goes stale.
- Do not block GUI open during an active run. We don't have runs yet (Phase E); even after we do, admins should still be able to inspect rooms.
- Do not put confirmation dialogs on individual Remove buttons. Only on "Clear All" and "Reset Room."

#### References

- `DungeonControllerScreen` and its handler — the existing screen pattern.
- Existing `DungeonControllerPacketHandlers` — packet registration pattern.
- `ModScreenHandlers` — screen handler registration.

---

### PC-5 — Loom run config for gametests

**Goal**: enable `./gradlew runGametest` to launch a server and run our gametests. Without this, PC-6's gametest can't actually run.

**Files to modify:**
- `build.gradle`

#### Spec

Add a `loom { runs { gametestServer {} } }` block:

```gradle
loom {
    runs {
        gametestServer {
            server()
            name "Gametest Server"
            vmArg "-Dfabric-api.gametest"
            vmArg "-Dfabric-api.gametest.report-file=${project.buildDir}/gametest-report.xml"
            runDir "build/gametest"
        }
    }
}
```

The `runDir` is set to `build/gametest` so the run output goes into the build directory (cleaned by `gradle clean`) rather than persisting in the repo's `run/` dir.

#### Acceptance

- `./gradlew tasks --all | grep -i gametest` now lists `runGametestServer`.
- `./gradlew runGametestServer` launches without crashing. It will fail with "no gametests registered" since we haven't written any yet — that's fine, PC-6 fixes it.
- The gametest report file path is configured.

#### Don'ts

- Do not configure a client gametest (no `clientWithServer`, no `gametestClient`). Server-only is enough for us — all our tests are server-side.
- Do not add gametest VM args to existing run configs (the regular `runServer` shouldn't include gametest behavior).
- Do not commit the `build/gametest` directory.

#### References

- Fabric Loom docs on gametest run configs (loom version-specific; check the README of `fabric-loom` for 1.21.1).

---

### PC-6 — Smoke gametest

**Goal**: a single gametest that proves the spawn → clear → reset → door cycle works end-to-end with the legacy MobSpawnerBlockEntity. This is our smoke test for every subsequent Phase C/D/E change.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/gametest/RoomControllerGametests.java`
- `src/main/resources/data/arenas_ld/gametest/structure/room_smoke.snbt` (the structure spawned for the test)

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/gametest/ArenasLdGametests.java` — register the new gametest class via the gametest entrypoint mechanism. (Look up how fabric-gametest-api-v1 discovers tests; it's typically annotation-driven via `@GameTest` and an entrypoint impl. If your existing stub class needs to implement an interface, do it now.)

#### The structure (`room_smoke.snbt`)

A small platform built with the legacy mob_spawner block and a room_controller block. Easiest path: build this in-game once, save with `/test export room_smoke`, copy the resulting .snbt into the resource path.

Required contents:
- A 5×5×3 platform of stone.
- A `room_controller` block at one corner.
- 2× `mob_spawner` blocks somewhere on the platform.
- A `phase_block` at the door position.

The gametest harness will spawn this structure into the test world, run assertions, then clean up.

#### The test method

```java
package net.ledok.arenas_ld.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

public class RoomControllerGametests {

    @GameTest(template = "arenas_ld:room_smoke")
    public void roomSpawnsClearResetCycle(GameTestHelper helper) {
        // Find the room controller in the structure.
        BlockPos roomPos = helper.absolutePos(/* relative pos within structure */);
        BlockPos spawner1 = helper.absolutePos(/* ... */);
        BlockPos spawner2 = helper.absolutePos(/* ... */);
        BlockPos doorPos = helper.absolutePos(/* ... */);

        ServerLevel level = helper.getLevel();
        RoomControllerBlockEntity room = (RoomControllerBlockEntity) level.getBlockEntity(roomPos);
        helper.assertTrue(room != null, "RoomController block entity present");

        // Programmatically set up the room (Linker isn't available in v4.0 core yet).
        room.addSpawner(spawner1);
        room.addSpawner(spawner2);
        room.setDoorPos(doorPos);

        // Configure the legacy spawners to spawn husks.
        ((MobSpawnerBlockEntity) level.getBlockEntity(spawner1)).mobId = "minecraft:husk";
        ((MobSpawnerBlockEntity) level.getBlockEntity(spawner2)).mobId = "minecraft:husk";

        // Activate at NORMAL tier.
        int spawned = room.activate(level, TierConfig.NORMAL_DEFAULT);
        helper.assertTrue(spawned == 2, "2 mobs spawned (got " + spawned + ")");
        helper.assertTrue(room.isActivated(), "room is activated");
        helper.assertTrue(!room.isCleared(), "room is not yet cleared");
        helper.assertTrue(room.getAliveMobs().size() == 2, "2 mobs tracked");

        // Verify door is closed (SOLID=true).
        helper.assertBlockProperty(/* relative doorPos */, PhaseBlock.SOLID, true);

        // Kill all spawned mobs.
        room.getAliveMobs().forEach(uuid -> {
            var entity = level.getEntity(uuid);
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                living.kill();
            }
        });

        // Tick refreshAliveMobs to detect deaths.
        helper.succeedWhen(() -> {
            room.refreshAliveMobs(level);
            helper.assertTrue(room.getAliveMobs().isEmpty(), "alive mobs drained");
            helper.assertTrue(room.isCleared(), "room cleared");
        });

        // Manually open the door (controller would do this in Phase E).
        room.openDoor(level);
        helper.assertBlockProperty(/* relative doorPos */, PhaseBlock.SOLID, false);

        // Reset.
        room.reset(level);
        helper.assertTrue(!room.isActivated(), "reset clears activated");
        helper.assertTrue(!room.isCleared(), "reset clears cleared");
        helper.assertBlockProperty(/* relative doorPos */, PhaseBlock.SOLID, true);
    }
}
```

The exact relative coordinates depend on how the structure is built; Codex will need to determine them when creating the .snbt.

#### Acceptance

- `./gradlew runGametestServer` runs the test and passes.
- The test exercises: addSpawner, setDoorPos, activate, refreshAliveMobs (detecting deaths), openDoor, reset.
- No assertion failures; gametest report shows green.

#### Don'ts

- Do not test PC-4 (the GUI) here. UI testing is out of scope for gametest.
- Do not test multiple rooms or controllers. Single room, smoke test.
- Do not write multiple test methods. One end-to-end cycle is enough; if it works, every piece worked.
- Do not stub or mock anything. Real legacy spawners, real entities, real world.
- Do not test the boss spawner variant in this task. The boss variant works the same way; one test covers both via the polymorphism in `activate()`. If we hit issues we add a boss-specific test later.

#### References

- Fabric Gametest examples: typically in the `fabric-gametest-api-v1` README.
- `GameTestHelper` API for assertions and structure-relative positioning.

---

### PC-7 — Lang + creative tab

**Goal**: hook up the new block's display name and add it to the existing creative tab. Trivial cleanup task that closes Phase C.

**Files to modify:**
- `src/main/resources/assets/arenas_ld/lang/en_us.json` — add `"block.arenas_ld.room_controller": "Room Controller"`.
- `src/main/resources/assets/arenas_ld/lang/uk_ua.json` — add the Ukrainian translation. If unsure, use `"block.arenas_ld.room_controller": "Контролер кімнати"`.
- `src/main/java/net/ledok/arenas_ld/registry/ModCreativeModeTabs.java` — add the new block to the existing creative tab.

#### Acceptance

- Block displays the proper name in-game (not `block.arenas_ld.room_controller`).
- Block appears in the existing Arenas_LD creative tab.
- Build passes.

#### Don'ts

- Do not create a new creative tab.
- Do not add tooltips. The block has no tooltip in v4.0 core.
- Do not add a recipe.
- Do not localize for other languages — only en + uk to match existing scope.

---

### Phase C exit criteria

After all 7 tasks (PC-1 → PC-7) complete:

- `./gradlew build test runGametestServer` all pass.
- A RoomController block can be placed in-world, configured via its GUI (remove spawner / clear door / reset), and exercised end-to-end via the gametest.
- Legacy code is unchanged except for the two additive `spawnSingleScaled` methods (PC-3-old).
- Total new files: ~13 (block, BE, 3 assets, screen + handler + data, 4 packets, gametest, gametest structure).
- Total LOC: probably ~800-1000.
- Phase D can now build the V2 spawners that the room will eventually consume in addition to the legacy ones.

## Phase D — Slimming the spawners (1 PR)

Goal of the phase: new `MobSpawnerV2` and `DungeonBossSpawnerV2` blocks in the new package, stripped of legacy responsibilities. The old blocks **stay** — both coexist. Phase H deletes the old ones.

(Phases D, E, F, G specs to be written in detail when Phase C is closed. Stubs only below to confirm the shape.)

### PD-1 through PD-?: To be specified after Phase C completes

Planned tasks:
- `MobSpawnerV2Block` + `BlockEntity` with entity definition only (type + attributes + equipment), GUI for editing.
- `DungeonBossSpawnerV2Block` + `BlockEntity` similarly, plus entrance position field and room list field.
- Codec-based NBT.
- Admin GUI for each.
- No tier scaling here — the room applies it via PC-3-old's `spawnSingleMobScaled` pattern.

The shape is fully constrained by Phase C, but writing the specs now risks them being wrong against what Phase C actually produces. They will be specified concretely after Phase C lands and is reviewed.

---

## Phase E — The new Controller (1 PR — the big one)

Will include:
- New `DungeonControllerV2` block + BE.
- `DungeonRun.tick()`, `handleWin()`, `handleLoss()`, `handlePhaseTransition()` logic.
- Admin tabs GUI (Instances, Normal, Hard, Hell, General).
- Lobby system ported from old controller, cleaned up.
- Per-player `LootBundle` distribution on win.
- Leaderboard storage and display.
- Per-instance cooldown.
- Integration with `DungeonManagerV2`.

Specs deferred until Phase D closes.

---

## Phase F — Linker modes for the new blocks (1 PR)

Will include:
- Linker "Set Controller Instance" mode: click controller → click DBSes to add as instances.
- Linker "Set DBS Rooms" mode: click DBS → click RoomControllers in desired order.
- Linker "Set Room Spawners" mode: click RoomController → click MobSpawnerV2s / DBS to add.
- Linker "Set Room Door" mode: click RoomController → click PhaseBlock.
- Visual feedback / tooltip updates.

Specs deferred until Phase E closes.

---

## Phase G — Mixin + manager integration (1 PR)

Will include:
- `DungeonManagerV2` replacing `DungeonBossManager`, with `getRunForPlayer(player)` API.
- `LivingEntityMixin` updated to ask the manager for runs, not spawners.
- `BusyStateCompat` integration via the run, not the spawner.
- `ServerPlayConnectionEvents.JOIN/DISCONNECT` handlers.
- The disconnect grace period: a participant stays in the run for N minutes after disconnect; rejoin reactivates them; timeout removes them.

Specs deferred until Phase F closes.

---

## Phase H — Migration cliff & cleanup (1 PR)

Will include:
- Delete all old dungeon files: `DungeonBossSpawnerBlockEntity`, `DungeonBossSpawnerBlock`, `DungeonControllerBlockEntity`, `DungeonControllerBlock`, `DungeonBossManager`, old `MobSpawner*`, old `PhaseBlock` (or keep PhaseBlock — it's reused).
- Remove the corresponding entries from `BlockRegistry`, `BlockEntitiesRegistry`, `ModScreenHandlers`, `ModPackets`, `CommandRegistry`.
- Remove the `util` package classes superseded: `DungeonContext`, `DungeonInstanceRef` (or move into new package), `InstanceState`, `InstanceStatus`, `LeaderboardEntry`, `Lobby`, `LobbyStatus`, `LobbyVisibility`, `DungeonLeaderboardEntry`, `LinkableSpawner`, `LinkerDataComponent`, `LinkerModeDataComponent`.
- Update commands: `/arenasld dungeon` subcommands rewritten for new system.
- Bump `mod_version` to `4.0.0` in `gradle.properties`.
- Update `en_us.json` and `uk_ua.json`, removing dead keys, adding new ones.
- Final integration gametest: build a 3-room dungeon, run start to finish.

Specs deferred until Phase G closes.

---

## What's not in this plan

- **NeoForge port.** Out of scope for v4.0 core. Track separately.
- **Raid and Arena systems.** They have the same architectural problems but solving them is a v4.1+ effort. Touched only minimally in Phase H (removing dead utility classes that they shared).
- **Phase 5 gameplay features.** Disconnect resilience UX, post-run summary screen, lava-floor boss room, secret rooms, branching, modifiers — all deferred to v4.1 work.
- **Migration of old worlds.** Will not work; not implemented.

---

## Phase status tracker

| Phase | Status | PR | Last reviewed SHA |
|---|---|---|---|
| A | ✅ Complete (PA-1, PA-1.1, PA-2, PA-3, PA-4) | — | `1b226e2` |
| B | ✅ Complete (PB-1..PB-8, PB-10; PB-9 skipped as redundant) | — | `4717f45` |
| C | Specified, ready to start | — | — |
| D | Not specified | — | — |
| E | Not specified | — | — |
| F | Not specified | — | — |
| G | Not specified | — | — |
| H | Not specified | — | — |

We update this table as we go.

### Decisions made during execution

These supersede earlier guidance in the plan if they conflict:

- **PB-9 skipped**: per-task codec round-trip tests are already exhaustive; a consolidated meta-test would be pure duplication. Moved straight from PB-8 to PB-10.
- **Task granularity**: tasks are executed one at a time, not batched. Each gets a full review against acceptance criteria before the next one starts. This adds chat overhead but catches errors early.
- **Direct commits to `4.0`**: no per-task PRs; commits go straight to the branch. PR-per-phase was a hypothetical for multi-reviewer projects; for a single-author project, direct commits are fine.
- **`ParticipantStatus` is package-private**: kept narrow on purpose. Widen to public only when an outside-package consumer in Phase E actually needs to reference it.
- **`DungeonRun` mutators are package-private**: lifecycle code that drives state transitions must live in the `net.ledok.arenas_ld.dungeon.run` package. This is enforced architecturally, not by convention.
- **Map serialization uses `UUIDUtil.STRING_CODEC` as the key codec**: `UUIDUtil.CODEC` (int-array form) does not work as a NBT map key. The string form is also more debuggable.
- **NBT redundancy on participants/downedPlayers maps**: each UUID is stored both as the map key AND inside the value record. ~16 bytes per entry of waste, no behavior impact. Filed for future cleanup; not a current concern.
- **Damage source filter default**: `dungeon_damage_source_filter` defaults to `ALL` in v4.0 config. Players have high HP; fall/lava/drowning scaling is fair.
