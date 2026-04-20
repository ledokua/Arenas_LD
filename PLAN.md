# Arenas LD — Current State & Next Steps

## What's already done

All four original phases are implemented and compiling. Summary of what exists:

- **Phase 1 — Difficulty tiers**: `DifficultyTier` enum (Easy/Normal/Hard/Nightmare), `TierConfig` per-spawner overrides, tier-based health/damage/loot scaling, per-tier leaderboards, damage scaling mixin (`@ModifyArg` on `LivingEntity.actuallyHurt()`), `dungeon_damage_source_filter` config option.
- **Phase 2 — Instance pool + controller-owned cooldown**: `DungeonInstanceRef`, `InstanceState` (FREE/RUNNING/COOLDOWN), controller owns cooldown per slot, spawner fully resets on run end, `addinstance`/`removeinstance`/`listinstances` commands, `DungeonBossManager` watches controller not spawner.
- **Phase 3 — Lobby system**: `Lobby` class with owner/members/invites/visibility/tier/hardcore/status, invite send/accept/decline via GUI and commands, ownership transfer, stale offline cleanup, queue flow with notifications, controller UI reworked to 256×256 with four UI states (BROWSING/MEMBER/OWNER/QUEUED/IN_DUNGEON).
- **Phase 4 — Phase block rework**: `PhaseBlockEntity` uses relative spawner offsets instead of Group IDs, Linker phase-block linking mode reworked, legacy GroupId migration warning on load, `PhaseBlockManager` removed.

---

## Known bugs to fix now

### Bug 1 — HIGH: `reopenLobbiesAfterDungeonRun()` reopens all lobbies

When any run ends, it reopens every lobby with `IN_DUNGEON` status, not just the one that used that instance. With multiple simultaneous runs, ending one run incorrectly reopens parties from other running instances.

**Decision: disband the lobby entirely when a run ends (win or loss).** Players re-form a new lobby to play again. The dungeon cleared/failed message is enough context.

**Fix:**

1. Add `Map<UUID, DungeonInstanceRef> lobbyInstanceMap` to `DungeonControllerBlockEntity` (keyed by lobby ID, saved to NBT).
2. When a run starts successfully: `lobbyInstanceMap.put(lobby.id, instanceRef)`.
3. In `onRunEnded(DungeonInstanceRef ref, boolean wasWin)`: find the lobby whose instance matches `ref` via `lobbyInstanceMap`, silently disband it (remove from `lobbies`, clean up `offlineSinceTick` entries), remove the map entry.
4. Delete `reopenLobbiesAfterDungeonRun()` entirely.

**NBT for `lobbyInstanceMap`:** Save as a `ListTag` of `{LobbyId, SpawnerPos, Dimension}` compound tags. On load, discard entries whose lobby ID no longer exists in `lobbies`.

### Bug 2 — HIGH: No cooldown on dungeon failure

Currently `onRunEnded()` always applies `respawnTimeTicks` cooldown regardless of win/loss.

**Decision: cooldown only on win. Loss → instance goes FREE immediately.**

**Fix in `onRunEnded()`:**
```java
int cooldownTicks = wasWin ? Math.max(0, respawnTimeTicks) : 0;
InstanceStatus nextStatus = cooldownTicks > 0 ? InstanceStatus.COOLDOWN : InstanceStatus.FREE;
```

Note: players in a dungeon are marked busy by BusyLib, so they cannot queue or interact with the controller while a run is active — the edge case of a lobby being QUEUED while IN_DUNGEON cannot occur.

### Bug 3 — HIGH: `ScheduledExecutorService` sends packets off the main thread

`DungeonControllerScreen` spawns a background thread that calls `ClientPlayNetworking.send()` every second. This is not thread-safe in Minecraft.

**Fix:** Replace with the screen's `tick()` method:
```java
private int refreshTickCounter = 0;

@Override
public void tick() {
    if (++refreshTickCounter >= 20) {
        refreshTickCounter = 0;
        updateInfo();
    }
}
```
Remove: `scheduler` field, `scheduler.scheduleAtFixedRate(...)` call, `scheduler.shutdown()` in `removed()`.

### Bug 4 — HIGH: `pruneMissingInstances()` removes unloaded chunks

`spawnerLevel.getBlockEntity(pos)` returns `null` for both unloaded chunks and missing blocks. Valid instances whose spawner chunk is simply unloaded get silently pruned from the pool every 100 ticks.

**Fix:** Add a loaded check before the block entity lookup:
```java
if (!spawnerLevel.isLoaded(instance.ref().spawnerPos())) continue;
```

### Bug 5 — MEDIUM: `buildRightPanelFromLobbies()` mutates `joinButton.active` as side effect

When the lobby list is empty, this list-building method directly sets `joinButton.active = false`, bypassing `updateActionButtons()`.

**Fix:** Remove the `joinButton.active = false` line from the list builder. Move all button state decisions into `updateActionButtons()` exclusively.

### Bug 6 — MEDIUM: `Lobby` fields are all public and mutable

All fields on `Lobby` are public and written to directly throughout the controller (`lobby.status = LobbyStatus.IN_DUNGEON`, etc.), with no `setChanged()` calls. This is the same encapsulation problem fixed on the spawners.

**Fix:** Make fields private, add setters that call the controller's `markDirtyAndSync()`, or at minimum document that all mutations must be followed by `markDirtyAndSync()` on the controller.

---

## Controller UI rework

The current UI has several usability problems that need a full redesign pass.

### Problems

- **Lobby list rows are unreadable.** Current format: `"Steve* 2/4 N L Q#2 [LOCK]"` — truncated name, size, tier abbreviation (E/N/H/NM), visibility letter (O/L), status code, action tag. Too compressed for in-game use.
- **Tier is a single cycle-through button.** No way to jump directly to a tier. Replace with 4 small side-by-side buttons (Easy / Normal / Hard / Nightmare), active tier highlighted.
- **Invite accept/decline targets are `[A]`/`[D]` text — ~10×7px, nearly impossible to click.** Replace with proper labeled buttons: `Accept` / `Decline`.
- **Admin panel replaces the entire screen.** Navigating to admin settings removes all dungeon/lobby info. Should be an inline sub-section or overlay, not a full replacement.
- **Tier and hardcore shown during BROWSING, misleadingly.** These controls appear to configure the player's upcoming lobby but actually set controller-wide defaults. Either hide them during BROWSING or label them clearly as "Default for new lobbies".
- **Instance panel is tiny unstyled text.** Instances need distinct visual states: Free (green), Running (yellow/amber), Cooldown with countdown (red). Row-per-instance with color indicators.
- **No leaderboard tier tabs.** Per-tier leaderboard data exists on the server but the screen shows a flat list. Four tab buttons (Easy / Normal / Hard / Nightmare) needed above the leaderboard.
- **Layout uses dynamic Y stacking.** Subtitle lines and invite banners push other elements down by computed amounts, causing layout shifts. Use fixed layout zones.

### New layout — four states

**State 1 — BROWSING (not in any lobby)**
```
┌─────────────────────────────────────────────────────────┐
│  Dungeon Name                                           │
│                                                         │
│  Instances                                              │
│  [ #1 FREE ] [ #2 RUNNING ] [ #3 CD 03:24 ]            │
│                                                         │
│  ── Pending invites (if any) ──────────────────────     │
│  Bob invited you  (Nightmare)  [Accept] [Decline]       │
│                                                         │
│  ── Open Lobbies ──────────────────────────────────     │
│  Steve's party   2/4   Normal   Open    [Join]          │
│  Alex's party    1/4   Hard     Open    [Join]          │
│  Bob's party     3/4   Nightmare  Invite-only  [Locked] │
│                                                         │
│  [Create Lobby]                                         │
│                                                         │
│  ── Leaderboard ───────────────────────────────────     │
│  [ Easy ] [ Normal ] [ Hard ] [ Nightmare ]             │
│  1. Steve — 04:12                                       │
│  2. Alex  — 05:33                                       │
└─────────────────────────────────────────────────────────┘
```

**State 2 — MEMBER (in lobby, not owner)**
```
│  Bob's Lobby   Nightmare   Hardcore   Invite-only       │
│                                                         │
│  ★ Bob (owner)                                          │
│    You                                                  │
│    Alex                                                 │
│    (3/4 — waiting for owner to start)                   │
│                                                         │
│  [Leave Lobby]                                          │
```

**State 3 — OWNER**
```
│  Your Lobby                                             │
│  Visibility:   [Open]  [Invite Only]                    │
│  Difficulty:   [Easy]  [Normal]  [Hard]  [Nightmare]    │
│  [ ] Hardcore                                           │
│                                                         │
│  ★ You (owner)                                          │
│    Alex                               [Kick]            │
│    (2/4)                                                │
│                                                         │
│  Invite:  [_____________] [Send]  ← only in invite-only │
│                                                         │
│  [Start Dungeon]   [Disband]                            │
│                                                         │
│  Note: if no free instance, Start becomes [Join Queue]  │
```

**State 4 — QUEUED**
```
│  Your Lobby  Nightmare  — Queued #2                     │
│  You'll be notified when a slot opens.                  │
│  Next slot in: 03:24                                    │
│                                                         │
│  ★ You (owner)                                          │
│    Alex                                                 │
│                                                         │
│  [Leave Queue]                                          │
```

**Admin sub-section (inline, always at bottom for ops)**
```
│  ── Admin ──────────────────────────────────────────    │
│  Cooldown: 05:00   [−30s] [Draft: 04:30] [+30s] [Apply]│
```
No full-screen takeover — admin controls are always visible at the bottom for ops regardless of state.

### Instance status pills

Replace raw text with per-instance status rows:
- `FREE` → green pill
- `RUNNING` → amber pill  
- `COOLDOWN 03:24` → red pill with countdown

### Lobby list rows

Each lobby row should be a clean readable line:
```
Steve's party   2 / 4   [ Normal ]   Open      [Join]
Bob's party     3 / 4   [ Nightmare ] Invite    [Locked]
```
Full owner name (truncated to 12 chars), size fraction, tier pill, visibility word, action button. No abbreviations.

---

## Summary — files that still need changes

| File | Work remaining |
|---|---|
| `DungeonControllerBlockEntity` | Add `lobbyInstanceMap`; fix `onRunEnded()` (disband lobby, no cooldown on loss); fix `pruneMissingInstances()` |
| `ModPackets` (start dungeon handler) | Write to `lobbyInstanceMap` when run starts |
| `DungeonControllerScreen` | Replace `ScheduledExecutorService` with `tick()`; full UI rework per layout above |
| `Lobby.java` | Encapsulate public fields |
| `DungeonControllerScreen` | Fix `buildRightPanelFromLobbies()` side effect on `joinButton` |

## Recommended order

1. Fix bugs 1–4 first (controller logic correctness)
2. Fix bugs 5–6 (code quality)
3. UI rework last — cleanest to do after logic is stable
