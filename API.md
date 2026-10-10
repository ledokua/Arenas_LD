# Arenas_LD API

Other mods can list the server's dungeons, raids and mob arenas, open an activity's lobby screen for a
player, veto entry with an **entry gate**, and hear about every finished run. It is what a hub mod
builds on — Eden_LD's Activities tab uses it — and Arenas_LD never depends on who calls it: with no
gate registered and nobody listening, it behaves exactly as without the API.

Arenas_LD is a standalone mod and holds nothing hub-specific. A hub keeps its own per-activity
settings keyed by the activity id — Eden_LD keeps whether an activity is shown, its card art and the
required level per difficulty in its Moderation window — and enforces them through the entry gate.

Everything is in one class, `net.ledok.arenas_ld.api.ArenasApi` (common module), with its records and
interfaces nested in it. All methods are server-side and must be called on the server thread.

## Setup

Compile against the Arenas_LD jar for your loader and keep it optional at runtime.

**Fabric** (`build.gradle`):

```groovy
modCompileOnly files("libs/arenas_ld-fabric-4.6.0.jar")
```

```json
"suggests": { "arenas_ld": ">=4.6.0" }
```

**NeoForge**: `compileOnly` the NeoForge jar, and an optional `[[dependencies.yourmod]]` entry with
`modId = "arenas_ld"`, `type = "optional"`, `versionRange = "[4.6.0,)"`.

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
    result.outcome();        // WIN or LOSS
    result.durationTicks();  // game ticks from run start to the win/loss
    result.wave();           // arenas: last wave reached; 0 otherwise
    result.players();        // every participant, including those who left or died out on hardcore
});
```

Called once per finished run, at the win or loss, after the rewards are paid and the win is counted in
the player stats. Dungeon leaderboards are already updated by then; raid and arena leaderboards (and an
arena's best-wave stat) are written when the run closes, after its close timer. An arena run is a WIN when it reached its maximum wave and a
LOSS otherwise — `wave` tells how far it got. A listener that throws is logged and skipped.

## Stability

The methods, records and interfaces on this page are the stable surface: they won't change or
disappear without a major version, and new features arrive as new methods or types. Anything outside
`net.ledok.arenas_ld.api` is internal and may change in any release.
