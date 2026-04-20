# Arenas LD — Big Changes Plan

## Overview

Four interconnected systems being redesigned plus a full controller UI rework. Each phase is ordered by dependency — Phase 1 can ship alone, Phase 2 builds on it, Phase 3 builds on Phase 2. Phase 4 (phase block rework) and Phase 5 (controller UI / lobby system) are designed together but Phase 5 depends on Phase 2 being done first.

---

## Phase 1 — Difficulty Tiers

**Goal:** Players choose a difficulty before entering. Each tier scales mob stats and gives different loot.

### New: `DifficultyTier` enum

Four fixed tiers. Defaults:

| Tier      | Health mult | Damage mult |
|-----------|-------------|-------------|
| Easy      | 0.75×       | 0.75×       |
| Normal    | 1.0×        | 1.0×        |
| Hard      | 1.5×        | 1.5×        |
| Nightmare | 2.0×        | 2.0×        |

### New: `TierConfig` data class

Per-spawner overrides for each tier. Stored as `Map<DifficultyTier, TierConfig>` on `DungeonBossSpawnerBlockEntity`. Fields per tier:
- Health multiplier override (falls back to enum default if empty)
- Damage multiplier override (falls back to enum default if empty)
- Loot table ID override (falls back to spawner default if empty)
- Per-player loot table ID override (falls back to spawner default if empty)
- Hardcore flag override

### Damage scaling — player receives more damage

Damage multiplier is applied to the **player**, not the mob. Works for all damage types — melee, arrows, fireballs, explosions, modded abilities — with zero projectile tracing needed.

Implementation: `@ModifyArg` mixin on `LivingEntity.actuallyHurt()`. Before damage is applied to a `ServerPlayer`, check if they are in an active dungeon and multiply by the active tier's damage multiplier.

Config option in `ArenasLdConfig`:
```json
"dungeon_damage_source_filter": "ALL"
```
Options: `"ALL"` (everything), `"LIVING"` (only from living entities), `"MOB"` (only from mobs, excludes players).

### Changes to `DungeonBossSpawnerBlockEntity`

- Add `Map<DifficultyTier, TierConfig> tierConfigs` field (saved to NBT)
- Add `DifficultyTier activeTier` field (saved to NBT — survives restart mid-run)
- In `startBattle()`: resolve active tier, multiply base attribute values by health multiplier
- In `handleBattleWin()`: use tier's loot table IDs if set, otherwise fall back to spawner defaults
- **Remove `respawnCooldown` field** — cooldown is now owned by the controller (Phase 2)

### Changes to leaderboard

`List<DungeonLeaderboardEntry>` → `Map<DifficultyTier, List<DungeonLeaderboardEntry>>`

NBT migration: existing entries load as `NORMAL` tier automatically.

`upsertLeaderboardEntry()` takes a `DifficultyTier` parameter.

### Changes to `DungeonControllerBlockEntity`

- Add `DifficultyTier selectedTier` field (default `NORMAL`, saved to NBT)
- Passed into `startDungeon()` and forwarded to the spawner

### New UI: Tier config tab on spawner screen

New tab in `DungeonBossSpawnerScreen`. Four rows, one per tier. Each row:
- Health multiplier field
- Damage multiplier field
- Loot table ID field
- Per-player loot table ID field
- Hardcore checkbox

Empty fields inherit enum defaults.

---

## Phase 2 — Instance Pool + Controller-Owned Cooldown

**Goal:** One controller manages multiple physical dungeon copies. Parties are routed to a free instance automatically. The controller owns all cooldown state — dungeon spawner chunks only need to be loaded during active runs.

**Requires:** Phase 1

### Core architecture change: controller owns the cooldown

Previously, `respawnCooldown` lived on `DungeonBossSpawnerBlockEntity`, forcing the spawner's chunk to be permanently force-loaded.

**New model:**
- Controller tracks a cooldown per instance slot
- When a run ends, spawner notifies controller → controller starts its own cooldown → controller calls `spawner.fullReset()` (clears all state, no cooldown on spawner)
- Spawner returns to completely idle — chunk can unload freely
- Only the controller chunk is force-loaded (lives in lobby, likely already loaded)
- Spawner chunk only loaded during active runs (players are inside it anyway)

**Run lifecycle:**
```
Party starts
  → Controller finds free slot (cooldownTicksRemaining == 0)
  → Controller force-loads spawner chunk
  → Controller calls spawner.startDungeon()
  → Spawner runs the encounter
  → Run ends (win or loss)
  → Spawner calls controller.onRunEnded(wasWin)
  → Controller starts its own cooldown for that slot
  → Controller calls spawner.fullReset()
  → Spawner chunk unloads naturally
  → Controller ticks cooldown until 0
  → Controller notifies DungeonBossManager → subscribers notified
```

### New: `DungeonInstanceRef` record

```java
record DungeonInstanceRef(BlockPos spawnerPos, ResourceKey<Level> dimension) {}
```

### New: `InstanceState` per-slot data

```java
record InstanceState(
    DungeonInstanceRef ref,
    InstanceStatus status,   // FREE, RUNNING, COOLDOWN
    int cooldownTicksRemaining
) {}
```

### Changes to `DungeonControllerBlockEntity`

```java
// Before
private BlockPos dungeonSpawnerPos;
private ResourceKey<Level> dungeonSpawnerDimension;

// After
private List<InstanceState> instances = new ArrayList<>();
private int respawnTimeTicks = 6000; // replaces per-spawner respawnTime
```

NBT migration: existing single spawner pos becomes a one-element list on load with status FREE.

**Instance selection:** Pick first slot where `status == FREE`. All difficulty tiers share the same cooldown on a slot.

**Cooldown tick:** Controller's own `tick()` decrements `cooldownTicksRemaining` per COOLDOWN slot. When it hits 0 → FREE → notify `DungeonBossManager`.

### Changes to `DungeonBossSpawnerBlockEntity`

- Remove `respawnCooldown` and all cooldown logic
- Remove self-managed chunk-forcing
- Add `notifyControllerRunEnded(boolean wasWin)`
- Add `fullReset()` — clears all run state, returns to completely idle
- `tick()` simplified — only handles active run logic

### Changes to `DungeonBossManager`

```java
// Before: name → spawner pos, force-loads spawner chunk
// After:  name → controller pos, force-loads controller chunk only
```

- `tick()` calls `controller.hasAnyFreeInstance()` instead of reading spawner cooldown
- Subscribe notification fires when any slot transitions COOLDOWN → FREE
- `/arenasld dungeon register` now targets the **controller block**, not the spawner

### Registering instances — two methods

**In-world:** Linker in `Dungeon Controller Linking` mode.
- Right-click controller → select it
- Right-click a spawner → add to pool (right-click again → remove)
- Right-click controller while selected → list all instances + status

**Cross-dimension:** New commands:
```
/arenasld dungeon addinstance <ctrl_x> <ctrl_y> <ctrl_z> <spawner_x> <spawner_y> <spawner_z> <dimension>
/arenasld dungeon removeinstance <ctrl_x> <ctrl_y> <ctrl_z> <spawner_x> <spawner_y> <spawner_z>
/arenasld dungeon listinstances <ctrl_x> <ctrl_y> <ctrl_z>
```

---

## Phase 3 — Lobby System + Controller UI Rework

**Goal:** Replace the current single party system with a full lobby system. Multiple parties can form simultaneously on one controller. Lobbies can be open (anyone joins) or invite-only. Only the lobby owner can start the dungeon.

**Requires:** Phase 2

### New: `Lobby` class

```java
class Lobby {
    UUID id;
    UUID ownerUuid;
    String ownerName;
    List<UUID> members;        // ordered — first member after owner inherits ownership
    List<UUID> pendingInvites; // expires after 5 minutes
    LobbyVisibility visibility; // OPEN, INVITE_ONLY
    DifficultyTier selectedTier;
    boolean hardcoreEnabled;
    LobbyStatus status;        // OPEN, QUEUED, IN_DUNGEON
}
```

Replaces the old `QueuedParty` record — a lobby with `status == QUEUED` is the queue entry.

### Changes to `DungeonControllerBlockEntity`

```java
private List<Lobby> lobbies = new ArrayList<>();
private int maxPartySize = 4; // map maker configures this, saved to NBT
```

- **Create lobby:** Any player can create one. They become the owner.
- **Join lobby:** Any open lobby can be joined directly. Invite-only lobbies require a pending invite.
- **Start dungeon:** Owner only. Finds a free instance slot, starts immediately if one is available, otherwise sets lobby `status = QUEUED`.
- **Queue resolution:** When a slot becomes FREE, the oldest QUEUED lobby is notified in chat.
- **Ownership transfer:** When owner leaves or goes offline, ownership passes to `members.get(0)`. They receive a chat message: *"You are now the lobby owner."* If the last member leaves, the lobby dissolves.
- **Invite expiry:** Pending invites auto-expire after 5 minutes.
- **Stale lobby cleanup:** Every 30 seconds, check all lobbies for players offline > 5 minutes. Remove them. If this empties a lobby, dissolve it.

### Invite flow

**From GUI:** Owner types a player name in the lobby screen → server looks up the online player → adds UUID to `pendingInvites` → sends target a chat notification.

**From command:** `/arenasld lobby invite <playerName>` — works from anywhere on the server without opening the GUI.

**Receiving an invite:** The invited player gets a chat message:
```
[Arenas LD] Bob invited you to their Mythic dungeon lobby.
            [Accept] [Decline]
```
`[Accept]` and `[Decline]` are clickable components running `/arenasld lobby accept <lobbyId>` and `/arenasld lobby decline <lobbyId>` internally. The invite also appears as a banner the next time they open the controller screen.

### Controller screen — four UI states

**State 1 — Not in any lobby:**
```
[ Instance 1: Free ] [ Instance 2: Running ] [ Instance 3: Cooldown 3:24 ]

  Open Lobbies
  ┌────────────────────────────────────────────────────┐
  │ Steve's party    2/4   [ Normal ]  [ Open ]  [Join]│
  │ Alex's party     1/4   [ Hard   ]  [ Open ]  [Join]│
  │ Bob's party      3/4   [ Mythic ]  [  🔒  ]        │
  └────────────────────────────────────────────────────┘

  [ Create Lobby ]

  Leaderboard  [ Easy | Normal | Hard | Nightmare ]
```

Invite-only lobbies are visible but show a lock icon and no Join button. Players know they exist but cannot join uninvited.

**State 1b — Not in any lobby, pending invite:**

A banner appears above the lobby list for each pending invite:
```
  ⚑ Bob invited you to their Mythic lobby   [Accept] [Decline]
```

Multiple pending invites stack as separate banners.

**State 2 — In a lobby, not the owner:**
```
[ Instance 1: Free ] [ Instance 2: Running ] [ Instance 3: Cooldown 3:24 ]

  Bob's Lobby  [ Mythic ]  [ Hardcore ]  [ 🔒 Invite Only ]

  ┌─────────────────────────────────┐
  │ ★ Bob  (owner)                  │
  │   You                           │
  │   Alex                          │
  │   (waiting... 3/4)              │
  └─────────────────────────────────┘

  [ Leave Lobby ]

  Leaderboard  [ Easy | Normal | Hard | Nightmare ]
```

**State 3 — In a lobby, you are the owner:**
```
[ Instance 1: Free ] [ Instance 2: Running ] [ Instance 3: Cooldown 3:24 ]

  Your Lobby
  Visibility:  [ Open ] [ Invite Only ]
  Difficulty:  [ Easy ] [ Normal ] [ Hard ] [ Nightmare ]
  [ ] Hardcore — 1 life, 2x rewards

  Members                          Invite a player
  ┌──────────────────────┐         ┌──────────────────────┐
  │ ★ You  (owner)       │         │ Player name...  [Send]│
  │   Alex        [Kick] │         └──────────────────────┘
  │   (waiting... 2/4)   │         (invite field only shown
  └──────────────────────┘          in Invite Only mode)

  [ Start Dungeon ]   [ Disband Lobby ]

  Leaderboard  [ Easy | Normal | Hard | Nightmare ]
```

If owner clicks Start and all instances are busy, the button becomes `[ Join Queue ]` and shows queue position once queued.

**State 4 — Lobby is queued (owner view):**
```
  Your Lobby  [ Mythic ]  — Queued (#2)

  All instances are currently busy. You will be notified
  when a slot opens.

  ┌──────────────────────┐
  │ ★ You  (owner)       │
  │   Alex               │
  └──────────────────────┘

  [ Leave Queue ]

  Leaderboard  [ Easy | Normal | Hard | Nightmare ]
```

### New commands

```
/arenasld lobby invite <playerName>     — invite a player from anywhere
/arenasld lobby accept <lobbyId>        — accept a pending invite
/arenasld lobby decline <lobbyId>       — decline a pending invite
/arenasld lobby leave                   — leave current lobby
/arenasld lobby kick <playerName>       — owner only, kick a member
```

---

## Phase 4 — Phase Block Rework

**Goal:** Phase blocks watch specific spawners by relative position instead of matching Group ID strings. Survives copying without any re-linking.

**Independent — can be done alongside any other phase.**

### The problem with Group ID for phase blocks

Group ID currently does two things:
1. Phase block gating — opens when all spawners with matching string are cleared
2. Mob team assignment — spawned mobs join a scoreboard team named after the Group ID

After copying a dungeon, copied spawners and phase blocks share the same Group ID strings as the originals — phase blocks in copy A respond to spawners in copy B.

### The fix: separate these two responsibilities

**Group ID stays** for mob team assignment only. Two copies having mobs on the same team is harmless.

**Phase blocks switch to relative position references** for gating. Group ID removed from phase block logic entirely.

### New `PhaseBlockEntity` data

```java
// Before
private String groupId = "room1";

// After
private List<BlockPos> watchedSpawnerOffsets = new ArrayList<>();

private List<BlockPos> getAbsoluteSpawnerPositions() {
    return watchedSpawnerOffsets.stream()
        .map(offset -> this.worldPosition.offset(offset))
        .toList();
}
```

Phase block opens when **all** watched spawners have `!isBattleActive`. Since spawners no longer own a cooldown after Phase 2, cleared = battle not running.

### Linker — Phase Block Linking mode rework

- Right-click a phase block → select it (tooltip shows watched spawner count)
- Right-click a spawner → adds `spawnerPos - phaseBlockPos` to offset list
- Shift + right-click a spawner while phase block selected → removes it
- Right-click selected phase block again → lists all watched spawners by absolute position

### Why relative coordinates solve the copy problem

```
Phase block at (100, 64, 100) watches offset (+10, 0, +5)
→ resolves to spawner at (110, 64, 105)

Copy room 100 blocks east:
Phase block now at (200, 64, 100), same offset (+10, 0, +5)
→ resolves to spawner at (210, 64, 105) ✓
```

As long as the phase block and its watched spawners are copied together (always true when copying a whole room), links are correct with zero re-linking.

### NBT migration

Existing phase blocks with a `groupId`: log a warning on first load that manual re-linking is needed. `groupId` kept for one version for backwards compatibility, then removed.

---

## Summary — what changes where

| File | Phase | Change |
|---|---|---|
| `DifficultyTier.java` | 1 | New enum |
| `TierConfig.java` | 1 | New data class |
| `DungeonBossSpawnerBlockEntity` | 1, 2 | Tier configs, active tier, scaling; remove cooldown + chunk-forcing |
| `DungeonControllerBlockEntity` | 1, 2, 3 | Tier field; instance pool + per-slot cooldown; lobby list + maxPartySize |
| `DungeonBossManager` | 2 | Register by controller; watch controller not spawner; 1 forced chunk per dungeon type |
| `Lobby.java` | 3 | New class |
| `InstanceState.java` | 2 | New record |
| `DungeonInstanceRef.java` | 2 | New record |
| `LivingEntityMixin` | 1 | Player damage scaling via `@ModifyArg` |
| `ArenasLdConfig` | 1 | `dungeon_damage_source_filter` field |
| `DungeonBossSpawnerScreen` | 1 | Tier config tab |
| `DungeonControllerScreen` | 1, 3 | Full rework — 4 UI states, lobby list, invite UI, instance panel, per-tier leaderboard |
| `DungeonControllerScreenHandler` | 3 | Extended to sync full lobby list + instance states to client |
| `CommandRegistry` | 2, 3 | addinstance / removeinstance / listinstances; lobby invite / accept / decline / leave / kick |
| `PhaseBlockEntity` | 4 | Relative offset list replaces groupId gating |
| `LinkerItem` | 4 | Phase Block Linking mode rework |

## Recommended shipping order

1. **Phase 1** — self-contained, most visible player-facing change
2. **Phase 4** — independent, do alongside Phase 1 or after
3. **Phase 2** — largest single change, do before Phase 3
4. **Phase 3** — depends on Phase 2; lobby system + full UI rework
