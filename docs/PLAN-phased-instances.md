# Phased instances (plan)

Status: **parked, 2026-10-19 (owner).** Phasing is not being built. Instances stay hand-made copies, because the
owner doesn't want structures generated or loaded per party. The approved follow-up is on `1.21.1`:
- **Step 1:** the load fixes in section 9: raid/arena cooldown sync, the Phase Block ticker, tickets instead of
  `setChunkForced` (including the clean-restart stranding check).
- **Step 3:** an admin `/arenasld instance clone` command. It pastes a copy of a linked instance once and registers it
  with the controller; the internal links are already offsets from the boss spawner.

Sections 5 and 6 (spells, pets, mounts) were never filled in; that research was stopped. The rest is kept as a record
in case phasing comes back, for example as a wave-arena pilot.
Branch: `phased-instances` in Arenas_LD (from `1.21.1` at 4.6.2). To roll back, switch back to `1.21.1` and rebuild. BusyLib
and Eden_LD get a branch of the same name before anything is changed there.

Owner's idea (2026-10-19): instead of building and linking a physical copy of a dungeon for every party that may play
at once, let several parties play **the same build at the same time**, each in its own **phase**, as Eden's tutorial
already does for tutorial players. The owner's reasons: the instances are the main problem, and load/performance.
Owner's hard requirements: Spell Engine spells must never affect another run's players or mobs; the same for players'
pets, mounts and horses; and pets and mounts must never be stranded when a run ends.

Words used here: a **run phase** is the new isolation layer (one per run). The existing **Phase Block** is the
dungeon door block. They are different things; the code calls the new layer `RunPhasing` to keep them apart.

## 1. The short answer

- **It can work for dungeons and wave arenas.** Every run gets an id; the run's players, mobs, drops and whatever
  they create carry that id as their phase; players only see, hear, hit and get hit by their own phase. Doors become
  per-run views of the same blocks.
- **Most raids can't be shared.** Three of the raid bosses found in the build schematics change the arena's blocks
  during the fight (Starlight Golem's energy blocks, the Decaying King's lava, the Wither's block breaking), and a
  block is the same for everyone. Those instances must stay one-party (capacity 1), which is exactly today's
  behavior.
- **It is a big change.** It needs a shared phase core (best placed in BusyLib, used by Eden's tutorial too), run ids,
  per-run room state, per-run doors, and about twenty cross-phase filters (targeting, projectiles, explosions,
  effects, pickups, spells, pets, mounts, voice chat). Estimated at several weeks of work plus a two-client test pass.
- **The load gain is real but specific.** One shared build instead of K copies saves roughly **(K − 1) × 729 loaded
  chunks** while K parties play at once (view distance 10), plus K − 1 copies of block entities, region files and the
  cold-load of each copy. Mob AI and combat cost the same: the mobs still exist, only in one place.
- **Several load fixes don't need phasing at all** and could ship first on `1.21.1` (section 9), including a raid/arena
  bug that resends the whole controller to nearby players every tick during cooldowns.
- **Capacity 1 stays exactly as today.** Phasing is a per-instance setting. An instance with capacity 1 keeps world
  doors and no phases, so any instance can be switched back without a rebuild.

## 2. What players see

- A dungeon or arena instance has a **capacity** (admin setting, default 1). With capacity 3, up to three parties play
  it at once.
- Each party sees only its own mobs, party members, doors, drops and effects; the other parties are not there for
  them. Other parties' spells, arrows and explosions pass through them and do nothing.
- Doors open and close per party: party A's sealed room is open for party B, who may be two rooms behind.
- The queue only forms when every slot of every instance is taken; the wait estimate counts free slots.
- Lobby screens show an instance as "2/3 running" instead of "running".
- Pets and mounts: see section 6 (owner decisions).

## 3. How it fits together

```
           BusyLib (shared, both loaders)                    Arenas_LD
  ┌─────────────────────────────────────────┐   ┌──────────────────────────────────────┐
  │ phase core: entity phase + NBT,         │   │ run id per run; runs keyed by id     │
  │ PhaseContext, visibility, ~20 filters,  │◄──┤ RunPhaseOwner: "run alive?"          │
  │ per-phase block overlay, liveness,      │   │ per-run room state, per-run doors    │
  │ Spell Engine + SVC hooks                │   │ capacity, slots, queue, tickets      │
  └─────────────────────────────────────────┘   └──────────────────────────────────────┘
                     ▲
                     │ TutorialPhaseOwner (phase = player id, private containers stay in Eden)
               Eden_LD tutorial
```

### 3a. Why the core goes in BusyLib

- Arenas must not depend on Eden_LD, and Eden only reads Arenas compile-only, so neither can host it for the other.
- Both already hard-depend on BusyLib (so do Duels_LD, Economy_LD and NPCs_LD), and BusyLib is multiloader.
- A second copy inside Arenas would double the hottest hooks (every outgoing packet, every entity-tracking check,
  every entity tick, every push), and every gap below would have to be fixed twice; the tutorial would miss Arenas'
  fixes.
- What it costs: BusyLib has no mixin setup today and is built with Architectury Loom at `1.0-SNAPSHOT`. It should
  first move to the house layout (fabric-loom common + fabric, ModDevGradle neoforge, like NPCs_LD) and get a real
  version (1.1.0), so mods can require `>=1.1.0`.
- With no phase registered the core does nothing, so it is safe next to Duels, Economy and NPCs.

### 3b. What moves from Eden and what stays

Moves to the core (generic): the entity phase field and its save/load, `PhaseContext`, `Phases.of/together/sees`, the
entity index, the tracking filter, the push filter, the hurt filter, the target filter, the hopper filter, the
sound/particle packet filter, phase inheritance in `addFreshEntity`, ticking in phase, the Spell Engine wrap, and the
per-phase block-state map from `PrivateBlocks` (as `PhaseBlocks`).

Stays in Eden (tutorial rules): phase = player id and its "player still here" check, the `always_visible` list,
private containers (`PrivateBlocks` copies, menus, scopes), returning thrown items to the inventory, and no loot/XP
from tutorial mobs. These become a `TutorialPhaseOwner` with rules.

Two Eden bugs the core fixes on the way: items a tutorial player drops with `drop(stack, false)` come out unphased
(the item is added after Eden's wrap has already left the phase), and `PrivateBlocks.open` is a plain int changed
from network threads.

### 3c. Gaps the core must close before parties can share a build

Eden's system is built for one player per phase with almost no mobs. Sharing a room between parties hits paths Eden
never needed (all verified in vanilla 1.21.1 or the installed jars):

| Channel | Problem today | Core fix |
|---|---|---|
| Mob targeting | Only `Mob.setTarget` is cancelled. `NearestAttackableTargetGoal` picks the nearest player of any phase, the set is cancelled, and the mob never targets its own party while another party's player is nearer. Brain mobs and the Wither set targets without `setTarget`. | Phase check in `TargetingConditions.test` (one class, no subclasses: covers goals, brain sensors, look goals) and `LivingEntity.canAttack`; keep `setTarget` as a backstop, wrapped rather than HEAD (Eternal Starlight, Create and puzzleslib modify its argument). |
| Projectiles | Arrows hit other phases' mobs, the damage is refused, and the arrow stops. | Phase check in `Projectile.canHitEntity` (Arenas already injects there). |
| Explosions | Knockback is applied even when damage is refused; the explode packet (with knockback) goes to everyone within 64 blocks. | Wrap the `ignoreExplosion` call site in `Explosion.explode` (Warden and ArmorStand override the method); filter `ClientboundExplodePacket` per receiver. |
| Effects | `addEffect` has no filter; potion clouds and splashes reach other phases; instant effects skip `addEffect`. | Filter the victim lists of `AreaEffectCloud` and `ThrownPotion`; phase check in `addEffect(effect, source)`. |
| Knockback | `knockback()` has no source; the sweep attack knocks back before it hurts. | Filter at the callers (sweep, `Mob.doHurtTarget`, shield) using the acting phase. |
| Lightning, cramming | Hurt without an attacker passes an attacker-based filter. | Fall back to the acting phase or the direct entity. |
| Pickups | `Player.touch` picks up other phases' items, XP orbs and arrows (hiding an entity does not stop it). | Phase check in `Player.touch`; orbs at `scanForEntities`. |
| Merging | Items and XP orbs of different phases merge (the stack moves to the other phase); orbs fly to the nearest player of any phase. | `ItemEntity.tryToMerge`, `ExperienceOrb.canMerge` / `tryMergeToExisting`, orb target. |
| Pushing | Cancelled pushes still count; more than 24 overlapping entities from all phases cause cramming damage. | Filter the push candidate list. |
| Area scans | Boss and spell area attacks use `level.getEntities(...)` and then push, slow or heal without `hurt`. | While a phase is acting, `Level.getEntities` and `getNearestPlayer` return only that phase's entities (one hook, covers most mod area attacks). |
| Delayed work | Spell Engine's `WorldScheduler` (delayed impacts, projectile sequences, clouds) and BOMD's scheduler run outside any phase. | Capture the acting phase when work is scheduled; restore it when it runs. |
| Packets | Eden filters sound, particle and level-event packets only. Eternal Starlight sends boss VFX and boss bars to every player in the level; mrpg-lib beams go to all players. | Add `ClientboundExplodePacket` and per-mod payload hooks; boss bars that add players by distance (Draugr) via `ServerBossEvent.addPlayer`. |
| Tracking refresh | A phase change on a standing player isn't applied until they move. | `refreshTracking(player)` through a `ChunkMap` accessor. |
| Respawn | `ServerPlayer.restoreFrom` doesn't copy the phase. | Copy it at `restoreFrom` TAIL. |
| Liveness | Eden discards a phased mob whose player is gone. A run phase is alive while the run exists, also right after a restart, when mobs load before their controller. | Owners answer ALIVE / DEAD / UNKNOWN; UNKNOWN freezes, never discards. Arenas' phases are persistent (saved list, loaded at boot). |
| Voice chat | Simple Voice Chat 2.6.24 (server and client) routes proximity voice on its own thread, outside every Minecraft packet. Parties in one build hear each other. | A voice chat plugin (API is compile-only) that cancels sound packets between different phases. |
| Map markers | Xaero's server-side party tracking shows a party/ally member at their real position. | `@Pseudo` hook on its tracked-player sender. |

The full method list, checked against both loaders, is in the research notes (section 13).

### 3d. Liveness and saved data

- An entity saves `BusyLibPhase` (UUID) and `BusyLibPhaseOwner` (for example `arenas_ld:run`).
- Eden registers the old key `EdenPhase` as an alias, so players who are in the tutorial during the switch keep their
  phase (the tutorial is live since Eden 1.1.2).
- On every tick of a phased mob: phase alive → it ticks in its phase; owner says dead → it is removed (owned entities
  excepted, section 6); owner unknown → it waits and stays hidden.

## 4. Arenas changes

All of these are on the branch; capacity-1 instances keep today's behavior.

1. **Run ids.** `DungeonRun` gets a `runId` (generated for old saves); `RaidRun` and `ArenaRun` reuse their persisted
   `lobbyId`. Every controller keys runs by id (`Map<UUID, Run>` plus an instance → runs index) instead of
   `Map<BlockPos, Run>`. Instance keys gain the dimension (today a position in another dimension collides).
2. **Room state moves into the run.** The run fields of `RoomControllerBlockEntity` (alive mobs, boss mobs, wave
   indices, activated, cleared, survive timer, protect target) move to a `RoomRunState` per room in `DungeonRun`. The
   block entity keeps only the builder's config. No client code reads those fields, so the client is unaffected. A run
   in progress at the upgrade is ended and its players are sent home (no migration), so deploy at a restart with no
   runs.
3. **Per-run doors.**
   - Each phased run keeps a `Map<pos, door state>` derived from its own progress (cleared rooms, the locked room); it
     needs no saving and is rebuilt after a restart.
   - `PhaseBlock` gets `dynamicShape()` and answers collision per entity: an entity in a run phase gets that run's
     door state, anything else gets the world state.
   - The world copy of the doors stays closed; runs never write it. Suffocation and other context-free checks see
     "no wall", so nobody suffocates in a door.
   - Each player's client gets fake block updates for their run's doors: on every change, right after each chunk is
     sent to them (`PlayerChunkSender.sendChunk` TAIL, both loaders), on join and leave; outgoing block updates for
     door positions are rewritten per player (single and section packets). The client then collides with what it
     sees, and the server checks the same shape, so there is no rubber-banding.
   - Known limit: ground pathfinding is computed without the mob and cached per position, so a mob plans paths with
     the world's closed door. Mobs never need to walk through doors (rooms lock while their mobs are alive), so this is
     acceptable.
4. **Phase lifecycle of a player.** Set before the teleport into the run (start, and re-asserted on reconnect); cleared
   on every exit (win, loss, `/exit`, hardcore death-out, return at login, orphan cleanup). An offline player's phase
   can't be edited, so a login check clears a phase whose run is gone or that no longer lists them.
5. **Run ticks and spawns in the phase.** The three run ticks (and `startRun`, `startBattle`, `beginRun`, the death and
   connection hooks) run inside the run's phase, so sounds, particles and anything created there take the phase.
   Every spawn site (wave mobs, bosses, protect target, raid boss, arena mobs, arena loot) sets the phase before
   adding the entity.
6. **Capacity, slots and the queue.** Instances get `capacity` (default 1). A run claims a free slot; cooldowns become
   per slot (see decision 4); a pending removal runs after the last run; the queue offers the front K lobbies when K
   slots free; the arena auto-launches while slots are free. Admin screens and lobby pills show "running x/N".
7. **Party teams by run.** Team names come from the run id, not the instance position (today the first run to end
   deletes the shared team of the others).
8. **Chunk loading by tickets.** Replace `setChunkForced` with region tickets keyed by run id (counted per run,
   released only by that run, not saved, rebuilt at boot from the saved runs). Raids and arenas also hold their battle
   area during a run (today it isn't held, so the boss lookup can fail with nobody near and the raid ends as a loss).
9. **Run leftovers.**
   - The arena's wave-start sweep deletes only the run's own items, not everything in the radius.
   - Arena communal loot becomes the run's loot (only its party sees and picks it up).
   - `RewardDelivery`'s full-inventory drop stays with the player and is never stranded in a phase.
   - Reward commands run inside the run's phase, and selectors (`@a[distance=..]`, `@p`, `@e`) see only that phase.
10. **API.** `RunResult` gains `runId`, the instance, and the forced flag Eden asked for. Status reports capacity and
    running count per instance and a run id per run. `forceEnd` takes a run id or an instance (section 10).

## 5. Spells

_Filled in from the Spell Engine research (running)._

## 6. Pets, mounts and horses

_Filled in from the pets, run-end and mounts research (running)._

## 7. What can't be phased (rules for builders)

- **Blocks are shared.** Vanilla doors, trapdoors, buttons, levers, pressure plates, redstone and anything a mob
  places or breaks are the same for every party. Shared instances must use Phase Blocks for doors and no redstone
  puzzles. Only Phase Blocks have per-run state.
- **Bosses that change blocks** need capacity 1: Starlight Golem (energy blocks, falling blocks), the Decaying King
  (lava), the Wither (breaks blocks with mobGriefing), BOMD bosses (pillars, blossoms, explosions), and the other
  block-changing bosses in the pack (Mine Cells Conjunctivius, Aquamirae Maze Mother, Mythika, Divine Encounters,
  Sculk & Scavenge). Eternal Starlight bosses also drop loot chests as blocks.
- **Local chat** (Eden, 100-block radius) reaches the other parties in the build; Eden has to ask the core.
- **Spectators**: a downed player is in Spectator mode and still sees only their own phase (tracking follows the
  phase, not the game mode).
- **Staff** without a phase see the empty build; a `/arenasld spectate <run>` command can put staff into a run's phase
  view-only (later).

## 8. Load: today and with phasing

Measured on the staging server config (Prism Server `server.properties`: view distance 10, simulation distance 5;
the live file is not available locally).

| What | Cost |
|---|---|
| A party standing in its copy | about 729 chunks read, 625 full, 169 block-ticking, 121 entity-ticking |
| Each force-loaded chunk (controllers, dungeon runs) | 1 entity-ticking, 9 block-ticking, 25 full |
| Every Phase Block | a block entity with a ticker on server and client that returns at once (the raid-watch code it serves has no callers) |
| Raid/arena controller in cooldown | the whole controller (lobbies, runs, leaderboards, tier settings) resent to nearby players every tick, up to 5 minutes per instance |

With phasing, K parties in one build cost about one copy's area plus how far apart they spread, instead of K areas:
about (K − 1) × 729 chunks fewer when copies are far apart. Disk, block entities and cold loads drop the same way.
What phasing adds: a fast check on every outgoing packet (already paid by Eden's tutorial today), per-entity checks in
tracking, targeting and pushing, and a lookup per door collision. Shared rooms are denser: K parties' mobs in one room
do more push and scan work than in K rooms, which is a reason to keep capacities small (2–4).

## 9. Fixes that don't need phasing (could ship first on `1.21.1`)

Ranked by value for effort:

1. **Raid/arena cooldown sync.** Store the cooldown's end time and sync only on status changes, not every tick.
2. **Phase Block ticker off.** `getTicker` returns null (no save change); later drop the block entity.
3. **Tickets instead of `setChunkForced`**, keyed per run and per controller. Also fixes:
   - **Clean restarts release controller chunks.** At a clean stop, `setRemoved` unforces the controller's chunk and
     that is saved, so after the restart controllers load only when a player comes near. Runs saved in an unloaded
     controller don't restore, and a player who reconnects inside a dungeon can be stranded. This follows from the
     vanilla code but has not been seen in game; check `/forceload query` after a clean restart.
   - The first run or controller to release a shared chunk releases it for everyone, including `/forceload`.
   - Run chunks leak when a controller or boss spawner is broken mid-run, or when rooms are edited mid-run.
4. **Hold raid and arena battle areas during runs** (above).
5. **Hold idle controllers only while busy** (lobbies, queue, runs, cooldowns, viewers). This only saves something if
   controllers sit outside always-loaded areas.
6. **Bounded door flood fill** (`propagateState` is an unbounded recursive search).
7. **Slim client update tags.** Every controller and spawner sends its full saved data to nearby clients (and exposes
   lobby and run data to them).

## 10. Bugs found on the way (today's code)

| Bug | Where | Proposed fix |
|---|---|---|
| Raid pending instance removals never run; a flagged instance is handed out again after its cooldown. | `RaidControllerBlockEntity.onRaidEnded`, `reserveFreeInstance` | Run the removal at run end; skip flagged instances. |
| Raid instance cooldown uses RESPAWN TIME, not COOLDOWN (documented in the README as a quirk). | `RaidControllerBlockEntity.releaseInstance` | Use COOLDOWN (the owner decides, since it changes live timings). |
| A hardcore-ejected raid player keeps the raid damage multiplier and lethal-hit interception while the raid runs. | `RaidBossSpawnerBlockEntity.isTracked` | Skip REMOVED participants; a raid player → run map. |
| After a restart raid and arena players are not busy (BusyLib is in memory). | raid/arena reconnect | Set busy on reconnect, as dungeons do. |
| The dungeon restart bootstrap only runs when the first participant is unregistered; with a forfeited owner it runs every tick. | `DungeonRunLifecycle.tickRunning` | An explicit `bootstrapped` flag. |
| The dungeon party name is blank after a restart. | `DungeonRun` load | Save `ownerName`. |
| The lobby is deleted before the run start can still fail. | `DungeonControllerBlockEntity.startRun` | Delete after success. |
| `@s` replacement breaks `execute as … run … @s` in reward commands. | `executeRewardCommands` | Only replace the placeholder token. |

## 11. Releases

| Step | What | Where |
|---|---|---|
| 0 | Arenas 4.7.0: the parts of Eden's API request that don't depend on phasing (location, teleport, rename, remote admin screen, forget), the three admin leak fixes, and the fixes from sections 9 and 10 the owner picks. | `1.21.1` |
| 1 | BusyLib 1.1.0: house layout, the phase core (inactive until a mod registers an owner). | BusyLib `phased-instances` |
| 2 | Eden 1.4.0: the tutorial on the core (`EdenPhase` alias). Test a player mid-tutorial across the upgrade. | Eden_LD (its own session) |
| 3 | Arenas 5.0.0-beta: run ids, runs keyed by id, room state in the run, tickets, teams by run. Every instance still capacity 1: no visible change. | Arenas `phased-instances` |
| 4 | Arenas 5.0.0: capacity, run phases, per-run doors, filters, spells, pets and mounts, `status`/`forceEnd` on run ids. | Arenas `phased-instances` |
| 5 | Dev test with two clients and two parties in one build (section 13 checks), then the owner deploys server and pack together. | dev server |

Steps 1–4 can stop at any point: until an instance's capacity is raised, nothing changes for players.

## 12. Decisions for the owner

1. **Go ahead with phasing at all** (on this branch), knowing raids with block-changing bosses stay one-party?
2. **Where the core lives:** BusyLib, ported to the house layout first (recommended), or a copy inside Arenas.
3. **Ship step 0 first** (4.7.0 on `1.21.1` with the non-phasing API parts and fixes)? Recommended.
4. **Cooldown in shared instances.** Today the instance cooldown is the only limit on completion loot (owner,
   2026-10-10). In a shared instance it can't block the instance for everyone. Options: (a) per slot, as today
   (recommended: the farm rate per instance stays the same); (b) per player per activity; (c) none.
5. **Default capacity** for new instances: 1 (recommended), admins raise it per instance.
6. **Pets and mounts:** section 6.
7. **Voice chat:** block proximity voice between parties (plugin, recommended) or accept it.
8. **Raid cooldown quirk:** switch the raid cooldown to the COOLDOWN setting?

## 13. Checks and research notes

Two-client dev checks (port 25598, own Xvfb, mob spawning off):
- Two parties in one dungeon: each sees only its own mobs and doors; doors seal per party; no rubber-banding at doors.
- A spell, arrow, explosion, potion cloud and sweep from party A do nothing to party B's players and mobs.
- Party B's mobs target party B even with party A standing nearer.
- Items and XP from A are invisible to B and can't be picked up by B.
- Restart mid-run: mobs come back in their phase, doors match each run's progress, players reconnect into their run.
- Capacity 1 instance behaves exactly as on 4.6.2.
- Pets and mounts: section 6.

Research notes (scratchpad, not committed): vanilla mechanics, the core split, the Arenas change list, the pack survey,
load, spells, pets, run-end returns, mounts.
