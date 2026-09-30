# Arenas_LD

Instanced PvE content built out of your own builds, for Minecraft **1.21.1** (Fabric and NeoForge).
Place a handful of blocks in a structure you already made, wire them together with one item, and it
becomes a dungeon, an endless arena or a raid boss that parties queue for, run, and get returned
from. Built on VectorLib for the UI.

> Status: in development. Every building block and every admin screen is gated behind creative mode
> or operator level 2 — see [Blocks and the Dungeon Tool](#blocks-and-the-dungeon-tool) for what
> each gate actually accepts.

## Contents

[Getting started](#getting-started) · [If the run won't start](#if-the-run-wont-start) ·
[Blocks and the Dungeon Tool](#blocks-and-the-dungeon-tool) ·
[Dungeons](#dungeons) · [Doors are direction-free](#doors-are-direction-free) ·
[Room objectives](#room-objectives) · [Difficulty tiers](#difficulty-tiers) ·
[Arenas](#arenas) · [The wave loop](#the-wave-loop) · [Scaling](#scaling) ·
[Raids](#raids) · [Tier scaling](#tier-scaling) ·
[Lobbies, parties and instances](#lobbies-parties-and-instances) · [Rewards and loot](#rewards-and-loot) ·
[LuckPerms reward perks](#luckperms-reward-perks) ·
[Commands](#commands) · [Config](#config) · [File locations](#file-locations) ·
[Compatibility](#compatibility) · [License](#license)

## Getting started

The shortest real path to a dungeon a party can play. Everything below is in the **Arenas LD**
creative tab; nothing has a recipe.

1. **Install** Arenas_LD for your loader on the server and on clients — every editor and lobby screen
   is client-rendered — plus **BusyLib**. See [Compatibility](#compatibility).
2. **Build two rooms** with a doorway between them, and one more doorway as the way in. Fill each
   doorway with **Phase Blocks** — they start as solid red barriers.
3. **Place the blocks**: one **Room Controller** in each room, a **Mob Spawner** or two per room, one
   **Dungeon Boss Spawner** for the whole dungeon, and a **Dungeon Controller** wherever players
   should queue (spawn is fine — it may be in a different dimension).
4. **Link it.** Hold the **Dungeon Tool**, right-click in air, pick *Link structures*. Then click a
   source block and a target block:
   - Dungeon Boss Spawner → each Room Controller (adds the rooms)
   - each Room Controller → its Mob Spawners (adds the room's enemies)
   - Dungeon Controller → Dungeon Boss Spawner (registers that dungeon as an **instance** — one
     playable copy; without at least one the controller has nothing to start, and linking several
     copies of the build lets parties run in parallel)
5. **Doors** (*Door* mode). Click room A, then any Phase Block of the doorway between A and B; then
   click room B and the **same** doorway. Two rooms linked to one door is what connects them. The way
   in is linked from room A **only** — a door no second room shares is a boundary door, and the Start
   room needs one.
6. **Positions.**
   - *Entrance position* (**required**): select the Dungeon Boss Spawner, click the block the party
     should land on. Put it **outside** the Start room, in front of its boundary door, facing in — a
     room only activates when someone stands in a Phase Block of one of its doors, and at run start
     the Start room's boundary door is the only one that is open. Nothing validates the entrance: if
     you never set one the party is teleported to the Dungeon Boss Spawner's own coordinates **in the
     Overworld**, whatever dimension you built in. Raid Boss Spawners default the same way.
   - *Respawn position* (**at least one per room**): select each Room Controller, click one or more
     floor blocks. These are where the party arrives when the room activates and where downed players
     revive. A room with none leaves the player who opened it standing in the doorway at the moment
     the doors re-solidify — Phase Blocks still collide with players, so they end up inside a block.
   - *Mob spawn position*: select a Mob Spawner, click the floor blocks its mobs should stand on.
7. **Configure.** Put the tool away — holding it makes every mod block pass the click through instead
   of opening its screen. Right-click each Mob Spawner (mob id, count, wave, attributes, equipment),
   each Room Controller (name, objective, rewards), and the Dungeon Boss Spawner: press **S** on room
   A and **F** on room B to mark Start and Final.
8. **Tune the controller.** Look at the Dungeon Controller and run `/arenasld admin` for the dungeon
   name, party size, timers and the four difficulty tiers.
9. **Play it.** Right-click the Dungeon Controller as a normal player: Create Lobby, Ready, Start Run.
10. **Test it solo.** There is no minimum party size — Create Lobby, Ready and Start Run on your own.
    Put the Dungeon Tool away first: while you hold it, right-clicking the controller performs the
    tool action instead of opening the lobby. The run switches you to Adventure and restores your
    game mode and position when it ends; `/exit` leaves early at any time.

Arenas and raids are shorter — one spawner block, an entrance, respawn points, and a controller. An
Arena Spawner ships with an **empty mob list**, though: add at least one mob row on its screen, or the
arena cycles through instantly-cleared waves forever. A fresh Mob Spawner (a husk) and a fresh Raid
Boss Spawner (a 300 HP zombie) already work untouched. See [Arenas](#arenas) and [Raids](#raids).

### If the run won't start

- **Told you're queued when nothing is running?** The controller has no usable instance. Check the
  Dungeon Controller → Dungeon Boss Spawner link from step 4 — a controller with zero instances parks
  the lobby in the queue with "All instances are busy" and never says anything again. Raid and Arena
  Controllers do the same.
- **A red "Cannot start" line?** The Start/Final markers or the Start room's boundary door are wrong.
  See [Start, Final, and the two progression styles](#start-final-and-the-two-progression-styles) —
  and note that either error costs you the lobby, so you have to Create Lobby and Ready again for
  each attempt.
- **Party lands somewhere odd, or the HUD sits on "Explore"?** Check the entrance. Nothing validates
  it, and a room activates only when someone stands in the Start room's boundary doorway. See step 6.

## Blocks and the Dungeon Tool

Nine blocks, all with hardness -1 and blast resistance 3600000 — unbreakable and blast-proof, so only
a creative player can remove one.

| Block | Id | What it is |
|---|---|---|
| **Dungeon Controller** | `arenas_ld:dungeon_controller` | The block players click. Owns lobbies, the queue, instances, difficulty tiers, leaderboards and every timing setting, and ticks every run it started. |
| **Dungeon Boss Spawner** | `arenas_ld:dungeon_boss_spawner` | One per dungeon *instance*: holds that copy's room list, the party's entrance and the Start/Final markers. It also carries a mob definition, but that is used only when the same block is *additionally* linked into a room as that room's boss (Room Controller → Dungeon Boss Spawner) — there is no dungeon-level boss that appears on its own. |
| **Room Controller** | `arenas_ld:room_controller` | One per room: its spawners, its doors, its respawn points, its objective, its name and its clear reward. |
| **Mob Spawner** | `arenas_ld:mob_spawner` | A room's wave enemies — name (its label in the Dungeon Tool's spawner list), mob id, count, wave number, spawn positions, attributes, equipment. |
| **Phase Block** | `arenas_ld:phase_block` | Doorways. A connected clump of them is one door. |
| **Raid Controller** | `arenas_ld:raid_controller` | The raid equivalent of the Dungeon Controller. |
| **Raid Boss Spawner** | `arenas_ld:raid_boss_spawner` | One raid instance: the boss, its entrance and its respawn points. |
| **Arena Controller** | `arenas_ld:arena_controller` | The arena equivalent of the Dungeon Controller. |
| **Arena Spawner** | `arenas_ld:arena_spawner` | One arena room: geometry, wave timing, cadence, the mob list and the per-wave loot. |

The three **Controllers** open their lobby screen for anyone, with no permission check. The five
**Spawner / Room Controller** blocks open their editor only in creative or at permission level 2, and
every edit packet is re-checked server-side. Admin settings are not on any block: they open only with
`/arenasld admin` while looking at a controller.

**Creative alone is not enough to save.** The blocks open their editors on creative *or* operator
level 2, but the dungeon and raid save packets check **operator level 2 only** — in creative without
op the Room Controller, Mob Spawner, Dungeon Boss Spawner and Raid Boss Spawner screens all open and
Save silently does nothing. The arena screens and the Dungeon Tool's own block actions accept creative
on its own. Admin screens are operator-only either way.

### The Dungeon Tool

One item, `arenas_ld:dungeon_tool`, does all linking, door wiring and position setting. Its block
actions do nothing unless you are in creative or at permission level 2 — and **Door mode needs
creative specifically**: Phase Blocks have no interaction shape for non-creative players, so an op in
survival raycasts straight through the doorway and gets the mode picker instead of a link. The mode
picker itself, shift + scroll and the shift-right-click selection clear are ungated, so anyone
holding the item can change its mode.

- **Right-click in air** (with nothing in reach) opens **Dungeon Tool — Mode**, grouped into
  *Linking*, *Doors* and *Positions*. **Shift + scroll** cycles modes with the tool in the main hand.
  **Shift + right-click in air** clears the selected source.
- **With a Room Controller selected, the mode picker also lists the room's linked spawners** (by the
  name given on the spawner's screen, or coordinates). Picking one makes it the room's *acting
  spawner*: the tool switches to *Mob spawn position* and world clicks edit that spawner's spawn
  positions without walking over to it — reopen the picker to switch spawners. The **⚙** button on a
  row opens that spawner's settings screen, as if the block were right-clicked.
- Every mode is two clicks: **select a source block, then apply at a target**. Clicking a block that
  is a valid source for the current mode always re-selects it instead of applying, so you can chain
  selections without clearing. The one exception: in *Mob spawn position* mode, clicking a spawner
  that is linked to the selected room picks it as the room's acting spawner and keeps the room
  selected.
- The mode and the selection live on the item stack, so two tools can hold two different selections.
- **Holding it suppresses block screens.** Right-clicking any mod block with the tool in either hand
  performs the tool action. Put it away to configure a block. The one exception is the **Raid
  Controller**, which opens its lobby screen anyway and swallows the click — sneak-right-click it to
  select it as a link source.
- **The overlay shows the whole selection, not just the current mode.** While the tool is held, a
  through-wall outline marks everything the selected block owns, whatever mode you are in — select a
  Room Controller and you see its doors, respawn points, protect position, linked spawners and those
  spawners' own spawn positions at once. The kinds the active mode acts on are drawn at full
  strength; the rest stay visible but dimmer.

  | Color | What it marks |
  |---|---|
  | Gold, doubled outline | the selected block itself (the outer line widens with distance so it stays readable); a room's acting spawner is marked the same way, on top of the room's overlay |
  | Green | a linked block: an instance, a room, a room's spawner |
  | Azure / rose | the Start room / the Final room of a Dungeon Boss Spawner |
  | Violet | a door, boxed as its whole connected Phase Block group rather than the block you clicked (a second, inset box means the stored anchor is no longer a Phase Block — a broken link) |
  | Yellow | a mob or boss spawn position; with an acting spawner picked, only that spawner's positions are yellow |
| Red | a spawn position of one of the room's *other* spawners while an acting spawner is picked, dimmed |
  | Cyan | a respawn point |
  | Orange | the entrance |
  | Magenta | a PROTECT objective's target position |

  Every box is also tinted: its block's sides are washed in its own color under the outline, so a
  marker reads as a volume rather than a wire frame, and a door is skinned over its real shape
  instead of being squared off into a box. A block is tinted once no matter how many markers land on
  it — the outermost, or the one the active mode is placing — so nothing stacks into an opaque blob.
  Outlines still nest inside the block's bounds — spawn, entrance, respawn, protect, in that order —
  so two markers on the same floor block stay distinguishable. Links that cross dimensions are only
  drawn while you are in the same dimension as the target, and an entrance that was never set is not
  drawn at all.

| Mode | Valid sources | What a target click does |
|---|---|---|
| **Link structures** | Dungeon / Raid / Arena Controller, Dungeon Boss Spawner, Room Controller | The (source, target) pair decides the relationship — nothing to pick. |
| **Door** | Room Controller | Links the clicked Phase Block's whole connected group to that room; clicking an already-linked door unlinks it. |
| **Mob spawn position** | Mob Spawner, Dungeon Boss Spawner, Raid Boss Spawner, Room Controller (through its acting spawner) | **Mob Spawner**: toggles one of a list of positions on top of the clicked block (and bumps *Count* by ±1). **Dungeon / Raid Boss Spawner**: a single position — a new click replaces it, clicking the same block clears it. **Room Controller**: applies to the acting spawner picked in the tool screen's list. |
| **Entrance position** | Dungeon Boss Spawner, Raid Boss Spawner, Arena Spawner | Sets where the party lands. Always replaces, never toggles. |
| **Respawn position** | Room Controller, Raid Boss Spawner, Arena Spawner | Toggles one respawn point on top of the clicked block. |
| **Protect target position** | Room Controller | Sets (or clears) where a PROTECT objective's target spawns. |

The six link pairs, and nothing else:

| Click first | Then click | Result |
|---|---|---|
| Dungeon Controller | Dungeon Boss Spawner | registers a dungeon instance |
| Raid Controller | Raid Boss Spawner | registers a raid instance |
| Arena Controller | Arena Spawner | registers an arena instance |
| Dungeon Boss Spawner | Room Controller | adds a room to the dungeon |
| Room Controller | Mob Spawner | adds wave enemies to the room; clicking a linked one unlinks it |
| Room Controller | Dungeon Boss Spawner | adds a boss to that room; clicking a linked one unlinks it |

**Click order is the whole disambiguation** for the last two rows against the fourth: the same two
blocks mean two different things depending on which you select first.

**Link mode unlinks a room's spawners on a second click**, like doors: with the room selected,
clicking an already-linked Mob Spawner or Dungeon Boss Spawner removes it from the room. Instances
and a boss spawner's rooms still only come off from the owning block's screen (*Remove* / *×* /
*Clear All*). Door, Mob spawn position, Respawn position and Protect position also toggle on a
second click.

**Dimensions.** A controller may link an instance in another dimension, and an entrance may be in
another dimension — the dimension is stored with the position. Everything else is stored as an offset
from its owner, so a dungeon's boss spawner, its rooms and their spawners must all live in one
dimension; a cross-dimension click there is refused.

**Stale labels.** Several admin screens and chat messages still say "Linker" or "Spawner
Configurator" in their hint text. Both items were merged into the Dungeon Tool; those labels are
stale, not a second item.

## Dungeons

A hand-built structure becomes a replayable, instanced crawl. A run: the party is teleported to the
entrance in Adventure mode, walks through an open doorway, is locked into that room for a 3-second
countdown, fights its waves until the objective is met, takes the room reward, and the doors open
again. Clearing the room marked **Final** wins.

### Doors are direction-free

A door is not an entrance or an exit: it belongs to the rooms on either side of it, and which way it
opens is decided at clear time.

- A **door** is a connected group of Phase Blocks found by a 6-neighbor flood fill (capped at 1024
  blocks). The group's lowest `(x, y, z)` block is its identity, so clicking any block of a wide
  doorway, from either room, resolves to the same door.
- A door linked by **two** rooms connects them. A door linked by **one** room is a **boundary door** —
  the Start room's way in, or a dead end.
- **Whichever side clears first opens the door toward the other.** When a room clears, each of its
  doors is set individually: fully invisible if the room beyond is already cleared or there is none,
  **armed** (orange, passable) if the room beyond is still pending. Clearing a room also retints a
  cleared neighbor's shared door from armed to fully open.
- **Standing in a door is what activates the room beyond**, and a door only counts while exactly one
  of its two rooms is still uncleared and unactivated — the door between two pending rooms is
  ambiguous and matches nothing. At run start that leaves exactly one live door in the whole dungeon:
  the Start room's boundary door, which is opened armed. That is why the party's entrance belongs
  outside it, facing in.
- Entering closes **all** of that room's doors, including one shared with an already-cleared
  neighbor — there is no backing out.

Phase Block has two blockstate properties, `solid` and `armed`: solid renders a red barrier and blocks
movement, `armed` renders orange and is passable, neither renders nothing at all. For survival and
adventure players the outline shape is empty, so attacks and block-selection raycasts pass straight
through an armed door to the mobs behind it and no selection box is drawn — only creative players can
select or break one. That empty shape is also why the Dungeon Tool's Door mode needs creative and not
just operator level 2.

### Start, Final, and the two progression styles

Each room row on the Dungeon Boss Spawner screen carries an **S** button ("Mark as Start room") and an
**F** button ("Mark as Final room"). Marking toggles: clicking the marked room clears it, clicking
another moves it. Marked rooms show green **START** / red **FINAL** badges.

- **Branching (door graph)** — active as soon as **either** marker is set. The party explores; the
  door graph decides everything; clearing the Final room wins. Room list order is cosmetic.
  A room doesn't lock in on the first touch: touching an entrance door registers you on that room
  (touching another room's door re-registers you) and gives you a glowing outline so the party can
  see who waits where; the room starts once its **Required players %** share of the online party is
  registered on it (set per room in the Room Controller's Objective section, default 25% — 2 players
  in a 5-8 player party; parties of 4 or fewer enter on the first touch). While short of the quota
  the HUD shows *Waiting players 1/2* next to the rooms-cleared line, and the glow ends when the
  room locks in. Turning back after touching a door does not unregister you.
- **Legacy (linear)** — a dungeon with **no** markers at all walks the room list by index: clear room
  1, open its doors, move to room 2, and so on; the last room in the list wins. This is the only mode
  where room order matters, and it exists for dungeons built before markers.

Setting one marker but not the other makes the dungeon unstartable. A branching dungeon refuses to
start unless both markers are set, the Start room position really holds a Room Controller, **and** the
Start room has at least one boundary door — the run tells the whole party in red:

```
Cannot start: mark a Start and a Final room on the dungeon boss spawner.
Cannot start: the Start room needs a boundary door (a door not shared with another room) as the way in.
```

If you link the entry doorway from both the Start room and its neighbor, it stops being a boundary
door and the run refuses to start.

Both errors cost you the lobby: it is removed the moment an instance is claimed, before the run
validates anything. Fix the markers or the door, then Create Lobby, Ready and Start again.

### Room objectives

Four types, as a segmented row on the Room Controller. An untouched room is **Kill everything**, which
is exactly the pre-objective behavior.

| Objective | How it clears | Options |
|---|---|---|
| **Kill everything** | every wave's mobs die | — |
| **Survive** | the timer expires — mob deaths never clear it | *Survive time (s)* 1–3600, default 60; *Wave every (s)* 5–600, default 15 |
| **Kill the boss** | the wave's boss mobs die; the remaining adds are discarded on the spot and unspawned waves skipped | falls back to Kill everything with no boss spawner linked |
| **Protect the target** | all waves die while the target lives; the target dying **loses the run** | *Target mob* (default `minecraft:villager`), absolute attribute overrides, *Stationary* On/Off, and the protect position |

- **Survive** force-spawns the next wave every interval on top of whatever is still alive, looping past
  the last wave back to the first, so a party cannot stall behind one leftover mob. When the timer
  expires every remaining mob is discarded. A long survive time with a short interval piles up a very
  large number of mobs.
- **Protect** costs more than it looks. The target joins the party's no-friendly-fire team (players
  cannot hurt it) but deliberately not the mob team (wave mobs can), and idle mobs with no live target
  are retargeted onto it each tick. While it lives, a downed player's respawn timer is multiplied by
  **1.5** and revived players come back at the **target's current HP percentage** (minimum 1 HP). The
  run HUD shows the target's health in the boss slot. Its attributes are **absolute** — not scaled by
  tier or party size — so a default villager dies to a Nightmare wave in seconds unless you give it
  `max_health` explicitly. *Stationary* (on by default) gives it no AI.
- A missing protect position, an unparseable mob id or a non-living entity type logs a warning and the
  room simply behaves as Kill everything. Nothing bricks a run.

### Waves, respawn points and entry

- **Waves are implicit**: a room's wave list is the sorted distinct *Wave* numbers (1–10) of its linked
  spawners, computed at activation. When every mob of the current wave dies there is a 60-tick (3 s)
  gap, telegraphed to clients, then the next wave. A wave whose spawners all fail to spawn anything is
  skipped; if every remaining wave is empty the room is marked cleared rather than soft-locking.
- A **Mob Spawner** spawns `max(1, Count)` mobs, cycling round-robin through its spawn positions. With
  no positions set, every mob spawns one block above the spawner. Placing a position with the tool
  bumps *Count* by 1 (capped at 64) and removing one drops it by 1.
- A **Dungeon Boss Spawner** linked into a room spawns exactly one mob, flagged as a boss and tagged
  **★ BOSS** in the room's spawner list. When all of a wave's bosses die, the wave's remaining adds are
  discarded instead of having to be hunted down.
- Every spawned mob gets the tier × party health multiplier applied to its configured
  `minecraft:generic.max_health` attribute — nothing else, and nothing at all if the spawner has no
  attribute rows — is healed to full, is marked persistence-required so vanilla never despawns it,
  and joins a shared no-friendly-fire scoreboard team. Vanilla mob conversions (zombie → drowned,
  skeleton → stray, zombie-villager curing…) are blocked for every run-spawned mob: conversion would
  replace the entity, falsely counting a kill and leaving an untracked, unscaled copy behind.
- When 15% or less of the current wave is left alive (10 mobs → the last one), the stragglers start
  glowing so the party can find them. Each wave counts separately — the next wave's spawn resets the
  threshold. SURVIVE rooms never glow (their timer clears the room either way).
- Run mobs never wreck the run: their explosions (creepers, ghast fireballs, wither skulls…) hurt no
  teammate on the shared no-friendly-fire team (mob projectiles like skeleton arrows already respect
  it) and destroy no blocks, and run endermen neither pick up nor place blocks. (A wither boss chewing through walls
  after being hit, zombies breaking doors and similar griefing are vanilla `mobGriefing` behaviors
  this does not touch.)
- **A room holds a list of respawn points, not one.** Both room entry and downed-player revives pick
  the point **closest to the player's own position**, so a party split across two doors enters on both
  sides. With no respawn point set, the player who crossed the door stays put and everyone else is
  teleported onto them — and since entering re-solidifies every one of that room's doors, the one who
  stayed put is left standing inside a solid Phase Block. Give every room at least one respawn point.
- **Entering** closes the room's doors, teleports the party, shows a "Get ready!" title with a bell,
  telegraphs the first wave's spawn positions as red wireframe boxes, and counts down 3 seconds to a
  red **GO!**. The HUD reads "Fight starts in N s".

### Death, disconnects and the run's end

- A lethal hit does not kill: the player drops to spectator at 1 HP with "You're downed. Respawning
  shortly...", the **death time penalty** comes off the shared run timer, and a respawn countdown
  starts. They return in Adventure at full health (or the protect target's HP%) at the active room's
  nearest respawn point. The room is read **at revive time**, so clearing rooms while a teammate is
  down pushes their respawn forward; while exploring it falls back to the most recently cleared room
  with a respawn point, then the dungeon entrance.
- **Hardcore** removes the player from the run on their first death instead: healed, effects wiped,
  prior game mode restored, sent home, no further reward eligibility. The party continues.
- **Logging out** marks a participant disconnected and starts a grace countdown (default 5 minutes);
  the party is told how long they have, and a downed player's respawn timer pauses while offline.
  Reconnecting inside the window puts them back at the active room's respawn point, still downed if
  they were. Past it they are removed and ejected on their next login.
- **Losses**: the timer hitting zero, nobody left in the run or online, the protect target dying, or
  the Dungeon Boss Spawner block being gone. A loss discards the boss room's live mobs, wipes every
  participant's status effects, and plays a red **Defeat** title.
- **A win** pays each loot-eligible player (anyone not removed, online or not) the tier's per-player
  loot table, the tier's currency — **doubled in hardcore** — and the tier's skill XP, and writes online
  winners to that tier's leaderboard with their run time. All status effects are then stripped.
- Either way a **close timer** runs (default 30 s) with a clickable **[Exit Now]** in chat before
  everyone is teleported back to their captured return point with their original game mode and full
  health. Players who were offline at that moment are sent home on their next login.

`/exit` during a dungeon run that is still going is a **forfeit**: the leaver goes home, loses all
reward eligibility, the party is told, and the run ends as abandoned if they were the last one in.

### Difficulty tiers

Four tiers per Dungeon Controller — Easy, Normal, Hard, Nightmare — each its own tab on the admin
screen. A tier's settings are **snapshotted onto a run at start**, so editing one mid-run does not
affect runs already going.

| Setting | Easy | Normal | Hard | Nightmare | Admin range |
|---|---|---|---|---|---|
| Health multiplier | 0.75 | 1.0 | 1.5 | 2.5 | step 0.25, 0–100 |
| Damage multiplier | 0.75 | 1.0 | 1.5 | 2.5 | step 0.25, 0–100 |
| Dungeon time | 600 s | 600 s | 600 s | 600 s | step 30, 1–86400 |
| Tier availability | enabled | enabled | enabled | enabled | toggle (hides it from the lobby picker) |
| Currency reward | 0 | 0 | 0 | 0 | free text, ± steps of 10, 0–1000000000 |
| Skill XP per win | 100 | 100 | 100 | 100 | step 10, 0–1000000 |

Every tier's **per-player loot table** starts unset; the admin screen offers a picker of the server's
loot tables.

The **health multiplier** scales the mob's configured `minecraft:generic.max_health` attribute row,
boss included. Like the raid tiers, it iterates only the attributes you set on the spawner, so a Mob
Spawner or Dungeon Boss Spawner with an empty Attributes list spawns unscaled — give every spawner an
explicit `max_health` row or the tier multipliers do nothing and Easy and Nightmare are identical.
The **damage multiplier** scales damage players take, filtered by the config option
[`dungeon_damage_source_filter`](#config). On top of it, the General tab's **Mob HP per extra player**
(percent, step 5, 0–1000, default 0 = off) gives `1 + (players - 1) × scale`, frozen at run start, so a
solo party is always 1.0 and people leaving mid-run do not weaken the mobs.

**Leaderboards**: one per tier, fastest run time, each player's best only, top 10. They live in the
controller's block entity, so breaking the block destroys them.

### Dungeon Controller General settings

| Field | Default |
|---|---|
| Dungeon name (shown in invites, max 48 chars) | — |
| Cooldown | 300 s |
| Close timer | 30 s |
| Max party size | 4 (the server accepts 1–16; the admin stepper goes to 64 and anything above 16 is silently rejected) |
| Invite expiry | 30 s |
| Respawn time | 40 ticks (step 10, 0–12000) |
| Death time penalty | 10 s (step 1, 0–600) |
| Mob HP per extra player | 0 % |

Disconnect grace and the lobby offline timeout both default to 5 minutes and have no field.

## Arenas

Endless waves. Two blocks: an **Arena Controller** (lobby, queue, instance pool, leaderboard, and the
per-tick driver for every run) and one or more **Arena Spawners**, each one physical arena room holding
its geometry, timing, cadence, mob list and per-wave loot. Arenas have **no difficulty tiers** — the
lobby screen has no tier picker and difficulty comes entirely from per-wave and per-player scaling.

Arena mobs need no spawn positions: regular mobs are placed procedurally on a random ring around the
spawner block between *Spawn distance* and *Battle radius − 2*, at a y from the spawner's up to +5,
requiring solid ground, no collision and no liquid, with up to 10 attempts before falling back to one
block above the spawner. Bosses spawn at the spawner's own x/z.

Entry puts players in **Survival**, unlike dungeons and raids, which use Adventure. Arenas_LD does no
block protection of its own, so arena players can mine and build inside your arena; only the mod's own
nine blocks are unbreakable. Claim the arena in your protection plugin, or build it out of blocks you
do not mind losing.

### The wave loop

A prepare countdown, then wave 1, 2, 3… Clearing every mob of a wave chats "Wave N cleared!", pays that
wave's loot, heals and repairs the party, revives everyone who went down, and starts a between-wave
pause. Every wave start clears all dropped items within the battle radius.

**Archetype is chosen per wave by cadence, first match wins** — boss, then objective, then elite, then
horde. They do not stack: with the defaults (5 / 7 / 3) wave 15 is a boss wave, not an elite one.

| Archetype | Cadence default | Mobs spawned |
|---|---|---|
| **Boss** | every 5 | exactly one boss, no regular mobs |
| **Objective** | every 7 | `base` |
| **Elite** | every 3 | `max(1, base/3)`, at a hardcoded 1.6× scale |
| **Horde** | the fallback | `round(base × 1.5)` |

where `base = 5 + wave/2 + 2 × (active players − 1)` (integer division). A cadence of 0 means never.
**Boss** is skipped unless at least one mob has the BOSS toggle on *and* is valid for that wave;
**Objective** is skipped if all three objective toggles are off — either falls through to the next
archetype silently.

Each mob row is valid for a wave when `minWave ≤ wave ≤ maxWave`. Regular mobs are picked one at a time
by weight; a boss wave picks one boss uniformly at random.

### Objective waves

One type is drawn uniformly at random from the spawner's enabled list (all three on by default).

- **Hold the zone** — at least one active player must stay within `max(3, spawnDistance / 2)` blocks of
  the spawner for the whole wave; the instant nobody is inside, it fails. The edge is drawn once a
  second as a ring of particles.
- **Nobody goes down** — fails the moment any party member goes down. It does not track ordinary
  damage.
- **Slay the marked target** — the first regular mob of the wave is marked and glowing; killing it
  clears the wave at once and discards the rest. It cannot appear on a boss wave.

Failing one is announced in red and **forfeits that wave's loot and the heal/repair bonus** — downed
players are still revived. A failed objective is a resource loss, not a wipe.

### Scaling

Three multipliers stack on every attribute a mob row configures — not just health, despite the setting
names, so attack damage and movement speed grow with them too:

- **Per player**: `1 + (party size at run start − 1) × hpScalePerPlayer` — the admin's *Mob HP per extra
  player (%)*, default 10 %. Frozen at run start.
- **Per wave, linear**: `1 + (wave − 1) × hpScalePerWave` — the admin's *Mob HP per wave (%)*, **default
  0 = off**.
- **Per wave, compounding**: `(1 + attributeScale)^(wave − 1)` — the spawner's *Attribute scale per
  wave*, default 0.1 (+10 % per wave).

**Bosses get only the per-player and per-wave-linear part** — not the compounding scale, not the elite
multiplier. With *Mob HP per wave* at its default 0, a wave-50 boss is exactly as strong as a wave-5
boss while the regular mobs around it have grown enormously. Raise that setting, or give bosses high
attribute values, if bosses should keep up.

The party's **mob count** uses the live active-player count each wave, while the HP multiplier is frozen
at run start — players dropping out reduce how many mobs spawn, not how tough they are.

### Waves, timing and the wave-clear bonus

Each wave's clock is `waveTimer + (wave − 1) × additionalTime` seconds, plus `bossWaveAdditionalTime`
if a boss spawned. Zero means TIMEOUT.

| Arena Spawner setting | Default |
|---|---|
| Battle radius | 64 |
| Spawn distance | 8 |
| Attribute scale per wave | 0.1 |
| Wave timer | 120 s |
| Added time per wave | 5 s |
| Time between waves | 10 s |
| Prepare time | 10 s |
| Boss wave bonus time | 60 s |
| Boss / Elite / Objective wave every N | 5 / 3 / 7 |

On each cleared wave with the objective intact, every standing participant heals `maxHealth × pct` and
has all four armor pieces plus both hands repaired by `maxDamage × pct`, where `pct = min(1.0, wave/100)`
— so the bonus grows 1 % per wave and is a full heal and full repair from wave 100.

**Downed players revive only between waves.** A lethal hit makes them a full-health spectator with
"You're downed. You'll revive next wave.", and the wave clock loses the death penalty; they come back
in Survival at the closest respawn point (or the entrance) when the wave is cleared. There is no
mid-wave respawn timer — the admin screen's *Respawn time (ticks)* is stored but never counted down in
an arena. If everyone is down at once, the run ends as a wipe first.

### How an arena ends

**COMPLETED** when *Max wave* (default −1 = endless) is reached, otherwise **WIPED**, **TIMEOUT**,
**ABANDONED** (nobody online), or **FORCED** (the spawner block was broken mid-run). Every outcome pays
the same end-of-run summary as long as one wave was started:

```
currency = round(currencyBase × Σ w^currencyExp)   for w = 1..waves completed
xp       = round(xpBase      × Σ w^xpExp)
```

Both default to base 1.0 and exponent 1.5, and both double in hardcore. The Arena Admin screen edits
them as four free-text fields under *Reward Curve (base x sum of wave^exp)*.

The leaderboard is a single top-10 list keyed by **deepest wave reached**, each player's best only.

### Arena Controller settings

| Setting | Default |
|---|---|
| Max party size | 10 (clamped 1–20) |
| Max wave | −1 (endless) |
| Close timer | 30 s |
| Instance cooldown | 6000 ticks (5 min) |
| Respawn time | 6000 ticks — unused in arenas, see above |
| Invite expiry | 600 ticks (30 s) |
| Death time penalty | 200 ticks (10 s) |
| Mob HP per extra player | 10 % |
| Mob HP per wave | 0 % |

The reward curve is edited on the same screen. Disconnect grace and the lobby offline timeout are
persisted at 5 minutes each but have no field.

**Mind the entrance.** Respawn points are stored as offsets from the **spawner** but resolved into the
**entrance's** dimension, so keep the entrance in the same dimension as the arena; and an arena whose
entrance was never set teleports the party to the spawner's coordinates in the **Overworld**, so
always set one. Separately, the spawner's *Highlight mobs at N seconds left* field is saved and synced
but read by nothing, so it does nothing today.

## Raids

One scripted boss fight for a party. A **Raid Controller** owns the lobbies, queue, instance pool,
tiers and leaderboards; a **Raid Boss Spawner** is a passive block describing one room — which mob the
boss is, its attributes and equipment, where it spawns, where the party enters, and where downed
players come back. Entry puts players in **Adventure** mode and announces the raid to the whole server.

Raids have **no rooms, no doors, no waves and no adds**: the run creates exactly one entity and nothing
in the raid code spawns anything else. There are no boss phases either — the lifecycle is
STARTING → RUNNING → CLOSING → DONE at the run level only.

The **Raid Boss Spawner screen** holds only a mob id (with an entity dropdown), a read-only count of
linked respawn points, **Attributes**, **Equipment** and **Save**. Everything numeric lives on the
controller's tier tabs. A fresh spawner is a `minecraft:zombie` with `minecraft:generic.max_health` 300
and `minecraft:generic.attack_damage` 15.

### Boss gear and attributes

The shared **Attributes** editor is a list of attribute id + value rows (values are absolute, not
multipliers). **Save** applies without closing, so you can keep editing; **×** or **Esc** goes back
to the spawner screen that opened the editor — the same back-navigation the Equipment editor and the
room's Rewards screen use. The shared **Equipment** editor has six ghost slots — Head, Chest, Legs, Feet, Main Hand,
Off Hand — each with its own 0–100 % spawn chance rolled independently, plus an **Enable drops**
toggle. Clicking a slot with an item on the cursor stamps a template without consuming your item.

**Configured gear never drops** — its drop chance is forced to 0. *Enable drops* controls something
else: the mob's own natural loot table (bones, rotten flesh…), canceled when it is off.

On spawn the boss is healed to full, marked persistence-required (and protected from vanilla mob
conversions), and put on a no-friendly-fire team.

### Tier scaling

| Setting | Easy | Normal | Hard | Nightmare |
|---|---|---|---|---|
| Health multiplier | 0.75 | 1.0 | 1.5 | 2.5 |
| Damage multiplier | 0.75 | 1.0 | 1.5 | 2.5 |
| Run time limit | 600 s | 600 s | 600 s | 600 s |
| Skill XP per win | 100 | 100 | 100 | 100 |
| Boss regeneration | 0 | 0 | 0 | 0 |
| HP scale per player | 0.10 | 0.10 | 0.10 | 0.10 |
| Loot table / currency | — / 0 | — / 0 | — / 0 | — / 0 |

- **Health and damage multipliers scale the boss's configured `max_health` and `attack_damage` base
  values.** They iterate only the attributes you configured, so deleting those two rows in the
  Attributes editor silently disables all tier scaling.
- The **damage multiplier is applied twice**: once to the boss's attack damage, and again as a blanket
  multiplier on every point of damage a participant takes. Unlike dungeons, the raid path has no
  damage-source filter, so fall damage, fire and drowning are multiplied too.
- **HP scale per player**: `1 + (players − 1) × hpScalePerPlayer` — at the 0.10 default a 4-player party
  faces +30 % boss HP. The stepper allows −0.99 to 10.0 in 0.05 steps, so it can shrink the boss too.
- **Boss regeneration**, above 0, heals the boss exactly that many HP every 100 ticks (5 seconds) for
  as long as the run is RUNNING.

The HUD shows "Defeat the boss", the countdown and the boss's remaining HP as a whole percentage
(rounded up, so it never reads 0 % while the boss lives). A tier time limit of 0 makes the run untimed
and hides the bar — though the admin stepper's minimum is 1 second, so that state cannot be configured
through the screen.

**Winning** is simply the boss no longer being alive; it pays the tier's loot, currency (doubled in
hardcore — loot and XP are not) and skill XP, and records the run time on that tier's top-10
leaderboard. **Losing** is the timer expiring, the boss vanishing, or everyone being eliminated; the
boss is despawned and nothing is paid.

Downed players drop to 1 HP as spectators and return in Adventure at the nearest respawn point
(or the entrance) after the controller's respawn time. In hardcore, a lethal hit — **or a disconnect** —
eliminates them immediately, with no grace window.

### Raid Controller settings

The General tab, alongside the four tier tabs above.

| Field | Default |
|---|---|
| Raid name (shown in invites, max 48 chars) | — |
| Cooldown | 300 s (step 5, 0–86400) |
| Close timer | 30 s (step 5, 0–86400) |
| Max party size | 10 (clamped 1–20; the admin stepper goes to 64, values above 20 are clamped down) |
| Invite expiry | 30 s (step 5, 1–86400) |
| Respawn time | 6000 ticks (step 20, 0–1200000) — the only General field in ticks |
| Death time penalty | 10 s (step 1, 0–600) |

**Two things to watch.** The **TIER AVAILABILITY** toggle does nothing for raids: it is stored and
shown, but the start path never checks it and the player tier picker always offers all four. And the
post-run instance cooldown uses the **RESPAWN TIME** setting, not COOLDOWN — the COOLDOWN field only
feeds the queue's wait estimate. Both are 6000 ticks by default (COOLDOWN shows that as 300 s), so
the difference only shows once you change one.

## Lobbies, parties and instances

All three systems share one player-facing flow. Right-click a controller — no permission needed — and
you get a four-tab screen: **LOBBIES**, **MY LOBBY**, **LEADERBOARD**, **INVITES** — the first,
second and fourth carrying a live count. The raid and arena screens add a strip of instance pills
above the tabs (FREE green, RUNNING amber, COOLDOWN red with seconds left); the dungeon lobby screen
has no instance strip, so its pool is only visible on the admin screen.

- **Create Lobby** makes one owned by you — hardcore off, PUBLIC, defaulting to Normal difficulty
  (or the first enabled tier when Normal is disabled; with every tier disabled no lobby can be
  created). Disabled tiers can't be picked, and Start re-checks the selected tier in case an
  admin disabled it while the lobby was forming. The same rules apply to raid lobbies.
- **Visibility**: PUBLIC (a JOIN button), FRIENDS (a REQUEST the owner accepts or declines), PRIVATE
  (invisible in the browser, invite only). FRIENDS has no actual friend system behind it — the only
  difference is the request step.
- **Invites** are owner-only and expire with the controller's invite expiry (default 30 s). An invitee
  also gets a chat line naming the content, the tier and a red hardcore warning, with clickable
  **[Accept] [Decline] [Open]** — no screen needed.
- **Ready** is per member and chats "<name> is ready (2/4)." to the party with a clickable toggle.
- **Only the owner starts**, and the button is disabled unless everyone is ready and online. Ownership
  transfers automatically if the owner leaves with members remaining; leaving alone disbands.
- **Hardcore** is an owner toggle described in-screen as "Permadeath. No second attempts. 2× rewards on
  win." In dungeons and raids it doubles the win currency; in arenas it doubles the per-wave loot rolls
  and the end-of-run currency and XP.
- **One activity at a time.** A player already in any Arenas_LD run is flagged busy through BusyLib and
  cannot create, join, request or accept. Without BusyLib every busy check silently returns false.

**Instances.** Each linked spawner block is one instance — one physical copy of the content, so several
parties can play it at once. A run claims the first instance that is idle, off cooldown and not queued
for removal, so the admin screen's pool order is the order instances are handed out. Removing an
instance mid-run queues the removal until the run finalizes ("Instance is running. Removal queued until
run finalizes."); pressing the button again cancels it.

**The queue.** With every instance busy, a lobby is queued and told its position and an estimated wait
(the soonest instance's remaining run time plus its cooldown). Dungeons and raids then **never
auto-start**: when an instance frees, the front lobby gets a clickable **[Start]** and 20 seconds of
priority before it rotates to the back with "priority lost" and the next lobby is offered the slot.
**Arenas are the opposite** — the front queued lobby is promoted and launched automatically.

**Runs keep ticking while nobody is nearby.** Each controller force-loads its own chunk, and a
starting **dungeon** run also force-loads its instance's chunks plus the entrance chunk in its own
dimension. Raid and arena runs force-load only their controller's chunk, so keep those instances
inside an area that stays loaded.

**The run HUD** replaces vanilla boss bars entirely (they are suppressed while it is up): a dark panel
at the top with a large MM:SS countdown, a red *Hardcore* tag, an objective line, and an optional
boss/protect health strip. It counts down locally between server updates and hides itself after 4
seconds of silence. Objective lines include "Explore • Rooms cleared: 2/7", "Fight starts in 3 s",
"Wave 2/3 • Enemies left: 4", "Protect the target • Enemies: 4", "Survive: 1:20", "Prepare for battle",
"Wave 4 • Enemies: 12", "Defeat the boss", and "Returning home — /exit to leave now".

**Players bring their own gear.** No inventory is stored, taken or handed back on entry or exit —
only game mode, health, status effects (wiped on exit) and the scoreboard team change. What is
restricted is a config
[item blacklist](#config) (blocked on use in air, on a block and on an entity, with a red action-bar
message) and a config effect blacklist stripped every tick.

Quirks worth knowing: a dungeon lobby is **deleted** when its run starts, while raid and arena lobbies
stay as IN_RUN — so a dungeon party in progress is not in the lobby list. The lobby list draws at most
6 lobbies and the member list at most 5 rows. There is no minimum party size anywhere: a lobby of one
can start any run. And "Leave Queue" on the raid and arena screens leaves the whole lobby, not just the
queue.

## Rewards and loot

Rewards are shown as an animated popup under the run HUD — item cards in their rarity frames, tagged
`inbox` or `dropped` when the item did not fit, then a currency line and an XP line. A room reward's
status effects get cards of their own, split off with `Items:` / `Effects:` captions: the effect icon
in a frame colored by category (beneficial green, harmful red), the level as a numeral on the sprite
(Regeneration **II**), and the duration below (`5:00`, or `∞`). Headings read
Dungeon Reward, Room Cleared, Raid Reward, Wave Reward or Arena Reward. The popup holds for about
12 seconds — but entering the next room dismisses it (and anything queued behind it) at once, so a
bonus room's reward never clutters the screen once a fight is starting.

**Where a reward actually lands**: into the player's inventory first; overflow to the Economy_LD inbox
when that mod is present, otherwise dropped at their feet. A winner who is offline gets everything
through the inbox — without Economy_LD those items are logged and lost. Currency goes to the wallet
when online and to the claimable inbox when offline, and is a no-op without Economy_LD. Skill XP is
granted only when `puffish_skills` is loaded.

| Level | What it pays | When |
|---|---|---|
| **Per room** (dungeon) | loot table, currency, skill XP, status effects, commands | the moment the room clears |
| **Per wave** (arena) | one loot-table row, `rolls` times | each wave a row matches |
| **Per run** (dungeon, raid) | the tier's loot table, currency and skill XP | on a win |
| **Per run** (arena) | the wave-curve currency and XP summary | on any outcome, win or loss |

**Room clear rewards** are edited from the Room Controller's *Rewards* button into a Room Rewards
screen. Everything defaults to empty, so an untouched room grants nothing. Loot and currency reach
offline players through the inbox; status effects (applied with no particles but a visible HUD icon)
and skill XP require the player to be online. Server-side limits: loot table id trimmed to 256 chars;
at most 16 effects, each 1–3600 s and amplifier 0–9 (shown as level = amplifier + 1); at most 8
commands, each trimmed to 256 chars; currency and XP clamped to ≥ 0.

**Reward commands** run as the server at permission level 2 with output suppressed, positioned at the
room controller. A command containing `@dungeonplayer` or `@s` has the placeholder replaced with each
eligible online player's name and runs once per player; one without a placeholder runs once. Note the
consequence: anyone who can edit a Room Controller can have arbitrary level-2 commands executed on room
clear.

**Arena per-wave rows** fire on wave *W* when `W ≥ minWave && W ≤ maxWave && every > 0 && (W − minWave) % every == 0`
— so "every" counts from that row's `minWave`, not from wave 1 (minWave 3, every 5 pays on 3, 8, 13…),
and an `every` of 0 disables the row. A row is either **each** (per-player: rolled separately for every
online participant, delivered to their inventory) or **one** (communal: rolled once and scattered on
the arena floor). Communal drops are **deleted when the next wave starts**, along with every other
dropped item in the battle radius — use per-player rows for loot that must not be missed. Arena mobs
also default to suppressed natural drops, so all arena loot is expected to come from these rows.

### LuckPerms reward perks

With [LuckPerms](https://luckperms.net) installed, meta values on a player (usually set on a group)
boost their rewards from a raid win, a dungeon completion and the mob arena. Per-room dungeon rewards
are never affected.

| Meta key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `arenas_ld.raid_loot_rolls` | integer | 1 | 1–10 | How many times the raid tier's per-player loot table rolls; all results are delivered together. |
| `arenas_ld.dungeon_loot_rolls` | integer | 1 | 1–10 | The same for the dungeon tier's completion loot table. |
| `arenas_ld.arena_loot_rolls` | integer | 1 | 1–10 | Multiplies the player's rolls on every **per-player** (*each*) wave reward row — a row with `rolls 2` rolls 4 times at value 2, on top of the hardcore ×2. Communal (*one*) rows are rolled once for the whole party and stay unchanged. |
| `arenas_ld.currency_multiplier` | decimal | 1.0 | 0–10 | Currency × this, rounded (`1.1` = +10%). Applied after the hardcore ×2, so both stack. Raid win, dungeon completion and the arena's end-of-run summary. |
| `arenas_ld.xp_multiplier` | decimal | 1.0 | 0–10 | Skill XP × this, rounded (`1.25` = +25%), at the same three payouts. |

A missing or unparseable value, or a value outside its range, falls back to (or is clamped into) the
default range. The popup shows the boosted amounts. Example for a `prime` group:

```
lp group prime meta set arenas_ld.raid_loot_rolls 2
lp group prime meta set arenas_ld.dungeon_loot_rolls 2
lp group prime meta set arenas_ld.arena_loot_rolls 2
lp group prime meta set arenas_ld.currency_multiplier 1.1
lp group prime meta set arenas_ld.xp_multiplier 1.1
```

**Offline winners get the defaults.** LuckPerms only has online players loaded, so a loot-eligible
player who is offline at payout (their reward goes through the Economy_LD inbox) receives one roll and
unmultiplied currency. Arena wave rows only pay online players anyway. Without LuckPerms every player
simply gets the defaults.

## Commands

```
/arenasld admin                    open the admin screen of the controller you are looking at (within 10 blocks)
/arenasld reload                   re-read config/arenas_ld.json
/arenasld stats                    your lifetime dungeon / raid / arena runs, wins and best wave
/arenasld exit                     leave a run  (see /exit)
/exit                              top-level alias of /arenasld exit
/arenasld lobby ready   <dim> <pos>
/arenasld lobby start   <dim> <pos>
/arenasld lobby open    <dim> <pos>
/arenasld lobby accept  <dim> <pos> <lobbyId>
/arenasld lobby decline <dim> <pos> <lobbyId>
```

`admin` and `reload` require **permission level 2**; everything else is open to all players. The
`lobby` subcommands exist as the click targets behind the chat buttons and re-validate membership,
ownership and invites server-side, which is why they need no gate.

`/exit` is registered as an unconditional top-level alias and can collide with another mod or plugin
that registers the same name.

## Config

One pretty-printed JSON file at `config/arenas_ld.json`, created with defaults on first launch and
**rewritten on every load** — hand-added keys and comments are erased on the next start or
`/arenasld reload`.

| Option | Default | Meaning |
|---|---|---|
| `puffish_skills_tree_id` | `"puffish_skills:combat"` | The Puffish Skills category skill-XP rewards go into. Ignored without the mod. |
| `dungeon_damage_source_filter` | `"ALL"` | Which damage the **dungeon** tier damage multiplier applies to: `LIVING` (only from a living entity), `MOB` (only from a mob), anything else = all. Case-insensitive. Raid multipliers ignore it; arenas do not use this path. |
| `run_item_blacklist` | `["minecraft:ender_pearl", "minecraft:chorus_fruit"]` | Items unusable during any run. Item ids, or tags with a `#` prefix. |
| `run_effect_blacklist` | `[]` | Mob effects stripped from run participants every tick. **Plain ids only** — the `#` tag syntax is not supported here. |
| `debug_logging` | `false` | Verbose `[debug]` INFO logging of teleports, reconnect decisions and chunk force-loading. |

## File locations

| What | Where |
|---|---|
| Config | `config/arenas_ld.json` |
| Instances, lobbies, queue, invites, tier settings, leaderboards, in-flight runs | the controller block's own NBT, i.e. that chunk |
| Rooms, doors, respawn points, objectives, room rewards | the Room Controller block's NBT |
| Lifetime player stats | `<world>/data/arenas_ld_player_stats.dat` |
| Pending ejects for players offline when a run ended | `<world>/data/arenas_ld_pending_restores.dat` |
| Saved pre-run scoreboard teams | `<world>/data/arenas_ld_party_teams.dat` |

Because lobbies, instance links, tier settings and leaderboards live in the controller's block entity,
**breaking or replacing a controller block destroys all of them.** Only the three world-level files
above survive it.

## Compatibility

| Mod | Required | What it adds |
|---|---|---|
| Fabric API / [Forgified Fabric API](https://modrinth.com/mod/forgified-fabric-api) | yes | The API surface the shared code is written against. Fabric API on Fabric; Sinytra's Forgified Fabric API on NeoForge. |
| VectorLib ≥ 0.2.5 | yes | Every screen and HUD. **Bundled in both jars** — no separate download. |
| [BusyLib](https://github.com/ledokua/BusyLib) | yes | The cross-mod "player is busy" flag that stops a player being in two activities at once. **Not bundled**, and it has no published release yet — build it from source (`./gradlew build`) and drop the jar in the server's `mods/` folder next to Arenas_LD. |
| Economy_LD | optional | Currency rewards and the inbox that delivers loot to offline players or a full inventory. Without it, currency is skipped silently and an offline player's items are logged and lost. |
| [Pufferfish's Skills](https://modrinth.com/mod/pufferfishs-skills) | optional | Skill XP on tier and room rewards. Every call is guarded, so the XP part of a reward is simply skipped when absent. |
| [LuckPerms](https://luckperms.net) | optional | [Reward perks](#luckperms-reward-perks) from player/group meta: extra loot rolls and currency / XP multipliers for raids, dungeons and the mob arena. Same API on Fabric and NeoForge; without it everyone gets the defaults. |

Both jars are side `BOTH` and load on client and server. The Fabric jar pins Minecraft to exactly
1.21.1; the NeoForge jar accepts `[1.21.1,1.22)`. Java 21.

One gap to plan around: **Economy_LD has no NeoForge build**, so on NeoForge currency rewards and the
reward inbox effectively do not exist, even though Arenas_LD's own integration is loader-neutral
reflection.

## License

MIT — see [`LICENSE.txt`](LICENSE.txt).
