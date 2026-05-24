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

This phase is bigger than Phase B in lines of code, but the structure is well-defined. It contains: the new block + block entity, runtime methods that drive a room's lifecycle, additive methods on legacy spawners so the new room can use them, the admin GUI, and the creative tab entry.

Tasks in this phase (6 active, 2 skipped):
- **PC-1** — Register the new block + block entity in the registries, with placeholder texture and basic placement.
- **PC-2** — Block entity data model (fields, getters, admin operations, NBT via codec).
- **PC-3-old** — Additive methods on legacy `MobSpawnerBlockEntity` and `DungeonBossSpawnerBlockEntity` so the room can call them.
- **PC-3** — Block entity runtime methods (`activate`, `reset`, `openDoor`, `refreshAliveMobs`).
- **PC-4** — Admin GUI (screen + handler + packets). Includes PC-4.1 follow-up for i18n.
- ~~**PC-5** — Loom run config for gametests.~~ **SKIPPED.**
- ~~**PC-6** — Smoke gametest validating spawn/clear/reset/door cycle.~~ **SKIPPED.**
- **PC-7** — Creative tab entry (lang strings already shipped in PC-4.1).

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

### PC-5 — Loom run config for gametests — **SKIPPED**

> **Status: SKIPPED on review.** This task existed only to support PC-6 (which is also skipped). Without a gametest, there is nothing to run. **Codex must not implement this task.** If revisited later, the original spec below is preserved verbatim for reference.
>
> **Rationale**: see the *Decisions made during execution* section at the bottom of the plan, under "PC-5/PC-6 skipped".

<details>
<summary>Original spec (for reference only — do not implement)</summary>

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

</details>

---

### PC-6 — Smoke gametest — **SKIPPED**

> **Status: SKIPPED on review.** A live gametest does not earn its keep against the cost: building the structure in-game, exporting `.snbt`, fighting Loom's gametest runner. The first dungeon run after Phase E exercises every code path this gametest would. **Codex must not implement this task.**
>
> **Rationale**: see the *Decisions made during execution* section at the bottom of the plan.

<details>
<summary>Original spec (for reference only — do not implement)</summary>

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

</details>

---

### PC-7 — Creative tab entry

**Goal**: add the Room Controller block to the existing Arenas_LD creative tab. Closes Phase C.

> **Note**: the `block.arenas_ld.room_controller` lang strings (en_us and uk_ua) were already added in PC-4.1. PC-7 is now smaller than originally planned — only the creative tab entry remains.

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/registry/ModCreativeModeTabs.java` — add the new block to the existing creative tab in the same style as the other entries.

#### Acceptance

- Block appears in the existing Arenas_LD creative tab in survival creative inventory.
- Block displays its display name "Room Controller" (already wired up via PC-4.1).
- Build passes.

#### Don'ts

- Do not create a new creative tab.
- Do not add tooltips. The block has no tooltip in v4.0 core.
- Do not add a recipe.
- Do not localize for other languages — only en + uk to match existing scope.

---

### Phase C exit criteria

After all active tasks (PC-1, PC-2, PC-3-old, PC-3, PC-4, PC-4.1, PC-7) complete:

- `./gradlew build test` passes.
- A RoomController block can be placed in-world, configured via its GUI (remove spawner / clear door / reset), and appears in the creative tab.
- Legacy code is unchanged except for the two additive `spawnSingleScaled` methods (PC-3-old).
- Total new files: ~11 (block, BE, 3 assets, screen + handler + data, 4 packets).
- Total LOC: probably ~700-800.
- Phase D can now build the V2 spawners that the room will eventually consume in addition to the legacy ones.

## Phase D — V2 Spawners (1 PR)

**Goal of the phase:** introduce the brand-new `MobSpawner` and `DungeonBossSpawner` blocks in `net.ledok.arenas_ld.dungeon` — slim, codec-serialized workers that own only what's intrinsic to "a spawner that produces one entity." The legacy spawners under `net.ledok.arenas_ld.block.entity` **stay in place** through this phase. Phase H deletes them.

### Architectural reminder

The new spawners differ from the legacy ones in what they *don't* carry. From the legacy `MobSpawnerBlockEntity` we drop:
- `lootTableId` — loot is controller-owned per tier
- `triggerRadius`, `battleRadius` — no trigger zone; rooms activate spawners directly
- `regeneration` — no self-heal; bosses get regen via attribute scaling if needed
- `skillExperiencePerWin` — moves to controller
- `mobCount`, `mobSpread` — one spawn per call, period
- `groupId` — party/team logic lives on the controller
- `linkedSpawners` — the room owns the linking now
- All runtime fields: `isBattleActive`, `activeMobUuids`, `playerDamageDealt`, `regenerationTickTimer`, `firstTick`, `activeDungeonContext`, `dungeonCleared`, `triggerScanTick`

Same exercise for the legacy `DungeonBossSpawnerBlockEntity` — drops the lot above plus `dungeonCloseTimer`, `dungeonTime`, `lootTableId`, `perPlayerLootTableId`, `exitPosition*`, `tierConfigs`, `activeTier`, `trackedPlayers`, `dungeonStartTick`, all the run-state fields. What survives on the new DBS: `mobId`, `attributes`, `equipment`, `entrancePos`, `entranceDimension`, and an ordered `rooms: List<BlockPos>` list. That's it.

### Naming

Calling them `MobSpawnerV2` / `DungeonBossSpawnerV2` would be ugly long-term and the "V2" suffix is a temporary scaffolding signal. Since both old and new versions need to coexist until Phase H, we **distinguish them by package**, not by class name:

| Layer | Legacy | New |
|---|---|---|
| Block class | `net.ledok.arenas_ld.block.MobSpawnerBlock` | `net.ledok.arenas_ld.dungeon.block.MobSpawnerBlock` |
| Block entity | `net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity` | `net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity` |
| Block id | `arenas_ld:mob_spawner` | `arenas_ld:mob_spawner_v2` |
| BE id | `arenas_ld:mob_spawner_be` | `arenas_ld:mob_spawner_v2_be` |
| Block class (boss) | `...block.DungeonBossSpawnerBlock` | `...dungeon.block.DungeonBossSpawnerBlock` |
| BE (boss) | `...block.entity.DungeonBossSpawnerBlockEntity` | `...dungeon.blockentity.DungeonBossSpawnerBlockEntity` |
| Block id (boss) | `arenas_ld:dungeon_boss_spawner` | `arenas_ld:dungeon_boss_spawner_v2` |
| BE id (boss) | `arenas_ld:dungeon_boss_spawner_be` | `arenas_ld:dungeon_boss_spawner_v2_be` |

The class names collide across packages but each file imports the specific one it needs. The `_v2` suffix on registry IDs is the only ugly leftover; Phase H reclaims the un-suffixed names by deleting the legacy blocks.

### Tasks in this phase

7 active tasks, in order:

- **PD-1** — Shared `EntityDefinition` record (mobId + attributes + equipment), codec-serialized. Used by both new spawners.
- **PD-2** — New `MobSpawnerBlock` + `MobSpawnerBlockEntity` in `dungeon.block` / `dungeon.blockentity`. Data model only.
- **PD-3** — `spawnSingleScaled(ServerLevel, double)` method on the new `MobSpawnerBlockEntity`, mirroring PC-3-old.
- **PD-4** — Update the room's `activate(...)` method to also dispatch to the new `MobSpawnerBlockEntity` (in addition to the legacy one). This is the cross-cutting change that makes the room consume both old and new spawners.
- **PD-5** — New `DungeonBossSpawnerBlock` + `DungeonBossSpawnerBlockEntity` in `dungeon.block` / `dungeon.blockentity`. Data model + `spawnSingleScaled` + room list management.
- **PD-6** — Admin GUIs for both new spawners. Lighter than PC-4 because there's less to configure.
- **PD-7** — Lang + creative tab entries.

After Phase D, the world has 4 spawner block types coexisting: legacy MobSpawner, legacy DungeonBossSpawner, new MobSpawner, new DungeonBossSpawner. Rooms work with all four. The new ones are dramatically simpler. Phase E builds the controller against the new types. Phase H deletes the legacy ones.

---

### PD-1 — `EntityDefinition` shared record

**Goal**: a single codec-serialized record describing "what entity to spawn and how to configure it." Used by both new spawner block entities, avoiding duplication.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/blockentity/EntityDefinition.java`

#### Spec

```java
package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.EquipmentData;

import java.util.List;

/**
 * Immutable description of an entity that a spawner will produce. Pure data; spawning logic
 * lives on the spawner block entities.
 *
 * @param mobId      the entity type's registry id (e.g. "minecraft:husk"). Spawner falls back
 *                   to logging a warning and producing null if this is invalid.
 * @param attributes per-instance attribute overrides. Tier scaling (applied at spawn time) is
 *                   layered on top of these — see {@code MobSpawnerBlockEntity.spawnSingleScaled}.
 * @param equipment  per-slot item ids with drop chance. Empty strings = no item in that slot.
 */
public record EntityDefinition(
    String mobId,
    List<AttributeData> attributes,
    EquipmentData equipment
) {
    public static final EntityDefinition DEFAULT =
        new EntityDefinition("minecraft:husk", List.of(), new EquipmentData());

    public static final Codec<EntityDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("mobId").forGetter(EntityDefinition::mobId),
        AttributeData.CODEC.listOf().fieldOf("attributes").forGetter(EntityDefinition::attributes),
        EquipmentData.CODEC.fieldOf("equipment").forGetter(EntityDefinition::equipment)
    ).apply(i, EntityDefinition::new));

    public EntityDefinition withMobId(String newId) {
        return new EntityDefinition(newId, attributes, equipment);
    }

    public EntityDefinition withAttributes(List<AttributeData> newAttrs) {
        return new EntityDefinition(newId(newAttrs), newAttrs, equipment);
    }

    public EntityDefinition withEquipment(EquipmentData newEquip) {
        return new EntityDefinition(mobId, attributes, newEquip);
    }

    private String newId(List<AttributeData> ignore) { return mobId; }  // helper to silence warnings; remove if not needed
}
```

#### Pre-flight check Codex must do

`AttributeData.CODEC` and `EquipmentData.CODEC` exist in the legacy `util` package. Codex must verify these codecs exist before writing PD-1. If they don't:

- If `AttributeData` is a record without a codec: add the codec inline in PD-1 next to the EntityDefinition codec, or add it as a one-line static field to the existing `AttributeData` class.
- Same for `EquipmentData`.

If both classes are records but lack codecs, write a thin codec for each in this commit so PD-1 compiles. They'll be needed by PD-2 anyway.

If the existing codecs *are* present, use them and skip the inline definition.

#### Acceptance

- Record with 3 fields.
- Public static `DEFAULT` instance.
- Public static `Codec<EntityDefinition> CODEC` round-tripping cleanly.
- `with*` builders return new instances, leave the original unchanged.
- `EntityDefinition.CODEC` parses NBT produced by `EntityDefinition.CODEC.encodeStart(...)` to an equal instance.

#### Don'ts

- Do not store mob spawn count, spread, group ID, loot table, regeneration, or any of the dropped legacy fields. They have no home in v4.0.
- Do not make `attributes` or `equipment` mutable. The whole record is value-typed.
- Do not add a `Builder` class — `with*` methods are enough.
- Do not validate `mobId` format. Spawner does the validation at spawn time.
- Do not include a `StreamCodec`. EntityDefinition doesn't travel over the network as a unit — admin GUI changes send individual field updates via existing payloads.

#### Unit test

`src/test/java/net/ledok/arenas_ld/dungeon/blockentity/EntityDefinitionTest.java`:

```java
package net.ledok.arenas_ld.dungeon.blockentity;

import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.EquipmentData;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class EntityDefinitionTest {

    @Test
    void defaultHasExpectedValues() {
        assertEquals("minecraft:husk", EntityDefinition.DEFAULT.mobId());
        assertEquals(List.of(), EntityDefinition.DEFAULT.attributes());
    }

    @Test
    void codecRoundTripsPopulatedInstance() {
        EntityDefinition original = new EntityDefinition(
            "minecraft:zombie",
            List.of(new AttributeData("minecraft:generic.max_health", 50.0)),
            new EquipmentData()
        );
        Tag encoded = EntityDefinition.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        EntityDefinition decoded = EntityDefinition.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(original, decoded);
    }

    @Test
    void withMobIdProducesNewInstance() {
        EntityDefinition a = EntityDefinition.DEFAULT;
        EntityDefinition b = a.withMobId("minecraft:skeleton");
        assertNotSame(a, b);
        assertEquals("minecraft:husk", a.mobId());
        assertEquals("minecraft:skeleton", b.mobId());
    }
}
```

#### References

- `net.ledok.arenas_ld.util.AttributeData` and `EquipmentData` — read these to verify their structure before writing the codec composition.

---

### PD-2 — New `MobSpawnerBlockEntity` (data model)

**Goal**: register the new block + block entity, with the `EntityDefinition` field, codec NBT, and op-permission `useWithoutItem` opening a GUI (the GUI itself lands in PD-6). No spawning logic yet — PD-3.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/block/MobSpawnerBlock.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/blockentity/MobSpawnerBlockEntity.java`
- `src/main/resources/assets/arenas_ld/blockstates/mob_spawner_v2.json`
- `src/main/resources/assets/arenas_ld/models/block/mob_spawner_v2.json`
- `src/main/resources/assets/arenas_ld/models/item/mob_spawner_v2.json`
- `src/main/resources/assets/arenas_ld/textures/block/mob_spawner_v2.png` — 16×16 placeholder, solid cyan `#00FFFF`

**Files to modify:**
- `BlockRegistry.java` — register as `mob_spawner_v2`
- `BlockEntitiesRegistry.java` — register as `mob_spawner_v2_be`

#### Block spec

Mirror `RoomControllerBlock`. `BaseEntityBlock`, `simpleCodec`, `newBlockEntity`, `getRenderShape = MODEL`. The `useWithoutItem` opens the GUI (which exists from PD-6's perspective; in PD-2 stub it to a no-op like PC-1).

For PD-2 alone, the `useWithoutItem` body should be:

```java
@Override
protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
    if (player.getMainHandItem().getItem() instanceof LinkerItem || player.getOffhandItem().getItem() instanceof LinkerItem) {
        return InteractionResult.PASS;
    }
    return InteractionResult.SUCCESS;  // GUI lands in PD-6
}
```

Don't add the op check or `openMenu` here yet — PD-6 wires those in.

#### Block entity spec

```java
package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MobSpawnerBlockEntity extends BlockEntity {

    private EntityDefinition entityDefinition = EntityDefinition.DEFAULT;

    public MobSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.MOB_SPAWNER_V2_BLOCK_ENTITY, pos, state);
    }

    public EntityDefinition getEntityDefinition() {
        return entityDefinition;
    }

    public void setEntityDefinition(EntityDefinition def) {
        this.entityDefinition = def;
        setChanged();
    }

    // NBT via codec
    private static final Codec<MobSpawnerBlockEntity.State> STATE_CODEC =
        RecordCodecBuilder.create(i -> i.group(
            EntityDefinition.CODEC.fieldOf("entity").forGetter(State::entity)
        ).apply(i, State::new));

    private record State(EntityDefinition entity) {}

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        STATE_CODEC.encodeStart(NbtOps.INSTANCE, new State(entityDefinition))
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                "Failed to save MobSpawner at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        if (nbt.contains("State")) {
            STATE_CODEC.parse(NbtOps.INSTANCE, nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                    "Failed to load MobSpawner at {}: {}", worldPosition, err))
                .ifPresent(state -> this.entityDefinition = state.entity());
        }
    }
}
```

#### Acceptance

- Block places, renders as solid cyan.
- Block entity persists `entityDefinition` across save/load.
- `setEntityDefinition` calls `setChanged()`.
- `getEntityDefinition()` returns a non-null value (defaults to `EntityDefinition.DEFAULT`).
- Build passes.

#### Don'ts

- No GUI yet — PD-6.
- No spawning method yet — PD-3.
- No tick method.
- No `ExtendedScreenHandlerFactory` implementation — PD-6.
- Do not flatten `entityDefinition` into the BE's top-level NBT (i.e. don't write `mobId` directly into the BE tag). Keep it nested under `State.entity` so the codec is one atomic unit.

#### References

- PC-1 + PC-2 — same shape, different fields.

---

### PD-3 — `spawnSingleScaled` on new MobSpawner

**Goal**: add the spawn method to the new `MobSpawnerBlockEntity`, mirroring PC-3-old's signature.

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/dungeon/blockentity/MobSpawnerBlockEntity.java`

#### Method spec

```java
/**
 * Spawn one mob using this spawner's EntityDefinition, scaled by the given health multiplier.
 * Called by the v4.0 RoomController.
 *
 * @return the spawned entity, or null on failure
 */
@Nullable
public LivingEntity spawnSingleScaled(ServerLevel world, double healthMultiplier) {
    // Same body as the legacy MobSpawnerBlockEntity.spawnSingleScaled, except:
    // - source the mobId, attributes, and equipment from `entityDefinition`
    // - use the legacy applyEquipment helper or inline equivalent (NO drop chance — same as legacy MobSpawnerBlockEntity)
    // - log warns referencing "MobSpawner (v4.0)" so admins can tell where it came from
}
```

The detailed body is a near-verbatim copy of PC-3-old's `MobSpawnerBlockEntity.spawnSingleScaled` method body, with field accesses changed:
- `this.mobId` → `this.entityDefinition.mobId()`
- `this.attributes` → `this.entityDefinition.attributes()`
- `this.equipment.head` → `this.entityDefinition.equipment().head` (and same for chest/legs/feet/mainHand/offHand)

#### Equipment helper note

The legacy `MobSpawnerBlockEntity` has a private `applyEquipment(LivingEntity, EquipmentSlot, String)` method. We can't call it from the new class (it's private and in a different class). Two options:

- **Option A (use the boss's helper)**: call `EntityEquipmentHelper.applyEquipment(living, slot, itemId, 0.0F)` with drop chance 0 — gives the regular-mob behavior. This is what I'd do.
- **Option B (inline the implementation)**: copy the 5-line body into a private method on the new BE.

Pick option A. The drop chance of 0 means equipped items won't drop on death, matching legacy behavior.

#### Acceptance

- Method added.
- Returns null + WARN log on invalid mobId.
- Returns null + WARN log when entity isn't a `LivingEntity`.
- Scales `minecraft:generic.max_health` only.
- Equipment applied via `EntityEquipmentHelper.applyEquipment(..., 0.0F)`.
- Healed to full.
- Spawn position: spawner center + 1 Y, random yaw.

#### Don'ts

- Do not introduce per-call overrides (extra attributes, etc.). The signature is `(ServerLevel, double)`.
- Do not call legacy MobSpawnerBlockEntity methods.
- Do not add team-assignment.
- Do not retry spawning on failure.

#### References

- `MobSpawnerBlockEntity.spawnSingleScaled` (legacy) — the template, just with field accesses through `entityDefinition`.
- `EntityEquipmentHelper.applyEquipment(LivingEntity, EquipmentSlot, String, float)` — the helper to use.

---

### PD-4 — Teach `RoomController` to dispatch to new MobSpawner

**Goal**: extend `RoomControllerBlockEntity.activate(...)` to recognize the new `MobSpawnerBlockEntity` in addition to the legacy one. Without this, rooms can't drive the v4.0 spawners.

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/dungeon/blockentity/RoomControllerBlockEntity.java`

#### Spec

In the `activate(ServerLevel world, TierConfig tier)` method's loop, add a third `instanceof` arm:

```java
for (BlockPos pos : spawnerPositions) {
    BlockEntity be = world.getBlockEntity(pos);
    LivingEntity entity = null;
    if (be instanceof net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity legacyMob) {
        entity = legacyMob.spawnSingleScaled(world, tier.healthMultiplier());
    } else if (be instanceof net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity legacyBoss) {
        entity = legacyBoss.spawnSingleScaled(world, tier.healthMultiplier());
    } else if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity newMob) {
        entity = newMob.spawnSingleScaled(world, tier.healthMultiplier());
    } else {
        // ... existing warn-and-skip
    }
    // ... existing tracking
}
```

The new arm goes AFTER the two legacy arms so the order is "legacy first, new second." Order doesn't actually matter for correctness (the classes are mutually exclusive), but legacy-first reads as "support old code, then new."

Imports to add: there will be a name collision because both legacy and new `MobSpawnerBlockEntity` types exist. Codex must use **fully-qualified class names** in this method body (as shown above with `net.ledok.arenas_ld.block.entity...` and `net.ledok.arenas_ld.dungeon.blockentity...`) OR alias one of them via import statement order — but fully-qualified is clearer here. Don't try to import both unqualified.

#### Acceptance

- The `activate` method now has three `instanceof` arms (legacy MobSpawner, legacy DBS, new MobSpawner — DBS new is added in PD-5).
- Existing behavior unchanged for legacy spawners.
- The new MobSpawner can be linked to a room and spawned by `activate`.

#### Don'ts

- Do not refactor the if/else chain into a polymorphic dispatch (interface, visitor pattern, etc). The 4-class case is the entire universe; switch-expression-by-instance is fine for 4 cases.
- Do not extract the spawn-and-track logic into a helper method. The chain is straightforward and changing it now means re-touching this file in PD-5.
- Do not add the new DBS arm yet — PD-5 does that.

#### References

- PC-3's `activate` method (the current state).

---

### PD-5 — New `DungeonBossSpawnerBlockEntity`

**Goal**: a new DBS in `dungeon.block` + `dungeon.blockentity`, owning `EntityDefinition`, entrance position, and room list. With `spawnSingleScaled`. With `RoomController.activate` extended to recognize it.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/block/DungeonBossSpawnerBlock.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/blockentity/DungeonBossSpawnerBlockEntity.java`
- `src/main/resources/assets/arenas_ld/blockstates/dungeon_boss_spawner_v2.json`
- `src/main/resources/assets/arenas_ld/models/block/dungeon_boss_spawner_v2.json`
- `src/main/resources/assets/arenas_ld/models/item/dungeon_boss_spawner_v2.json`
- `src/main/resources/assets/arenas_ld/textures/block/dungeon_boss_spawner_v2.png` — 16×16 placeholder, solid red `#FF0000`

**Files to modify:**
- `BlockRegistry.java` — register `dungeon_boss_spawner_v2`
- `BlockEntitiesRegistry.java` — register `dungeon_boss_spawner_v2_be`
- `RoomControllerBlockEntity.java` — add a 4th `instanceof` arm in `activate`

#### Block spec

Same shape as PD-2's MobSpawnerBlock: `BaseEntityBlock` + `simpleCodec` + `getRenderShape = MODEL` + `useWithoutItem` returning SUCCESS (GUI in PD-6).

#### Block entity spec

Fields:

| Field | Type | Purpose |
|---|---|---|
| `entityDefinition` | `EntityDefinition` | boss's mob/attrs/equipment (same shape as MobSpawner) |
| `entrancePos` | `BlockPos` | where players spawn when this dungeon instance is selected |
| `entranceDimension` | `ResourceKey<Level>` | dimension for the entrance |
| `rooms` | `List<BlockPos>` | ordered list of RoomController positions; rooms[last] is the boss room (it'll reference *this* DBS as its spawner) |

```java
package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DungeonBossSpawnerBlockEntity extends BlockEntity {

    private EntityDefinition entityDefinition = EntityDefinition.DEFAULT
        .withMobId("minecraft:zombie");
    private BlockPos entrancePos = BlockPos.ZERO;
    private ResourceKey<Level> entranceDimension = Level.OVERWORLD;
    private final List<BlockPos> rooms = new ArrayList<>();

    public DungeonBossSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.DUNGEON_BOSS_SPAWNER_V2_BLOCK_ENTITY, pos, state);
    }

    public EntityDefinition getEntityDefinition() { return entityDefinition; }
    public void setEntityDefinition(EntityDefinition def) {
        this.entityDefinition = def;
        setChanged();
    }
    public BlockPos getEntrancePos() { return entrancePos; }
    public ResourceKey<Level> getEntranceDimension() { return entranceDimension; }
    public void setEntrance(BlockPos pos, ResourceKey<Level> dim) {
        this.entrancePos = pos;
        this.entranceDimension = dim;
        setChanged();
    }
    public List<BlockPos> getRooms() { return Collections.unmodifiableList(rooms); }

    public boolean addRoom(BlockPos pos) {
        if (rooms.contains(pos)) return false;
        rooms.add(pos);
        setChanged();
        return true;
    }

    public boolean removeRoom(BlockPos pos) {
        boolean removed = rooms.remove(pos);
        if (removed) setChanged();
        return removed;
    }

    public boolean moveRoom(int from, int to) {
        if (from < 0 || from >= rooms.size() || to < 0 || to >= rooms.size()) return false;
        if (from == to) return false;
        BlockPos moved = rooms.remove(from);
        rooms.add(to, moved);
        setChanged();
        return true;
    }

    public void clearRooms() {
        if (!rooms.isEmpty()) {
            rooms.clear();
            setChanged();
        }
    }

    // --- spawnSingleScaled (mirrors PD-3) ---
    // Same body as PD-3 but uses entityDefinition.equipment().dropChance for the drop chance
    // (a boss is special; equipment drop chance comes from the equipment record).
    //
    // NOTE: this is the key difference from PD-3. Boss equipment drops with the equipment's
    // configured chance, regular mobs do not drop equipped items.

    // --- NBT ---

    private record State(
        EntityDefinition entity,
        BlockPos entrancePos,
        ResourceKey<Level> entranceDim,
        List<BlockPos> rooms
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityDefinition.CODEC.fieldOf("entity").forGetter(State::entity),
            BlockPos.CODEC.fieldOf("entrancePos").forGetter(State::entrancePos),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("entranceDim").forGetter(State::entranceDim),
            BlockPos.CODEC.listOf().fieldOf("rooms").forGetter(State::rooms)
        ).apply(i, State::new));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        State.CODEC.encodeStart(NbtOps.INSTANCE,
            new State(entityDefinition, entrancePos, entranceDimension, rooms))
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                "Failed to save DungeonBossSpawner at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        if (nbt.contains("State")) {
            State.CODEC.parse(NbtOps.INSTANCE, nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                    "Failed to load DungeonBossSpawner at {}: {}", worldPosition, err))
                .ifPresent(state -> {
                    this.entityDefinition = state.entity();
                    this.entrancePos = state.entrancePos();
                    this.entranceDimension = state.entranceDim();
                    this.rooms.clear();
                    this.rooms.addAll(state.rooms());
                });
        }
    }
}
```

#### Room dispatch update

Add the 4th arm in `RoomControllerBlockEntity.activate`:

```java
} else if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity newBoss) {
    entity = newBoss.spawnSingleScaled(world, tier.healthMultiplier());
}
```

Place it after the new MobSpawner arm from PD-4. So the order is: legacy mob, legacy boss, new mob, new boss.

#### Acceptance

- Block places, renders solid red, persists across save/load.
- Block entity round-trips `entityDefinition`, `entrancePos`, `entranceDimension`, `rooms`.
- `addRoom` deduplicates and returns false on duplicate.
- `moveRoom(from, to)` reorders correctly. Returns false on invalid indices.
- `clearRooms` no-ops on empty.
- `spawnSingleScaled` produces a boss-like entity with equipment-drop-chance applied.
- `RoomController.activate` correctly dispatches to the new DBS.

#### Don'ts

- Do not add fields beyond the four listed. No `closeTimer`, no `dungeonTime`, no loot — that's the controller's job in Phase E.
- Do not store backreferences to controllers. The controller knows the DBS (via its instance list); the DBS doesn't know about controllers. Top-down ownership.
- Do not add a `groupId`. Party logic is the controller's job.
- Do not add `setRoomsOrdered(List<BlockPos>)` — modify the list via add/remove/move only. Bulk-set will be useful in Phase F for the Linker, can be added then if needed.

#### References

- PD-2 (new MobSpawner) — same shape, more fields.
- PC-2 (RoomController) — same `add/remove/clear` pattern.
- PD-3 — `spawnSingleScaled` template (with the equipment dropChance variant).

---

### PD-6 — Admin GUIs for the new spawners

**Goal**: GUIs for both new spawners. They're admin-only screens for editing the `EntityDefinition`. The new DBS GUI also lets admins set the entrance position, view/manage the rooms list.

This is the largest task in Phase D in lines of code. Two screens, two handlers, two data records, and several C2S payloads.

**Files to create (MobSpawner GUI):**
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/MobSpawnerScreen.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/MobSpawnerScreenHandler.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/MobSpawnerData.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/UpdateMobSpawnerEntityDefPayload.java`

**Files to create (DungeonBossSpawner GUI):**
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/DungeonBossSpawnerScreen.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/DungeonBossSpawnerScreenHandler.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/DungeonBossSpawnerData.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/UpdateDbsEntityDefPayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/UpdateDbsEntrancePayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/DbsRemoveRoomPayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/DbsMoveRoomPayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/DbsClearRoomsPayload.java`

**Files to modify:**
- `MobSpawnerBlockEntity.java` (new) — implement `ExtendedScreenHandlerFactory<MobSpawnerData>`
- `DungeonBossSpawnerBlockEntity.java` (new) — implement `ExtendedScreenHandlerFactory<DungeonBossSpawnerData>`
- `MobSpawnerBlock.java` (new) and `DungeonBossSpawnerBlock.java` (new) — fill in `useWithoutItem` with op gate + open menu
- `ModScreenHandlers.java` — register both new handler types
- `ArenasLdClient.java` — register both screen factories
- `ModPacketTypeRegistry.java` — register 6 new C2S payload types
- `SpawnerPacketHandlers.java` — add 6 new C2S handlers
- Both lang files — add translation keys

#### MobSpawner screen content

Single column, ~250×220:

```
+----------------------------------+
| Mob Spawner                  [X] |
+----------------------------------+
| Mob ID: [text field            ] |
+----------------------------------+
| [ Edit Attributes... ]           |
| [ Edit Equipment...  ]           |
+----------------------------------+
| (status indicator if needed)     |
+----------------------------------+
```

The mob ID text field is server-synced on change (typical pattern: EditBox.setResponder sends a payload). The "Edit Attributes" and "Edit Equipment" buttons open the **existing** legacy screens (`MobAttributesScreen`, `EquipmentScreen`) — they're generic and tied to `AttributeProvider` / `EquipmentProvider` interfaces.

The new `MobSpawnerBlockEntity` therefore needs to implement those interfaces:

```java
public class MobSpawnerBlockEntity extends BlockEntity
        implements AttributeProvider, EquipmentProvider, ExtendedScreenHandlerFactory<MobSpawnerData> {
    // ...
    @Override public List<AttributeData> getAttributes() { return entityDefinition.attributes(); }
    @Override public void setAttributes(List<AttributeData> attrs) {
        setEntityDefinition(entityDefinition.withAttributes(attrs));
    }
    @Override public EquipmentData getEquipment() { return entityDefinition.equipment(); }
    @Override public void setEquipment(EquipmentData eq) {
        setEntityDefinition(entityDefinition.withEquipment(eq));
    }
}
```

That's enough to wire into the existing `UpdateAttributesPayload` and `UpdateEquipmentPayload` handlers in `SpawnerPacketHandlers`.

Similar for the new DBS — it implements `AttributeProvider` and `EquipmentProvider` too, sourcing/setting through `entityDefinition`.

#### DBS screen content

Two-column wider screen, ~340×260:

```
+--------------------------------------+
| Dungeon Boss Spawner             [X] |
+--------------------------------------+
| Mob ID:    [text field            ]  |
| [ Attributes ] [ Equipment ]         |
+--------------------------------------+
| Entrance: [x] [y] [z]  Dim: [overw.] |
| [ Set to player's position ]         |
+--------------------------------------+
| Rooms:                          [+]  |
|  1. [x, y, z]  [↑] [↓] [✗]           |
|  2. [x, y, z]  [↑] [↓] [✗]           |
|  3. [x, y, z]  [↑] [↓] [✗]           |
|                          [Clear All] |
+--------------------------------------+
```

The "Set to player's position" button captures the player's current location (server-side, on payload receipt) — useful because admins typically place the DBS, walk to the entrance spot, and want one click to set it.

The rooms list shows ordered positions with reorder (`↑` / `↓`), remove (`✗`), and a Clear All button. No inline add — Linker only (Phase F).

#### Acceptance

- Both screens open for ops; non-ops get the permission message.
- Mob ID edit syncs to server.
- Attributes/Equipment buttons open the existing generic screens, which save back through the new BE's AttributeProvider/EquipmentProvider impl.
- DBS entrance fields edit (each typed change sends a payload after a brief debounce, OR an "Apply" button — pick the simpler pattern, debounce is fine if it matches the existing screens).
- "Set to player's position" button captures the player's pos+dim and writes it server-side.
- Room reorder/remove/clear-all all work and persist.

#### Don'ts

- Do not write a custom AttributesScreen / EquipmentScreen for the new spawners. Reuse the existing generic ones.
- Do not store the AttributesScreen / EquipmentScreen entry positions in this BE. The generic screens hold a back-reference via the data payload they were opened with — let them.
- Do not allow inline room adding from the GUI.
- Do not validate mob IDs in the GUI. Server-side spawn handles invalid IDs gracefully.
- Do not auto-refresh either screen as the BE changes. Snapshot-on-open like PC-4.
- Do not include the BE's `entityDefinition` in the screen's title or display — just the static "Mob Spawner" / "Dungeon Boss Spawner" label.

#### References

- PC-4 — RoomController GUI pattern.
- Legacy `MobSpawnerScreen` and `DungeonBossSpawnerScreen` — old patterns, especially how they reach into `MobAttributesScreen` / `EquipmentScreen`. The new screens follow the same approach.

---

### PD-7 — Lang + creative tab

**Goal**: add display names for both new blocks and the GUI keys, plus creative tab entries.

**Files to modify:**
- `src/main/resources/assets/arenas_ld/lang/en_us.json`
- `src/main/resources/assets/arenas_ld/lang/uk_ua.json`
- `src/main/java/net/ledok/arenas_ld/registry/ModCreativeModeTabs.java`

#### Translation keys to add

Both files need:

```json
"block.arenas_ld.mob_spawner_v2": "Mob Spawner (v2)",
"block.arenas_ld.dungeon_boss_spawner_v2": "Dungeon Boss Spawner (v2)",
"gui.arenas_ld.mob_spawner_v2.title": "Mob Spawner",
"gui.arenas_ld.dungeon_boss_spawner_v2.title": "Dungeon Boss Spawner",
"gui.arenas_ld.spawner_v2.mob_id": "Mob ID:",
"gui.arenas_ld.spawner_v2.attributes_button": "Attributes...",
"gui.arenas_ld.spawner_v2.equipment_button": "Equipment...",
"gui.arenas_ld.dungeon_boss_spawner_v2.entrance": "Entrance:",
"gui.arenas_ld.dungeon_boss_spawner_v2.entrance_dim": "Dim:",
"gui.arenas_ld.dungeon_boss_spawner_v2.use_player_pos": "Set to my position",
"gui.arenas_ld.dungeon_boss_spawner_v2.rooms_label": "Rooms (use Linker to add):",
"gui.arenas_ld.dungeon_boss_spawner_v2.rooms_empty": "(empty — use Linker to add)",
"gui.arenas_ld.dungeon_boss_spawner_v2.button.clear_rooms": "Clear All",
"message.arenas_ld.spawner_v2.no_permission": "You don't have permission to configure this block."
```

Ukrainian translations follow the same pattern as PC-4.1.

The "(v2)" suffix on the block names is a temporary signal so admins can distinguish them in creative inventory. Phase H removes the legacy versions and we'll drop the suffix then.

#### Creative tab

Add both blocks to `ModCreativeModeTabs.java`, placed near the existing spawner entries. Use the same `entries.accept(...)` pattern.

#### Acceptance

- Both new blocks have proper display names in-game.
- Both appear in the Arenas_LD creative tab.
- All listed translation keys exist in both lang files.
- Build passes.

#### Don'ts

- Do not remove the legacy spawner entries from the creative tab. They stay until Phase H.
- Do not add "(v1)" or any suffix to the legacy spawner names. They're still the canonical names from a user's perspective today.

---

### Phase D exit criteria

After all 7 tasks:

- `./gradlew build test` passes.
- 4 spawner block types coexist: legacy MobSpawner, legacy DungeonBossSpawner, new MobSpawner, new DungeonBossSpawner.
- The room's `activate(...)` correctly dispatches to all 4.
- Both new spawners have working admin GUIs with the existing Attributes / Equipment screens reused.
- Both new spawners support full save/load via codec.
- Both new blocks are in the creative tab and have proper display names.
- Legacy code is unchanged except for one thing: PC-3-old's two `spawnSingleScaled` methods on the legacy spawners stay; the new spawners have their own copies.
- Total new files: ~17 (2 blocks, 2 BEs, 1 EntityDefinition, 6 assets, 2 screens + 2 handlers + 2 data, 6 packets).
- Total LOC: probably ~1200-1500.
- Phase E can now build the controller against `dungeon.blockentity.DungeonBossSpawnerBlockEntity` (new).

---

## Phase E — Controller + Run lifecycle (1 PR)

**Goal of the phase**: build the new `DungeonController` block in the new package, port the legacy lobby system into it (with cleanup), and implement the centerpiece of v4.0 — the run lifecycle: starting runs, ticking them, transitioning phases, handling win/loss, distributing loot, updating leaderboards.

After Phase E, players can right-click the new controller block, create a party (or run solo), pick a tier, start a dungeon instance, see the dungeon timer + close timer boss bars, fight through rooms, defeat the boss, get per-player loot, see the leaderboard update. The full v4.0 dungeon experience exists.

The legacy controller block stays in parallel. Phase G (was Phase H) deletes it.

### Architectural reminder

The controller is the brain. It owns:

- **Admin config (NBT)**: list of instance refs (DBS positions), tier configs per `DifficultyTier`, max party size, cooldown ticks, leaderboard maps per tier.
- **Lobby state (NBT)**: active lobbies (party formation pre-run).
- **Active runs (NBT)**: `Map<BlockPos, DungeonRun>` keyed by DBS position. NBT-persisted so server restart resumes runs — phase + outcome decide what happens on load.
- **Cooldown state (NBT)**: per-instance cooldown remaining ticks.

The controller drives everything per server tick via `RoomController.refreshAliveMobs(world)` calls into rooms, observation of `isCleared()`, advancement of `currentRoomIndex`, decrement of timers, etc.

### Decisions locked in for Phase E

These came up during planning; pinned here so individual tasks don't re-litigate:

- **Lobby = port-with-cleanup from legacy.** We copy the legacy lobby code from `DungeonControllerBlockEntity` and clean up while pasting. We don't redesign the lobby; we move it. Lobbies are tied to runs: starting a run dissolves the lobby into a run; participants can't join other lobbies until the run ends. The existing `BusyStateCompat` integration handles this in legacy.
- **Loot eligibility at win = "online right now."** Every UUID in `run.participants` with `status != REMOVED` is checked: if `world.getPlayerByUUID(uuid) != null` → gets a bundle; if offline → forfeits. Simple, removes need for pending-rewards persistence.
- **Tier configs are snapshotted at run start.** `DungeonRun.resolvedTierConfig` is the snapshot; admin edits to the controller's tier configs during the run don't affect that run.
- **Mid-run admin edits to instances.** Adding instances is fine. Removing the instance an active run is using is **deferred** — the controller marks it "pending deletion" and removes it in run finalization. Tracked via a Set field on the controller.
- **Static `CONTROLLERS` set lives in the `DungeonManager` singleton in Phase F, not on the BE.** For Phase E, the new controller keeps its own *instance method* registration to a TBD location. The mixin integration that needs cross-controller lookup is Phase F.
- **Block + BE naming**: new controller block is `arenas_ld:dungeon_controller_v2`, BE is `arenas_ld:dungeon_controller_v2_be`. Class names in `net.ledok.arenas_ld.dungeon.block.DungeonControllerBlock` and `net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity`. Same pattern as the v2 spawners. Phase G renames everything when legacy is deleted.
- **Boss bars**: two per run, attached to participants only. `ServerBossEvent` instances created and destroyed by the run.
  - Dungeon time bar (blue): visible during STARTING + RUNNING phase.
  - Close timer bar (red): visible during CLOSING phase.
  - No boss HP bar (deferred).
- **Save-and-restart resilience**: an active run persisted across server restart resumes in its saved phase. The run's tick logic handles "did anything change while we were down?" naturally — refresh aliveMobs in current room, check timer, etc.
- **One run per controller at a time, multi-instance.** Wait — actually multi-run. A controller can have N instances; each instance can have an active run independently. So the active-runs map is `Map<BlockPos, DungeonRun>` keyed by DBS position. Two parties can simultaneously run different instances of the same dungeon.

### Tasks in this phase

12 active tasks. Order matters for several pairs (each task lists prerequisites).

- **PE-1** — New `DungeonControllerBlock` + skeleton `DungeonControllerBlockEntity`. Empty BE, just registration. Same shape as PC-1 / PD-2.
- **PE-2** — `DungeonControllerBlockEntity` data model (instances, tier configs, cooldown timers, leaderboard, pendingDeletions). NBT via codec. No lobby, no runs yet.
- **PE-3** — Port lobby data structures (`Lobby`, `LobbyStatus`, `LobbyVisibility`) into `net.ledok.arenas_ld.dungeon.lobby`. Add codecs. New file location, same field shape as legacy. Update `DungeonControllerBlockEntity` to hold a `List<Lobby>`.
- **PE-4** — Lobby operations as methods on `DungeonControllerBlockEntity`: create/invite/accept/decline/leave/kick/disband/visibility/transferOrDissolve. New C2S packets where needed.
- **PE-5** — Lobby admin/player GUI. Tabbed: "Lobbies" tab listing active lobbies, "My Lobby" tab if the player is in one.
- **PE-6** — `DungeonRun.tick(ServerLevel)` method + phase transition logic. Pure state machine, no GUI yet, no real loot. Drives rooms via `refreshAliveMobs` + `isCleared`. Calls into stubs for `handleWin` / `handleLoss`.
- **PE-7** — `DungeonRun.startRun(ServerLevel, List<UUID> party, DifficultyTier, boolean hardcore)`. Capture return points, force-load chunks, reset rooms, teleport players, kick off room[0], start the boss bar. Returns true on success.
- **PE-8** — `DungeonRun.handleWin()`. Roll per-player loot from `tier.perPlayerLootTable`, distribute to online lootEligible UUIDs, upsert leaderboard, transition to CLOSING. (`finalize` is part of phase tick — already covered in PE-6.)
- **PE-9** — `DungeonRun.handleLoss(reason)`. Despawn boss, teleport players to their return points, transition to CLOSING with outcome.
- **PE-10** — Downed player tick + hardcore death handling. Wires into the existing mixin.
- **PE-11** — Admin GUI: Instances tab + General tab.
- **PE-12** — Admin GUI: per-tier tabs (NORMAL / HARD / HELL).

---

### PE-1 — New `DungeonControllerBlock` + skeleton BE

**Goal**: registration plumbing for the new controller block. No data, no logic. Smallest task, proves the plumbing.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/block/DungeonControllerBlock.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/blockentity/DungeonControllerBlockEntity.java` (empty stub, fields land in PE-2)
- `src/main/resources/assets/arenas_ld/blockstates/dungeon_controller_v2.json`
- `src/main/resources/assets/arenas_ld/models/block/dungeon_controller_v2.json`
- `src/main/resources/assets/arenas_ld/models/item/dungeon_controller_v2.json`
- `src/main/resources/assets/arenas_ld/textures/block/dungeon_controller_v2.png` — 16×16 placeholder, solid green `#00FF00`

**Files to modify:**
- `BlockRegistry.java` — register as `dungeon_controller_v2`
- `BlockEntitiesRegistry.java` — register as `dungeon_controller_v2_be`

#### Block spec

Mirror `DungeonBossSpawnerBlock` from PD-5: `BaseEntityBlock` with `simpleCodec`, `getRenderShape = MODEL`, `useWithoutItem` with the PD-2 no-op form (PASS on Linker, otherwise SUCCESS — PE-5 fills in the GUI open).

#### Block entity spec (PE-1 stub)

Just enough to compile:

```java
package net.ledok.arenas_ld.dungeon.blockentity;

import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DungeonControllerBlockEntity extends BlockEntity {
    public DungeonControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.DUNGEON_CONTROLLER_V2_BLOCK_ENTITY, pos, state);
    }
}
```

#### Acceptance

- Block places, renders solid green.
- BE persists empty NBT.
- `./gradlew build` passes.

#### Don'ts

- No fields yet. PE-2.
- No GUI, no `useWithoutItem` body beyond the PD-2 stub. PE-5.
- No `ExtendedScreenHandlerFactory` impl.
- No ticker. PE-6 adds it.

---

### PE-2 — `DungeonControllerBlockEntity` data model

**Goal**: fields, getters, admin setters, codec NBT. Pure data. Lobby + runs land in PE-3 and PE-6.

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/dungeon/blockentity/DungeonControllerBlockEntity.java`

#### Field design

| Field | Type | Purpose |
|---|---|---|
| `instances` | `List<BlockPos>` | DBS positions registered as dungeon instances |
| `tierConfigs` | `Map<DifficultyTier, TierConfig>` | per-tier config; defaults populated on creation |
| `cooldownTicks` | `int` | configured cooldown per instance, applies post-finalize |
| `closeTimerSeconds` | `int` | configured close-timer length after win/loss |
| `maxPartySize` | `int` | maximum players in a lobby/run |
| `instanceCooldownTimers` | `Map<BlockPos, Integer>` | current cooldown remaining per instance |
| `pendingInstanceRemovals` | `Set<BlockPos>` | instances marked for deletion mid-run; removed at run finalize |
| `leaderboards` | `Map<DifficultyTier, List<LeaderboardEntry>>` | per-tier leaderboard, append-only |

The `tierConfigs` map is initialized to `Map.of(NORMAL, NORMAL_DEFAULT, HARD, HARD_DEFAULT, HELL, HELL_DEFAULT)` on first construction. The `cooldownTicks` defaults to `5 * 60 * 20` (5 minutes). `closeTimerSeconds` defaults to 30. `maxPartySize` defaults to 4. Other maps start empty.

#### Public getters

All fields exposed via getters. Map/list getters return unmodifiable views.

#### Public setters (op-callable)

- `setTierConfig(DifficultyTier, TierConfig)` — overwrites one tier
- `setCooldownTicks(int)` — must be ≥ 0
- `setCloseTimerSeconds(int)` — must be > 0
- `setMaxPartySize(int)` — must be 1..16
- `addInstance(BlockPos)` — deduplicates, returns false on duplicate
- `removeInstance(BlockPos)` — returns false if not present; if there's an active run for this instance, mark for deletion (`pendingInstanceRemovals.add(pos)`) and return true without actually removing from `instances`. Caller checks state to know which happened.
- `moveInstance(int from, int to)` — reorder, returns false on invalid indices

#### Package-private mutators (called by Run lifecycle in PE-6+)

- `startInstanceCooldown(BlockPos)` — sets the cooldown to `cooldownTicks`
- `decrementInstanceCooldown(BlockPos)` — used in tick
- `clearInstanceCooldown(BlockPos)`
- `addLeaderboardEntry(DifficultyTier, LeaderboardEntry)` — appends to that tier's leaderboard
- `clearPendingRemoval(BlockPos)` — removes from `pendingInstanceRemovals` after `instances.remove()` succeeds
- `executePendingRemoval(BlockPos)` — atomic: `pendingInstanceRemovals.remove(pos)` + `instances.remove(pos)`. Called from run `finalize()`.

#### NBT codec

Internal `State` record with all fields, codec via `RecordCodecBuilder`. Map codecs:
- `Codec.unboundedMap(DifficultyTier.CODEC, TierConfig.CODEC)` for tier configs and leaderboards
- `Codec.unboundedMap(BlockPos.CODEC, Codec.INT)` for cooldown timers — **BUT** map keys must be string-form, not BlockPos. Use `BlockPos.CODEC.xmap(...)` to string-encode, or wrap in a record list.

Actually: for `instanceCooldownTimers` and `leaderboards`, easier to use a `List<Pair>`-style codec:

```java
record InstanceCooldown(BlockPos pos, int ticks) { ... }
private static final Codec<InstanceCooldown> COOLDOWN_CODEC = ...;
// in State CODEC:
COOLDOWN_CODEC.listOf().fieldOf("instanceCooldowns").forGetter(state -> 
    state.instanceCooldowns.entrySet().stream()
        .map(e -> new InstanceCooldown(e.getKey(), e.getValue()))
        .toList()
)
```

Or use `Codec.unboundedMap(BlockPos.CODEC, Codec.INT)` if NBT allows BlockPos-as-key — try the simpler form first; if it fails to encode, fall back to the list-of-pairs pattern.

For `leaderboards: Map<DifficultyTier, List<LeaderboardEntry>>`, `Codec.unboundedMap(DifficultyTier.CODEC, LeaderboardEntry.CODEC.listOf())` should work since DifficultyTier serializes to a string.

#### Acceptance

- All fields present with the visibility shown.
- Defaults populated on first BE creation (NORMAL/HARD/HELL tier configs, 5-minute cooldown, 30-second close timer, 4 party max).
- All setters work with their validation rules.
- `addInstance` dedups; `removeInstance` properly handles the pending-removal flow.
- Codec round-trips a populated BE.
- `setChanged()` called from every mutator (public + package-private).
- Build passes.

#### Don'ts

- No lobby fields. PE-3.
- No `Map<BlockPos, DungeonRun>` field for active runs. PE-6.
- No tick method. PE-6.
- No GUI. PE-11.
- No validation of `BlockPos` pointing at a real DBS. Admins can typo; runtime handles missing instances.
- Do not add `ExtendedScreenHandlerFactory`. PE-5 / PE-11.

#### References

- PC-2 for the State-record codec pattern.
- PD-5 for an example with multiple fields.
- Legacy `DungeonControllerBlockEntity` lines 60–230 for the field shape (and what NOT to copy).

---

### PE-3 — Port lobby data structures

**Goal**: bring the lobby data classes into the new package with codecs. Field shape preserved; cleaned up where it's safe.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/lobby/Lobby.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/lobby/LobbyStatus.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/lobby/LobbyVisibility.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/lobby/PendingInvite.java`

**Files to modify:**
- `DungeonControllerBlockEntity.java` — add a `List<Lobby>` field, getter, mutator, NBT.

#### `LobbyStatus` enum

Same values as legacy: `FORMING`, `READY`, `IN_RUN`, `DISBANDED`. Plus a `Codec<LobbyStatus>` fail-safe to `DISBANDED`.

#### `LobbyVisibility` enum

Same as legacy: `PUBLIC`, `FRIENDS`, `PRIVATE`. Codec fail-safe to `PRIVATE`.

#### `Lobby` record

Read the legacy `Lobby` class first. Re-create as a record (legacy is likely a mutable class). Fields:

```java
public record Lobby(
    UUID lobbyId,
    UUID ownerUuid,
    String ownerName,
    Set<UUID> members,             // includes the owner
    Map<UUID, String> memberNames, // cached for display when offline
    Set<UUID> readyMembers,
    DifficultyTier selectedTier,
    boolean hardcoreEnabled,
    LobbyVisibility visibility,
    LobbyStatus status,
    long createdAtTick
) {
    public static final Codec<Lobby> CODEC = ...;

    public Lobby withMemberAdded(UUID uuid, String name) { ... }
    public Lobby withMemberRemoved(UUID uuid) { ... }
    public Lobby withReady(UUID uuid) { ... }
    public Lobby withUnready(UUID uuid) { ... }
    public Lobby withOwner(UUID newOwner) { ... }
    public Lobby withStatus(LobbyStatus newStatus) { ... }
    public Lobby withTier(DifficultyTier tier) { ... }
    public Lobby withHardcore(boolean hardcore) { ... }
    public Lobby withVisibility(LobbyVisibility v) { ... }

    public boolean isFull(int maxSize) { ... }
    public boolean isOwner(UUID uuid) { ... }
    public boolean isMember(UUID uuid) { ... }
    public boolean allReady() { ... }
}
```

The record is immutable; `with*` methods return new instances. Operations in PE-4 use these.

#### `PendingInvite` record

```java
public record PendingInvite(
    UUID lobbyId,
    UUID invitedUuid,
    UUID inviterUuid,
    long expiresAtTick
) {
    public static final Codec<PendingInvite> CODEC = ...;
}
```

Lobby invite expiry: legacy code uses ~30 seconds. Configurable via the controller's `inviteExpiryTicks` field (added to PE-2's spec retroactively — see below).

#### Update PE-2

Add to `DungeonControllerBlockEntity`:

```java
private final List<Lobby> lobbies = new ArrayList<>();
private final List<PendingInvite> pendingInvites = new ArrayList<>();
private int inviteExpiryTicks = 30 * 20; // 30 seconds

public List<Lobby> getLobbies() { return Collections.unmodifiableList(lobbies); }
public List<PendingInvite> getPendingInvites() { return Collections.unmodifiableList(pendingInvites); }
public int getInviteExpiryTicks() { return inviteExpiryTicks; }
public void setInviteExpiryTicks(int ticks) { ... }

// package-private mutators
void addLobby(Lobby l) { ... }
void replaceLobby(Lobby l) { ... } // matches by lobbyId
void removeLobby(UUID lobbyId) { ... }
void addInvite(PendingInvite invite) { ... }
void removeInvite(UUID lobbyId, UUID invitedUuid) { ... }
```

NBT: extend the codec to include the new fields.

#### Acceptance

- Three enum / record types in the new lobby package.
- Codecs round-trip cleanly.
- `Lobby.with*` methods return new instances with the field changed.
- `Lobby.allReady()` true only when every member is in `readyMembers`.
- BE has the new lists + invites + expiry config, codec extended.

#### Don'ts

- Do not port the lobby manipulation **logic** here. Just data structures + state. PE-4 has the operations.
- Do not reference the new BE from inside `Lobby` (no `lobby.startRun(controller, ...)` method). Logic lives on the controller.
- Do not implement equality based on member identity comparison — record's auto-generated `equals` on the full state is fine.

---

### PE-4 — Lobby operations

**Goal**: port lobby manipulation logic from legacy onto the new controller. Each operation is a method on the controller. New C2S packets for each player-initiated action.

**Files to create (operation packets):**
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/CreateLobbyPayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/InvitePlayerPayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/AcceptInvitePayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/DeclineInvitePayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/LeaveLobbyPayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/KickFromLobbyPayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/SetLobbyTierPayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/SetLobbyVisibilityPayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/ToggleReadyPayload.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/StartRunPayload.java`

**Files to modify:**
- `DungeonControllerBlockEntity.java` — add lobby operation methods.
- `ModPacketTypeRegistry.java` — register the 10 new payload types.
- `SpawnerPacketHandlers.java` — add server handlers (rename to `DungeonPacketHandlers` if it's getting too large — but only if Codex wants to; otherwise leave it).

#### Operations on `DungeonControllerBlockEntity`

Each is a server-side method. Validation + side effects + return code. None of them call `setChanged()` directly — the mutators on `Lobby` do that (via the controller's `replaceLobby(...)` mutator after a `with*` call).

```java
/**
 * @return the newly created lobby UUID, or empty if the player is already in a lobby on this controller.
 */
public Optional<UUID> createLobby(ServerPlayer player) { ... }

/** @return true on success. */
public boolean invitePlayer(ServerPlayer inviter, UUID inviteeUuid) { ... }

/** @return true on success. */
public boolean acceptInvite(ServerPlayer invitee, UUID lobbyId) { ... }

/** @return true on success (always true unless the invite didn't exist). */
public boolean declineInvite(ServerPlayer invitee, UUID lobbyId) { ... }

/** @return true on success. Owner leaving triggers transferOrDissolve. */
public boolean leaveLobby(ServerPlayer player) { ... }

/** @return true on success. Owner-only operation. */
public boolean kickFromLobby(ServerPlayer kicker, UUID targetUuid) { ... }

/** Owner-only. */
public boolean setLobbyTier(ServerPlayer player, DifficultyTier tier) { ... }

/** Owner-only. */
public boolean setLobbyHardcore(ServerPlayer player, boolean hardcore) { ... }

/** Owner-only. */
public boolean setLobbyVisibility(ServerPlayer player, LobbyVisibility v) { ... }

/** Toggle the player's own ready state. Anyone in the lobby can call. */
public boolean toggleReady(ServerPlayer player) { ... }

/** Owner-only. Requires all members ready and an available instance. @return Instance BlockPos that the run will use, or empty if blocked. */
public Optional<BlockPos> startRun(ServerPlayer player) { ... }
```

#### Validation rules

These mirror the legacy code (read it):
- A player can only be in one lobby on this controller at a time.
- Invites expire after `inviteExpiryTicks` ticks (cleanup in tick — PE-6).
- Invites can only be sent by lobby owner.
- Invites can only be sent to players who aren't already in a lobby on this controller.
- Lobby owner leaving: ownership transfers to the longest-tenured remaining member; if no other members, the lobby disbands.
- A lobby can't be modified once status is `IN_RUN`.
- `startRun` requirements: status is `READY` or `FORMING` with all-ready, all members online, at least one instance available (not in cooldown, not in active run, not in pending removal), members ≤ maxPartySize.

#### Server handlers

Each packet has a corresponding `ServerPlayNetworking.registerGlobalReceiver` block:
1. Server.execute()
2. Resolve BE from `payload.blockPos()`
3. Verify it's a `DungeonControllerBlockEntity`
4. **Permission**: ops only? No — these are player-facing operations. Anyone can use them. The validation logic in each method enforces party membership/ownership.
5. Call the corresponding method on the BE.
6. After mutation, call `markDirtyAndSync(world, be)`.

The `StartRunPayload` handler is special — on success, the lobby method returns the instance BlockPos and the handler calls `DungeonRun.startRun(...)` which doesn't exist yet (PE-7). For PE-4, the handler can be:

```java
if (be instanceof DungeonControllerBlockEntity controller) {
    Optional<BlockPos> instance = controller.startRun(player);
    if (instance.isPresent()) {
        // TODO PE-7: actually start the run
        ArenasLdMod.LOGGER.info("Run would start at instance {}", instance.get());
    }
    markDirtyAndSync(world, controller);
}
```

#### Acceptance

- All 10 packets exist with codecs and types.
- All 10 operation methods exist on the controller with the validation rules.
- Server handlers wire each packet to its method, op-gate-less (player-facing), with markDirtyAndSync.
- Leaving the lobby as owner correctly transfers or disbands.
- Cannot be in two lobbies on the same controller.
- Cannot start a run unless every member is ready AND an instance is available.

#### Don'ts

- Do not actually start the run. PE-7. StartRunPayload handler logs intent only for now.
- Do not S2C-broadcast lobby changes to every player on the server. The lobby list will be polled in the GUI when opened, and the player joining/leaving gets a chat message.
- Do not add an admin lobby-management API (force disband, force kick). Out of scope; admins can `/data merge` if needed.
- Do not modify `ServerPlayConnectionEvents` here. The disconnect grace period is Phase F.
- Do not store the `ServerPlayer` reference on a `Lobby`. Players come and go; UUIDs are durable. Look up `ServerPlayer` via `world.getPlayerByUUID(uuid)` whenever needed.

#### References

- Legacy `DungeonControllerBlockEntity` lines 539-767 for the operations.
- Each operation's existing chat messages — port the translation keys; add new ones if needed.

---

### PE-5 — Lobby GUI

**Goal**: a player-facing GUI for lobby operations. Two tabs: "Lobbies" (browse + create + join via invite) and "My Lobby" (configure + invite + ready + start).

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/DungeonControllerScreen.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/DungeonControllerScreenHandler.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/DungeonControllerData.java`
- (translation keys in both lang files for tab titles, button labels, status messages)

**Files to modify:**
- `DungeonControllerBlockEntity.java` — implement `ExtendedScreenHandlerFactory<DungeonControllerData>`.
- `DungeonControllerBlock.java` — wire `useWithoutItem` to open the menu.
- `ModScreenHandlers.java` — register.
- `ArenasLdClient.java` — register screen factory.

#### Screen layout

```
+--------------------------------------------+
| Dungeon Controller             [Tab: Lobby]|
+--------------------------------------------+
| [ Lobbies ] [ My Lobby ]                   |
+--------------------------------------------+
| (Lobbies tab content OR My Lobby content)  |
+--------------------------------------------+
```

**Lobbies tab** lists all visible lobbies on this controller:

```
| Public lobbies:                            |
|   Steve's party (2/4)  [Hard]  [Hardcore]  |
|     [ Request invite ]                     |
|   Alice's party (1/4)  [Normal]            |
|     [ Request invite ]                     |
| You have invites:                          |
|   from Bob (Hard)  [ Accept ]  [ Decline ] |
| [ Create new lobby ]                       |
```

"Request invite" sends a chat message to the lobby owner; owner approves via... actually no, simplest: anyone with a public lobby can be joined directly. The "Request invite" button only appears for `FRIENDS` lobbies and sends a "Player X wants to join" message to owner. For `PUBLIC` it's a "Join" button.

Actually, even simpler: leave "request invite" as legacy behavior — it's a join-request message to the owner. Don't redesign.

**My Lobby tab** (only if player is in a lobby):

```
| Lobby: Steve's party                       |
| Owner: Steve                               |
| Members (2/4):                             |
|   Steve  [READY]   (you, owner)            |
|   Alice  [pending]                         |
| Tier: [Normal] [Hard] [Hell]               |
| Visibility: [Public] [Friends] [Private]   |
| Hardcore: [On] [Off]                       |
| [ Ready / Unready ]                        |
| [ Invite Player... ]                       |
| [ Start Run ]  (disabled if not all ready) |
| [ Leave Lobby ]                            |
```

#### `DungeonControllerData` shape

```java
public record DungeonControllerData(
    BlockPos blockPos,
    List<Lobby> visibleLobbies,            // filtered by visibility for this player
    Optional<Lobby> ownLobby,              // the player's current lobby on this controller
    List<PendingInvite> myInvites,         // invites for this player
    int maxPartySize,
    Map<DifficultyTier, TierConfig> tiers  // for display: time, hardcore default
) { ... }
```

The data is computed server-side at `getScreenOpeningData` time using the player's UUID to filter.

#### Acceptance

- Player can open the controller and see lobbies.
- Player can create a lobby; their tab becomes "My Lobby."
- Owner can invite a player; the invitee gets a chat message linking back to the controller.
- Invitee can accept/decline; on accept they join the lobby.
- Owner can toggle tier / visibility / hardcore.
- Any member can toggle ready.
- Owner can start the run (button disabled until all ready and instance available).
- Player can leave; owner-leaving triggers transfer.
- Tab navigation works.

#### Don'ts

- Do not show admin-only fields (instance list, leaderboard, tier configs editing). Admin GUI is PE-11/12.
- Do not auto-refresh the screen on every tick. Snapshot on open. Player closes/reopens for fresh data.
- Do not display lobbies from other controllers. Each controller manages its own lobbies.
- Do not let the screen mutate `Lobby` records client-side. All mutations go through C2S packets.

---

### PE-6 — `DungeonRun.tick` + phase transitions

**Goal**: the centerpiece. The state machine that drives a run from STARTING through DONE.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/run/DungeonRunLifecycle.java` — static utility class containing `tick`, `startRun`, `handleWin`, `handleLoss`, `finalize` methods that operate on a `DungeonRun` + `DungeonControllerBlockEntity`. **Why a static utility class?** `DungeonRun` is in `dungeon.run`, `DungeonControllerBlockEntity` is in `dungeon.blockentity`. Putting lifecycle methods *on* `DungeonRun` would require the run to know about the controller (circular dependency, or the controller in `dungeon.run` package which we don't want). Static helpers in `dungeon.run` package can use the package-private mutators on `DungeonRun` without exposing them publicly. **Note**: lifecycle methods package-private (not public) so they can only be called from same-package + tests.

**Files to modify:**
- `DungeonControllerBlockEntity.java` — add `Map<BlockPos, DungeonRun> activeRuns` field with codec, getter, package-private mutators (`startRun`, `removeRun`). Add ticker via `BlockEntityTicker` that calls into `DungeonRunLifecycle.tick(...)` for each active run.

#### `DungeonControllerBlockEntity.tick`

```java
public static void tick(Level world, BlockPos pos, BlockState state, DungeonControllerBlockEntity be) {
    if (world.isClientSide || !(world instanceof ServerLevel sl)) return;

    // Tick instance cooldowns
    Iterator<Map.Entry<BlockPos, Integer>> cdIter = be.instanceCooldownTimers.entrySet().iterator();
    while (cdIter.hasNext()) {
        var e = cdIter.next();
        int remaining = e.getValue() - 1;
        if (remaining <= 0) {
            cdIter.remove();
            be.setChanged();
        } else {
            e.setValue(remaining);
        }
    }

    // Tick lobbies: expire invites
    long currentTick = sl.getGameTime();
    be.pendingInvites.removeIf(invite -> invite.expiresAtTick() <= currentTick);

    // Tick each active run
    for (DungeonRun run : new ArrayList<>(be.activeRuns.values())) {
        DungeonRunLifecycle.tick(sl, be, run);
    }
}
```

Register the ticker via `getTicker` on the block.

#### `DungeonRunLifecycle.tick`

```java
static void tick(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
    switch (run.phase()) {
        case STARTING -> tickStarting(world, controller, run);
        case RUNNING -> tickRunning(world, controller, run);
        case CLOSING -> tickClosing(world, controller, run);
        case DONE -> { /* shouldn't tick; remove via controller */ }
    }
}

private static void tickStarting(...) {
    // STARTING is brief. Should immediately transition to RUNNING after startRun completes.
    // Defensive: if we land here on a tick, transition to RUNNING.
    run.setPhase(DungeonPhase.RUNNING);
}

private static void tickRunning(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
    // 1. Decrement timer
    run.setDungeonTimerTicks(run.dungeonTimerTicks() - 1);
    if (run.dungeonTimerTicks() <= 0) {
        handleLoss(world, controller, run, DungeonOutcome.LOSS_TIMEOUT);
        return;
    }

    // 2. Check abandonment (no online participants)
    boolean anyOnline = run.participants().keySet().stream()
        .anyMatch(uuid -> world.getPlayerByUUID(uuid) != null);
    if (!anyOnline) {
        handleLoss(world, controller, run, DungeonOutcome.LOSS_ABANDONED);
        return;
    }

    // 3. Drive the current room
    DungeonBossSpawnerBlockEntity dbs = (DungeonBossSpawnerBlockEntity)
        world.getBlockEntity(run.dbsPos());
    if (dbs == null) {
        // DBS unloaded or removed. Hard fail.
        handleLoss(world, controller, run, DungeonOutcome.LOSS_FORCED);
        return;
    }

    List<BlockPos> rooms = dbs.getRooms();
    if (run.currentRoomIndex() >= rooms.size()) {
        // No more rooms; this means the last (boss) room cleared. Win.
        // Actually we should have detected this when room cleared. Defensive.
        handleWin(world, controller, run);
        return;
    }

    BlockPos currentRoomPos = rooms.get(run.currentRoomIndex());
    RoomControllerBlockEntity room = (RoomControllerBlockEntity)
        world.getBlockEntity(currentRoomPos);
    if (room == null) {
        // Room unloaded. Skip this tick; chunk-load will re-engage.
        return;
    }

    if (!room.isActivated()) {
        room.activate(world, run.resolvedTierConfig());
    } else {
        room.refreshAliveMobs(world);
        if (room.isCleared()) {
            room.openDoor(world);
            int next = run.currentRoomIndex() + 1;
            if (next >= rooms.size()) {
                handleWin(world, controller, run);
            } else {
                run.setCurrentRoomIndex(next);
            }
        }
    }

    // 4. Tick downed players
    tickDownedPlayers(world, controller, run);

    // 5. Update boss bar
    updateDungeonTimeBossBar(world, run);
}

private static void tickClosing(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
    run.setCloseTimerTicks(run.closeTimerTicks() - 1);
    updateCloseTimerBossBar(world, run);
    if (run.closeTimerTicks() <= 0) {
        finalize(world, controller, run);
    }
}

static void finalize(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
    // Teleport all online players to their return points
    for (Map.Entry<UUID, PlayerReturnPoint> e : run.returnPoints().entrySet()) {
        ServerPlayer p = world.getServer().getPlayerList().getPlayer(e.getKey());
        if (p != null) {
            PlayerReturnPoint rp = e.getValue();
            ServerLevel target = world.getServer().getLevel(rp.dimension());
            if (target != null) {
                p.teleportTo(target, rp.pos().x, rp.pos().y, rp.pos().z, rp.yaw(), rp.pitch());
            }
        }
    }
    // Hide boss bars
    hideBossBars(run);
    // Clear participants from busy state (BusyStateCompat — Phase F)
    // For PE-6, skip; Phase F integrates.
    // Mark phase DONE
    run.setPhase(DungeonPhase.DONE);
    // Remove from controller
    controller.removeRun(run.dbsPos());
    // Start cooldown on the instance
    controller.startInstanceCooldown(run.dbsPos());
    // Execute pending removal if applicable
    if (controller.getPendingInstanceRemovals().contains(run.dbsPos())) {
        controller.executePendingRemoval(run.dbsPos());
    }
    // Discard rooms
    DungeonBossSpawnerBlockEntity dbs = (DungeonBossSpawnerBlockEntity) world.getBlockEntity(run.dbsPos());
    if (dbs != null) {
        for (BlockPos roomPos : dbs.getRooms()) {
            BlockEntity be = world.getBlockEntity(roomPos);
            if (be instanceof RoomControllerBlockEntity rc) {
                rc.reset(world);
            }
        }
    }
}
```

For PE-6, `handleWin` and `handleLoss` are stubs that just transition phase + set outcome + start the close timer. PE-8 and PE-9 fill them in.

```java
static void handleWin(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
    run.setOutcome(DungeonOutcome.WIN);
    run.setPhase(DungeonPhase.CLOSING);
    run.setCloseTimerTicks(controller.getCloseTimerSeconds() * 20);
    // PE-8: loot, leaderboard
}

static void handleLoss(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run, DungeonOutcome reason) {
    run.setOutcome(reason);
    run.setPhase(DungeonPhase.CLOSING);
    run.setCloseTimerTicks(controller.getCloseTimerSeconds() * 20);
    // PE-9: despawn boss
}
```

#### Acceptance

- Controller's BE ticker is registered and fires.
- Cooldowns decrement and expire.
- Pending invites expire by tick time.
- For each active run: phase machine runs, rooms activate in order, dungeon timer counts down, win condition reached when last room clears, loss triggers on timeout / abandonment.
- Build passes (lots of stubs but compiles).

#### Don'ts

- Do not implement loot. PE-8.
- Do not implement boss despawn on loss. PE-9.
- Do not implement downed player tick. PE-10. The `tickDownedPlayers` call is a stub.
- Do not implement boss bars yet. The `updateDungeonTimeBossBar` / `updateCloseTimerBossBar` calls are stubs.
- Do not integrate `BusyStateCompat`. Phase F.

---

### PE-7 — `DungeonRun.startRun`

**Goal**: the `startRun` method. Capture return points, force-load chunks, reset rooms, teleport players, push `DungeonRun` into the controller's `activeRuns`.

**Files to modify:**
- `DungeonRunLifecycle.java`

#### Spec

```java
/**
 * @return the new DungeonRun on success, or null if the instance is busy or chunks can't load.
 */
@Nullable
static DungeonRun startRun(
    ServerLevel world,
    DungeonControllerBlockEntity controller,
    BlockPos dbsPos,
    List<UUID> partyUuids,
    DifficultyTier tier,
    boolean hardcore
) {
    // Validation
    if (controller.getActiveRuns().containsKey(dbsPos)) return null;
    if (controller.getInstanceCooldownTimers().containsKey(dbsPos)) return null;

    DungeonBossSpawnerBlockEntity dbs = (DungeonBossSpawnerBlockEntity) world.getBlockEntity(dbsPos);
    if (dbs == null) return null;

    // Force-load chunks for DBS and every room
    forceLoadChunks(world, dbsPos, dbs.getRooms());

    // Resolve tier
    TierConfig tierConfig = controller.getTierConfigs().getOrDefault(tier, TierConfig.defaultFor(tier));

    // Create the run
    DungeonRun run = new DungeonRun(tier, tierConfig, hardcore, dbsPos, world.dimension(), world.getGameTime());

    // For each party member: add as participant, capture return point, teleport
    for (UUID uuid : partyUuids) {
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
        if (player == null) continue; // skip offline
        run.addParticipant(new RunParticipant(uuid, player.getGameProfile().getName(), ParticipantStatus.ACTIVE, world.getGameTime()));
        run.setReturnPoint(uuid, PlayerReturnPoint.capture(player));

        ServerLevel targetLevel = world.getServer().getLevel(dbs.getEntranceDimension());
        if (targetLevel == null) targetLevel = world; // fallback
        BlockPos entrance = dbs.getEntrancePos();
        player.teleportTo(targetLevel, entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5, 0f, 0f);
    }

    // Reset all rooms
    for (BlockPos roomPos : dbs.getRooms()) {
        BlockEntity be = world.getBlockEntity(roomPos);
        if (be instanceof RoomControllerBlockEntity rc) {
            rc.reset(world);
        }
    }

    // Push into controller
    controller.startRun(dbsPos, run);

    // Phase machine takes over from here on next tick
    return run;
}

private static void forceLoadChunks(ServerLevel world, BlockPos dbsPos, List<BlockPos> roomPositions) {
    Set<ChunkPos> chunks = new HashSet<>();
    chunks.add(new ChunkPos(dbsPos));
    for (BlockPos r : roomPositions) chunks.add(new ChunkPos(r));
    // Also chunks for spawner positions and doors within each room.
    for (BlockPos r : roomPositions) {
        if (world.getBlockEntity(r) instanceof RoomControllerBlockEntity rc) {
            for (BlockPos sp : rc.getSpawnerPositions()) chunks.add(new ChunkPos(sp));
            BlockPos door = rc.getDoorPos();
            if (door != null) chunks.add(new ChunkPos(door));
        }
    }
    for (ChunkPos cp : chunks) {
        world.setChunkForced(cp.x, cp.z, true);
    }
}
```

Chunk *unloading* on finalize: track the chunks in the run state or recompute from the DBS+rooms; un-force them in `finalize()`. Simplest: store `Set<ChunkPos> forcedChunks` as a transient field on `DungeonRun` (not codec-serialized; recomputed on save/load if needed).

Wait — that doesn't work for save-restart resilience. If we save the run mid-tick and restart, the chunks aren't force-loaded anymore.

**Two options:**

- **A**: serialize `forcedChunks` in `DungeonRun` NBT. On load, the controller re-applies `setChunkForced(true)` for each.
- **B**: don't store; recompute from DBS+rooms on load, re-force at that moment.

Pick **B**. It's data we can always derive. Add to `DungeonControllerBlockEntity.loadAdditional` (or its NBT loaded path): for each active run, recompute and re-force chunks.

Update `startRun` and the load path accordingly. Add a `forceLoadChunksForRun(...)` static helper that both paths call.

Same idea for un-forcing in `finalize`: recompute the set and call `setChunkForced(false)`.

#### Wire to PE-4's StartRunPayload handler

Update the handler to actually call `DungeonRunLifecycle.startRun(...)` and remove the TODO log line.

#### Acceptance

- `startRun` returns a working `DungeonRun` instance and pushes it into the controller's active runs map.
- All party members are teleported to the DBS entrance.
- Return points captured per player.
- All rooms reset before the run starts.
- Chunks force-loaded so rooms can be ticked even if no players are nearby.
- `StartRunPayload` handler invokes this.
- Build passes.

#### Don'ts

- Do not start the run on the client. Server-side only.
- Do not validate hardcore mode permissions here. The lobby owner already approved hardcore via their toggle.
- Do not bypass the tier config snapshot. `run.resolvedTierConfig()` is the immutable snapshot for this run.

---

### PE-8 — `DungeonRun.handleWin`

**Goal**: per-player loot distribution, leaderboard upsert.

**Files to modify:**
- `DungeonRunLifecycle.java`

#### Spec

```java
private static void handleWin(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
    long runDurationTicks = world.getGameTime() - run.startTick();
    int runDurationSeconds = (int) (runDurationTicks / 20);

    // Roll per-player loot
    String lootTableId = run.resolvedTierConfig().perPlayerLootTable();
    ResourceLocation lootLoc = lootTableId.isEmpty() ? null : ResourceLocation.tryParse(lootTableId);
    LootTable lootTable = null;
    if (lootLoc != null) {
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, lootLoc);
        lootTable = world.getServer().reloadableRegistries().getLootTable(key);
    }

    for (UUID uuid : run.lootEligibleUuids()) {
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
        if (player == null) continue; // offline = forfeit

        // Add leaderboard entry per player
        controller.addLeaderboardEntry(run.tier(),
            new LeaderboardEntry(player.getGameProfile().getName(), runDurationSeconds, System.currentTimeMillis()));

        // Deliver loot bundle (if loot table is configured)
        if (lootTable != null) {
            ItemStack bundle = createLootBundle(lootLoc.toString());
            if (!player.getInventory().add(bundle)) {
                player.drop(bundle, false);
            }
        }
    }

    // Show "you won" message to all online participants
    for (UUID uuid : run.participants().keySet()) {
        ServerPlayer p = world.getServer().getPlayerList().getPlayer(uuid);
        if (p != null) {
            p.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.win", runDurationSeconds));
        }
    }

    // Phase + close timer
    run.setOutcome(DungeonOutcome.WIN);
    run.setPhase(DungeonPhase.CLOSING);
    run.setCloseTimerTicks(controller.getCloseTimerSeconds() * 20);
}

private static ItemStack createLootBundle(String lootTableId) {
    ItemStack bundle = new ItemStack(ItemRegistry.LOOT_BUNDLE);
    // Set the loot table component (existing LootBundleDataComponent)
    bundle.set(DataComponentRegistry.LOOT_BUNDLE_DATA, new LootBundleDataComponent(lootTableId));
    return bundle;
}
```

Wait: the existing `LOOT_BUNDLE` item already has a `right-click to roll` behavior. The bundle is a *deferred* loot roll, not an immediate one. So we don't actually roll the loot at win — we hand the player a bundle item, they roll it themselves later.

That simplifies a lot. We don't even need to verify the loot table exists at win-time. We just hand out the bundle item with the table ID stamped on it. If the table doesn't exist when they open the bundle, the bundle's right-click code logs and disappears.

Updated body:

```java
private static void handleWin(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
    long runDurationTicks = world.getGameTime() - run.startTick();
    int runDurationSeconds = (int) (runDurationTicks / 20);
    String lootTableId = run.resolvedTierConfig().perPlayerLootTable();

    for (UUID uuid : run.lootEligibleUuids()) {
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
        if (player == null) continue;

        controller.addLeaderboardEntry(run.tier(),
            new LeaderboardEntry(player.getGameProfile().getName(), runDurationSeconds, System.currentTimeMillis()));

        if (!lootTableId.isEmpty()) {
            ItemStack bundle = createLootBundle(lootTableId);
            if (!player.getInventory().add(bundle)) {
                player.drop(bundle, false);
            }
        }
    }

    for (UUID uuid : run.participants().keySet()) {
        ServerPlayer p = world.getServer().getPlayerList().getPlayer(uuid);
        if (p != null) {
            p.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.win", runDurationSeconds));
        }
    }

    run.setOutcome(DungeonOutcome.WIN);
    run.setPhase(DungeonPhase.CLOSING);
    run.setCloseTimerTicks(controller.getCloseTimerSeconds() * 20);
}
```

#### Translation keys to add

```json
"message.arenas_ld.dungeon.win": "You won the dungeon! Time: %s seconds.",
"message.arenas_ld.dungeon.loss_timeout": "Dungeon failed — out of time.",
"message.arenas_ld.dungeon.loss_abandoned": "Dungeon failed — no participants remaining.",
"message.arenas_ld.dungeon.loss_forced": "Dungeon ended by admin."
```

(Ukrainian translations too.)

#### Acceptance

- On boss room clear (last room), `handleWin` fires.
- Online eligible participants get a loot bundle.
- Offline eligible participants get nothing.
- Online eligible participants get a leaderboard entry.
- Run transitions to CLOSING with the close timer.
- All participants get a win message.

#### Don'ts

- Do not roll the loot table at win-time. Hand out the deferred bundle.
- Do not write the leaderboard for non-online players. Offline = forfeit means everything: no loot, no leaderboard.
- Do not drop the bundle on the ground if inventory is full — `player.drop(bundle, false)` makes it fall toward the player (false = no throw). That's correct.

---

### PE-9 — `DungeonRun.handleLoss`

**Goal**: despawn the boss if alive; teleport players to their return points immediately (not on close timer). Trigger close timer for the message phase, then `finalize()` cleans up.

Wait — earlier I said teleport happens in `finalize`. Let me reconcile:

- On win: close timer counts down with players still in the dungeon (looking around, gathering loot from the bundle), then `finalize` teleports them out.
- On loss: arguably we want immediate teleport-out because they just died. But that's a UX call.

I'll go with **finalize handles teleport in both cases.** Loss just shows a sad message during the close timer. Players can run around for those 30 seconds. Simpler model.

```java
private static void handleLoss(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run, DungeonOutcome reason) {
    // Despawn the active boss if any (in the last room)
    DungeonBossSpawnerBlockEntity dbs = (DungeonBossSpawnerBlockEntity) world.getBlockEntity(run.dbsPos());
    if (dbs != null && run.currentRoomIndex() < dbs.getRooms().size()) {
        BlockPos lastRoomPos = dbs.getRooms().get(dbs.getRooms().size() - 1);
        BlockEntity bossRoomBe = world.getBlockEntity(lastRoomPos);
        if (bossRoomBe instanceof RoomControllerBlockEntity bossRoom) {
            // Discard alive boss(es) in the boss room — same as reset for that room
            // But don't fully reset — that's finalize's job
            for (UUID bossUuid : new ArrayList<>(bossRoom.getAliveMobs())) {
                Entity entity = world.getEntity(bossUuid);
                if (entity != null && entity.isAlive()) {
                    entity.discard();
                }
            }
        }
    }

    // Notify
    String messageKey = switch (reason) {
        case LOSS_TIMEOUT -> "message.arenas_ld.dungeon.loss_timeout";
        case LOSS_ABANDONED -> "message.arenas_ld.dungeon.loss_abandoned";
        case LOSS_FORCED -> "message.arenas_ld.dungeon.loss_forced";
        default -> "message.arenas_ld.dungeon.loss_timeout";
    };
    for (UUID uuid : run.participants().keySet()) {
        ServerPlayer p = world.getServer().getPlayerList().getPlayer(uuid);
        if (p != null) {
            p.sendSystemMessage(Component.translatable(messageKey));
        }
    }

    run.setOutcome(reason);
    run.setPhase(DungeonPhase.CLOSING);
    run.setCloseTimerTicks(controller.getCloseTimerSeconds() * 20);
}
```

#### Acceptance

- Loss path discards the boss (if any) from the boss room.
- Loss message sent to participants (translatable).
- Run transitions to CLOSING; finalize handles teleport-out after close timer.

#### Don'ts

- Do not teleport players immediately on loss. `finalize` handles that.
- Do not despawn mobs in other rooms (the rooms `reset` in finalize does that).
- Do not do anything for `LOSS_ABANDONED` differently — same close-timer flow.

---

### PE-10 — Downed players + hardcore handling

**Goal**: wire `ParticipantStatus.DOWNED` lifecycle. When a player's HP hits 0:
- Hardcore mode → `ParticipantStatus.REMOVED`, player respawns at their world spawn, kicked from run.
- Non-hardcore → `ParticipantStatus.DOWNED`, spectator mode, `DownedPlayer` countdown ticks; on expiry, respawn at DBS entrance at 50% HP, status back to `ACTIVE`.

The mixin (`LivingEntityMixin`) currently lives in the legacy mod and applies tier damage scaling. For PE-10, we need a mixin (or event listener) that catches player death and routes through the run.

**Cleanest approach**: an `arenasLd$onPlayerDeath(ServerPlayer)` static method in a helper class. The mixin invokes it on player death; the helper looks up the player's run via `DungeonManager.getRunForPlayer(player)` (Phase F)... wait. Phase F adds the DungeonManager.

For PE-10, we use a stopgap: each tick, iterate `controller.getActiveRuns()` and check each participant's HP. If `ACTIVE` and HP <= 0 → handle down/death. Inefficient (polls every tick) but correct, and Phase F replaces it with a death event hook.

**Spec:**

In `DungeonRunLifecycle`:

```java
private static void tickRunning(...) {
    // ... existing logic ...
    detectPlayerDeaths(world, controller, run);
    tickDownedPlayers(world, controller, run);
}

private static void detectPlayerDeaths(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
    for (Map.Entry<UUID, RunParticipant> e : new HashMap<>(run.participants()).entrySet()) {
        if (e.getValue().status() != ParticipantStatus.ACTIVE) continue;
        ServerPlayer p = world.getServer().getPlayerList().getPlayer(e.getKey());
        if (p == null) continue;
        if (p.isDeadOrDying() || p.getHealth() <= 0.0F) {
            handlePlayerDown(world, controller, run, p);
        }
    }
}

private static void handlePlayerDown(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run, ServerPlayer player) {
    if (run.hardcoreEnabled()) {
        // Remove from run; player respawns vanilla-style at world spawn
        run.updateParticipant(run.participants().get(player.getUUID())
            .withStatus(ParticipantStatus.REMOVED, world.getGameTime()));
        player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.hardcore_death")
            .withStyle(ChatFormatting.RED));
    } else {
        // Set to spectator, start countdown
        run.updateParticipant(run.participants().get(player.getUUID())
            .withStatus(ParticipantStatus.DOWNED, world.getGameTime()));
        run.setDowned(new DownedPlayer(player.getUUID(), 40)); // 40 ticks = 2 seconds
        player.setGameMode(GameType.SPECTATOR);
        player.setHealth(1.0F); // prevent death respawn screen
        player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.you_are_downed"));
    }
}

private static void tickDownedPlayers(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
    Map<UUID, DownedPlayer> downed = new HashMap<>(run.downedPlayers());
    for (Map.Entry<UUID, DownedPlayer> e : downed.entrySet()) {
        DownedPlayer dp = e.getValue().tick();
        if (dp.isReadyToRespawn()) {
            respawnDownedPlayer(world, controller, run, e.getKey());
            run.clearDowned(e.getKey());
        } else {
            run.setDowned(dp);
        }
    }
}

private static void respawnDownedPlayer(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run, UUID uuid) {
    ServerPlayer p = world.getServer().getPlayerList().getPlayer(uuid);
    if (p == null) return;

    // Teleport to DBS entrance
    DungeonBossSpawnerBlockEntity dbs = (DungeonBossSpawnerBlockEntity) world.getBlockEntity(run.dbsPos());
    if (dbs != null) {
        ServerLevel target = world.getServer().getLevel(dbs.getEntranceDimension());
        if (target == null) target = world;
        BlockPos entrance = dbs.getEntrancePos();
        p.setGameMode(GameType.SURVIVAL); // or whatever the player's pre-dungeon mode was; for now SURVIVAL
        p.setHealth(p.getMaxHealth() * 0.5F);
        p.teleportTo(target, entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5, 0f, 0f);
    }

    // Status back to ACTIVE
    run.updateParticipant(run.participants().get(uuid)
        .withStatus(ParticipantStatus.ACTIVE, world.getGameTime()));
}
```

Sticky issue: `p.setGameMode(GameType.SURVIVAL)` assumes survival was the previous mode. If the player was creative/spectator before the run, we'd be lying. Use the `PlayerReturnPoint` if we capture gamemode there... but we don't. Add `gameMode: GameType` to `PlayerReturnPoint`? That changes the record signature.

**Decision**: do NOT add gamemode to PlayerReturnPoint. The risk of "I was creative and the dungeon put me in survival on respawn" is small (admins testing). For the v4.0 release, players in survival is the assumed case. If admins need to do funky stuff, they can fix their gamemode after.

#### Translation keys

```json
"message.arenas_ld.dungeon.hardcore_death": "You died in hardcore mode. You've been removed from the run.",
"message.arenas_ld.dungeon.you_are_downed": "You're downed. Respawning shortly..."
```

#### Acceptance

- Player HP hitting 0 during RUNNING phase triggers down/death.
- Hardcore → REMOVED, kicked from run, vanilla respawn handles it.
- Non-hardcore → DOWNED status, spectator mode, set health to 1 (no death screen).
- After 40 ticks → respawn at DBS entrance, 50% HP, ACTIVE status.

#### Don'ts

- Do not modify the existing `LivingEntityMixin`. PE-10 is poll-based for now; Phase F adds a proper death event.
- Do not save the player's gamemode in the return point.
- Do not apply hardcore to losses (timeout/abandoned). Hardcore only kicks the individual player on death.
- Do not change `currentRoomIndex` when a player dies. The room state is independent of individual players.

---

### PE-11 — Admin GUI: Instances + General tabs

**Goal**: the tabbed admin screen, two tabs landing in this task.

The "Lobby" GUI is already the player-facing screen from PE-5 (DungeonControllerScreen). The "Admin" version is a separate screen opened by ops with a different intent. This is a design call — separate screen or extra tabs?

**Decision: separate screen, opened via different command.** Add a command `/arenasld dungeon admin` (or similar) that opens the admin GUI for the controller the player is looking at. Players don't get an "admin button" in the player GUI; admins use the command.

This is cleaner than mixing player and admin tabs in one screen, and matches how a lot of mods handle it.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/DungeonControllerAdminScreen.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/DungeonControllerAdminScreenHandler.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/DungeonControllerAdminData.java`
- Several new packets for admin operations: `AddDungeonInstancePayload`, `RemoveDungeonInstancePayload`, `MoveDungeonInstancePayload`, `SetCooldownTicksPayload`, `SetCloseTimerSecondsPayload`, `SetMaxPartySizePayload`.

**Files to modify:**
- `CommandRegistry.java` (or whichever file has commands) — add `/arenasld dungeon admin`.
- `ModScreenHandlers.java` — register.
- `ModPacketTypeRegistry.java` — register.
- `SpawnerPacketHandlers.java` — handlers (op-gated).

#### Layout

Two-tab GUI (Instances / General). Per-tier tabs come in PE-12.

**Instances tab:**

```
+--------------------------------------------+
| Admin: Dungeon Controller                  |
| [Instances] [General] [Normal] [Hard] [Hell]|
+--------------------------------------------+
| Active runs: 2 / 5 instances               |
| Cooldown: 0 | Pending removal: 0           |
| Instances:                                 |
|  1. [123, 64, -500]  RUNNING  [Up][Down][X]|
|  2. [120, 64, -510]  IDLE     [Up][Down][X]|
|  3. [125, 64, -515]  COOLDOWN (3:21)       |
|                              [Up][Down][X] |
| (use Linker to add)                        |
|                          [Clear All]       |
+--------------------------------------------+
```

**General tab:**

```
| Cooldown: [300] seconds  [Apply]           |
| Close timer: [30] seconds  [Apply]         |
| Max party size: [4]  [Apply]               |
| Invite expiry: [30] seconds  [Apply]       |
```

#### Acceptance

- `/arenasld dungeon admin` opens the screen, op-only.
- Instances tab shows live status of each instance (idle/running/cooldown).
- General tab edits all four numeric configs with Apply buttons.
- Remove button on a running instance correctly enqueues pending removal with a yellow warning chat message; the instance stays in the list with "PENDING REMOVAL" badge.
- Add via Linker only — no inline add.

#### Don'ts

- Do not open the admin GUI on right-click. Command-only.
- Do not let non-ops use the command.
- Do not show admin status in the player GUI from PE-5.
- Do not auto-refresh the screen. Re-open for fresh data.

---

### PE-12 — Admin GUI: per-tier tabs

**Goal**: the three tier tabs (Normal/Hard/Hell). Each edits one `TierConfig`.

**Files to modify:**
- `DungeonControllerAdminScreen.java`
- Add a `SetTierConfigPayload` payload.

#### Tier tab layout

```
| Normal tier:                               |
| Health multiplier: [1.0]    [Apply]        |
| Damage multiplier: [1.0]    [Apply]        |
| Loot table:       [arenas_ld:dungeon/...]  |
|                                  [Apply]   |
| Dungeon time:     [600] seconds  [Apply]   |
| Hardcore default: [Off]          [Toggle]  |
+--------------------------------------------+
| Leaderboard (Normal):                      |
|   1. Steve   132s                          |
|   2. Alice   145s                          |
|   ... (top 10)                             |
+--------------------------------------------+
```

Repeat for Hard and Hell.

#### Acceptance

- Each tier tab edits its `TierConfig` independently.
- Per-tier leaderboard shows top 10 entries sorted by time ascending.
- Apply buttons send one packet per field — or one consolidated packet per Apply per tier (simpler).

#### Don'ts

- Do not validate that loot table IDs exist. Server checks at win-time.
- Do not let admins reset the leaderboard from this GUI. Add a `/arenasld dungeon clearLeaderboard <tier>` command if/when needed; out of scope.
- Do not snapshot tier configs into active runs on edit. The snapshot happens at `startRun`. Edits here affect future runs only.

---

### Phase E exit criteria

After all 12 tasks:

- `./gradlew build test` passes.
- New dungeon controller block placeable, op-configurable via `/arenasld dungeon admin`.
- Players can right-click to see lobbies, create one, invite others, configure tier, start a run.
- Running a dungeon: rooms activate in order, mobs spawn with tier scaling, boss room triggers, loot bundles delivered to online winners, leaderboard updates.
- Per-instance cooldown after each run.
- Hardcore mode removes dying players from the run.
- Non-hardcore mode downs them, respawns at entrance.
- Dungeon timer triggers loss on timeout.
- All-offline triggers loss on abandonment.
- Legacy controller still works in parallel (unchanged).
- Total new files: ~30+. Total LOC: ~2000-2500.

---

## Phase F — Linker + DungeonManager (1 PR)

**Goal of the phase**: make the v4.0 dungeon system fully usable end-to-end without `/data` commands.

After Phase E, the controller exists with admin/lobby/run logic, but linking blocks together (controller↔DBS, DBS↔rooms, room↔spawners, room↔door) requires manually editing NBT via `/data merge`. Phase F adds proper Linker modes for the new blocks.

Phase F also introduces the **DungeonManager**, a server-level singleton that:
- Tracks all v4.0 controllers across all dimensions
- Maintains an O(1) `UUID → DungeonRun` lookup (for both participants and mobs spawned by runs)
- Replaces the legacy static `CONTROLLERS` set pattern, which leaked memory across chunk unloads
- Provides the integration point for the `LivingEntityMixin` (damage scaling) and `ServerLivingEntityEvents.AFTER_DEATH` (death detection), replacing PE-10's poll-based death detection

Finally, Phase F integrates `BusyStateCompat` for the new run lifecycle so v4.0 dungeons participate in the busy-state system as legacy dungeons did.

After Phase F, an admin can place a v4.0 controller, configure tiers via the admin GUI, use the Linker to set up instances/rooms/spawners/doors, and players can run dungeons end-to-end without ever touching `/data`.

### Decisions locked in for Phase F

These were settled during the spec discussion:

- **DungeonManager is a static singleton on `ArenasLdMod`** (e.g. `ArenasLdMod.DUNGEON_MANAGER`). Cleared on `ServerLifecycleEvents.SERVER_STOPPING`. Matches existing patterns.
- **No `WeakReference` for controllers.** Instead the manager stores `Map<ResourceKey<Level>, Set<BlockPos>>` and looks up BE via `level.getBlockEntity(pos)` on demand. Simpler, no stale-ref risk.
- **`Map<UUID, DungeonRun>` for participants AND mobs.** Maintained by lifecycle code via `manager.registerParticipant(uuid, run)` / `registerMob(uuid, run)` and their unregister counterparts. O(1) mixin queries.
- **Append new `LinkerMode` enum values** at the end. Legacy modes stay unchanged. New modes: `CONTROLLER_INSTANCE`, `DBS_ROOM`, `ROOM_SPAWNER`, `ROOM_DOOR`. These names use simpler labels than legacy modes — admin's mental model is "I'm setting X on Y."
- **Replace poll-based death detection from PE-10 with `ServerLivingEntityEvents.AFTER_DEATH` listener.** The listener routes through `DungeonManager.getRunForPlayer(uuid)` to find the run, then calls into `DungeonRunLifecycle.handlePlayerDown` (which gets promoted from private to package-visible-via-manager). Poll-based code in PE-10's `detectPlayerDeaths` is removed.
- **`LivingEntityMixin` gets a v4.0 path.** Existing legacy logic stays. New path: ask `DungeonManager.getRunForEntity(uuid)` → if non-null, get the resolved tier config, apply damage scaling. Same shape as legacy mixin, different data source.
- **`BusyStateCompat.setBusy(player)` on run start, `setNotBusy(player)` on finalize.** Wraps existing v4.0 lifecycle calls. Both for legacy AND v4.0 runs. Legacy already does this; v4.0 needs the calls added.
- **Disconnect handling**: a `ServerPlayConnectionEvents.DISCONNECT` listener marks the player DOWNED in any active run they're in, with a grace period. Reconnect within grace = restored to ACTIVE. Grace period default 5 minutes (`6000` ticks), configurable.

### Tasks in this phase

7 tasks:

- **PF-1** — `DungeonManager` skeleton: singleton, controller registry, lifecycle hooks (server stop clears it). No participant/mob lookup yet.
- **PF-2** — Participant + mob registration. `manager.registerParticipant`, `manager.registerMob`, `getRunForPlayer`, `getRunForEntity`. Lifecycle code wires in.
- **PF-3** — New Linker modes (4 of them). Linker code recognizes new blocks and dispatches to the right BE operation.
- **PF-4** — `LivingEntityMixin` v4.0 path. Damage scaling for v4.0 mobs.
- **PF-5** — `ServerLivingEntityEvents.AFTER_DEATH` listener. Replaces poll-based death detection.
- **PF-6** — Disconnect/reconnect handling with grace period. Updates participant status on connection events.
- **PF-7** — `BusyStateCompat` integration on v4.0 start/finalize.

After Phase F, end-to-end gameplay works without `/data`. Phase G can begin removing legacy code.

---

### PF-1 — `DungeonManager` skeleton

**Goal**: introduce the manager class with the controller registry. No participant/mob lookups yet — those land in PF-2.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/manager/DungeonManager.java`

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/ArenasLdMod.java` — instantiate `DUNGEON_MANAGER` static field; hook `SERVER_STARTING` and `SERVER_STOPPING` lifecycle events.
- `src/main/java/net/ledok/arenas_ld/dungeon/blockentity/DungeonControllerBlockEntity.java` — register/unregister with manager in `clearRemoved` and `setRemoved`.

#### `DungeonManager` spec

```java
package net.ledok.arenas_ld.dungeon.manager;

import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-side singleton tracking all v4.0 dungeon controllers. Provides cross-controller
 * lookup APIs used by the mixin, death listener, and admin commands.
 *
 * <p>Lifecycle: instance is created at mod init; state is cleared on server stop via
 * {@link #clearForServerStop()}. Per-server state is rebuilt as controllers load.
 *
 * <p>Threading: all mutators are server-thread-only. No synchronization.
 */
public final class DungeonManager {

    /** Controllers registered by their dimension + position. */
    private final Map<ResourceKey<Level>, Set<BlockPos>> controllersByDimension = new HashMap<>();

    public DungeonManager() {
        // No-op constructor. Lifecycle events drive the rest.
    }

    // ---- Controller registry ----

    public void registerController(DungeonControllerBlockEntity be) {
        Level level = be.getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        ResourceKey<Level> dim = sl.dimension();
        controllersByDimension.computeIfAbsent(dim, k -> new HashSet<>()).add(be.getBlockPos());
    }

    public void unregisterController(DungeonControllerBlockEntity be) {
        Level level = be.getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        ResourceKey<Level> dim = sl.dimension();
        Set<BlockPos> set = controllersByDimension.get(dim);
        if (set != null) {
            set.remove(be.getBlockPos());
            if (set.isEmpty()) controllersByDimension.remove(dim);
        }
    }

    /**
     * Look up a controller BE by (dimension, position). Returns null if the chunk isn't loaded
     * or no controller exists at that position.
     */
    public DungeonControllerBlockEntity getController(MinecraftServer server, ResourceKey<Level> dim, BlockPos pos) {
        ServerLevel level = server.getLevel(dim);
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof DungeonControllerBlockEntity controller ? controller : null;
    }

    /** All registered controller positions in the given dimension. */
    public Set<BlockPos> getControllersIn(ResourceKey<Level> dim) {
        return controllersByDimension.getOrDefault(dim, Set.of());
    }

    // ---- Lifecycle ----

    /** Clear all state on server stop. Re-population happens organically as controllers load. */
    public void clearForServerStop() {
        controllersByDimension.clear();
    }
}
```

#### `ArenasLdMod` changes

Add a public static `DUNGEON_MANAGER` field, initialized at mod init:

```java
public static final DungeonManager DUNGEON_MANAGER = new DungeonManager();
```

Hook `ServerLifecycleEvents.SERVER_STOPPING`:

```java
ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
    DUNGEON_MANAGER.clearForServerStop();
});
```

No `SERVER_STARTING` hook needed — the manager starts empty and fills as controllers load their chunks.

#### `DungeonControllerBlockEntity` changes

Override `clearRemoved` and `setRemoved`:

```java
@Override
public void clearRemoved() {
    super.clearRemoved();
    ArenasLdMod.DUNGEON_MANAGER.registerController(this);
}

@Override
public void setRemoved() {
    super.setRemoved();
    ArenasLdMod.DUNGEON_MANAGER.unregisterController(this);
}
```

`clearRemoved` is called when the BE is added to the world (load or place). `setRemoved` is called when the BE is removed (chunk unload, block break). This matches the legacy pattern's invocations but routes through the manager instead of a static set.

#### Acceptance

- `DungeonManager` exists in the new package with controller registry methods.
- `ArenasLdMod.DUNGEON_MANAGER` is the singleton; `clearForServerStop` is called on server stop.
- `DungeonControllerBlockEntity.clearRemoved/setRemoved` register/unregister with the manager.
- Build passes.
- Placing two controllers in the world, opening one's admin GUI, then breaking and replacing one: no leaks, both still discoverable via the manager.

#### Don'ts

- Do not add `Map<UUID, DungeonRun>` yet. PF-2.
- Do not add WeakReferences. The (dim, pos) + `level.getBlockEntity(pos)` lookup pattern is enough.
- Do not synchronize access — manager is server-thread-only.
- Do not replace the legacy `CONTROLLERS` set on the legacy `DungeonControllerBlockEntity` — leave it untouched. Phase G deletes the legacy file entirely.

#### References

- Legacy `DungeonControllerBlockEntity.CONTROLLERS` — the pattern we're replacing.
- `ServerLifecycleEvents` from `fabric-lifecycle-events-v1` for the server stop hook.

---

### PF-2 — Participant + mob registration

**Goal**: add the `UUID → DungeonRun` maps to the manager, with O(1) lookup APIs. Lifecycle code wires registrations in.

**Files to modify:**
- `DungeonManager.java`
- `DungeonRunLifecycle.java` — register/unregister at the right transitions.
- `RoomControllerBlockEntity.java` — register mobs when they spawn; unregister when room resets or refreshAliveMobs prunes a dead UUID.

#### `DungeonManager` additions

```java
/** Player UUID → run they're in (active OR closing). Removed on run finalize. */
private final Map<UUID, DungeonRun> runByPlayer = new HashMap<>();

/** Entity UUID (mobs) → run they were spawned by. Removed on mob death or room reset. */
private final Map<UUID, DungeonRun> runByMob = new HashMap<>();

public void registerParticipant(UUID uuid, DungeonRun run) {
    runByPlayer.put(uuid, run);
}

public void unregisterParticipant(UUID uuid) {
    runByPlayer.remove(uuid);
}

public DungeonRun getRunForPlayer(UUID uuid) {
    return runByPlayer.get(uuid);
}

public void registerMob(UUID uuid, DungeonRun run) {
    runByMob.put(uuid, run);
}

public void unregisterMob(UUID uuid) {
    runByMob.remove(uuid);
}

public DungeonRun getRunForEntity(UUID uuid) {
    return runByMob.get(uuid);
}

// Update clearForServerStop:
public void clearForServerStop() {
    controllersByDimension.clear();
    runByPlayer.clear();
    runByMob.clear();
}
```

#### `DungeonRunLifecycle.startRun` additions

After the run is constructed and pushed to the controller:

```java
for (UUID uuid : partyUuids) {
    // ... existing teleport + capture logic ...
    ArenasLdMod.DUNGEON_MANAGER.registerParticipant(uuid, run);
}
```

#### `DungeonRunLifecycle.finalize` additions

Before any teleport / cleanup:

```java
// Unregister all participants from the manager
for (UUID uuid : run.participants().keySet()) {
    ArenasLdMod.DUNGEON_MANAGER.unregisterParticipant(uuid);
}
// Unregister any tracked mobs (room reset will discard them anyway, but be defensive)
DungeonBossSpawnerBlockEntity dbs = (DungeonBossSpawnerBlockEntity) world.getBlockEntity(run.dbsPos());
if (dbs != null) {
    for (BlockPos roomPos : dbs.getRooms()) {
        if (world.getBlockEntity(roomPos) instanceof RoomControllerBlockEntity rc) {
            for (UUID mobUuid : rc.getAliveMobs()) {
                ArenasLdMod.DUNGEON_MANAGER.unregisterMob(mobUuid);
            }
        }
    }
}
```

#### `DungeonRunLifecycle.handlePlayerDown` (hardcore branch)

The hardcore branch removes the player from the run. Also unregister them:

```java
if (run.hardcoreEnabled()) {
    // ... existing code ...
    run.removeReturnPoint(player.getUUID());
    ArenasLdMod.DUNGEON_MANAGER.unregisterParticipant(player.getUUID());  // ADD THIS
    player.sendSystemMessage(...);
    return;
}
```

#### `RoomControllerBlockEntity.activate` change

When a mob is spawned and tracked, also register with the manager. But the room doesn't know what run it's in. **Two options:**

- **A**: The `activate(...)` method's caller (lifecycle's `tickRunning`) passes the run, and the room registers each spawned mob. Cleanest but couples Room to Run.
- **B**: Lifecycle code does the registration after calling `activate`. Lifecycle already has the run, has the room reference, has access to `room.getAliveMobs()`. Just walk the freshly-populated alive mobs and register each.

**Pick B.** No new coupling on the Room.

In `tickRunning`, after calling `room.activate(world, tier)`:

```java
if (!room.isActivated()) {
    room.activate(world, run.resolvedTierConfig());
    // Register all freshly-spawned mobs with the manager
    for (UUID uuid : room.getAliveMobs()) {
        ArenasLdMod.DUNGEON_MANAGER.registerMob(uuid, run);
    }
}
```

#### `RoomControllerBlockEntity.refreshAliveMobs` — unregister dead mobs

The current `refreshAliveMobs` prunes UUIDs whose entities are dead/missing. When that prune happens, ALSO unregister from the manager:

```java
// In refreshAliveMobs:
while (it.hasNext()) {
    UUID uuid = it.next();
    Entity entity = world.getEntity(uuid);
    if (entity == null || !entity.isAlive() || entity.isRemoved() || entity.level() != world) {
        it.remove();
        ArenasLdMod.DUNGEON_MANAGER.unregisterMob(uuid);   // ADD THIS
        changed = true;
    }
}
```

Adds a dependency from the room to `ArenasLdMod`, which is fine — `ArenasLdMod` is the singleton entry point.

#### `RoomControllerBlockEntity.reset` — unregister all mobs before discarding

The current `reset` iterates `aliveMobs` and discards each entity. Also unregister:

```java
// In reset:
for (UUID uuid : new ArrayList<>(aliveMobs)) {
    Entity entity = world.getEntity(uuid);
    if (entity != null && entity.isAlive()) {
        entity.discard();
    }
    ArenasLdMod.DUNGEON_MANAGER.unregisterMob(uuid);   // ADD THIS — regardless of whether entity was alive
}
clearRuntimeState();
closeDoor(world);
```

#### Acceptance

- Manager has two new maps, four public getters/registers/unregisters, and the lookups (`getRunForPlayer`, `getRunForEntity`).
- `clearForServerStop` clears all three maps.
- Run start populates `runByPlayer` for every party member.
- Run finalize clears `runByPlayer` for every participant.
- Hardcore death clears `runByPlayer` for the dying player.
- Room activate registers spawned mobs.
- Room refresh + reset unregister cleared/discarded mobs.
- Build passes.

#### Don'ts

- Do not call `registerParticipant` for downed players — they're already registered.
- Do not register OFFLINE party members (the startRun loop already skips them via the `player == null` check).
- Do not store entire DungeonRun in NBT for these maps. They're transient state, rebuilt on demand.
- Do not iterate the manager's maps from the lifecycle — only mutate.

#### References

- PE-7's startRun for the existing iteration pattern.
- PE-6's finalize for the existing cleanup pattern.

---

### PF-3 — New Linker modes

**Goal**: 4 new Linker modes that wire v4.0 blocks together via right-click sequences.

The existing Linker (read its current code first) likely uses a `LinkerMode` enum stored in the item's NBT, a "current selection" stored in the item's NBT, and a per-mode dispatch when the player right-clicks a target block.

PF-3 adds 4 new modes:

- **`CONTROLLER_INSTANCE`**: source = controller, target = DBS → `controller.addInstance(dbsPos)`
- **`DBS_ROOM`**: source = DBS, target = room → `dbs.addRoom(roomPos)`
- **`ROOM_SPAWNER`**: source = room, target = mob spawner OR DBS → `room.addSpawner(targetPos)`
- **`ROOM_DOOR`**: source = room, target = phase block → `room.setDoorPos(targetPos)`

Each mode has the same UX: right-click source (becomes "selected"), right-click target (link is established). Chat feedback on success/failure.

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/item/LinkerItem.java` (or wherever the Linker code lives) — add the 4 new enum values and the dispatch logic.
- `src/main/resources/assets/arenas_ld/lang/en_us.json` + `uk_ua.json` — labels for the 4 new modes + success/failure messages.

#### Mode dispatch logic

```java
case CONTROLLER_INSTANCE -> {
    if (sourceBe instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller
        && targetBe instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity) {
        boolean added = controller.addInstance(targetPos);
        sendFeedback(player, added
            ? "message.arenas_ld.linker.controller_instance.added"
            : "message.arenas_ld.linker.controller_instance.duplicate");
        markDirtyAndSync(world, controller);
    } else {
        sendFailure(player, "message.arenas_ld.linker.controller_instance.wrong_blocks");
    }
}
// ... 3 more cases
```

#### Translation keys (need both lang files)

```json
"item.arenas_ld.linker.mode.controller_instance": "Controller → Instance",
"item.arenas_ld.linker.mode.dbs_room": "DBS → Room",
"item.arenas_ld.linker.mode.room_spawner": "Room → Spawner",
"item.arenas_ld.linker.mode.room_door": "Room → Door",

"message.arenas_ld.linker.controller_instance.added": "Instance added to controller.",
"message.arenas_ld.linker.controller_instance.duplicate": "This instance is already registered.",
"message.arenas_ld.linker.controller_instance.wrong_blocks": "Linker mode requires a v4.0 Controller as source and a v4.0 Dungeon Boss Spawner as target.",

"message.arenas_ld.linker.dbs_room.added": "Room added to DBS.",
"message.arenas_ld.linker.dbs_room.duplicate": "This room is already in the DBS's list.",
"message.arenas_ld.linker.dbs_room.wrong_blocks": "Linker mode requires a v4.0 DBS as source and a v4.0 Room Controller as target.",

"message.arenas_ld.linker.room_spawner.added": "Spawner added to room.",
"message.arenas_ld.linker.room_spawner.duplicate": "This spawner is already in the room's list.",
"message.arenas_ld.linker.room_spawner.wrong_blocks": "Linker mode requires a v4.0 Room as source and a v4.0 Mob Spawner or DBS as target.",

"message.arenas_ld.linker.room_door.set": "Door set on room.",
"message.arenas_ld.linker.room_door.cleared_first": "Replaced previous door on room.",
"message.arenas_ld.linker.room_door.wrong_blocks": "Linker mode requires a v4.0 Room as source and a Phase Block as target."
```

Ukrainian translations follow the same patterns as previous lang work.

#### Op-gating

The Linker is already op-gated in legacy (verify). The new modes inherit the same op-gate — they're admin tools.

#### Source-block storage

The Linker likely stores the "first selected" block in the item's NBT. The 4 new modes use the same storage mechanism — no new fields needed.

#### Mode-cycle behavior

The Linker probably has shift+right-click or a similar input to cycle modes. The new 4 modes are appended to the cycle. Pre-existing legacy modes still cycle in their original order.

#### Acceptance

- 4 new `LinkerMode` enum values appended.
- Each mode has its dispatch case in the Linker's right-click handler.
- Mode display name (in tooltip/HUD) shows the translated label.
- Success/failure chat messages are translatable.
- After a successful link, the source BE's data persists across save/load.
- Wrong block combinations produce a failure message and don't change state.
- Op-only.
- Build passes.

#### Don'ts

- Do not replace any existing legacy Linker mode.
- Do not add `removeInstance` or `removeRoom` modes — those use the admin GUI's X button. The Linker is for *adding*.
- Do not add a "set entrance" mode for the DBS — entrance is handled via the DBS's GUI (PD-6's "Set to my position" button). Linker stays focused on positional linking.
- Do not support cross-dimension links. Each link is within one dimension; admin must place all blocks in the same world.
- Do not add a Linker mode for "linker controller→room" or "linker room→spawner+door at once" — keep each mode atomic.

#### References

- Existing `LinkerItem.java` — read first; mirror the existing pattern.

---

### PF-4 — `LivingEntityMixin` v4.0 path

**Goal**: apply damage scaling to mobs in v4.0 runs, parallel to the existing legacy behavior.

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/mixin/LivingEntityMixin.java`

#### Current behavior (legacy)

The existing mixin scales damage for entities in active legacy dungeons. It walks `DungeonControllerBlockEntity.CONTROLLERS`, finds the matching dungeon, applies the damage multiplier.

#### New behavior (v4.0)

In the existing mixin's damage-modification method, BEFORE the legacy code runs:

```java
// v4.0 path: check if the entity is in a v4.0 dungeon run
LivingEntity self = (LivingEntity) (Object) this;
DungeonRun run = ArenasLdMod.DUNGEON_MANAGER.getRunForEntity(self.getUUID());
if (run != null) {
    TierConfig tier = run.resolvedTierConfig();
    // Apply v4.0 damage multiplier
    float scaled = damage * (float) tier.damageMultiplier();
    // ... apply scaled damage following the same approach as legacy ...
    return scaled;
}

// Legacy path: existing code, unmodified
// ...
```

Exact integration depends on what the existing mixin does. Codex needs to read the existing mixin first and decide whether to:
- Add a check at the top of the existing method (returns early if v4.0 match)
- Refactor the legacy logic into a helper method, call the appropriate one
- Insert the v4.0 check via the same `@Inject` callback

Pick the approach with the **smallest diff to the existing mixin**.

#### Acceptance

- Mobs in v4.0 runs take damage scaled by their run's `tier.damageMultiplier()`.
- Mobs in legacy runs still take legacy damage scaling.
- Mobs in neither take un-scaled damage.
- Build passes.

#### Don'ts

- Do not modify the legacy mixin path.
- Do not change the damage formula — just multiply by `tier.damageMultiplier()`.
- Do not apply damage scaling to players in dungeons. Only to MOBS spawned BY dungeons. Players take full damage from mobs (the mob's damage is what's scaled).

#### References

- Existing `LivingEntityMixin.java` — start by reading.

---

### PF-5 — Death event listener (replaces PE-10's poll)

**Goal**: replace poll-based death detection in `DungeonRunLifecycle.detectPlayerDeaths` with an event-driven listener.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/event/DungeonDeathListener.java`

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/ArenasLdMod.java` — register the listener.
- `src/main/java/net/ledok/arenas_ld/dungeon/run/DungeonRunLifecycle.java` — remove `detectPlayerDeaths`; promote `handlePlayerDown` to package-public so listener can call it.

#### Spec

```java
package net.ledok.arenas_ld.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLivingEntityEvents;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.run.DungeonRun;
import net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class DungeonDeathListener {

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            DungeonRun run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(player.getUUID());
            if (run == null) return;
            if (!(player.level() instanceof ServerLevel sl)) return;

            // Find the controller for this run; route through handlePlayerDown
            // ... look up controller by run.dbsPos() (need to know the controller's pos somehow)
        });
    }
}
```

Wait — this is tricky. The listener fires for player death. It needs to find the right `DungeonControllerBlockEntity` to pass to `handlePlayerDown`. But `DungeonRun` doesn't directly store the controller's position; it stores `dbsPos`. The controller is "the one that has this dbsPos in its instances list."

The cleanest fix: **add `controllerPos` to `DungeonRun`** as a new field. This is added at run-start time and gives us a direct controller reference. Backward-compat consideration: old saves don't have this field, so codec defaults to `BlockPos.ZERO` or similar. We then have to validate it.

Better alternative: **the manager tracks `Map<DungeonRun, DungeonControllerBlockEntity>`** so we can look up the controller given a run. Simpler — the controller registers the run when it's started, and that registration includes a back-reference.

Even simpler: **iterate `manager.getControllersIn(player.level().dimension())` and find the one whose `getActiveRuns()` contains the run.**

```java
DungeonControllerBlockEntity controller = null;
for (BlockPos pos : ArenasLdMod.DUNGEON_MANAGER.getControllersIn(sl.dimension())) {
    BlockEntity be = sl.getBlockEntity(pos);
    if (be instanceof DungeonControllerBlockEntity c && c.getActiveRuns().containsValue(run)) {
        controller = c;
        break;
    }
}
if (controller == null) return; // shouldn't happen
DungeonRunLifecycle.handlePlayerDown(sl, controller, run, player);
```

Looking through N controllers per death is O(N) but N is typically small (≤10). Fine.

Promote `handlePlayerDown` from private to package-visible-via-static-utility — it can stay in `DungeonRunLifecycle` but with package or public access so the listener can call it.

Actually, since `DungeonDeathListener` is in a *different* package (`net.ledok.arenas_ld.event` vs `net.ledok.arenas_ld.dungeon.run`), package-visible won't work. **Promote `handlePlayerDown` to public.**

Or — move `DungeonDeathListener` to the `dungeon.run` package so it stays package-visible. Slightly hacky but cleaner architecturally.

**Recommendation**: keep `handlePlayerDown` package-visible-static (no `private`). Move the listener to `dungeon.run` package. This way the listener is colocated with the lifecycle code that's its real implementation partner.

**Files to create (revised):**
- `src/main/java/net/ledok/arenas_ld/dungeon/run/DungeonDeathListener.java`

#### Remove `detectPlayerDeaths` from `tickRunning`

The poll-based detection is now obsolete. Remove the `detectPlayerDeaths` private method and the call site in `tickRunning`. Keep `tickDownedPlayers` (the countdown ticker is still poll-based; deaths are now event-driven).

#### Acceptance

- A player dying in a v4.0 run triggers `handlePlayerDown` via the event listener (not poll).
- `detectPlayerDeaths` is removed.
- Hardcore and non-hardcore branches both still work.
- Build passes.

#### Don'ts

- Do not listen for entity (non-player) death events here. The room's `refreshAliveMobs` already handles mob death.
- Do not implement `AFTER_DEATH` for legacy dungeons. The legacy mixin has its own death handling.
- Do not move `handlePlayerDown` out of `DungeonRunLifecycle`. Just change its visibility.

#### References

- `ServerLivingEntityEvents.AFTER_DEATH` from `fabric-lifecycle-events-v1`.
- PE-10 for the existing `handlePlayerDown` body.

---

### PF-6 — Disconnect / reconnect with grace period

**Goal**: when a player disconnects mid-run, mark them DOWNED with a grace period. Reconnect within grace restores them to ACTIVE. After grace expires, they're REMOVED from the run.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/run/DungeonConnectionListener.java`

**Files to modify:**
- `DungeonControllerBlockEntity.java` — add `disconnectGraceTicks` config field (default 6000 = 5 minutes), with codec.
- `DungeonRun.java` — track `Map<UUID, Long> disconnectedAtTick` (player → tick they disconnected); package-private mutators `markDisconnected`, `clearDisconnected`.
- `DungeonRunLifecycle.tickRunning` — tick disconnected players; remove from run if grace expired.

#### Listener spec

```java
public final class DungeonConnectionListener {

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            UUID uuid = player.getUUID();
            DungeonRun run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(uuid);
            if (run == null) return;
            // Mark as disconnected at current game tick
            if (player.level() instanceof ServerLevel sl) {
                run.markDisconnected(uuid, sl.getGameTime());
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            UUID uuid = player.getUUID();
            DungeonRun run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(uuid);
            if (run == null) return;
            // Clear disconnect marker; player is back
            run.clearDisconnected(uuid);
            // Send "welcome back" message
            player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.reconnected"));
        });
    }
}
```

#### `DungeonRun` additions

```java
private final Map<UUID, Long> disconnectedAt = new HashMap<>();

void markDisconnected(UUID uuid, long tick) {
    disconnectedAt.put(uuid, tick);
}

void clearDisconnected(UUID uuid) {
    disconnectedAt.remove(uuid);
}

public Map<UUID, Long> disconnectedAt() {
    return Collections.unmodifiableMap(disconnectedAt);
}
```

Plus codec extension to persist `disconnectedAt` across save/load.

#### `DungeonRunLifecycle.tickRunning` additions

After the existing tick logic, add a `tickDisconnectedPlayers` call:

```java
private static void tickDisconnectedPlayers(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
    long now = world.getGameTime();
    int grace = controller.getDisconnectGraceTicks();
    for (Map.Entry<UUID, Long> e : new HashMap<>(run.disconnectedAt()).entrySet()) {
        if (now - e.getValue() > grace) {
            // Grace expired; remove from run
            UUID uuid = e.getKey();
            RunParticipant p = run.participants().get(uuid);
            if (p != null) {
                run.updateParticipant(p.withStatus(ParticipantStatus.REMOVED, now));
            }
            run.clearDisconnected(uuid);
            ArenasLdMod.DUNGEON_MANAGER.unregisterParticipant(uuid);
        }
    }
}
```

Add the call in `tickRunning` after `tickDownedPlayers`.

#### `DungeonControllerBlockEntity` additions

Add field, getter, setter (with validation), and extend the codec:

```java
private int disconnectGraceTicks = 6000; // 5 minutes

public int getDisconnectGraceTicks() { return disconnectGraceTicks; }

public boolean setDisconnectGraceTicks(int ticks) {
    if (ticks < 0) return false;  // 0 means "no grace, kick immediately"
    this.disconnectGraceTicks = ticks;
    setChanged();
    return true;
}
```

Update the State codec to include this field.

Also add this to the admin GUI's General tab in PE-11's screen — actually that's already shipped. We need a separate follow-up to add this field to the admin GUI, OR we leave it as a `/data` config for now.

**Decision**: leave it as a `/data` config for v4.0. Admins who want to change the grace period can edit NBT. Adding a 5th General-tab field is a layout problem; defer.

#### Translation key

```json
"message.arenas_ld.dungeon.reconnected": "Reconnected — welcome back to the run!"
```

Ukrainian:
```json
"message.arenas_ld.dungeon.reconnected": "Ви повернулися — раді бачити вас знову в підземеллі!"
```

#### Acceptance

- Disconnecting mid-run marks the player in `disconnectedAt` map.
- Reconnecting clears the marker and sends a welcome-back message.
- After `disconnectGraceTicks` (default 6000 = 5 min), grace expires; player is REMOVED, unregistered.
- The participant remains in `run.participants()` with REMOVED status (consistent with hardcore death's pattern).
- Save/load round-trips the `disconnectedAt` map.
- Build passes.

#### Don'ts

- Do not teleport disconnected players anywhere. They're offline — there's nothing to teleport.
- Do not modify the player's gamemode on disconnect.
- Do not unregister mobs on player disconnect. The room's `refreshAliveMobs` handles mob lifecycle independently.
- Do not add a chat message broadcast for "player X disconnected" — keeps signal:noise high. Quietly mark them.
- Do not extend the abandonment check to "all players are disconnected." Abandonment is still "no online participants" (already covered).

#### References

- `ServerPlayConnectionEvents` from `fabric-networking-api-v1`.

---

### PF-7 — `BusyStateCompat` integration

**Goal**: v4.0 runs participate in `BusyStateCompat` so the existing busy-state system knows the player is in a dungeon.

**Files to modify:**
- `DungeonRunLifecycle.java` — `setBusy` on each party member at run start; `setNotBusy` at finalize.

#### Spec

In `startRun`, after the participant teleport loop:

```java
for (UUID uuid : partyUuids) {
    ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
    if (player == null) continue;
    // ... existing teleport/capture logic ...
    BusyStateCompat.setBusy(player);
}
```

In `finalize`, before the teleport-out loop OR after it (either works; the busy state should be cleared once the player is no longer "in a dungeon"):

```java
for (Map.Entry<UUID, PlayerReturnPoint> e : run.returnPoints().entrySet()) {
    ServerPlayer p = world.getServer().getPlayerList().getPlayer(e.getKey());
    if (p != null) {
        // ... existing teleport ...
        BusyStateCompat.setNotBusy(p);
    }
}
```

Also in the hardcore branch of `handlePlayerDown`:

```java
if (run.hardcoreEnabled()) {
    // ... existing teleport + unregister ...
    BusyStateCompat.setNotBusy(player);  // ADD THIS
    player.sendSystemMessage(...);
    return;
}
```

#### Acceptance

- Starting a v4.0 run calls `BusyStateCompat.setBusy(player)` for each online party member.
- Finalizing a run calls `BusyStateCompat.setNotBusy(player)` for each online return-point holder.
- Hardcore death clears the busy state for the dying player.
- Build passes.

#### Don'ts

- Do not call `setBusy` on disconnected players (the startRun loop already skips them).
- Do not check `BusyStateCompat` BEFORE starting a run — that's the responsibility of the lobby system, not the lifecycle.
- Do not refactor `BusyStateCompat` — it's a stable external API.

#### References

- `BusyStateCompat` — existing legacy compat layer (search the codebase for `BusyStateCompat`).
- Legacy `DungeonBossSpawnerBlockEntity.startBattle` — pattern of `setBusy` call.

---

### Phase F exit criteria

After all 7 tasks:

- `DungeonManager` exists and registers controllers via clearRemoved/setRemoved.
- O(1) participant + mob lookups available.
- Linker has 4 new modes wired up for the v4.0 block topology.
- Mixin applies tier damage scaling to v4.0 mobs.
- Death events drive participant transitions (no poll).
- Disconnect/reconnect with grace period works.
- `BusyStateCompat` integration parallels legacy behavior.
- Build passes.
- Total new files: ~5 (manager, death listener, connection listener, +2-3 helper files).
- Total LOC: ~700-900.
- End-to-end gameplay possible without `/data`: place blocks, link via Linker, start run, complete dungeon.

After Phase F, Phase G (formerly H) deletes legacy code and renames `_v2` → canonical.

## Phase G — Migration cliff & cleanup (sequential PRs)

**Goal**: delete every line of legacy dungeon code, drop `_v2` suffixes from new dungeon registry IDs and class/file names, bump `mod_version` to 4.0.0.

**The point of no return.** After Phase G lands, the only dungeon implementation in the mod is the v4.0 one. Pre-v4.0 dungeon save data (legacy controller blocks placed in worlds) becomes unrecognized and silently dropped on load. The `_v2` test dungeons from your dev sessions become the canonical configuration once the suffix is removed.

### Phase G is NOT a single PR

A naïve "delete everything legacy + rename v2 to canonical" approach would produce a 2000-3000 line diff across 50+ files. Impossible to review carefully. Phase G is **split into 6 sequential tasks**, each one a reviewable unit with its own acceptance criteria.

The order matters — each task removes a dependency the next task needs gone.

### Scope: dungeon system ONLY

The mod has four independent legacy systems:
1. **Dungeon** (`DungeonControllerBlockEntity`, `DungeonBossSpawnerBlockEntity`, `MobSpawnerBlockEntity` in legacy locations, plus `DungeonBossManager`)
2. **Mob Arena** (`MobArenaControllerBlockEntity`, `MobArenaSpawnerBlockEntity`)
3. **Raid** (`RaidControllerBlockEntity`)
4. **Boss Spawner** (standalone `BossSpawnerBlockEntity`, used outside dungeons)

**Phase G only touches the dungeon system.** Arena, raid, and standalone boss spawner code remains untouched. Their packet handlers (`ArenaPacketHandlers`, `RaidPacketHandlers`), screen files, manager classes, and registrations are out of scope.

What gets deleted: anything whose **sole purpose** was to support the legacy dungeon system. What stays: shared utilities, the PhaseBlock (reused by v4.0), screens shared with other systems, code paths shared with arena/raid.

### Decisions locked in for Phase G

These came up during scoping:

- **Sequential tasks, not single PR.** 6 tasks (PG-1 through PG-6), each reviewable.
- **`PhaseBlock` and `PhaseBlockEntity` STAY.** Phase blocks are reused by the new Room system (PC-3). They're not "legacy dungeon code" — they're shared infrastructure.
- **`DungeonBossManager` GOES.** It exists exclusively for legacy dungeon support.
- **`LinkerItem` legacy modes GO.** Modes that link only legacy blocks (`MOB_SPAWNER_BOSS_LINK`, etc.) are deleted. Linker keeps the 4 v4.0 modes from PF-3 as its full mode set.
- **`SpawnerConfiguratorItem` STAYS** — it serves arena/raid/standalone boss too. Only the dungeon-related branches (which became the v4.0 DBS entrance) need cleanup if they're now redundant.
- **`AttributesScreen` / `EquipmentScreen` STAY.** They serve both legacy (other systems) and v4.0 (via AttributeProvider/EquipmentProvider interfaces). Already shared.
- **Legacy `Lobby`, `LobbyStatus`, `LobbyVisibility` in `util/` — GO.** Phase E (PE-3) ported them to `dungeon.lobby/` package. The util-package versions are obsolete.
- **Rename strategy: rename in place via Git.** Don't copy-paste-delete; use `git mv` so history is preserved. Codex can simulate this via `git rm` + new file creation if needed.
- **The v2 rename happens AFTER legacy deletion.** PG-1..PG-3 delete legacy; PG-4 renames `_v2` → canonical. This avoids any moment where two blocks compete for the same ID.
- **Mod version bump is PG-6, last.** No use bumping until everything compiles and tests pass.

### Tasks in this phase

- **PG-1** — Delete legacy block entity + block + manager files (the actual class files). Update registrations. Likely leaves the build broken — that's OK; PG-2 + PG-3 fix it.
- **PG-2** — Delete legacy networking (the dungeon-related parts of `ModPackets.java`, `SpawnerPacketHandlers.java`, `DungeonPacketHandlers.java`) and screens.
- **PG-3** — Delete legacy `Lobby`/`LobbyStatus`/`LobbyVisibility` in `util/`, legacy command subcommands, legacy lang keys, legacy mixin branches.
- **PG-4** — Rename `_v2` → canonical. Drops the v2 suffix from registry IDs, class names, lang keys, display strings.
- **PG-5** — Clean up `LinkerItem` legacy modes. Keep only the 4 v4.0 modes from PF-3.
- **PG-6** — Bump `mod_version` to `4.0.0`. Update mod metadata. Final smoke test.

After Phase G, the codebase has one dungeon implementation, `arenas_ld:dungeon_controller` is the v4.0 block, and `mod_version` is `4.0.0`.

---

### PG-1 — Delete legacy block + BE + manager files

**Goal**: remove the actual class files for legacy dungeon-only blocks, BEs, and the manager. Update registrations so the project at least *knows* they're gone. Build will likely break — that's expected; PG-2 fixes the network/screen leftovers.

**Files to DELETE:**

```
src/main/java/net/ledok/arenas_ld/block/DungeonControllerBlock.java
src/main/java/net/ledok/arenas_ld/block/DungeonBossSpawnerBlock.java
src/main/java/net/ledok/arenas_ld/block/MobSpawnerBlock.java
src/main/java/net/ledok/arenas_ld/block/entity/DungeonControllerBlockEntity.java
src/main/java/net/ledok/arenas_ld/block/entity/DungeonBossSpawnerBlockEntity.java
src/main/java/net/ledok/arenas_ld/block/entity/MobSpawnerBlockEntity.java
src/main/java/net/ledok/arenas_ld/manager/DungeonBossManager.java
```

Use `git rm` if Codex supports it; otherwise file deletion + commit. **DO NOT delete:**
- `BossSpawnerBlockEntity.java` (standalone boss, not dungeon)
- `MobArenaSpawnerBlockEntity.java` (arena system)
- `RaidControllerBlockEntity.java` (raid system)
- `PhaseBlockEntity.java` / `PhaseBlock.java` (reused by v4.0)
- `RespawnPointBlockEntity.java` / `RespawnPointBlock.java`
- The textures or assets for legacy blocks (PG-4 will retarget v2 textures to canonical names; legacy textures can be removed in PG-4 too)

**Files to MODIFY:**

- `src/main/java/net/ledok/arenas_ld/registry/BlockRegistry.java` — remove these registrations:
  - `DUNGEON_CONTROLLER_BLOCK`
  - `DUNGEON_BOSS_SPAWNER_BLOCK`
  - `MOB_SPAWNER_BLOCK`
- `src/main/java/net/ledok/arenas_ld/registry/BlockEntitiesRegistry.java` — remove these registrations:
  - `DUNGEON_CONTROLLER_BLOCK_ENTITY`
  - `DUNGEON_BOSS_SPAWNER_BLOCK_ENTITY`
  - `MOB_SPAWNER_BLOCK_ENTITY`
- `src/main/java/net/ledok/arenas_ld/registry/ModCreativeModeTabs.java` — remove legacy entries for the 3 deleted blocks. Keep v2 entries (PG-4 will rename them).
- `src/main/java/net/ledok/arenas_ld/ArenasLdMod.java` — remove `DUNGEON_BOSS_MANAGER` static field, `initialize()` call, any registration of its events. **Do NOT** remove `DUNGEON_MANAGER` (the v4.0 one).

#### Acceptance

- Files listed above are deleted from disk.
- Registry files compile (because they no longer reference deleted classes).
- **Build may fail** elsewhere — that's expected. PG-2 fixes the cascade.
- `git status` shows the 7 deletions plus the 4 modifications.

#### Don'ts

- Do NOT try to "delete everything that imports the deleted classes" in this task. That cascade is PG-2's job. PG-1 is just the surgical deletion of the BE files and their direct registrations.
- Do NOT delete the `PhaseBlock` or `PhaseBlockEntity`.
- Do NOT delete the `block/entity/BossSpawnerBlockEntity.java` (standalone — not dungeon).
- Do NOT touch `LinkerItem.java`, `LivingEntityMixin.java`, networking files, or screens. PG-2/3.

#### Expected breakage

After PG-1, files importing the deleted classes will fail to compile:
- `LinkerItem.java` (legacy modes)
- `LivingEntityMixin.java` (legacy dungeon branch)
- `ModPackets.java`, `SpawnerPacketHandlers.java`, `DungeonPacketHandlers.java`
- `CommandRegistry.java` (legacy dungeon subcommands)
- Various screen files

This is fine. PG-2/3 cleans them up.

---

### PG-2 — Delete legacy networking and screens

**Goal**: remove all networking handlers and screen files that exclusively served the legacy dungeon system. Restore build.

**Files to DELETE:**

```
src/main/java/net/ledok/arenas_ld/screen/DungeonBossSpawnerScreen.java
src/main/java/net/ledok/arenas_ld/screen/DungeonBossSpawnerScreenHandler.java
src/main/java/net/ledok/arenas_ld/screen/DungeonControllerScreen.java
src/main/java/net/ledok/arenas_ld/screen/DungeonControllerScreenHandler.java
src/main/java/net/ledok/arenas_ld/screen/DungeonControllerData.java
src/main/java/net/ledok/arenas_ld/screen/MobSpawnerScreen.java
src/main/java/net/ledok/arenas_ld/screen/MobSpawnerScreenHandler.java
src/main/java/net/ledok/arenas_ld/screen/MobSpawnerData.java
src/main/java/net/ledok/arenas_ld/screen/BossSpawnerData.java
src/main/java/net/ledok/arenas_ld/screen/BossSpawnerScreen.java
src/main/java/net/ledok/arenas_ld/screen/BossSpawnerScreenHandler.java
src/main/java/net/ledok/arenas_ld/networking/DungeonPacketHandlers.java
```

Wait — `BossSpawnerData/Screen/ScreenHandler` might be the standalone boss spawner, NOT dungeon. **Codex MUST verify** by reading these files first. If they reference `BossSpawnerBlockEntity` (not `DungeonBossSpawnerBlockEntity`), they're the standalone boss screens and **SHOULD NOT BE DELETED**.

A safer file list for PG-2 (definitely dungeon-only):

```
src/main/java/net/ledok/arenas_ld/screen/DungeonBossSpawnerScreen.java
src/main/java/net/ledok/arenas_ld/screen/DungeonBossSpawnerScreenHandler.java
src/main/java/net/ledok/arenas_ld/screen/DungeonControllerScreen.java
src/main/java/net/ledok/arenas_ld/screen/DungeonControllerScreenHandler.java
src/main/java/net/ledok/arenas_ld/screen/DungeonControllerData.java
src/main/java/net/ledok/arenas_ld/screen/MobSpawnerScreen.java
src/main/java/net/ledok/arenas_ld/screen/MobSpawnerScreenHandler.java
src/main/java/net/ledok/arenas_ld/screen/MobSpawnerData.java
src/main/java/net/ledok/arenas_ld/networking/DungeonPacketHandlers.java
```

For `BossSpawnerData/Screen/ScreenHandler`, Codex inspects and decides — if `BossSpawnerBlockEntity` still exists (it does — we kept it in PG-1), these stay.

**Files to MODIFY:**

- `src/main/java/net/ledok/arenas_ld/networking/ModPackets.java` — search for blocks of code that import or instantiate the legacy dungeon types. Remove **only those blocks**. Keep arena/raid/MobArena packet handlers. **Read carefully line by line; this file is 1263 lines.**
- `src/main/java/net/ledok/arenas_ld/networking/SpawnerPacketHandlers.java` — same approach. Keep arena/standalone-boss handlers. Remove dungeon ones.
- `src/main/java/net/ledok/arenas_ld/networking/ModPacketTypeRegistry.java` — remove registrations for deleted payload types. (Note: v4.0 payloads stay; only legacy dungeon ones go.)
- `src/main/java/net/ledok/arenas_ld/screen/ModScreenHandlers.java` — remove registrations for the deleted legacy dungeon screen handlers.
- `src/main/java/net/ledok/arenas_ld/client/ArenasLdClient.java` — remove screen factory registrations for the deleted screens.

#### Acceptance

- Files listed are deleted.
- `BossSpawnerData/Screen/ScreenHandler` were inspected by Codex; if they belonged to standalone boss, they STAYED.
- Build passes.
- `LivingEntityMixin.java` still has legacy DBS branches (NOT removed yet — that's PG-3's job). It compiles because PG-1 only removed the class FILES; if the mixin references them, the build is still broken until PG-3.

Hmm — wait. If PG-1 deletes the legacy classes and PG-3 is what removes the mixin's references, then PG-2 won't actually compile cleanly. Let me reorder:

**Revised PG ordering:**
- PG-1 deletes block + BE + manager files
- **PG-2 deletes legacy mixin/command/lifecycle code** (anything that imports the deleted classes). Build passes here.
- **PG-3 deletes legacy networking and screens** (cleanup that doesn't depend on classes).

Actually re-thinking: if the mixin uses `import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;` and that file is deleted in PG-1, then after PG-1 the mixin **fails to compile**. The cascade must be cleared before build can pass again. So PG-2 needs to fix the build — which means deleting from networking, mixin, command registry, and screens **in one task**.

Let me re-scope PG-2 to be "restore the build after PG-1":

**Revised PG-2: Restore build after deletions** — cleans up every file that referenced the deleted classes.

#### Don'ts

- Do not delete `BossSpawnerBlockEntity.java`-related screens (they serve the standalone boss spawner, which is NOT a dungeon component).
- Do not delete `EquipmentScreen.java`, `MobAttributesScreen.java`, or related Data files — these are shared with v4.0.
- Do not delete `MobArenaControllerScreen.java` family — arena system.
- Do not delete `RaidControllerScreen.java` family — raid system.

#### Sub-task: PG-2 actual file list (restore-build)

Since after PG-1 the build is broken, PG-2 must touch ALL files that reference the now-deleted classes. This is a bigger task than I initially scoped. Let me simplify by combining PG-2 and PG-3 into a single "restore build" task, and split based on direction (networking-side vs everything-else):

---

### PG-2 (revised) — Restore build: remove all references to deleted legacy classes

**Goal**: after PG-1's deletion, fix all the compilation errors by removing every reference to the deleted types.

**Files to MODIFY (mass cleanup):**

- `LinkerItem.java` — remove all legacy mode cases (search for references to deleted BE classes; remove the case blocks and the corresponding `LinkerMode` enum values).
- `LivingEntityMixin.java` — remove the legacy DBS branch from damage scaling. Remove the legacy DBS branch from death capture in `onSetHealth` / `onDie`. The legacy branches use `DUNGEON_BOSS_MANAGER` (already gone in PG-1) so they're dead code.
- `CommandRegistry.java` — remove dungeon subcommand branches that use the deleted classes. Keep arena/raid commands.
- `ArenasLdMod.java` — remove all `DUNGEON_BOSS_MANAGER` references.
- `networking/ModPackets.java` — remove legacy dungeon payloads + handlers. **Surgical, file is large.**
- `networking/SpawnerPacketHandlers.java` — remove legacy dungeon handlers.
- `networking/DungeonPacketHandlers.java` — entire file gone, but check it isn't called from elsewhere first.
- `networking/ModPacketTypeRegistry.java` — remove deleted payload registrations.
- `screen/ModScreenHandlers.java` — remove deleted screen handler registrations.
- `client/ArenasLdClient.java` — remove deleted screen factory registrations.

**Files to DELETE (legacy screens):**

- `screen/DungeonBossSpawnerScreen.java`
- `screen/DungeonBossSpawnerScreenHandler.java`
- `screen/DungeonControllerScreen.java`
- `screen/DungeonControllerScreenHandler.java`
- `screen/DungeonControllerData.java`
- `screen/MobSpawnerScreen.java`
- `screen/MobSpawnerScreenHandler.java`
- `screen/MobSpawnerData.java`
- `networking/DungeonPacketHandlers.java`

**Files to LEAVE ALONE:**

- `util/Lobby.java`, `util/LobbyStatus.java`, `util/LobbyVisibility.java` — legacy lobby utilities. PG-3 deletes them.
- `assets/arenas_ld/lang/*.json` — legacy lang keys. PG-3.
- Anything `_v2` — that's still the active code.

#### Acceptance

- All files listed are modified or deleted as specified.
- `./gradlew build` passes.
- `LivingEntityMixin` still works for v4.0 dungeons + arena + raid (legacy DBS branches gone).
- No references to deleted classes anywhere in the codebase.

#### Don'ts

- Do not also delete the legacy `Lobby`/`LobbyStatus`/`LobbyVisibility` from `util/` here. PG-3.
- Do not touch lang files yet. PG-3.
- Do not rename `_v2` anything. PG-4.

---

### PG-3 — Delete legacy lobby utils, command subcommands, lang keys

**Goal**: now that the build compiles without legacy classes, finish cleaning up the secondary remnants.

**Files to DELETE:**

```
src/main/java/net/ledok/arenas_ld/util/Lobby.java
src/main/java/net/ledok/arenas_ld/util/LobbyStatus.java
src/main/java/net/ledok/arenas_ld/util/LobbyVisibility.java
```

**Files to MODIFY:**

- `CommandRegistry.java` — remove any remaining legacy dungeon subcommands (e.g., `/arenasld lobby accept`, `/arenasld lobby decline` if they referenced the legacy Lobby util).
- `src/main/resources/assets/arenas_ld/lang/en_us.json` — remove every key that references the deleted legacy systems. Codex must walk through the file and identify dead keys. Look for keys starting with `gui.arenas_ld.dungeon_controller.*` (without `_v2`), `gui.arenas_ld.mob_spawner.*` (without `_v2`), `gui.arenas_ld.dungeon_boss_spawner.*` (without `_v2`), `message.arenas_ld.lobby.*` if those came from legacy.
- `src/main/resources/assets/arenas_ld/lang/uk_ua.json` — same.

**Important**: do NOT remove v4.0 lang keys that happen to use these prefixes. The v4.0 ones use `_v2` suffix in their keys: `gui.arenas_ld.dungeon_controller_v2.*`. Anything WITHOUT `_v2` is legacy. After PG-4 renames v2 → canonical, the prefix returns to no-suffix; PG-4 handles those lang renames.

#### Acceptance

- `util/Lobby.java`, `util/LobbyStatus.java`, `util/LobbyVisibility.java` deleted.
- No code in the project imports those classes.
- Legacy commands removed (or their bodies replaced with no-ops if needed for compat).
- Both lang files cleaned of dead keys.
- Build passes.

#### Don'ts

- Do not rename anything `_v2`. PG-4.
- Do not bump version yet. PG-6.
- Do not delete `dungeon.lobby/` package classes — those are the v4.0 ports.

---

### PG-4 — Rename `_v2` → canonical

**Goal**: now that no legacy class blocks the canonical names, drop the `_v2` suffix from registry IDs, class names, file names, lang keys, and display strings.

This is **the rename task**. It's substantial because the suffix appears in many places.

#### Registry ID renames

- `arenas_ld:dungeon_controller_v2` → `arenas_ld:dungeon_controller`
- `arenas_ld:dungeon_controller_v2_be` → `arenas_ld:dungeon_controller_be`
- `arenas_ld:dungeon_boss_spawner_v2` → `arenas_ld:dungeon_boss_spawner`
- `arenas_ld:dungeon_boss_spawner_v2_be` → `arenas_ld:dungeon_boss_spawner_be`
- `arenas_ld:mob_spawner_v2` → `arenas_ld:mob_spawner`
- `arenas_ld:mob_spawner_v2_be` → `arenas_ld:mob_spawner_be`

**Side effect**: dev test dungeons built before PG-4 use the v2 registry IDs. After PG-4, those blocks become unrecognized in saves. **You'll need to rebuild test dungeons.** Acceptable cost; v4.0 isn't released.

#### Asset renames

- `blockstates/dungeon_controller_v2.json` → `blockstates/dungeon_controller.json`
- `models/block/dungeon_controller_v2.json` → `models/block/dungeon_controller.json`
- `models/item/dungeon_controller_v2.json` → `models/item/dungeon_controller.json`
- `textures/block/dungeon_controller_v2.png` → `textures/block/dungeon_controller.png`

Same for `dungeon_boss_spawner_v2.*` and `mob_spawner_v2.*`. 12 file renames total (4 per block × 3 blocks).

#### Static field renames in `BlockRegistry.java` and `BlockEntitiesRegistry.java`

- `DUNGEON_CONTROLLER_V2_BLOCK` → `DUNGEON_CONTROLLER_BLOCK`
- `DUNGEON_CONTROLLER_V2_BLOCK_ENTITY` → `DUNGEON_CONTROLLER_BLOCK_ENTITY`
- (etc. — 6 static fields renamed)

#### Lang key renames

- `gui.arenas_ld.dungeon_controller_v2.*` → `gui.arenas_ld.dungeon_controller.*`
- `gui.arenas_ld.dungeon_controller_admin.*` — STAYS (no v2 collision since legacy never had this concept; admin GUI is v4.0-only)
- `gui.arenas_ld.mob_spawner_v2.*` → `gui.arenas_ld.mob_spawner.*`
- `gui.arenas_ld.dungeon_boss_spawner_v2.*` → `gui.arenas_ld.dungeon_boss_spawner.*`
- `block.arenas_ld.dungeon_controller_v2` → `block.arenas_ld.dungeon_controller`
- (etc.)

Both lang files.

#### Display name updates

- `"block.arenas_ld.dungeon_controller_v2": "Dungeon Controller (v2)"` → `"block.arenas_ld.dungeon_controller": "Dungeon Controller"`

Remove the `(v2)` suffix from the display strings now that there's no legacy counterpart.

#### Class names: KEEP `_v2` suffix or rename?

The class files are at:
- `dungeon/block/DungeonControllerBlock.java` (no _v2 suffix in class/file name)
- `dungeon/block/DungeonBossSpawnerBlock.java` (no _v2)
- `dungeon/block/MobSpawnerBlock.java` (no _v2)
- `dungeon/blockentity/*.java` — same

**Class/file names DON'T have the `_v2` suffix.** Only registry IDs and lang keys do. So no Java class renames are needed in PG-4 — they're already canonically named (since legacy classes were in different packages: `block/` vs `dungeon/block/`).

Phew. The Java class rename was a phantom worry. PG-4 is registry IDs + assets + lang only.

#### Files to MODIFY

- `BlockRegistry.java` — rename the 6 static fields and update their string IDs.
- `BlockEntitiesRegistry.java` — rename the 3 BE registrations and update their string IDs.
- All callers of those static fields (~10-15 files) — Codex does a global find-replace `BlockRegistry.DUNGEON_CONTROLLER_V2_BLOCK` → `BlockRegistry.DUNGEON_CONTROLLER_BLOCK` (etc.).
- Both lang files — update keys.
- `ModCreativeModeTabs.java` — update field references.

#### Files to RENAME

The 12 asset files (3 blockstates + 3 block models + 3 item models + 3 textures). Use `git mv` for history.

#### Acceptance

- All v2 registry IDs are gone from the codebase.
- All v2 lang keys are gone.
- All assets are renamed to canonical names.
- Display names dropped the "(v2)" suffix.
- Build passes.
- Loading a world with v2-named test dungeons gracefully drops them (vanilla behavior for unknown blocks).

#### Don'ts

- Do not rename Java class names — they already don't have `_v2`.
- Do not touch package paths — `dungeon.block`, `dungeon.blockentity` are correct.
- Do not rename anything in arena/raid/standalone-boss systems.

---

### PG-5 — Linker cleanup (remove legacy modes)

**Goal**: trim `LinkerItem.java` down to ONLY the 4 v4.0 modes from PF-3.

PG-2's cleanup should already have done most of this (it removed legacy mode cases that referenced deleted BE classes). PG-5 finishes the job: confirm the enum has exactly 4 values and they correspond to the 4 v4.0 modes. Remove any leftover legacy switch-case branches, helper methods, lang keys, etc.

**Files to MODIFY:**

- `LinkerItem.java` — final pass; ensure clean state.
- `assets/arenas_ld/lang/*.json` — verify no leftover `item.arenas_ld.linker.mode.*` keys for legacy modes.

#### Acceptance

- `LinkerMode` enum has exactly 4 values: `CONTROLLER_INSTANCE`, `DBS_ROOM`, `ROOM_SPAWNER`, `ROOM_DOOR`.
- Mode-cycle wraps cleanly through 4 modes.
- No dead branches or unreachable code.
- All lang keys for the 4 modes present in both locales.
- Build passes.

#### Don'ts

- Do not delete `LinkerItem.java` itself — it's still used (for v4.0 modes).
- Do not change the v4.0 mode semantics or names.

---

### PG-6 — Version bump and final smoke test

**Goal**: bump the mod version to `4.0.0` and verify everything works end-to-end.

**Files to MODIFY:**

- `gradle.properties` — change `mod_version` to `4.0.0` (verify exact property name).
- `src/main/resources/fabric.mod.json` — update version field if it doesn't read from gradle.

#### Acceptance

- Build passes with mod_version 4.0.0.
- In-game: mod info menu shows version 4.0.0.
- Smoke test: build a complete dungeon (controller + DBS + rooms + spawners + doors), link via Linker, run as player, win, lose, disconnect/reconnect — all of Phase F's features still work.
- No exceptions in log on world load.

#### Don'ts

- Do not bump to `4.0.0` until PG-1..PG-5 are all done.
- Do not change the mod ID (`arenas_ld` stays).
- Do not change author/license metadata.

---

### Phase G exit criteria

After all 6 tasks:

- No legacy dungeon code in the repo.
- Canonical registry IDs (no `_v2`).
- Linker has 4 modes, all v4.0.
- mod_version is `4.0.0`.
- Build passes.
- End-to-end gameplay still works as expected.
- The branch `4.0` can be merged to `main` and tagged as a release.

Estimated total: ~50 files changed, ~2500 net deletions, ~30 net additions (the rename diff is roughly even).

---

## Phase status tracker

| Phase | Status | PR | Last reviewed SHA |
|---|---|---|---|
| A | ✅ Complete (PA-1, PA-1.1, PA-2, PA-3, PA-4) | — | `1b226e2` |
| B | ✅ Complete (PB-1..PB-8, PB-10; PB-9 skipped as redundant) | — | `4717f45` |
| C | ✅ Complete (PC-1, PC-2, PC-3-old, PC-3, PC-4, PC-4.1, PC-5, PC-7; PC-6 skipped) | — | `e0ba2e5` |
| D | ✅ Complete (PD-1..PD-7, PD-6.1) | — | `965831a` |
| E | ✅ Complete (PE-1..PE-12, +PE-5.1, +PE-10.1, +PE-11.1, +PE-12.1) | — | `716f73a` |
| F | ✅ Complete (PF-1..PF-4, PF-6, +follow-ups PE-4.1/PC-3.1/PF-3.5/PF-3.5b/PE-4.2/PE-6.1/PF-4.1; PF-5 + PF-7 superseded) | — | `7f8924b` |
| G | Specified (PG-1..PG-6, sequential PRs) | — | — |

We update this table as we go.

### Decisions made during execution

These supersede earlier guidance in the plan if they conflict:

- **PB-9 skipped**: per-task codec round-trip tests are already exhaustive; a consolidated meta-test would be pure duplication. Moved straight from PB-8 to PB-10.
- **PC-5 / PC-6 skipped (live gametest dropped)**: rationale below. The plan originally called for a Loom run config and a smoke gametest. After Phase B + Phase C tasks 1–4 landed cleanly via the review loop, the value of an automated gametest against the cost of building it (in-game structure construction, .snbt export, Loom runner setup, ongoing maintenance) didn't pencil out. The reasons:
  - This is a single-developer project where the developer is testing in-game between phases anyway.
  - The first dungeon run after Phase E exercises every code path the gametest would have.
  - Unit tests in Phase B are cheap and already cover the algorithmic risks (codecs).
  - The architectural risk in v4.0 ("did we model the new system right?") is what gametests *don't* answer — that's a design-review question, which we already do via this chat loop.
  - The mod has shipped 3.3.0 with zero gametests; the existing safety nets are sufficient.
  - If a regression is hard to catch by eye later (likely once Phase E lifecycle code lands), gametests can be added then at low cost.
  - **Codex must not implement PC-5 or PC-6.** The original specs remain in the plan inside `<details>` blocks for future reference only.
  - **PC-5 was technically done before this decision** — Codex added the loom block to build.gradle in commit `05fc5f6`. We're leaving that block in place; it's harmless idle config. If we ever want to do gametests later, the infra is ready.
- **Task granularity**: tasks are executed one at a time, not batched. Each gets a full review against acceptance criteria before the next one starts. This adds chat overhead but catches errors early.
- **Direct commits to `4.0`**: no per-task PRs; commits go straight to the branch. PR-per-phase was a hypothetical for multi-reviewer projects; for a single-author project, direct commits are fine.
- **`ParticipantStatus` is package-private**: kept narrow on purpose. Widen to public only when an outside-package consumer in Phase E actually needs to reference it.
- **`DungeonRun` mutators are package-private**: lifecycle code that drives state transitions must live in the `net.ledok.arenas_ld.dungeon.run` package. This is enforced architecturally, not by convention.
- **Map serialization uses `UUIDUtil.STRING_CODEC` as the key codec**: `UUIDUtil.CODEC` (int-array form) does not work as a NBT map key. The string form is also more debuggable.
- **NBT redundancy on participants/downedPlayers maps**: each UUID is stored both as the map key AND inside the value record. ~16 bytes per entry of waste, no behavior impact. Filed for future cleanup; not a current concern.
- **Damage source filter default**: `dungeon_damage_source_filter` defaults to `ALL` in v4.0 config. Players have high HP; fall/lava/drowning scaling is fair.
- **PC-4.1 follow-up**: PC-4 shipped with hardcoded user-facing strings. PC-4.1 added the i18n translation keys and the `markDirtyAndSync` calls in the new room handlers. Going forward, every user-facing string in new code uses `Component.translatable(...)` from the start.
- **Codec conventions (PD-1 onwards)**:
  - **`optionalFieldOf("key", default)` over `fieldOf("key")`** wherever a reasonable default exists. Lets old saves load when fields are added/removed in future. Especially for any field on a class that already exists in legacy saves.
  - **Add `equals` + `hashCode` to any non-record class that gets a codec.** Codec round-trip tests use `assertEquals`, and consumers may use the instances as map keys / set members. The two changes are inseparable in practice. Records get this for free; plain classes don't.
- **`EntityEquipmentHelper.applyEquipment` signature (corrected during PD-3)**: the 4th parameter is `boolean dropChance`, not `float`. `false` = regular mob (no drops on death). `true` = boss-style (drops at full chance, since `EquipmentData.dropChance` is itself a boolean). My PD-3 spec wrongly said "0.0F" — the corrected pattern is what's actually in PD-3's commit.
- **Known edge case from PF-2** (filed, not fixed): mid-run server restarts may leave mobs spawned by previous-session rooms unregistered in the `DungeonManager.runByMob` map. The bootstrap in `tickRunning` only fires once (when no participant is registered yet) and registers all currently-known mobs at that instant. If the DBS chunk isn't loaded at that moment, mob registration is missed, and `LivingEntityMixin` (PF-4) won't apply tier damage scaling to those mobs. The mob still functions correctly; players just experience them as "underpowered" until the next room spawns fresh mobs. Acceptable for v4.0; revisit if it becomes a real complaint.
