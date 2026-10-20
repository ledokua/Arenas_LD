# Arenas_LD API

Other mods can list the server's dungeons, raids and mob arenas, open an activity's lobby screen for a
player, veto entry with an **entry gate**, hear about every finished run, and give staff the
[admin side](#admin): live status, teleporting to a controller, its admin screen from anywhere,
renaming, ending runs. It is what a hub mod builds on — Eden_LD's Activities tab uses it — and
Arenas_LD never depends on who calls it: with no gate registered and nobody listening, it behaves
exactly as without the API.

Arenas_LD is a standalone mod and holds nothing hub-specific. A hub keeps its own per-activity
settings keyed by the activity id — Eden_LD keeps whether an activity is shown, its card art and the
required level per difficulty in its Moderation window — and enforces them through the entry gate.

Everything is in one class, `net.ledok.arenas_ld.api.ArenasApi` (common module), with its records and
interfaces nested in it. All methods are server-side and must be called on the server thread.

## Setup

Compile against the Arenas_LD jar for your loader and keep it optional at runtime.

**Fabric** (`build.gradle`):

```groovy
modCompileOnly files("libs/arenas_ld-fabric-4.9.0.jar")
```

```json
"suggests": { "arenas_ld": ">=4.9.0" }
```

**NeoForge**: `compileOnly` the NeoForge jar, and an optional `[[dependencies.yourmod]]` entry with
`modId = "arenas_ld"`, `type = "optional"`, `versionRange = "[4.9.0,)"`.

The [admin calls](#admin) and `Outcome.FORCED` need 4.9.0 and `spawnAsMinions` 4.8.0; the rest works
from 4.6.0.

Only touch `ArenasApi` after checking the mod is loaded, from a class nothing else loads when it is
absent:

```java
if (FabricLoader.getInstance().isModLoaded("arenas_ld")) {
    MyArenasHook.register();   // the only class that imports net.ledok.arenas_ld.*
}
```

## Activities

An activity is one Dungeon, Raid or Arena Controller. Its **id** is a resource location set on the
controller's admin screen (`arenas_ld:crypt`) — stable, unique on the server, and what you key
everything by. A controller without one gets a generated id (`arenas_ld:dungeon_old_crypt`) on its
first tick, so ask the server owner to set real ids before you store them.

```java
// record Activity(ResourceLocation id, ActivityType type, Component name, List<Difficulty> difficulties)
// record Difficulty(String id)
for (ArenasApi.Activity activity : ArenasApi.activities(server)) {
    activity.id();            // arenas_ld:crypt
    activity.type();          // DUNGEON, RAID or ARENA
    activity.name();          // Component: the admin name, or "Unnamed dungeon"
    activity.difficulties();  // enabled ones, lowest first: [Difficulty("easy"), Difficulty("normal")]
}
ArenasApi.activity(server, id);   // Optional<Activity>
```

`difficulties` holds the enabled tiers of a dungeon or raid, ids `easy`, `normal`, `hard`,
`nightmare` (raids show the top tier as Legendary but use `nightmare`). Arenas have no difficulties,
so theirs is always empty.

The list is read from a world-saved index (`data/arenas_ld_activities.dat`), so it is complete even
while a controller's chunk isn't loaded. To react to changes:

```java
ArenasApi.addActivitiesListener(() -> rebuildMyCards(server));
```

It fires after an activity is added or removed (a controller placed or broken), or after its id, name
or enabled difficulties change.

## Opening the lobby screen

```java
boolean opened = ArenasApi.openLobbies(player, ResourceLocation.parse("arenas_ld:crypt"));
```

Opens the same screen as right-clicking the controller — lobbies, my lobby, leaderboard, invites — from
any distance and any dimension; it stays open until the player closes it, and lobby actions from it
reach the right controller. It returns false when the id is unknown or the entry gate refused the
player (they are told why in chat).

## The entry gate

```java
ArenasApi.registerEntryGate((player, activity, difficulty) -> {
    int needed = mySettings.requiredLevel(activity, difficulty);   // your own per-activity settings
    return levelOf(player) >= needed ? null : Component.literal("🔒 Lv " + needed + "+");
});
```

Return `null` to allow, or the reason to refuse with — it is shown to players **as is**, in chat and on
the lobby screen, so keep it short. `difficulty` is a difficulty id, or `null` for the activity itself
(opening its lobby screen at all, and every arena check, since arenas have no difficulties). Several
gates may register; they are asked in registration order and the first refusal wins. A gate that throws
is logged and skipped. Gates are asked often (every lobby screen refresh asks once per enabled
difficulty for each viewer), so keep them cheap.

When it is asked:

| When | Player asked | `difficulty` |
|---|---|---|
| Opening a lobby screen: using the controller, the chat **[Open]** button, `/arenasld lobby open`, `openLobbies`, `/arenasld activity open` | the player | `null` |
| Creating a lobby | the owner | `normal`; if refused, the first enabled one the owner passes (the lobby starts there); if none, the `normal` reason is shown |
| Changing the lobby's difficulty | the owner | the new one |
| Joining a public lobby, requesting to join, accepting an invite | the joiner | the lobby's |
| An owner accepting a join request, sending an invite | the requester / invitee, if online (the owner hears why; a refused requester too) | the lobby's |
| Starting a run (and an arena's queue launching one on its own) | **every online member** — one refusal blocks the start and the party is told who and why | the lobby's |
| Building each viewer's lobby screen | the viewer | each enabled difficulty — refused ones are dimmed with the reason and can't be picked, joined or accepted |

Every check with a difficulty asks about the activity itself (`null`) first, so a refusal there also
closes every difficulty — entering never depends on having opened the screen. Arenas ask with `null`
throughout. Offline members are not asked: a dungeon run doesn't start while anyone is offline, and
raids and arenas only take the online members in.

Arenas_LD can't know when your verdict changes (a player levels up with the screen open), so open
dungeon and raid lobby screens are re-checked every 2 seconds and re-sent to the viewers whose locks
changed. Ask `ArenasApi` from `SERVER_STARTED` on; before the worlds exist the activity list is empty.

## Run events

```java
ArenasApi.addRunListener(result -> {
    result.activity();       // arenas_ld:crypt
    result.type();           // DUNGEON, RAID or ARENA
    result.difficulty();     // "hard", or null for arenas
    result.outcome();        // WIN, LOSS or FORCED
    result.durationTicks();  // game ticks from run start to the win/loss
    result.wave();           // arenas: last wave reached; 0 otherwise
    result.players();        // every participant, including those who left or died out on hardcore
});
```

Called once per finished run, at the win or loss, after the rewards are paid and the win is counted in
the player stats. Dungeon leaderboards are already updated by then; raid and arena leaderboards (and an
arena's best-wave stat) are written when the run closes, after its close timer. An arena run is a WIN when it reached its maximum wave and a
LOSS otherwise — `wave` tells how far it got. A listener that throws is logged and skipped.

`FORCED` means staff ended the run ([`forceEnd`](#ending-runs), `/arenasld activity stop`) while it was
still going: neither a win nor a loss for its players, so leave it out of their stats and streaks. It
is reported once, at the moment it was ended. A run that was already won or lost keeps that result,
and closing it early reports nothing again. Since 4.9.0: a `switch` over `Outcome` needs a `FORCED` case.

## Admin

For staff tools, such as a hub's moderation screen. Every call is server-side, on the server thread,
and keyed by activity id. The ones that act return `null` when done, or the reason they were refused
(a `Component` to show staff as is); an unknown id is refused with "Unknown activity: …".

```java
ArenasApi.location(server, id);                  // Optional<ControllerLocation(dimension, pos)>
ArenasApi.status(server, id);                    // Optional<ActivityStatus>, see below
ArenasApi.teleportToController(player, id);      // @Nullable Component
ArenasApi.openAdmin(player, id);                 // @Nullable Component
ArenasApi.rename(server, id, "Sunken Crypt");    // @Nullable Component
ArenasApi.forceEnd(server, id, instance);        // @Nullable Component; instance null = every run
ArenasApi.forget(server, id);                    // @Nullable Component
```

**Who may use them is your call.** Arenas_LD checks a permission only in `openAdmin`: the admin screen
is for operators (level 2), the same as `/arenasld admin`, and it refuses anyone else ("You need
operator permission"). The others check nothing; gate them behind your own staff permission. The
`/arenasld activity` commands that use them need level 2.

### Status

```java
ArenasApi.status(server, id).ifPresent(status -> {
    status.instances();       // List<InstanceStatus>, in the controller's order:
                              //   instance() (InstanceRef(dimension, pos): the boss / arena spawner),
                              //   state() FREE, RUNNING or COOLDOWN, cooldownTicksLeft(), pendingRemoval()
    status.runs();            // List<RunStatus>: instance(), difficulty() (null for arenas), players(),
                              //   phase() (Component), ticksElapsed(), ticksLeft() (-1 = no timer), closing()
    status.openLobbies();     // forming or ready, queued ones included
    status.queuedLobbies();   // of those, waiting for a free instance
    status.waitingPlayers();  // members of the open lobbies
    status.warnings();        // Set<Warning>
});
```

**Poll it freely.** It reads the saved index and what the controller holds in memory, and never loads a
chunk or drops an entry, so asking every second or two for every activity is fine. Controllers keep
their own chunk loaded; when it isn't, status says `CONTROLLER_NOT_LOADED` instead of looking (usually
a controller removed by a tool that skipped block updates).

`phase` is a short translatable line for staff: "Starting", "Room 3", "Exploring, 2 cleared", "Boss
room" for dungeons; "Boss 42%" for raids (just "Boss" while the boss's chunk isn't loaded); "Prepare",
"Wave 4/10", "Wave 4" (endless) or "Wave 5 incoming" for arenas; and "Closing (won)", "Closing (lost)"
or "Closing (ended by an admin)" for a run on its exit timer (`closing()` true). `ticksLeft` is the
timer counting now: the run timer, the wave or prepare timer, or the exit timer.

| Warning | Means |
|---|---|
| `GENERATED_ID` | Arenas_LD made the id up; set a real one on the admin screen before storing it. Saves from before 4.9.0 didn't record this: only the position form (`arenas_ld:dungeon_10_64_-3`) is recognized there. |
| `UNNAMED` | no display name |
| `NO_DIFFICULTIES` | a dungeon or raid with every difficulty off: nobody can start it (never for arenas) |
| `NO_INSTANCES` | nothing linked: nobody can start it |
| `PENDING_REMOVALS` | instances unlinked during their run, removed once it ends |
| `CONTROLLER_MISSING` | the controller (or its dimension) is gone; `forget` drops the entry |
| `CONTROLLER_NOT_LOADED` | its chunk isn't loaded, so nothing is known about its runs |

With either of the last two, the lists are empty and the counts 0; the other warnings still come
from the index.

### Teleport, admin screen, rename

- **`teleportToController(player, id)`** puts the player beside the controller, facing it, from any
  dimension: the closest spot with two free blocks over solid ground and no liquid, fire, lava,
  magma, powder snow or other harmful block, looking at the 4 sides, then the diagonals, at the
  controller's height and one up or down, then 2 and 3 blocks out, then on top of the controller and
  straight up from it. It is refused while the player is in a run or busy (BusyLib; the reasons
  are named), when the dimension is gone, or when there is no such spot. It may load the chunks
  around the controller.
- **`openAdmin(player, id)`** opens the controller's admin screen from any distance or dimension.
  It stays open until the player closes it, is deopped, or the controller is gone; its edits reach
  that controller, and it is updated when runs start or end.
- **`rename(server, id, name)`** sets the display name as the admin screen would. The name is
  trimmed; blank or longer than 48 characters is refused. The activities listener fires once when
  the name changed, and open lobby and admin screens are updated.

### Ending runs

`forceEnd(server, id, instance)` ends the activity's runs as staff, or only the one at `instance` (the
`InstanceRef` a `RunStatus` gives). A run still going ends as a loss marked as ended by staff: its
players read "Dungeon ended by an admin." (Raid, Arena), get no rewards (arenas still pay for the waves
reached, as on any loss), the boss is removed (a dungeon clears its boss room, as on any loss; the
room being fought keeps its mobs until the players leave), and they are returned after the usual exit
timer. The run listener hears `FORCED`. A run already on its exit timer is closed at once instead,
returning its players and keeping its result. It is refused when nothing is running (at that
instance).

`forget(server, id)` drops an activity whose controller is gone without its block being broken, and
lets go of its chunk. It is refused while the controller is still there: break the block instead.

## Minions

Mobs a run mob spawns by itself become its minions (see the README). They join its team and
scaling, drop nothing, never hold up a room, wave or raid, and vanish when it dies. Arenas_LD
recognizes them by itself in these cases:
- spawned from the summoner's own tick, scheduled events it runs there included;
- spawned from vanilla's `LivingEntity.hurt` while it is being damaged;
- the summoner is their owner (`OwnableEntity`, `TraceableEntity`).

Spawns from a subclass's `hurt` override outside `super.hurt` are not recognized; neither are
spawns from a delayed task or another entity. For those, wrap the spawn:

```java
// In your boss, inside a task that runs later, outside the boss's tick:
ArenasApi.spawnAsMinions(boss, () -> {
    Zombie add = EntityType.ZOMBIE.create(level);
    add.moveTo(x, y, z);
    level.addFreshEntity(add);   // becomes the boss's minion when the boss is a run mob
});
```

Outside a run (the boss is not a run mob) it just runs the code. While the boss is dead or dying,
mobs added for it are not spawned (`addFreshEntity` returns false). Server thread only.

## Stability

The methods, records and interfaces on this page are the stable surface: they won't change or
disappear without a major version, and new features arrive as new methods or types. Enums may gain
constants in a minor version (4.9.0 added `Outcome.FORCED`; `Warning` may grow), so give a `switch`
over them a default. Anything outside `net.ledok.arenas_ld.api` is internal and may change in any
release.
