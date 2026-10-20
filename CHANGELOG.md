# Changelog

## 4.8.0

- **Bosses that summon minions.** Mobs a run mob spawns by itself are now its minions: a vanilla
  Evoker's Vexes, a Bosses of Mass Destruction lich's phantoms, your own bosses' adds. A minion:
  - joins its summoner's team and health and damage scaling;
  - drops no loot or experience;
  - never holds up a room, wave or raid;
  - vanishes when its summoner dies or is cleaned up.

  Before, they were strangers to the run: unscaled, dropping loot, and left behind in the instance
  for the next party. A minion is recognized in these cases:
  - it is spawned while the run mob acts: its tick, vanilla's damage code, a zombie's
    reinforcements, or a projectile it shot;
  - the run mob is its owner.

  A mob owned by someone else (a player's pet or summon), or converted during the boss's turn, is
  never adopted. Minions are removed in the same tick as their summoner, so run cleanup leaves none
  behind.
- **Nothing is spawned for a dying top summoner** (a slime's split, a boss's death summons): those
  mobs would vanish with it at once. A dying minion's spawns are still minions.
- New API: `ArenasApi.spawnAsMinions(summoner, spawning)` for minions spawned from delayed tasks.

## 4.7.0

### Copying instances

- **`/arenasld instance add <activity> <pos> [dimension]`** links a pasted copy of an instance to the
  activity's controller from anywhere (what the Dungeon Tool does by clicking both blocks), then
  checks it. Copies are made with WorldEdit or Axiom: every link inside an instance is an offset
  from its own block, so a paste keeps them.
- **`/arenasld instance check <activity>`** and **`check at <pos> [dimension]`** report:
  - links that don't point at the right block;
  - Start/Final markers that aren't rooms, and a Start room with no way in;
  - an entrance that was never set;
  - entrances and respawn points inside a block;
  - an entrance in another dimension, since a copy keeps the same offset there. This is a warning
    for dungeons and an error for raids and arenas, whose fight is at the spawner.

  It also says which activity lists the instance, and lists errors first. Op level 2.

### Load

- **Raid and arena cooldowns no longer resend the controller every tick.** While an instance was
  cooling down (5 minutes by default), the whole controller (lobbies, runs, leaderboards, tier
  settings) went to every nearby player 20 times a second. Now only an instance coming free is sent;
  open arena lobby screens still count down once a second.
- **Phase Blocks no longer tick.** Every door block had a ticker on the server and the client that
  did nothing. The few legacy raid doors made by the old Linker (main blocks that close while a linked
  raid runs) keep working and are the only ones still checked.
- The dungeon controller marks itself changed once per tick during cooldowns, not once per instance.

### Chunk loading

- **Controllers come back after a clean restart.** A controller let go of its force-loaded chunk
  whenever the chunk unloaded, and a clean server stop unloads every chunk, so after a restart
  controllers (and the runs saved in them) stayed unloaded until someone came near. A player who
  reconnected inside a dungeon could be stuck there. Now only breaking the controller lets go of
  its chunk (unless another controller shares it), and every known controller is loaded at server
  start, which also repairs worlds already affected.
- **Dungeon runs hold their chunks with their own tickets** instead of force-loading them. One run
  ending no longer unloads chunks another run, a controller or an admin `/forceload` still needs.
  Editing rooms or breaking the boss spawner mid-run no longer leaves chunks force-loaded for good.
  The tickets are not saved; a run restored after a restart takes them again. A run started before
  this version lets go of its old force-loads once when it ends.
- `/arenasld activity forget` also lets go of the chunk of a controller that is gone without its
  block being broken; stale index entries found at server start are released the same way.
- Arenas_LD no longer calls `setChunkForced` when nothing would change. Such a call resets the saved
  data's dirty flag and could drop another unsaved force-load change.

## 4.6.2

- **Arena run mates are allies.** A mob arena run now puts its players on their own no-friendly-fire
  team (`ald_a_*`), like dungeon and raid runs, so lobby mates who aren't partied or guilded can't AOE
  or sweep each other where PvP is on. Mobs keep their own team.
- **Players stay off the mob teams.** A player found on `arenas_ld` or `arenas_dungeon` (which makes
  every run mob their ally) is taken off it at login, and a run never gives it back as their prior
  team. Arenas_LD itself never puts players there; this cleans up hand-made or leftover memberships.
- The three run types share one run-team helper.

## 4.6.1

- Reward loot tables (dungeon and raid completion, room rewards, arena wave rows) now roll with the
  player's luck, so luck-weighted entries and quality work. Offline winners roll without luck.

## 4.6.0

Arenas_LD is standalone again: nothing hub-specific is stored in it. Hubs keep their own per-activity
settings keyed by the activity id (Eden_LD does, in its Moderation window).

### Breaking API change

- `ArenasApi.Difficulty(String id, int requiredLevel)` is now `Difficulty(String id)`.
- `ArenasApi.Activity` lost its `requiredLevel` and `cardArt` components:
  `Activity(ResourceLocation id, ActivityType type, Component name, List<Difficulty> difficulties)`.
- The activities listener now fires on id, name or enabled-difficulty changes only.

Callers that only use `id()`, `type()`, `name()`, `difficulties()` and `Difficulty.id()` work with both
4.5.0 and 4.6.0.

### Removed

- The admin screens' **card art** field, the **required level** field on each difficulty tab and the
  arena's **required level** field. Saves from 4.5.0 still load; the old values are ignored and no
  longer written (controller data and `arenas_ld_activities.dat`).

Kept: stable ids, the arena name, `/arenasld activity …`, remote lobby screens, the entry gate with its
dimmed difficulties (the reason comes from whichever mod registered the gate), and run events.

### Fixed

- Leaving an arena now wipes status effects, as leaving a dungeon does, so skill buffs and potions no
  longer stick after a run. Players who were offline when it ended get the same on their next login.
- A copied controller (structure block, schematic) no longer drags the original's instances, lobbies
  and in-progress runs along (a copied run could tick as a second copy of the original's). The copy
  gets its own id, keeps its settings and starts empty.

## 4.5.0

### Activities API (for hub mods such as Eden_LD)

- **Stable activity ids.** Every Dungeon, Raid and Arena Controller has a unique id
  (`arenas_ld:crypt`), set on its admin screen and checked for uniqueness. Controllers from older
  saves get a generated one on their first tick, with a warning in the log.
- **Hub fields on the admin screens**: a card art id next to the id, and a required level on each
  difficulty tab (one for the whole arena). Arenas_LD stores and reports them; it never checks levels.
- **Arenas have a name** now, set on their admin screen.
- **`net.ledok.arenas_ld.api.ArenasApi`**: list the activities (id, type, name, enabled difficulties
  with required levels, card art) with change listeners; open an activity's lobby screen for a player
  from anywhere; register entry gates; listen to finished runs (activity, difficulty, outcome,
  duration, wave, players). See [API.md](API.md).
- **Entry gate everywhere a player gets in**: opening a lobby screen, creating a lobby, changing its
  difficulty, joining / requesting / accepting invites and join requests, and starting a run — where
  every online member must pass and the party is told who can't and why. Arena queue launches
  re-check the party too.
- **Lobby screens show the locks**: on the dungeon and raid lobby screens a difficulty the gate
  refuses is dimmed with the reason under it and can't be picked, and lobbies and invites at such a
  difficulty show the reason instead of Join / Request / Accept.
- **Lobby screens open from anywhere**: a hub (or `/arenasld activity open`, `/arenasld lobby open`,
  the chat **[Open]** button) opens the lobby screen at any distance and in any dimension, and it
  stays open. The arena lobby screen now gets its data from the server instead of the client-side
  block, so it works when the controller is far away.
- New op commands: `/arenasld activity list`, `/arenasld activity open <id> [players]`,
  `/arenasld activity forget <id>`. The index is saved in `<world>/data/arenas_ld_activities.dat`.
- Lobby screen updates now go only to the players who have that lobby screen open.

### Also in this release

- **NeoForge no longer needs Forgified Fabric API.** Arenas_LD talks to each loader through its own
  small platform layer. NeoForge 21.1.249 or newer, and the FFAPI-free BusyLib build, are required.
- LuckPerms reward perks are snapshotted at run start, so winners who are offline at the end still
  get their extra rolls and multipliers.
- The loot popup merges identical stacks into one card with a count and wraps onto centered rows when
  the cards don't fit the screen width.

Older versions: see the git history.
