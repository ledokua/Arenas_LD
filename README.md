# Arenas LD

A Fabric mod for creating custom arenas, dungeons, and raid encounters. Designed for map makers who need fine-grained control over mob spawning, battle progression, gating, and reward distribution.

---

## Blocks

### Mob Spawner
A configurable spawner for standard encounter areas.

- **Trigger system** — activates when a player enters a configurable radius
- **Battle logic** — spawns a set number of mobs; the encounter is won only when all mobs are defeated; resets if all players leave the battle area
- **Mob scaling** — configurable mob count and spawn spread radius
- **Attributes & equipment** — set custom Max Health, Attack Damage, armor, and weapon items with optional drop chances via dedicated GUI screens
- **Group ID** — tags spawned mobs with a scoreboard team (team-based encounter setups)

### Boss Spawner
A single-boss encounter block with portal and raid mechanics.

- **Minimum players** — requires a configured number of players before the encounter can begin; broadcasts a server-wide message on activation
- **Enter Portal** — spawns a teleportation portal when the boss is ready
- **Exit Portal** — spawns a timed exit portal when the boss is defeated
- **Loot distribution** — drops standard loot plus gives every participating player a personal Loot Bundle item

### Mob Arena Spawner
A wave-based arena that scales in difficulty over time.

- **Wave system** — spawns waves of mobs defined per wave slot, with configurable wave timer, inter-wave rest period, and prepare time before the first wave
- **Boss waves** — designate specific mobs as bosses; boss waves get extra time and are handled separately from regular mobs
- **Attribute scaling** — mob attributes increase each wave by a configurable scale factor, keeping encounters challenging
- **Entity highlighting** — optionally highlights mobs for a configurable duration after they spawn
- **Leaderboard** — tracks the highest wave reached per player, persisted across sessions
- **Run summary** — sends players a message on both win and fail showing the wave reached and total duration
- **Linked to Mob Arena Controller** — the controller block provides a player-facing interface to join, start, and spectate arenas

### Dungeon Boss Spawner
A full dungeon encounter with a time limit, player tracking, and state persistence.

- **Dungeon timer** — players must defeat the boss before a configurable time limit expires; displayed as a boss bar
- **Entrance & exit positions** — teleports players in at the start and out when the dungeon ends (win, loss, or timeout)
- **Downed system** — players who die enter a spectator-mode downed state and respawn at the entrance after a short timer, at the cost of time being deducted from the dungeon clock
- **Hardcore mode** — one life per run; doubled rewards on victory
- **Leaderboard** — tracks the top 20 fastest clear times per player, persisted across sessions
- **Cooldown** — after a successful clear, the controller starts instance cooldown (controller-owned)
- **Group ID gating** — optionally restricts dungeon entry to players in a specific Minecraft team
- **Linked spawners** — the dungeon can be gated behind a set of linked Mob Spawners; the boss only triggers once all linked spawners are cleared
- **Chunk forcing** — controller is the persistent anchor; dungeon instances are loaded only while runs are active
- **Linked to Dungeon Controller** — the controller block provides a player-facing interface to queue, start, and view leaderboard

### Dungeon Controller
The player-facing block for dungeon management.

- Shows current dungeon time remaining and cooldown status
- Hosts the party lobby: players join and leave via the GUI
- Displays the fastest-clear leaderboard
- Linked to a Dungeon Boss Spawner via the Linker tool

### Mob Arena Controller
The player-facing block for mob arena management.

- Shows current wave and arena status
- Hosts the party lobby
- Displays the highest-wave leaderboard
- Linked to a Mob Arena Spawner via the Linker tool

### Phase Block
A dynamic gating block that switches between solid and passable states.

- Linked to watched spawners by **relative offsets** (copy/paste-safe room logic)
- Becomes passable when **all watched spawners** are cleared
- Returns to solid when any watched spawner is active again
- Does not use Group ID for gating

### Enter & Exit Portals
Teleportation blocks used to move players into and out of encounters. Spawned automatically by Boss Spawners or placed manually and configured via GUI.

---

## Items

### Loot Bundle
A personal reward item given to each player after a boss encounter.

- Right-click to open and generate items from a configured per-player loot table
- Loot table ID is stored as a data component on the item

### Linker
A multi-mode tool for wiring up the mod's blocks.

- **Group Config** — copy and paste Group IDs between spawners (team/group behavior)
- **Spawner Linking** — link secondary spawners to a main spawner (for linked-spawner gating)
- **Phase Block Linking** — link spawners to a phase block
- **Arena Controller Linking** — link a Mob Arena Controller to a Mob Arena Spawner
- **Dungeon Controller Linking** — link a Dungeon Controller to a Dungeon Boss Spawner

Use **Shift + Scroll** to switch modes.

### Spawner Configurator
A tool for setting position-based configuration on spawners in the world.

- **Spawner Selection** — select a target spawner (Shift + Right-click)
- **Exit Position** — right-click a block to set that spawner's exit teleport destination
- **Entrance Position** — right-click a block to set the dungeon entrance teleport destination
- **Enter Portal Spawn / Destination** — set where the enter portal appears and where it sends players

Use **Shift + Scroll** to switch modes.

---

## Commands

All commands are under `/arenasld` and require operator permission level 2.

| Command | Description |
|---|---|
| `/arenasld reload` | Reload the mod config |
| `/arenasld dungeon register <name>` | Register a Dungeon Controller as a named dungeon (look at the controller block) |
| `/arenasld dungeon unregister <name>` | Unregister a named dungeon |
| `/arenasld dungeon subscribe <name>` | Subscribe yourself to dungeon-ready notifications |
| `/arenasld dungeon unsubscribe <name>` | Unsubscribe from dungeon-ready notifications |
| `/arenasld dungeon subscriptions` | List your current subscriptions |
| `/arenasld dungeon list` | List all registered dungeons |
| `/arenasld dungeon get <name>` | Show info for a registered dungeon |
| `/arenasld debug clearTrackedPlayers` | Clear all tracked players from mob arenas (emergency reset) |
| `/arenasld debug endDungeon` | Force-end the dungeon of a looked-at Dungeon Boss Spawner |

---

## Compatibility

- **PuffishSkills** — if loaded, skill XP is awarded to players on mob spawner and dungeon boss wins (configurable per spawner)

---

## Configuration

All spawner settings are editable in-game via right-click GUIs. Key settings include:

- Mob ID, count, spread, and respawn cooldown
- Trigger radius and battle radius
- Custom attributes (Max Health, Attack Damage, Movement Speed, etc.)
- Custom equipment per armor slot and hand, with optional drop chance
- Loot table IDs (shared and per-player)
- Portal and teleport coordinates (set via Spawner Configurator)
- Wave count, timers, scaling, and boss wave parameters (Mob Arena)
- Dungeon time limit, close timer, hardcore mode, and group gating (Dungeon)
