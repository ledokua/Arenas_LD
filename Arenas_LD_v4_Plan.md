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

Goal of the phase: introduce the brand-new `RoomController` block. It does not interact with anything outside itself yet — the controller doesn't exist; the new DBS doesn't exist. The room's `activate(tier)` and `reset()` work against the world directly (spawning/despawning entities by querying linked spawner positions).

The new `MobSpawner` block is *not* introduced in this phase — the room talks to the *existing* `MobSpawnerBlockEntity` for now. Phase D replaces it.

### PC-1: `RoomController` block + block entity

**Goal**: register a new block + block entity in the new package.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/block/RoomControllerBlock.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/blockentity/RoomControllerBlockEntity.java`
- `src/main/resources/assets/arenas_ld/blockstates/room_controller.json`
- `src/main/resources/assets/arenas_ld/models/block/room_controller.json`
- `src/main/resources/assets/arenas_ld/models/item/room_controller.json`
- `src/main/resources/assets/arenas_ld/textures/block/room_controller.png` (placeholder solid color is fine)
- Lang entries in `en_us.json`: `block.arenas_ld.room_controller` = `"Room Controller"`.

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/registry/BlockRegistry.java` — register the block.
- `src/main/java/net/ledok/arenas_ld/registry/BlockEntitiesRegistry.java` — register the block entity type.

**Block spec:**
```java
public class RoomControllerBlock extends Block implements EntityBlock {
    public RoomControllerBlock(Properties props) { super(props); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RoomControllerBlockEntity(pos, state);
    }
    // Standard iron-block-style properties: strength(-1.0f, 3600000.0f), unbreakable.
}
```

**Block entity spec:** (full spec in PC-2)

**Acceptance:**
- Block placeable in creative.
- Block entity persists across save/load (empty NBT for now is fine).
- `./gradlew build` passes.

**Don'ts:** don't add a GUI yet (Phase C-4). Don't add `tick()` yet (Phase C-3). Don't add the spawner list yet (PC-2). Texture can be a solid color placeholder.

---

### PC-2: `RoomControllerBlockEntity` — data model

**Goal**: the room's persisted state: spawner positions and door position.

**Files to modify:** `RoomControllerBlockEntity.java`

**Spec:**
```java
public class RoomControllerBlockEntity extends BlockEntity {
    /** Positions of spawners (MobSpawner or DungeonBossSpawner) this room owns. */
    private final List<BlockPos> spawnerPositions = new ArrayList<>();

    /** The phase block this room opens on clear. Null = no door (e.g. spawn room). */
    @Nullable
    private BlockPos doorPos = null;

    /** UUIDs of currently-alive mobs spawned by this room. Cleared on reset. */
    private final Set<UUID> aliveMobs = new HashSet<>();

    /** Has the room been activated (mobs spawned) in the current run? */
    private boolean activated = false;

    /** Has the room been cleared (all mobs dead)? */
    private boolean cleared = false;

    public RoomControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.ROOM_CONTROLLER_BLOCK_ENTITY, pos, state);
    }

    // Getters
    public List<BlockPos> getSpawnerPositions() { return Collections.unmodifiableList(spawnerPositions); }
    @Nullable public BlockPos getDoorPos() { return doorPos; }
    public Set<UUID> getAliveMobs() { return Collections.unmodifiableSet(aliveMobs); }
    public boolean isActivated() { return activated; }
    public boolean isCleared() { return cleared; }

    // Admin/Linker operations
    public boolean addSpawner(BlockPos pos) { ... add if not present, return true ... }
    public boolean removeSpawner(BlockPos pos) { ... }
    public void clearSpawners() { ... }
    public void setDoorPos(@Nullable BlockPos pos) { ... }

    // Runtime operations (called by Phase E controller code, not implemented here yet)
    void markActivated() { activated = true; setChanged(); }
    void markCleared() { cleared = true; setChanged(); }
    void clearRuntimeState() {
        activated = false;
        cleared = false;
        aliveMobs.clear();
        setChanged();
    }
    void trackSpawnedMob(UUID uuid) { aliveMobs.add(uuid); setChanged(); }
    void untrackSpawnedMob(UUID uuid) { aliveMobs.remove(uuid); setChanged(); }

    // NBT via codec — codec defined as static field, used in saveAdditional/loadAdditional
    private static final Codec<RoomState> STATE_CODEC = ...;

    private record RoomState(
        List<BlockPos> spawners,
        Optional<BlockPos> door,
        Set<UUID> aliveMobs,
        boolean activated,
        boolean cleared
    ) {
        static final Codec<RoomState> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().fieldOf("spawners").forGetter(RoomState::spawners),
            BlockPos.CODEC.optionalFieldOf("door").forGetter(RoomState::door),
            UUIDUtil.CODEC.listOf().xmap(HashSet::new, ArrayList::new).fieldOf("aliveMobs").forGetter(rs -> new ArrayList<>(rs.aliveMobs())),
            Codec.BOOL.fieldOf("activated").forGetter(RoomState::activated),
            Codec.BOOL.fieldOf("cleared").forGetter(RoomState::cleared)
        ).apply(i, RoomState::new));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.saveAdditional(nbt, lookup);
        RoomState state = new RoomState(spawnerPositions, Optional.ofNullable(doorPos), aliveMobs, activated, cleared);
        STATE_CODEC.encodeStart(NbtOps.INSTANCE, state)
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to save RoomController: {}", err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.loadAdditional(nbt, lookup);
        if (nbt.contains("State")) {
            STATE_CODEC.parse(NbtOps.INSTANCE, nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to load RoomController: {}", err))
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

**Acceptance:**
- All getters/setters work.
- `addSpawner` deduplicates (returns false if already present).
- `removeSpawner` returns true only if it was present.
- NBT round-trip: place block, add 3 spawners, set door, mark activated, save, /reload, fields preserved.

**Don'ts:**
- No `activate(tier)` / `reset()` / `openDoor()` yet — Phase C-3.
- No GUI / screen handler yet — Phase C-4.
- Do not store the *parent* DBS reference here. The DBS owns its rooms; the room doesn't need to know its parent.
- Do not validate that `pos` is a valid spawner — admins can typo, the validation happens at activate time.

**References:**
- Old `DungeonBossSpawnerBlockEntity.linkedSpawners` (lines 86, 405–431) — same idea, but for a room.

---

### PC-3: `RoomController` runtime methods

**Goal**: `activate(tier)`, `reset()`, `openDoor()`, `refreshAliveMobs()`, `isCleared()` semantics implemented against the world.

**Files to modify:** `RoomControllerBlockEntity.java`

**Spec:** (added methods on the same class)

```java
/**
 * Spawn this room's mobs, applying the tier's health multiplier.
 * For each spawner position in the room:
 *   - If it's a MobSpawnerBlockEntity (old) — call spawner.spawnMob() and track UUID.
 *   - If it's a DungeonBossSpawnerBlockEntity — call dbs.spawnBoss() and track UUID.
 *   - If neither — log a warning and skip.
 * Sets activated=true. No-op if already activated.
 *
 * @param tier the active tier for scaling
 * @return number of mobs actually spawned
 */
public int activate(ServerLevel level, TierConfig tier) { ... }

/**
 * Despawn any alive mobs this room spawned, clear runtime state, close the door.
 * Idempotent — safe to call multiple times.
 */
public void reset(ServerLevel level) { ... }

/**
 * Open the room's door (set phase block to non-solid). No-op if no door, or door isn't a phase block.
 */
public void openDoor(ServerLevel level) { ... }

/**
 * Close the room's door (set phase block to solid). No-op if no door.
 */
public void closeDoor(ServerLevel level) { ... }

/**
 * Remove any aliveMobs UUIDs whose entities are dead or unloaded.
 * Updates `cleared` to true if no alive mobs remain after activation.
 * Should be called by the controller's tick each tick during a run.
 */
public void refreshAliveMobs(ServerLevel level) { ... }
```

**Detailed behavior:**

`activate(level, tier)`:
1. If `activated`, return 0.
2. For each `BlockPos spawnerPos` in `spawnerPositions`:
   - Get the block entity. If not loaded, force-load the chunk first.
   - If `instanceof net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity old`, call a NEW method `old.spawnSingleMobScaled(tier.healthMultiplier())` which returns the spawned `Mob` or null. (This method is added to the old class in PC-3-old below.)
   - If `instanceof net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity old`, call `old.spawnSingleBossScaled(tier.healthMultiplier())`. Same return.
   - If neither: log warn, skip.
   - For each returned entity, add its UUID to `aliveMobs`.
3. Set `activated=true`, set `setChanged()`.
4. Return the count.

`reset(level)`:
1. For each UUID in `aliveMobs`, look up the entity in the level. If present and alive, `entity.discard()`.
2. Clear `aliveMobs`, set `activated=false`, set `cleared=false`.
3. Call `closeDoor(level)`.
4. `setChanged()`.

`openDoor(level)`:
- If `doorPos == null` return.
- Get block at `doorPos`. If `instanceof PhaseBlock`, change its blockstate to "unsolid" (the existing PhaseBlock has solid/unsolid variants per the assets).
- The exact API to change PhaseBlock state — see References below.

`refreshAliveMobs(level)`:
1. Iterator over `aliveMobs`. For each UUID, look up the entity. If null, removed, dead, or in another dimension → remove from set.
2. If `activated && !cleared && aliveMobs.isEmpty()` → set `cleared=true`, `setChanged()`.
3. **Do not call `openDoor()` here.** That's the controller's job — the controller observes `isCleared()` becoming true and then decides to open the door + advance.

**Acceptance:**
- Gametest PC-3-T1: place RoomController, link 2 MobSpawners with zombie configs, call activate(NORMAL), assert 2 zombies in world, both UUIDs in aliveMobs, activated=true.
- Gametest PC-3-T2: as above, then kill both zombies, call refreshAliveMobs each tick for 5 ticks, assert aliveMobs empty, cleared=true.
- Gametest PC-3-T3: as above, then call reset(), assert no zombies in world, aliveMobs empty, activated=false, cleared=false.
- Gametest PC-3-T4: place phase block, call setDoorPos, call openDoor, assert blockstate changed.

**Don'ts:**
- Do not despawn mobs in `activate()` — only spawn.
- Do not auto-open the door inside `refreshAliveMobs()` — controller's job.
- Do not retry spawning on failure — log and continue.
- Do not synchronize access to `aliveMobs` — server thread only.
- Do not check for the parent DBS or controller here — rooms are owned-from-above; they don't reach up.

**References:**
- Existing `PhaseBlock` and its blockstates JSON (`blockstates/phase_block.json`, `models/block/phase_block_solid.json`, `models/block/phase_block_unsolid.json`).
- Existing `MobSpawnerBlockEntity.spawn*` methods.
- Existing chunk-forcing pattern in `DungeonBossSpawnerBlockEntity.updateChunkLoading()` (line ~521).

---

### PC-3-old: Add `spawnSingleMobScaled` to old `MobSpawnerBlockEntity` and `DungeonBossSpawnerBlockEntity`

**Goal**: minimal extension to old classes so the new room can drive them. Old behavior elsewhere unchanged.

**Files to modify:**
- `src/main/java/net/ledok/arenas_ld/block/entity/MobSpawnerBlockEntity.java`
- `src/main/java/net/ledok/arenas_ld/block/entity/DungeonBossSpawnerBlockEntity.java`

**Spec:**

On `MobSpawnerBlockEntity` add:
```java
/**
 * Spawn one mob using this spawner's config, scaled by the given health multiplier.
 * Used by the v4.0 RoomController. Does not interact with the legacy wave/loot system.
 *
 * @return the spawned Mob, or null on failure
 */
@Nullable
public Mob spawnSingleMobScaled(double healthMultiplier) { ... }
```

On `DungeonBossSpawnerBlockEntity` add:
```java
/**
 * Spawn one boss entity using this spawner's config (entityType, attributes, equipment),
 * scaled by the given health multiplier. v4.0 RoomController consumer.
 *
 * @return the spawned LivingEntity, or null on failure
 */
@Nullable
public LivingEntity spawnSingleBossScaled(double healthMultiplier) { ... }
```

Each method:
1. Look up the entity type from the stored mobId.
2. Create the entity. If null, log error and return null.
3. Apply attributes (existing pattern), with `max_health` multiplied by `healthMultiplier`.
4. Apply equipment.
5. Move to spawn position (existing pattern: above the spawner block).
6. `level.addFreshEntity(entity)`.
7. Heal to max.
8. Return the entity.

**Acceptance:** old code unaffected for current callers. New methods callable from PC-3 gametests.

**Don'ts:** don't refactor anything else in these old classes. Don't change existing behavior. Don't add new fields.

---

### PC-4: `RoomController` admin GUI

**Goal**: a screen for admins to manage the room's spawner list and door, without using commands.

**Files to create:**
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/RoomControllerScreen.java` (client)
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/RoomControllerScreenHandler.java`
- `src/main/java/net/ledok/arenas_ld/dungeon/screen/RoomControllerData.java` (the ExtendedScreenHandlerFactory data record)
- `src/main/java/net/ledok/arenas_ld/dungeon/packet/RoomControllerPackets.java` (C2S: RemoveSpawnerPacket, SetDoorPacket, ClearSpawnersPacket)

**Files to modify:**
- `RoomControllerBlockEntity.java` — implement `ExtendedScreenHandlerFactory<RoomControllerData>`.
- `RoomControllerBlock.java` — `use(...)` opens the screen for ops only.
- `ModScreenHandlers.java` — register the new screen handler.
- `ModPackets.java` — register the new C2S packets (we'll split this file in a later phase; for now add here).

**Screen layout:**
- Title: "Room Controller"
- A list (scrollable) of `spawnerPositions`, each row: `[x, y, z]  [Remove]`
- An "Add spawner" hint: "Use the Linker tool" (no inline add via GUI).
- A field showing `doorPos` (or "no door"), with a "Clear door" button. Setting the door is via Linker.
- A "Reset Room" button (calls `reset()` — admin-only).

**Acceptance:**
- Op opens the block, sees the GUI.
- Non-op gets no GUI (use() returns InteractionResult.PASS).
- Remove button removes the spawner from the list, sync'd to server, persists.
- Reset Room calls `reset()` on the BE.

**Don'ts:**
- No inline "add spawner" form in the GUI — Linker is the only way to add (Phase F).
- No mob preview, no spawner config editing here.
- Do not use shared components from old screens — start fresh in the new package.

---

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
| A | Not started | — | — |
| B | Not started | — | — |
| C | Not started | — | — |
| D | Not specified | — | — |
| E | Not specified | — | — |
| F | Not specified | — | — |
| G | Not specified | — | — |
| H | Not specified | — | — |

We update this table as we go.
