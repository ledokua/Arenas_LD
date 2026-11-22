# Arenas_LD API

Other mods can list the server's dungeons, raids and mob arenas, open an activity's lobby screen for a
player, veto entry with an **entry gate** (and label what it needs), hear about every finished run, and give staff the
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
boolean shown = ArenasApi.openLeaderboard(player, ResourceLocation.parse("arenas_ld:crypt")); // 4.43.2
```

`openLeaderboard` opens the same screen on its Leaderboard tab (it stays there until the player picks another tab).
A hub-hosted view (`ArenasClientApi.LobbyHost.open`) gets the activity's name as its title (4.43.2), or the
controller's name when the activity has none.

Opens the same screen as right-clicking the controller — lobbies, my lobby, leaderboard, invites — from
any distance and any dimension; it stays open until the player closes it, and lobby actions from it
reach the right controller. It returns false when the id is unknown or the entry gate refused the
player (they are told why in chat).

### The player's own lobby

```java
Optional<ArenasApi.OwnLobby> mine = ArenasApi.ownLobby(player);
mine.ifPresent(l -> {
    l.activity();    // its activity id
    l.type();        // DUNGEON, RAID or ARENA
    l.name();        // the activity's display name
    l.members();     // of l.maxMembers()
    l.ready();       // members marked ready
    l.owner();       // the player owns it
    l.inRun();       // its run has started
});
boolean opened = ArenasApi.openOwnLobby(player); // that lobby's screen, from anywhere
```

For a "My lobby" button: `ownLobby` is a cheap in-memory read; `openOwnLobby` opens the same remote screen as
`openLobbies` (ready, leave, invite, run modifiers all work from it) and is what `/arenasld lobby open` (no
arguments) does. A player is in one lobby at a time: creating or joining one takes them out of the old one
(since 4.21.0).

## Run details

```java
boolean opened = ArenasApi.openRunDetails(player);                    // the player's own dungeon/raid run
boolean shown  = ArenasApi.openRunDetails(staff, activityId, instance); // staff: that run (instance null: any of it)
```

Opens the live run details screen (players' state, deaths, respawn timer, HP, damage dealt/taken, healing; the run's
modifiers, phase, time, boss HP, rewards). False when there is no such dungeon or raid run (or `run_stats.enabled` is
off). The staff overload also shows the HP nobody was credited for; check staff permission before calling it.

### Finished runs (4.23.0+)

```java
List<ArenasApi.RunHistoryEntry> runs = ArenasApi.runHistory(server, activityId /* or null */, playerUuid /* or null */, 50);
boolean shown = ArenasApi.openRunDetails(staff, runs.get(0).runId()); // final numbers with the staff details
boolean last  = ArenasApi.openLastRunDetails(player);                 // the player's last finished run
```

`RunHistoryEntry(runId, activity, type, name, difficulty, hardcore, modifiers, start, durationSeconds, result,
party, partyUuids, runLevel, playerLevels, gearPower)`, newest first (4.24.0: `runLevel` is the difficulty's loot
table `level_N`, `playerLevels` the `loot_stage` level and `gearPower` the average gear power level of each player at
run start, in party order; -1 when unknown; 4.24.1: `classes` the equipped spell book's item id, or
`skills:<branch>`, or ""); `result` is `HistoryResult.WIN`, `FAIL`, `ABANDONED` or `FORCED`. The history
covers the last 30 days (files under `<world>/arenas_ld/runs/`). `runHistory` reads the in-memory index, cheap to
call; `openRunDetails(staff, runId)` reads the run's file off the server thread and opens the screen when it is in
(false for an unknown id). Check staff permission yourself.

### Combat log sessions: duels (4.42.0+)

`ArenasCombatLog` records fights that are not Arenas runs, so far duels, with the run recorder. Each fighter gets the
same data as in a run: the event log (hits with raw and applied amounts and block reasons, spells through Spell Engine
Expansion, effects, gear swaps, deaths), damage and healing by source, top hits, the best minute, and gear and skills
at the start. Fighters' hits on each other count as dealt damage. Only a real attacker (or their pet, summon or
projectile) or the fighter who applied a harmful effect is credited. Damage with neither, such as falls, lava or an
effect from the console, is nobody's dealt damage; a death from it goes to the last fighter who hit them. A hit that leaves someone at 0 HP counts as a kill
and a death (the duel mod stops the death itself). Others who did 10%+ of the victim's max HP since their last death
get an assist.

```java
if (!ArenasCombatLog.inRun(uuid)) { … }                     // in a dungeon/raid run now?
ArenasCombatLog.Start start = ArenasCombatLog.start(server, "duels:1a2b3c4d", title, List.of(
    new ArenasCombatLog.Fighter(alice, "A", "kits:rental_mage"),   // side label, rental kit id or "" (own gear)
    new ArenasCombatLog.Fighter(bob,   "B", "")));
ArenasCombatLog.Session session = start.session();          // null: start.refusal() says why
session.rejoin(player);                                     // optional: a relog is picked up within a second anyway
session.remove(uuid);                                       // out for good: their row shows "out"
ArenasCombatLog.Result result = session.end(server, "A");   // winning side, or "draw"; null if already ended
session.cancel(server);                                     // ends without storing anything
boolean shown = ArenasCombatLog.openDetails(viewer, result.recordId(), staff);
```

- **`start`** is refused when run stats are off, the list is empty, or a fighter is in a run or another session.
  Recording starts at once: call it when the fight begins, after any rental lend. Fighters are listed side by side
  in the details.
- **`end`** stores the session like a run, with type `"duel"`. It's kept 30 days, and stays out of
  `ArenasApi.runHistory`, `openLastRunDetails` and `/arenasld run last`; Arenas sends no chat line for it. It returns
  `Result(recordId, players)`, one `Summary(uuid, name, side, dealt, taken, healed, blocked, topHit, kills, deaths,
  assists, bestMinute, durationSeconds)` per fighter. `topHit` is the most HP one hit took off (a 1000 hit on 70 HP
  counts 70). `blocked` is what shield, armor, Resistance, protection enchantments and absorption stopped. A hit
  cancelled outright (immunity, a dodge, another mod refusing it) shows in the Timeline as "Hit stopped" but isn't
  counted as blocked. Call it before cleaning up the arena or giving inventories back,
  so those don't end up in the log.
- **The details screen** shows the session's title. The result reads "<names> won" or "Draw", and the winning side's
  fighters show as won, the rest as lost. Its tabs are Party, Damage, Healing and Timeline, with the kind and player
  toggles.
- **`openDetails`** without `staff` opens the record only for its own fighters; with `staff` (check the permission
  yourself) anyone can open it, staff lines included. It returns false for an unknown id.
- **A server stop** cancels the sessions still going.
- **Timeline attackers:** since 4.42.0 the event log names player attackers as `player:<name>`. Before, they showed
  as `minecraft:player`, in runs too.

## Client: hosting the lobby view in another screen (4.35.0+)

Client only: `net.ledok.arenas_ld.api.client.ArenasClientApi`. A hub (Eden_LD) can show Arenas' lobby view inside its own
screen instead of Arenas' lobby screen (LeDok: "only the hub's screens when Eden is there").

```java
ArenasClientApi.setLobbyHost(@Nullable LobbyHost host)
interface LobbyHost { boolean open(ResourceLocation activity, Component name, HostedLobby view); }
interface HostedLobby { CanvasNode node(); void resize(float w, float h); boolean keyPressed(int key, int scan, int mods);
                        boolean charTyped(char c, int mods); void mouseClicked(float canvasX, float canvasY);
                        void close(); boolean closed(); }
```

- **When the host is asked:** the server opens the lobby menu as always (controller block, `/arenasld lobby open`,
  invites, the badge, `ArenasApi.openLobbies` / `joinOpenLobby` / `createLobby`). On the client, Arenas builds the view
  from that menu and calls `open` on the next render-thread task, so the host may switch screens right away.
- **`true`:** the host shows it, and Arenas' own screen never appears. Lobby actions and updates keep going through
  the open menu.
- **`false`, or no host:** Arenas' own screen, as before.
- **The view:** it has no title bar; the host shows `name` and its own back button. It has tabs at the top and Leave
  / Ready / Start at the bottom, and it is one column below 560 wide.
- **Popups** (the rental preset list, the invite suggestions) open on `node().canvas()`.
- **`close()`** closes that view's menu only (idempotent). Call it on back, on a tab switch, and when your screen closes.
- **`closed()`** is true once the menu is gone: the server closed it (run start, kick), a newer lobby opened, or it was
  closed.
- **Dev check without Eden:** `-Darenas_ld.devLobbyHost=true` registers a bare test host.
- **Run start (4.36.1):** on Start the server closes every party member's screen, the hub too (vanilla container close),
  so the countdown title shows.

### Client settings for a hub's settings page (4.36.1+)

```java
List<ArenasClientApi.Setting> ArenasClientApi.settings()
record Setting(String id, Component name, Component description, List<Choice> choices,
               Supplier<String> value, Consumer<String> set)
record Choice(String value, Component label)
```

- **What's there:** Arenas' player-facing client settings, in display order:
  - `run_hud_style`: `new` / `classic`;
  - `loot_reveal_style`: `new` / `classic`;
  - `ally_sidebar`: `left` / `right` / `hidden`;
  - `lobby_badge`: `top_left` / `top_right` / `hidden`.
- **How to use it:** show `name` and the choices with `value()` selected. `set` saves `config/arenas_ld-client.json`
  and the HUDs pick it up right away (the run timer from its next update).
- **Stability:** ids and values stay stable.

## Ally HP sidebar parties (4.34.0+)

`ArenasApi.setPartyProvider(Function<ServerPlayer, Collection<UUID>>)`: who is in a player's party outside dungeon, raid
and arena runs, so the ally HP sidebar shows them too (Eden_LD: its parties). It's called a few times a second per online
player who isn't in a run, so keep it cheap. Return the members (the player may be included); empty when none. Inside a
run the sidebar always shows the run's members.

## Lobby creation and settings for a hub (4.33.0+)

For a hub's lobby browser and «Створити лобі» panel (Eden_LD):

```java
enum LobbyVisibility { PUBLIC, FRIENDS, PRIVATE }
record LobbySettings(String difficulty, boolean hardcore, List<String> modifiers, LobbyVisibility visibility, int minLevel)
void setLevelProvider(@Nullable ToIntFunction<ServerPlayer> levels)
@Nullable Component createLobby(ServerPlayer owner, ResourceLocation activity, LobbySettings settings, boolean openScreen)
@Nullable Component updateLobby(ServerPlayer owner, LobbySettings settings)
Optional<ActivityInfo> activityInfo(MinecraftServer server, ResourceLocation activity)
List<ItemStack> possibleDrops(MinecraftServer server, ResourceLocation activity, @Nullable String difficulty)
```

- **`createLobby`** goes through the controller's usual flow: busy check, entry gate for the tier, one lobby at a time
  (a forming lobby elsewhere is left). The settings are checked before anything is made. If the owner already has a
  forming lobby at that activity, it just gets the new settings.
  - Returns null when done; otherwise the reason. The reason isn't sent to the player, so show it yourself.
  - `difficulty`: "easy" / "normal" / "hard" / "nightmare", or empty for arenas (keeps the current tier).
  - `hardcore` switches the `hardcore_run` modifier.
  - Unknown modifiers, and pairs that conflict or share an exclusive group, are refused.
- **`updateLobby`** changes the lobby the player owns, anywhere. The owner may change settings even with members in the
  lobby.
  - The members get a line naming the change.
  - A change to the run (difficulty, hardcore, modifiers) un-readies everyone but the owner.
- **Minimum join level:**
  - stored on the lobby (`OpenLobby.minLevel()`, 0 = none);
  - checked on join and on asking to join (including `joinOpenLobby` and the in-world screens), but not on an
    owner's invite;
  - checked only while a level provider is set; without one it's ignored.
- **`lobbySettings(owner)`** (4.33.1+): returns `Optional<LobbySettings>` for the forming lobby the player owns, private
  ones included, to prefill an edit form. It's empty when they own none or the run has started. `difficulty` is ""
  for arenas.
- **`ActivityInfo`:**
  - activity, type and name;
  - the enabled tiers' ids (empty for arenas);
  - `ModifierInfo(id, name, description, rewards, icon, conflicts)`: `rewards` is the reward effect text, e.g.
    "+2 loot · +25% XP";
  - `maxMembers`;
  - `levelsActive`: whether a level provider is set.
- **`possibleDropsCount(server, activity, difficulty)`** (4.34.4+): how many distinct items `possibleDrops` would list
  without its 64 cap, for a "+N" counter.
- **`possibleDrops`:** the items a win can give, from the tier's reward loot tables.
  - Arenas: every instance spawner's reward tables. A null or empty difficulty means all tiers.
  - Includes nested tables, item tags and alternatives/groups. No rolls; at most 64 items.
  - Copies, cached until the next data reload.

## Open lobbies, server-wide (4.31.0+)

For a hub overview of every lobby forming now (dungeons, raids, arenas):

```java
List<ArenasApi.OpenLobby> ArenasApi.listOpenLobbies(MinecraftServer server);
long ArenasApi.openLobbiesRevision();
void ArenasApi.addOpenLobbiesListener(Runnable listener);
@Nullable Component ArenasApi.joinOpenLobby(ServerPlayer player, ResourceLocation activity, UUID lobbyId);
```

- **`listOpenLobbies`**: every PUBLIC and FRIENDS lobby that isn't in a run (forming or queued), across every loaded
  controller, sorted by activity id then age. Private lobbies are never listed. It reads memory only.
  - `OpenLobby(activity, type, name, lobbyId, owner, ownerName, members, maxMembers, ready, difficulty, hardcore,
    modifiers, access, createdAtTick, minLevel)` has a `full()` helper. `minLevel` arrived in 4.33.0.
  - `access` is `JOIN` (public) or `REQUEST` (friends-only: anyone may ask and the owner accepts). Arenas doesn't know
    friends, so a hub that shows REQUEST lobbies only to the owner's friends filters them itself.
- **`openLobbiesRevision` / `addOpenLobbiesListener`**: the list is checked once a second while at least one listener
  is registered. When it changes, the revision goes up and the listeners run on the server thread. The first check
  only records a baseline.
- **`joinOpenLobby`**: joins a JOIN lobby, or asks to join a REQUEST one, from anywhere, through the controller's usual
  flow:
  - the busy check, the entry gate (the lobby's tier), and one lobby at a time (a forming lobby elsewhere is left);
  - a full lobby is refused;
  - then that activity's lobby screen opens. Already a member: it only opens the screen.

  It returns null when the join went through; otherwise the reason, which the player is told as well.

## Invite filter (4.28.0+)

`ArenasApi.setInviteFilter(InviteFilter)` (null removes it; one filter, the last call wins) decides who may invite a
player to a raid, dungeon or arena lobby, e.g. a privacy setting or an ignore list (Eden_LD).

```java
@Nullable Component refuse(ServerPlayer inviter, UUID invitee, String activityId);
```

- Asked at the start of every invite (raid, dungeon, arena), on the server thread, after the lobby checks and before
  the invite exists or the invitee is notified.
- Null lets the invite through. A line refuses it: there is no invite, the inviter gets the line as is, and the invitee
  hears nothing.
- `activityId` is the activity's id as in `status` (`""` for a controller without one). The invitee may be offline.
- A filter that throws is logged and the invite goes through.

## Rental builds (4.26.0+)

Arenas lends preset class loadouts in dungeon and raid runs through a provider another mod registers (Eden_LD does,
with its stash and Kits_LD hidden kits). Without one, the lobby shows no rental option.

```java
ArenasApi.setRentalProvider(new ArenasApi.RentalProvider() {
    public List<String> presets(int level) { ... }                 // kit ids the player can rent (see below); empty hides the option
    public String displayName(String kitId) { ... }                // plain text: the kit's name, else the preset id
    public boolean begin(ServerPlayer p, String sessionId, String kitId) { ... } // false: nothing changed
    public void end(MinecraftServer server, UUID player) { ... }   // also for offline players
    public void reequip(ServerPlayer p) { ... }                    // after a respawn in the run
    public boolean isRenting(ServerPlayer p) { ... }
    public List<ItemStack> preview(String kitId) { ... }           // the kit's items (copies) for the lobby preview
});
```

Kit ids are `<ns>:rental/<level>/<preset>`. `presets(level)` gives, for each class (the preset name up to the first
"_"), all its presets in the highest level folder at or below the player's `loot_stage` level that has that class
(4.26.1). Arenas reads the level (the folder) and the class from the id.

Server thread only. Arenas calls `begin` at the run's first stats tick (session `arenas:<dim>@<x,y,z>@<start tick>`),
again after a restart when `isRenting` is false (a few ticks after the login), `reequip` after each respawn in the run,
and `end` within a second once the player is no longer in the run. The renter's rewards are halved
("Rental ×0.5"). Run records and `RunHistoryEntry.loadouts` name the loadout.

Since 4.40.0 Arenas asks `presets(level)` with the player's level capped at the run level of the lobby's difficulty,
and after `begin`/`reequip` it may change the `spell_engine:spell_container` `spell_ids` of the lent spell book to the
player's pick (same pool). A provider needs no change for either.

### Run settings: rental only, base loot rolls (4.39.0+)

```java
Optional<String> mode = ArenasApi.rentalMode(server, activityId);           // "optional" | "required"; empty: unknown id or an arena
Component refusal = ArenasApi.setRentalMode(server, activityId, "required", actor); // null = done
Optional<Double> rolls = ArenasApi.baseLootRolls(server, activityId);       // -1 = the global completion_loot_rolls
Component refusal2 = ArenasApi.setBaseLootRolls(server, activityId, 9, actor);      // below 0 = back to the global
```

`required`: every lobby member must pick a rental kit before getting ready or starting, own gear is refused, and the
run pays renters in full (no "Rental ×0.5"). The run keeps the mode it started with. Run records carry `rentalMode`
and `baseLootRolls`. A hub's own lobby list can read `rentalMode` to label such runs.

## Breakable blocks (4.23.0+)

```java
Optional<BreakableBlocks> blocks = ArenasApi.breakableBlocks(server, activityId); // empty: unknown id or an arena
Component refusal = ArenasApi.setBreakableBlocks(server, activityId,
    new BreakableBlocks(BreakableBlocks.REPLACE, List.of("bosses_of_mass_destruction:vine_wall", "#minecraft:leaves")),
    staff.getGameProfile().getName());                                           // null = done
```

A dungeon or raid controller's own list (`net.ledok.arenas_ld.util.BreakableBlocks`: `mode` `"add"` to the global
`run_breakable_blocks` or `"replace"` it, and block ids / `#tags`). Refused for an unknown id, an arena, or an
unknown block id. Runs use the lists only in difficulties whose tier has `allowBlockBreaking` on (off by default);
players get the change within a second.

## Resetting instance cooldowns

```java
Component refusal = ArenasApi.resetCooldowns(server, id, instance /* or null: all */, staff.getScoreboardName());
```

Makes an instance on cooldown free at the next tick (the next queued lobby can start). Null when done; otherwise
why not (unknown id, nothing on cooldown there). Logged with `actor`. Check staff permission first, as for
`forceEnd`. `/arenasld activity resetcooldown <id> [pos [dimension]]` does the same.

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

### Showing the requirement when it is met (4.16.0+)

A gate only speaks when it refuses, so players who pass it never see what the activity needs. They then
can't tell why a friend can't be invited. Register a requirement label next to the gate:

```java
ArenasApi.registerEntryRequirement((player, activity, difficulty) -> {
    int needed = mySettings.requiredLevel(activity, difficulty);
    return needed <= 0 ? null : Component.literal("Lv " + needed + "+");
});
```

The lobby screens show it in green: under each difficulty the player may pick (dungeons, raids), and in the
header of an arena lobby (asked with `difficulty` null). A difficulty the gate refuses still shows the
gate's refusal, as before. Return `null` for nothing to show. Several may register; the first non-null
label wins, and one that throws is logged and skipped. It is asked as often as the gate, so keep it cheap.

## Reward modifiers (4.18.0+)

Add run-wide reward modifiers, e.g. a server event:

```java
ArenasApi.registerRewardModifierSource((activity, difficulty, mode) ->
    weekendEvent.active()
        ? List.of(RewardModifier.increased(RewardModifier.Stat.XP, 0.25, "myhub:weekend", "XP Weekend"))
        : List.of());
```

It is asked at every payout (dungeon completions and rooms, raid wins, arena waves and summary) and stacks with
the lobby's run modifiers (hardcore's `hardcore_run`: loot and currency more ×2, XP +25%), the config's run modifiers and each player's LuckPerms bonuses:
`final = base × (1 + Σ increased) × Π more`. `difficulty` is null for arenas. `name` is literal text or a
translation key; players see it in their reward breakdown. Keep it cheap; one that throws is logged and skipped.

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


## Player relations (4.40.3+)

`ArenasApi.setPlayerRelations(PlayerRelations)`: one provider; the last call wins; null removes it. The lobby's invite
picker calls `relation(ServerPlayer viewer, UUID other)` once per online player each time it opens, and groups the
players by the answer:
- `FRIEND` comes first;
- `PARTY` and `GUILD` come next;
- `NONE` comes last.

Return the closest relation when several apply. A provider that throws is logged once and dropped. Without a provider,
everyone is `NONE`. The picker also asks the invite filter (`setInviteFilter`) for each player, without telling anyone,
so blocked players show as "Doesn't accept invites from you".


## Loot rolls (4.40.4+)

- `ArenasApi.completionLootRolls(server)`: the global `completion_loot_rolls`.
- `ArenasApi.effectiveLootRolls(server, id)`: what a controller's completion starts from, its own rolls or else the
  global. It's empty for an unknown id or an arena.
- `baseLootRolls` is unchanged: -1 when the controller uses the global value.
