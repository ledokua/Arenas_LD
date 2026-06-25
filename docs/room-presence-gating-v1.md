# Dungeon Rooms — Presence Gating (v1 design)

Status: **design / not yet implemented**
Decisions locked: **flood-fill containment (approach C)** + **linear progression** (today's
`currentRoomIndex` is kept). Spatial/branching progression is explicitly out of scope for v1.

Round-2 decisions (locked):
- **Room name field** on the room controller — drives the ARMED hint text *and* the DBS rooms list.
- **Seed = respawn point only** (the designer hides controllers/spawners in walls or outside the
  room, so they're not reliable seeds). A room without a respawn point can't be presence-gated.
- **`maxInteriorBlocks` lives in the global config**, not per-controller.
- **Boundary preview**: a per-room-controller toggle that renders the flood-filled interior as a
  client-side overlay while building.

---

## 1. Goal

Make dungeon rooms behave like *Soul Knight*:

1. A room's mobs spawn **only when every active player is inside the room**.
2. The instant that happens, **all entrances seal** (existing phase-block doors close).
3. On clear, the doors **open** and the run advances to the next room (unchanged).

The room volume must support **any shape the designer builds**, not just boxes. We get that by
**flood-filling the interior from the built geometry** instead of asking the designer to mark a box.

---

## 2. What exists today (baseline)

- `DungeonBossSpawnerBlockEntity.getRooms()` → ordered `List<BlockPos>` of room controllers.
- `DungeonRunLifecycle.tickRunning` walks them linearly via `run.currentRoomIndex()`:
  - if `!room.isActivated()` → `room.activate(world, tier)` spawns mobs **immediately**;
  - else refresh alive mobs; if `isCleared()` → `openDoor` → advance index.
- `RoomControllerBlockEntity` holds: `spawnerOffsets`, `doorOffsets` (phase blocks),
  `respawnOffset`, `aliveMobs`, and the booleans `activated` / `cleared`.
- Doors are phase blocks; `closeDoor`/`openDoor` flip their `SOLID` property. `reset()` closes them.
- **No spatial volume anywhere.** Progression is purely "current room's mobs all dead → next".

Authoring already provides everything flood-fill needs: a controller, the entrance **doors**, and
solid **walls**.

---

## 3. New room phase model

Today a room is effectively two-state (`activated`, `cleared`). v1 inserts a waiting phase:

```
IDLE ──becomes current──▶ ARMED ──all players inside──▶ ACTIVE ──mobs cleared──▶ CLEARED
        (compute interior,   (doors open,                (doors SEALED,            (doors open,
         open doors)          waiting for party)          mobs spawned)             advance)
```

No new persisted field is strictly required: `ARMED` = "this is the current room **and** `!activated`".
`ACTIVE` = `activated && !cleared`. The flood-filled interior is **transient** (recomputed on demand;
the dungeon geometry is static during a run, so the result is deterministic and safe to drop on
reload).

### Door timing change (important)
Currently doors start **closed** and open only on clear. v1 flips the current room's doors so the
party can walk in:
- On entering **ARMED**: `openDoor` (entrances passable).
- On **ACTIVE** (seal): `closeDoor`.
- On **CLEARED**: `openDoor` (unchanged).

Future rooms keep their doors **closed** by default — those closed doors double as walls that bound
the current room's flood-fill (and door positions are treated as virtual walls regardless, see §4).

---

## 4. Flood-fill: computing the interior

Computed once when a room enters **ARMED**, cached in a transient `Set<BlockPos> interior` on the room
controller (cleared by `reset()` / on advance).

**Seed**: the room's **respawn point** (`respawnPos`) — and only that. The controller and spawners are
routinely hidden inside walls or outside the room, so they are *not* used as seeds. If `respawnPos` is
unset (or sits in a solid block), the room cannot be gated → log + fallback (§6). This makes "set a
respawn point inside the room" a setup requirement for presence-gated rooms.

**Traversal**: 6-connected BFS over **passable** blocks
(`state.getCollisionShape(world, pos).isEmpty()` — air, open phase blocks, non-colliding décor).

**Frontier stops at**:
- any block with a non-empty collision shape (walls, floors, ceilings);
- any position in this room's `doorOffsets` — treated as a **virtual wall even while physically
  open**, so the fill never leaks out the doorways (this is what removes the open/closed
  chicken-and-egg);
- the **volume cap** `maxInteriorBlocks` (config, default ~20 000).

**Result**: the set of interior block positions — arbitrary shape, including vertical/multi-floor.

**Cost**: one bounded BFS per room activation, then O(1) hash lookups per presence check.

---

## 5. Presence gate (the ARMED tick)

In `tickRunning`, when the current room is `ARMED`:

```
if room.interior == null: room.computeInterior(world)   // cache; fallback on failure (§6)
participants = active participants of the run            // status == ACTIVE only
if participants.nonEmpty && participants.allMatch(p -> room.interior.contains(p.blockPosition())):
    room.closeDoor(world)        // SEAL
    room.activate(world, tier)   // existing spawn path; sets activated=true
    register spawned mobs with the manager
else:
    throttled actionbar hint to players still outside: "Enter <room name> to begin"
```

The hint uses the room controller's **name field** (§7a). If the name is blank it falls back to a
generic "Enter the room to begin". The hint targets the active players who are **not yet** inside
(so people already in aren't nagged).

- **Who counts as "active"**: only participants with status `ACTIVE`. `DOWNED` players are spectators
  already teleported to the room respawn (inside), so they don't block the gate; `DISCONNECTED`
  players don't block it either (they reconnect to the room respawn, inside the seal).
- **Nobody active** (whole party downed/disconnected): gate holds, no spawn, until someone is active
  inside again. Avoids spawning into an empty room.
- **Throttle**: evaluate every ~5 ticks (cheap, but no need per-tick).

Because we only seal once **all** active players are inside, no active player is ever outside the
seal at the moment it slams — the "locked-out player" edge case can't occur in the linear model.

---

## 6. Safety / fallbacks

- **Un-enclosed room** (fill hits `maxInteriorBlocks` before closing): abort the fill, log a warning
  naming the controller pos, and **fall back to today's behaviour** (instant activate on becoming
  current). The room still works; it just isn't presence-gated.
- **No valid seed**: same fallback.
- **Feature toggle**: a controller/DBS flag `presenceGatedRooms` (admin screen). Default **off** so
  existing dungeons keep instant-spawn; opt in per dungeon. Rationale for off-by-default: the feature
  flips door timing (§3) and *requires* a respawn point per room (§4); existing rooms may have neither,
  so opting in is a deliberate, per-dungeon step. Easy to flip the default to on later once it's
  proven on your own content. (This resolves round-1 open question #3.)
- **Server restart while ARMED**: interior recomputes on next tick; presence re-evaluated. No
  persisted state needed.

---

## 7. Config

| Key | Where | Default | Meaning |
|---|---|---|---|
| `presenceGatedRooms` | controller (admin) | off | Master toggle for the whole feature |
| `maxInteriorBlocks` | **global config** (`ArenasLdConfig`) | 20000 | Flood-fill safety cap |
| presence re-check interval | constant | 5 ticks | Throttle for the ARMED gate |

## 7a. Room name field

A new `String roomName` on `RoomControllerBlockEntity` (persisted in its `State`, synced to client).

- **ARMED hint** (§5) uses it: "Enter `<roomName>` to begin".
- **DBS rooms list** (the editor list of rooms on the boss spawner) shows it instead of / alongside
  raw coordinates, so a designer reads "Crypt Antechamber" rather than `[123, 64, -88]`.
- Blank is allowed → hint falls back to "the room"; the DBS list falls back to coordinates.
- Edited from the room controller screen (small text field, like the dungeon-name field on the
  controller admin screen).

---

## 7b. Boundary preview (build-time visualisation)

A per-room-controller boolean `showBoundaryPreview` (persisted + synced). When on, the client draws
the flood-filled interior as an overlay so the designer can *see* the room the gate will compute —
invaluable for confirming the shell is airtight and the doors bound it correctly.

Reuses the existing `client/SelectionOverlayRenderer` (it already hooks
`WorldRenderEvents.AFTER_TRANSLUCENT`, draws coloured box edges via `addBoxEdges`/`edge`, and already
reads `RoomControllerBlockEntity` door/spawner/respawn data).

- **Client-side fill**: the client runs the *same* BFS (§4) using the synced `respawnOffset` +
  `doorOffsets`, so no per-block network payload is needed.
- **Render the shell, not every block**: only draw a block whose interior membership differs from a
  6-neighbour (i.e. boundary blocks). For a big room that's a thin surface instead of a solid mass of
  thousands of boxes — cheap and readable. Tint distinct from the gold/green/cyan selection colours
  (e.g. translucent magenta).
- **Bounded & cached**: only fills for controllers within render range with the toggle on; cache the
  result, recompute when the controller's synced data changes or the player moved far. Reuse the
  `maxInteriorBlocks` cap.
- **Toggle UI**: a button on the room controller screen (and/or the linker/configurator HUD), beside
  the door/spawner controls.
- **Visibility**: render only for creative/builder players, and ideally only while holding the
  configurator or linker, so it never shows to players mid-run.

## 8. Touch points (implementation sketch)

- `RoomControllerBlockEntity`
  - transient `Set<BlockPos> interior`; `computeInterior(ServerLevel)` (BFS from `respawnPos`, cap,
    fallback flag); `boolean isSealReady(...)` or expose `interior` for the lifecycle to test; clear
    `interior` in `reset()`.
  - new persisted `String roomName` (§7a) and `boolean showBoundaryPreview` (§7b) in its `State`
    codec; both synced via `getUpdateTag`.
  - on ARMED entry the lifecycle calls `openDoor`; `activate` path unchanged apart from being
    preceded by `closeDoor`.
- `DungeonRunLifecycle.tickRunning`
  - split the `!isActivated()` branch into ARMED (compute interior + open doors + presence gate)
    vs the existing seal+spawn, per §5; emit the hint using `roomName`.
- `DungeonControllerBlockEntity` (+ admin screen / `State` codec)
  - persist `presenceGatedRooms`.
- `ArenasLdConfig`
  - add `maxInteriorBlocks` (default 20000).
- `dungeon/screen/RoomController*` (data/screen/handler)
  - room-name text field + boundary-preview toggle button; carry `roomName`/`showBoundaryPreview`
    in `RoomControllerData`.
- DBS rooms-list UI
  - show `roomName` (fallback to coords) per room.
- `client/SelectionOverlayRenderer`
  - client-side BFS + shell rendering when `showBoundaryPreview` is on (§7b).
- Lang: actionbar hint key, room-name field label, preview-toggle label (+ uk_ua).

---

## 9. Open questions

All four round-1 questions are now resolved:
1. ~~Hint UX~~ → **yes**, actionbar hint using the new room-name field (§5, §7a).
2. ~~Seed preference~~ → **respawn point only** (§4).
3. ~~Toggle default~~ → **off / opt-in** for v1 (§6).
4. ~~`maxInteriorBlocks`~~ → **global config option**, default 20000 (§7).

Remaining minor calls (safe to make defaults, flag if you disagree):
- Preview tint colour (proposed translucent magenta) and whether it's gated to "holding linker/
  configurator" vs "always while toggle on for creative players".
- Whether the DBS list **replaces** coords with the room name or shows **both**.

---

## 10. Explicitly deferred (post-v1)

- Spatial / branching progression (rooms arm by which one the party enters).
- Box-union authoring (approach B) as an alternative for deliberately un-enclosed rooms.
- Mirroring presence gating into raids/arenas.
