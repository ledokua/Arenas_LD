# Changelog

## 4.45.0

**Client and server update.** Install matching builds on both sides because the run HUD, arena
settings, and lobby kick packets changed.

### Arenas

- Players enter and revive in **Adventure** by default. Enable **Survival mode** on the Arena
  Spawner for builds that need vanilla placing and breaking. Enable **Allow configured block
  breaking** to use the same global and controller block lists as dungeons and raids.
- The lobby's chunk-loading countdown is followed by **Arrival grace**, which defaults to three
  seconds and accepts zero. During intermission, every standing player can click **Ready for next
  wave** in chat or use `/arenasld nextwave` to continue early.
- Zero arrival grace starts wave one, and zero intermission advances immediately. A cleared wave
  pays loot and applies its heal and repair bonus once. A wave that cannot spawn enemies ends the
  run without awarding progress or loot for that wave.
- New arena reward rows default to **Each**. Select **Shared** for loot scattered on the arena
  floor. Loose items now follow normal despawn rules between waves.
- New mob rows cover all later waves by default. Arena waves cap their mob count at 64 and build
  the weighted selection once per wave.

### Dungeons, raids, and rentals

- Controllers respect each other's instance reservations. Preparations and runs track the
  spawner's dimension and position, so copies at identical coordinates in different dimensions
  no longer interfere with each other.
- Raid cleanup only discards stray bosses marked as belonging to that raid instance. Nearby
  mobs from other runs stay in play.
- **Rental kits only** waits for every rental to equip before launch. If a handoff fails, the
  lobby can retry and any kits already handed out are returned. Rentals work with combat stats
  disabled. Payout modifiers and loot statistics use the run that awarded the loot.
- **Reach the zone** rooms can activate without linked mobs. The client draws the goal outline,
  replacing the server's per-particle updates. Restored dungeons register their mobs once.

### Builder tools and records

- The instance checker reports shared links, invalid arena waves, missing room respawn points
  or goal-zone corners, and unreachable Final rooms.
- Operators can open **Test Run** from the checker and choose a room or wave to play solo.
  Test runs skip Arenas_LD rewards, lifetime stats, and rankings.
- `/arenasld run last` continues to return the last PvE run after a server restart. New run
  records use unique IDs to prevent filename collisions.
- Lobby kick notices match the activity and lobby, so a controller at the same coordinates in
  another dimension keeps its hub view open. A controller without an activity ID shows the notice in
  the action bar.
- Review follow-ups: stray raid bosses left by runs from before the ownership tag are swept too; a
  "Rental kits only" player is returned from the run only after every re-equip retry fails; an arena
  mob with no clear spawn spot spawns at the arena center again instead of ending the run.

## 4.44.0

LeDok's room-building requests (2026-11-20). **Client and server** (new packets, screen and overlay changes).
- **New room objective: Reach the zone.** Waves spawn every *Wave every (s)* seconds, looping, with no time
  limit, until *Required players %* of the active party (online, not downed) stand in the goal zone at once.
  The room then clears and its remaining mobs are discarded. The zone is a box set with the Dungeon Tool's
  new **Goal zone** mode (first click one corner, then the opposite one) and cleared from the Room
  Controller screen. Players see its edges as green particles; the HUD shows *Reach the zone: x/y ·
  enemies n*. Without a full zone the room plays as Kill everything (with a warning in the log).
- **Mob cap** for Survive and Reach the zone rooms (a field shown only for those two; 0 = none, at most 500):
  a wave spawns only as many mobs as fit under it. Boss spawners always spawn.
- **Outlines stay when you walk away from the selected block.** The Dungeon Tool overlay keeps the last data
  it saw for the selection, its spawners and its doors after their chunks leave your view distance, and a
  tool click on an out-of-range block sends you its new data (`ToolSourceSyncPayload`), so doors, spawn and
  respawn positions placed far from the room controller still show and update.
- The Room Controller's objective buttons wrap onto a second row when they don't fit.
- **Run stats: FloraFare's natural regen no longer counts as healing.** With FloraFare's `regenMode "curve"`
  (the live server's) the hunger heal runs in `NaturalRegeneration#curveHeal` instead of `FoodData#tick`, so it
  slipped past the natural-regen exclusion. A Fabric compat mixin marks it too (found by Eden_LD).

## 4.43.2

For Eden_LD 1.3.126 (LeDok on TEST, 2026-11-19: the create panel's «Таблиця лідерів» opened what looked like the
controller screen). **Client and server.**
- **`ArenasApi.openLeaderboard(player, activity)`:** as `openLobbies`, but it opens on the Leaderboard tab (hosted in
  the hub or on Arenas' own screen). The tab stays until the player picks another, even when they are in a lobby.
- **A hub-hosted lobby view is titled with the activity's name** («Забуті печери»), not the controller block's name;
  an activity without a name keeps the block's name. The opening packet carries the name and the tab.

## 4.43.1

LeDok's follow-up on the hub pass (2026-11-19). **Client and server** (a new packet).
- **Kicked from a lobby: back to the hub's lobby list with a notice.** The server tells the kicked player
  (`LobbyRemovedPayload`). A hub-hosted view on that controller closes as after «Leave», and a toast says
  "You were removed from the lobby." On Arenas' own lobby screen it's the toast only; elsewhere, the action bar.
  This replaces the raid and arena kick chat line, whose key was missing (it showed the raw key).
- There is no "owner disbands" case to cover: in dungeons, raids and arenas an owner who leaves hands ownership to
  another member, and a lobby only dissolves when nobody else is in it (or everyone has been offline past the
  timeout). The packet still carries a "disbanded" reason, with its text, should one be added.
- A run start never sends the notice: the hub closes as before.

## 4.43.0

Hub layout pass with Eden_LD (LeDok 2026-11-19, Issues/2026-11-19-hub-ui). **Client** (and server: same jar).
- **Leaving a lobby in the hub goes back to the hub's lobby list** («Покинути», and a raid's «leave queue»): the
  hosted view closes itself from the client and reports `closed()`. It no longer stays on "You're not in a lobby".
  Arenas' own lobby screen (no hub) is unchanged.
- **The rental build box never draws buttons over text.** «Change» and «Use my own gear» sit beside the title only
  when everything fits; otherwise they go under it, one row each if needed (GUI scale 3, narrow window).
- **The rental box's text wraps** instead of being cut with "…" (the hint, the reward cut line, your rental).

## 4.42.2

TEST duels (2026-11-19). **Server only.**
- **Every duel fighter's stats are taken at the start** (max health, damage reduction and the rest), also for a
  fighter who never lands a hit. Runs take them at the first hit, so a 3 s duel had none for the side that dealt 0.
  The gear and skills snapshot is also taken at once instead of at the next full second.

## 4.42.1

Duels_LD's dev test of 4.42.0 (2026-11-18). **Server and client** (the Rewards tab); duels only otherwise.
- **A duel credits only real attackers.** Damage with no attacker or effect applier (falls, lava, an effect given
  from the console) used to go to the last fighter who hit; it is now nobody's dealt damage. Kills still go to that
  fighter (knocked into lava).
- **A duel's top hits are the HP they took off,** not the raw amount (a 1000 hit on 70 HP counts 70).
- **The Rewards tab is hidden when there are no rewards** (duels).
- **Duel staff line** without the boss and loot parts.

## 4.42.0

LeDok, 2026-11-18 (option D, via the Duels_LD session): the duel combat log. **Server and client:** the pack needs it
for the details screen title fix; the API is server side.
- **New `ArenasCombatLog` API** (API.md, "Combat log sessions"): Duels records a match with the run recorder and opens
  the result in the run details screen (Party, Damage, Healing, Timeline with the 4.41.0 toggles). Duels uses it only
  when Arenas is installed, and Arenas doesn't depend on Duels.
- **Duel hits count:** a fighter's hit on another fighter (pets and summons, and damage over time from their harmful
  effects, included) is dealt damage, with top hits and the best minute. A hit to 0 HP is a kill and a death, with
  assists at 10%+ of max HP.
- **Stored like a run**, as type "duel": kept 30 days; left out of the run history, `/arenasld run last` and the
  run-end chat line. Arenas' own run lookups (rentals, `/exit`, live details) skip duel sessions.
- **The event log names player attackers** as `player:<name>`, in runs too; it used to say `minecraft:player`. Top
  hits on players name the player as well.
- **Details screen:** no " — " after the title when there is no difficulty. A duel's result is "<names> won" or
  "Draw".
- Staff line: a rental kit without a class shows its kit id.
- New game test: a duel session's hits, kill, record and cancel (52 tests); 4.42.1 adds environment damage.

## 4.41.1

LeDok's TEST run, 2026-11-18, via MCP eden. **Server fix;** the jar also carries the 4.41.0 client changes.
- **Fix: a picked rental could be dropped at a dungeon run's start, so the player ran with their own gear.** Starting a
  dungeon run removes its lobby at once, but the run's stats only appear at the controller's next tick. A once-a-second
  check that landed in between saw the player in neither a lobby nor a run and dropped the pick. A pick now survives 5
  seconds without its lobby before it goes.
- **Logs:** a WARN when a pick can't be lent at the start (the kit isn't offered at the run's level, or the rental
  couldn't begin), and an INFO when a pick is dropped because its player left the lobby.

## 4.41.0

LeDok, 2026-11-18, via MCP eden: run details «Timeline». **Client only:** the pack and Client TEST need it. The packets
are unchanged, so old clients and old servers keep working with it.
- **One timeline for the whole party.** The tab loads every player's log and merges them in time order. In a party run
  each line names its player.
- **Toggles instead of tabs.** The event kinds (Hits, Casts, Procs, Effects, Swaps, Downs) are on/off toggles, several at
  once. With all on, a click shows that kind alone; after that each click adds or removes one. "All" turns them all back
  on, and so does switching the last one off.
- **Player toggles** in a second row, the same way, with "Everyone" first (party runs only).
- The counter says "N of M events" while a kind is off.
- Layout: each toggle row has a fixed height and the counter sits below them with its own space, so the rows, the counter
  and "Show earlier (N)" can't overlap.

## 4.40.8

Live bug (LeDok, 2026-11-18, via MCP eden). **Server only.**
- **Fix: the invite picker stayed on "Loading…" in a lobby opened from the Eden hub.** The server looked for the
  controller in the player's own dimension. It now uses the lobby screen's bound controller, as every other lobby
  packet does.
- The server now always answers. If there's no lobby to invite to, the picker gets an empty list instead of an endless
  "Loading…".

## 4.40.7

LeDok, 2026-11-18, via MCP eden. **Client change:** the pack and Client TEST need it; the server takes it too.
- **The kit's spell book in the preview's item row now holds the current pick.** Its tooltip lists exactly the picked
  spells and follows every tier click.
  - When the preview opens again after Pick/Save, it starts from the stored pick.
  - The kit files are unchanged: their own spell_ids stay as the fallback book.

## 4.40.6

TEST fix (LeDok, 2026-11-18, via MCP eden). Server only; the client can stay on 4.40.5.
- **Fix: the picked spells weren't in the lent book.** Spell Engine caches each player's spell sources. In the tick
  the kit was handed out, the cache still held the player's previous book. When that book had the same pool (their own
  book, just put aside), the pick went into it and counted as done.
  - The spells now go in a second later.
  - Spell Engine's sources are rebuilt before the book is looked up and after it's changed, and the result is read
    back. A failed read-back retries.
- **Logs:** INFO `[Arenas] Rental: <player>'s <pool> book [old] -> [new]`, or "already holds", or "has no spell pick".

## 4.40.5

The GUI/UX overhaul (`docs/ui-rework/gui-ux-overhaul.md`). **Needs the client update** (screens only; the packets
are the same as 4.40.4).
- **Lobbies (standalone and hub-hosted):** tabs wrap; titles and status stay in their rows; instance states are
  translated.
- **Rental dialog:** sizes to the screen, wraps kit items, has an empty state and keyboard focus. The one-spell-per-tier
  rows stay.
- **Run Details / Timeline:** title and stats in separate rows; controls wrap; focus survives refresh; a missed
  Timeline reply offers a retry.
- **HUDs:** respond to GUI size.
- **Admin and configuration editors:** keep their width in a scrollable body with Tab navigation; ID suggestions open
  and close by keyboard.
- **Run settings:** Enter applies the rolls, and invalid values are explained.

## 4.40.4

Spell picker round 3 (LeDok, 2026-11-18, via MCP eden). **Needs the client update:** the preview packet changed.
- **One spell per tier ("3 pairs"):**
  - The picker shows only the tiers in the new config `rental_spell_tiers` (default `[2, 3, 4]`), one row each («Тир 1/2/3»), picked like radio buttons.
  - Other tiers are hidden and refused, which removes the lne_* tier-5 spells.
  - The server keeps exactly one spell per tier, at the pick and again at the run start. An empty tier gets the kit's own spell of that tier, else the row's first.
  - `rental_spell_max_tier` still drops rows above its cap. An empty `rental_spell_tiers` brings back the free choice.
- **Performance:** spell slots no longer poll anything per frame; they rebuild only on a click. Icon textures are checked once. A new preview closes the old one instead of stacking modals.
- **API:** `ArenasApi.completionLootRolls(server)` and `effectiveLootRolls(server, id)` (for Eden's hub).

## 4.40.3

Lobby UI (LeDok, 2026-11-18, via MCP eden). **Needs the client update:** new packets and the lobby screen.
- **No "Lobbies" tab:** the tabs are My lobby / Leaderboard / Invites. Without a lobby, the screen opens on My lobby
  with "Create lobby" in the footer. The Eden hub lists the open lobbies.
- **The invite picker:**
  - "+ Invite a player" opens a picker with a search and the online players: heads, names, state, and an Invite
    button each.
  - Groups: friends, then party & guild, then everyone else.
  - In your lobby, invited, busy, in a run, or not accepting your invites: the row is disabled and says why.
  - The name field, its dropdown and the INVITE button are gone.
- **API:** `ArenasApi.setPlayerRelations` (friend / party / guild, for sorting the picker), `PlayerRelation`, and
  `ActivityHost.pendingInvites()`.

## 4.40.2

TEST feedback on 4.40.1 (LeDok, 2026-11-18, via MCP eden). **Needs the client update** (the preview screen only; the
packets are unchanged).
- **The spell picker looks like the spell binding table:**
  - Each pool spell is an icon slot, the same size as the kit's item slots.
  - A spell in the book has an accent frame; the others are dimmed.
  - Hovering shows Spell Engine's own spell tooltip (name, description with values, cast, range, cooldown). Without it,
    the name and description.
  - The counter reads "Spells in the book: 2 / 3". When the book is full, a click on another spell does nothing and the
    tooltip says to take one out first.
- **Preview header:** it shows the kits' level ("level 5"). Before, it showed the cap itself, the lower of the player
  and run level (e.g. 6).

## 4.40.1

TEST feedback on 4.40.0 (LeDok, 2026-11-18, via MCP eden). **Needs the client update** (lobby + end screen).
- **Fix: the spell picker never showed.** Fallen Eden's kit books have no `max_spell_count` (Spell Engine reads it as
  0). Such a book now holds as many spells as its `spell_ids`. The book itself isn't changed. A game test covers it.
- **Base loot rolls and "Rental kits only" are shown in the own lobby**, above Run modifiers. The hub-hosted lobby has
  no title bar, so it didn't show them before.
- **End screen:** the Rewards tab starts with "Base loot rolls: 9". The per-player breakdown already had
  "Loot: base 9 · Rental kit ×0.5 → 4.5 (rolled 4)". The rental source is now translated («Орендований набір»).
- **Unit tests:** updated to the current code (they had stopped compiling).

## 4.40.0

**Needs the client update.** Rental round 2 (LeDok, 2026-11-17, via MCP eden), plus a 4.39.0 fix:
- **Fix: `/arenasld activity settings` was nested under `breakable`.** It's its own branch now; a game test parses it.
- **Kit level cap:**
  - **The rule:** the kit's level folder ≤ min(player level, run level of the lobby's difficulty). The run start
    checks the same cap.
  - **Difficulty change:** a pick that's no longer allowed is dropped («… оберіть орендований набір знову») and the
    preview opens again.
- **Recommended presets:** presets of the player's skill-tree class come first, marked «★ Рекомендовано».
  - **Config:** `rental_class_presets` maps a skill class to a preset class where the names differ.
- **Spell choice for the rental spell book:**
  - **In the preview:** spell toggles from the book's pool, up to its size.
  - **Prefilled from:** the last pick for that preset, else the player's own book (same pool), else the kit's.
  - **In the run:** the lent book gets the pick at the run start, after each respawn and after a rejoin. If the book
    isn't equipped yet, it's retried every second.
  - **Config:** `rental_spell_max_tier` limits spells by kit level (empty = the whole pool).
  - **Saved:** picks are kept per player and preset; run records have `rentalSpells` / `rentalPool`.
  - **Spell Engine:** read by reflection (no new dependency); no Eden_LD change.

## 4.39.0

**Needs the client update** (lobby, admin screen and network changes). Two per-controller run settings for dungeons
and raids (LeDok, 2026-11-17, via MCP eden). Both live in admin screen → General → Run settings, in
`/arenasld activity settings <id> …` and in `ArenasApi`, and are saved with the controller.
- **Rental kits only** (`rental optional|required`):
  - **Required:** every member must pick a rental kit. The lobby's rental row has no On/Off and opens the preview by
    itself. Own gear is refused, and getting ready and the start are refused until everyone has picked.
  - **Label:** the lobby header says «Лише орендовані набори».
  - **Rewards:** full (no ×0.5 rental cut; the cut stays for optional rental).
  - **Run:** keeps the mode it started with; the run record has `rentalMode`.
- **Base loot rolls** (`loot_rolls <n>|default`):
  - **Default:** empty = the global `completion_loot_rolls`.
  - **Where it's used:** the completion payout, the lobby header ("Base loot rolls: 9", only when set), the run
    details and the run record (`baseLootRolls`).
  - **Modifiers:** multiply on top as before.

## 4.38.0

**Server only** (the lobby texts are in the jar's lang, so clients see the new wording after their update; the
numbers come from the server either way). LeDok's decision via MCP eden, 2026-11-17:
- **Run modifiers add their bonuses instead of multiplying:** factor = `1 + Σ (value − 1)`, never below 0. The
  difficulty tier still multiplies once on top. Glass cannon (`4`) + Bloody mobs (`2`) now take ×5 (×7.5 on a ×1.5
  tier) instead of ×8 (×12). The same rule applies to `damage_dealt`, `mob_health`, `mob_speed` and `player_speed`.
  Config values are unchanged (`4` still means +300%).
- **Descriptions** say percentages: "+300% damage", "+100% health", "50% faster" (en_us and uk_ua).

## 4.37.1

**Needs the client update.** Includes 4.37.0. From the first TEST runs (MCP eden):
- **`hit_taken` gets two columns at the end** (older positions unchanged):
  - **What they are:** `applied` is what really came off health (read where `Player.actuallyHurt` sets it, so other
    mods' reductions such as Eden's damage reduction are in it). `other` = after Resistance/enchantments − absorbed −
    applied.
  - **Example:** the Wither row "raw 77, after magic 77, HP 46.8 → 23.7" now says applied 23.1 and other 53.9.
- **`saved` fixed:** it now means "what came off health was at least their HP, yet they lived". It used to be judged
  from the vanilla steps alone, which missed other mods' reductions, so that hit was wrongly marked saved.
- **Skill-tree passives:** the container shows as `skill_tree` instead of Spell Engine's UUID.
- **README:** explains the hit columns, the flags, and that `hit_ignored` / `effect_refresh` are counts only.

## 4.37.0

**Needs the client update.** Includes 4.36.3. Run record version 2.
- **Run event log** (LeDok 2026-11-17, with Spell Engine Expansion 0.3.7). What run players do and what happens to them,
  as events at the exact tick, instead of once-a-second snapshots:
  - **From Arenas' own hooks:**
    - hits taken: attacker, damage type, raw → after armor → after Resistance/enchantments, absorbed, HP before →
      after, the passives that fired inside the hit, and a lethal/saved flag;
    - hits that didn't land: dodge, shield or cancelled; a hit inside the half-second invulnerability is only counted;
    - effects added and removed; a refresh is only counted;
    - downed, back up, death;
    - swaps: hand, armor and Trinkets slots.
  - **From SEE's `SpellActivityEvents`** (read by reflection; SEE's jar is built with a newer Loom): casts, channels,
    passives with their source container, dodges.
  - **Storage:** `RunRecord.Player.events`, at most 5,000, plus `eventCounts` and `eventsDropped`. The `timeline`
    snapshot is every 30 s only, attributes and hands.
  - **Code:** `stats/RunEvents`, `ServerPlayerRunEventsMixin` (the hit window around `ServerPlayer.hurt`) and hooks for
    `onEffectRemoved`, the equipment check, `die`, and downed/released.
  - **Run details: a Timeline tab.**
    - **What it shows:** per player, with filters (hits, casts, procs, effects, swaps, downs).
    - **How it loads:** paged from the server (`RunTimelinePayload`), with "show earlier" in steps of 300. A going
      run adds new events every 2 s.
    - **Finished runs:** opened from [Details] or the staff history, the tab reads the saved record.
- **Raid regeneration per player** (LeDok).
  - **The new field:** the tier field `regenerationPerPlayer` = HP per second for each player still in the run, healed
    every 5 s.
  - **The old field:** the flat `regeneration` (HP per 5 s) is no longer read, so those controllers regenerate nothing
    until it is set.
  - **Admin screen:** "BOSS REGEN / PLAYER" (HP/s, steps of 0.5).

## 4.36.3

Includes 4.36.2.
- **Raid tier damage applied once** (LeDok: "fix the bug").
  - **The bug:** the boss's `attack_damage` was set to base × the tier's `damageMultiplier`, and every hit on a raid
    player is multiplied by that tier again, so raids hit × tier²: easy ×1.44, normal ×2.25, hard ×4, nightmare ×9.
  - **Now:** the boss keeps its configured attack damage, and only the hit on the player is × tier.
  - **Dungeons:** they already applied the tier once (bosses and mobs spawn with damage ×1); no change.

## 4.36.2

**Needs the client update.** Includes 4.36.1.
- **Class icon = the player's own spell book.** The pack has one book item (`spell_engine:spell_book`) and takes each
  class's look and name from its components.
  - **Saved with the run:** the book stack itself, as SNBT with its components (`RunRecord.Gear.playerClassStack`).
  - **On screen:** the run details send it (`RunDetailsPayload.Row.classIcon`) and draw it, with the book's own name
    as the tooltip.
  - **Older records:** runs saved before this build fall back to the item id.

## 4.36.1

**Needs the client update.** Includes 4.36.0. LeDok's feedback from a real raid on TEST:
- **Heads:** a skin's hat layer drew beside the face, over the name (a dark square). It's on the face now, in the run
  details, the lobby and the ally sidebar.
- **Class icon:** only a real spell book shows one. A skill-tree class no longer shows a vanilla book, and its name
  is in the row tooltip. The icon has no item tooltip.
- **Start closes every screen:** every party member's screens close on Start, the Eden hub and a hosted lobby too.
  - The same happens when players are moved into a dungeon, raid or arena run, and when they go home.
  - What was on the cursor or in a crafting grid, anvil or other input slot goes back into the inventory.
  - If the inventory is full, it goes to the economy inbox with a chat line, not the ground (`util/RunPlayerSafety`,
    `InventoryDropMixin`).
- **Old looks back as client settings:** `run_hud_style` and `loot_reveal_style` ("new" / "classic").
  - "Classic" is the small centered timer and the reveal without a panel, as before 4.34.0.
  - `ArenasClientApi.settings()` lists these and the sidebar/badge settings for a hub's settings page.
- **No auto-opened end screen.** The chat line's [Details] opens it, and a finished run you got rewards from opens on
  Rewards.
- **Rewards tab:**
  - each player's totals are right under their name (they were on the far right);
  - "how it was counted" is always listed, even when no modifier changed anything;
  - a payout of nothing says so.
- **Timer HUD:** the panel widens (up to 480) so the objective fits ("Returning home — /exit to leave now" was cut).

## 4.36.0

**Needs the client update.** Includes 4.35.0. UI rework step 5.

**Run details screen, reworked** (LeDok's D1/D3/D5):
- **Look:** 1:1 text and the hub theme (parchment without Eden). The shell fills the window up to 720×420, and below
  560 GUI px it drops columns instead of shrinking.
- **Title bar:** activity — tier, Hardcore and result pills, phase, time and boss HP. The modifiers line sits under it,
  with the highlights beside the tabs.
- **Tabs:**
  - Party: head, class icon, level, state word, HP bar, dealt, taken, healed and deaths, with the rest in the row
    tooltip;
  - Damage: share bar, blocked, kills, boss phases and the staff lines;
  - Healing: share, on others, on self;
  - Rewards (below).
- **Rewards tab (D5 = B: the top loot reveal stays):**
  - a dungeon or raid win's per-player breakdown now goes there instead of the gray chat line: the totals ("6 loot
    rolls · 840 currency · 1,200 XP") and how they were counted;
  - the end-of-run screen opens on it;
  - the arena summary, and runs without run stats, still use chat.
- **Data:**
  - `RunRecord.Player.rewards` (JSON text lines) is saved with the run;
  - `RunDetailsPayload.Row` gets uuid, playerClass, level, healedOthers and rewards;
  - `RunStats.recordRewards` and `RewardPayout.summary` are new.
- **Dev check:** `-Darenas_ld.devRunDetails=live|end` opens the screen with sample numbers.

## 4.35.0

**Needs the client update.** Includes 4.34.x.

**The lobby can live inside the Eden hub** (LeDok: "only the hub's screens when Eden is there"; built with Eden_LD):
- **New client API:** `ArenasClientApi.setLobbyHost`. When a host is registered, every lobby opening (controller
  block, `/arenasld lobby open`, invites, the badge, joining or creating from the hub) hands the lobby view to the host
  instead of opening Arenas' screen. Without a host, or when it declines, Arenas' own screen opens as before.
- **What stays the same:** the server side doesn't change. The lobby menu stays open while the view is hosted, so every
  action and update works as usual.
- **Hosted view:** it has no title bar (the hub shows the name and ← Back). Popups open on the hub's canvas, and
  closing the view closes only its own menu.
- **`LobbyOpeningPayload`:** tells the client the lobby's activity right before the menu opens.
- **Dev check:** `-Darenas_ld.devLobbyHost=true` hosts the view in a bare test screen.
- `possibleDrops` keeps its diagnostic log line (4.34.5). Eden no longer shows drops.

## 4.34.5

Includes 4.34.4.
- `possibleDrops` diagnostics: when no items are found, the log line now says what the walk saw for each table: missing,
  its class, how many pools and entries, the entry classes, and the last error. For the "No drop data" Fallen Eden's
  gear tables still showed on TEST.

## 4.34.4

Includes 4.34.3.
- `ArenasApi.possibleDropsCount(server, activity, difficulty)`: how many distinct items `possibleDrops` would list
  without its 64 cap (same rules and cache). Eden's create panel uses it for its "+N" cell.

## 4.34.3

Includes 4.34.2.
- **`possibleDrops` reads tables with modded loot functions.** Fallen Eden's gear tables (with
  `dungeon_difficulty:local_scaling`) gave "No drop data": the table was written out to JSON to be read, and that
  mod's function threw while being written, which hid the whole table.
- It now walks the loaded tables directly (pools → entries: items, tags, nested tables by id or inline,
  alternatives / groups / sequences). Fields are found by type, so it works under any mappings.
- The test adds a nested-table check (fishing → fish → cod).

## 4.34.2

Includes 4.34.1.
- **`possibleDrops`** (Eden's create panel showed "No drop data" for a raid):
  - also reads the loot tables a tier's reward commands hand out (`loot give … loot <table>`);
  - keeps whatever part of a table it can read when a mod's loot function can't be written out (that used to drop
    the whole table);
  - logs when an activity has no reward table at all.
- The test now checks real items (a dungeon chest table via a reward command), for one tier and for all tiers.

## 4.34.1

Includes 4.34.0 (needs the client update).

**Lobby screen fixes from LeDok's first look** (GUI scale 2, dark hub theme):
- **Rental build box:**
  - follows the hub theme (it was a light parchment box in a dark theme);
  - the title and its Change and On/Off buttons sit on one line, so the button no longer squeezes the text;
  - the hint, the "−50% to everything you earn" line and the picked build each get a full-width line that ends in
    "…" with the full text in the tooltip, so nothing runs into the other column;
  - more space below it.
- **Rental preset popup:** follows the hub theme too.
- **Run modifier rows:** the reward text gets the room the name doesn't need, so "×2 loot · ×2 currency · +25% XP"
  shows in full. Anything still cut ends in "…" (full text in the tooltip).

## 4.34.0

**Needs the client update.** Includes 4.33.1 and 4.32.0.

**Fix: the start countdown (4.29.0+) never ran on real servers.**
- **Cause:** the game-test class switched it off in a static block, and Fabric API's game-test module creates that
  class on every server.
- **Fix:** the override now applies only in a real game-test run. Live got the same fix as hotfix 4.29.2.

**UI rework, step 4: run HUD and ally HP sidebar** (LeDok's D3, D4, D5):
- **Timer HUD:**
  - a wide dark translucent panel with the countdown in big digits and the objective beside it;
  - a "Hardcore" tag;
  - a long boss-HP bar with the percent in bold (a split boss counts its whole tree);
  - the border uses the hub theme's accent;
  - a narrow window shrinks it slightly instead of clipping it.
- **Ally sidebar** (new): your teammates in a dungeon, raid or arena run, or in a party.
  - **Each row:** head, name, health numbers, an HP bar with absorption in gold, and downed (with the respawn
    seconds), out or offline.
  - **Lowest health:** whoever is lowest, under half, is outlined.
  - **Sync:** the server sends it four times a second, only when it changed.
  - **Visibility:** hidden when you're alone.
  - **Placement:** `ally_sidebar` ("left" / "right" / "hidden") and `ally_sidebar_offset_y` in arenas_ld-client.json,
    built as an anchor plus offset so drag-to-move can come later.
  - **Parties:** `ArenasApi.setPartyProvider` lets Eden_LD feed its parties outside runs.
- **Loot reveal:** stays at the top (D5) and gets a dark translucent backing panel so it reads over terrain.

## 4.33.1

- `ArenasApi.lobbySettings(owner)` returns the owner's forming lobby's settings (private lobbies too), for the hub's
  «Змінити лобі» form (Eden_LD).

## 4.33.0

Includes 4.32.0 (needs the client update).

**Lobby creation API for the hub** (UI rework step 3; Eden_LD builds the browser and «Створити лобі» panel on it):
- `ArenasApi.createLobby(owner, activity, LobbySettings, openScreen)`, `updateLobby(owner, settings)`,
  `activityInfo(server, activity)` and `possibleDrops(server, activity, difficulty)`. `setLevelProvider` sets where a
  player's level comes from. See API.md.
- **Minimum join level:**
  - a new lobby setting, checked on join and on asking to join (not on invites);
  - only checked while a level provider is set (Eden: its activity level);
  - shown as an "Lv N+" pill in the lobby list;
  - a change to it is announced like other setting changes;
  - `OpenLobby` gains `minLevel`.

## 4.32.0

**Needs the client update.** Includes 4.31.0.

**UI rework, step 1 + 2: one lobby screen for dungeons, raids and arenas** (LeDok's decisions D1, D2, D3, D6;
mockups in docs/ui-rework/mockups.html):
- **Shared view:** the three controller screens are now one shared lobby view (LobbyView + a small adapter each).
  It went from about 5,500 lines to about 1,800.
- **Text:** always 1:1, never shrunk. The view fills the window (at most 720×440).
- **Layout:**
  - two columns, party | settings, when there is room (at least 560 GUI px wide, e.g. GUI scale 3 at 1920×1080);
  - one column with Party / Settings sub-tabs below that (e.g. scale 3 at 1366×768);
  - a fixed action bar at the bottom: status line, Leave, Ready, Start.
- **Lists:** members and lobbies are no longer capped at 5 / 6, and everything scrolls.
- **Member cards:** player head, ★ owner, rental, offline, ready, and kick for the owner. A "+ Invite a player" slot
  opens the invite field.
- **Owner settings:** live controls for the owner (tier, visibility, run modifiers as compact rows with the reward
  on the right; details and conflicts in tooltips). Everyone else sees a read-only summary.
- **Hub theme:** with Eden_LD, the screens use the player's hub theme (EdenHubThemeApi by reflection); otherwise the
  Arenas parchment.

**Settings can be changed with others in the lobby** (D2):
- The other members get a line naming what changed.
- A change to the run (difficulty, hardcore, modifiers) un-readies everyone but the owner. Visibility changes only
  notify.

## 4.31.0

Server-only API (no client change of its own); includes 4.30.0.

**Open lobbies server-wide** (player suggestion, Tend1tnuy; for Eden_LD's hub):
- `ArenasApi.listOpenLobbies(server)`: every PUBLIC/FRIENDS lobby forming now across all dungeons, raids and arenas,
  with activity, owner, members/max, ready, tier, hardcore, modifiers and JOIN/REQUEST.
- A revision plus a listener that fires once a second at most when the list changes.
- `ArenasApi.joinOpenLobby(player, activity, lobbyId)` joins or requests through the usual flow, then opens the
  activity's lobby screen.
- See API.md.

## 4.30.0

**Needs the client update** for the new editor. The server works with old clients, but they can't open the editor,
and an old client saving an arena spawner's mob list drops these fields.

**Extra mob data and splitting mobs** (LeDok, high priority):
- **Extra mob data:** every summon (dungeon mob and boss spawners, raid boss spawner, arena mob rows) can carry
  extra mob data as SNBT. It's merged in before Arenas' attributes, equipment and scaling, e.g. `{Size:3}` for a
  size-4 slime.
  - The spawner screens have an **Extra data…** button, and each arena mob row has a `{}` button.
  - Invalid SNBT isn't saved.
- **Split parts stay in the run:** a run slime's or magma cube's parts are run mobs (same team, effects, stats).
  - They hold up the room, wave or raid, and are cleaned up with the run.
  - Before, Arenas stopped run slimes from splitting at all.
- **Part strength:** health is the parent's × (size ratio)², attack is the parent's × size ratio, both × the new
  **split part strength** % (slimes and magma cubes only, default 100).
- **One boss bar:** a splitting boss or room miniboss and its parts share the HUD's boss HP over the whole tree.
  It only goes down.
- **Raids:** a raid with a splitting boss is won when the last part dies.

## 4.29.1

Server-only; includes 4.29.0.
- The start countdown always runs to the end (LeDok: everyone gets the full 10 s to finish what they're doing).
  4.29.0 started early once the arena was loaded, which in practice was at about 2 s.
- The chat line now reads "Preparing the run: it starts in 10 s. Finish what you're doing."

## 4.29.0

Server-only (4.27/4.28 clients are fine); includes 4.28.0 and 4.27.1.

**Start countdown with an arena preload** (LeDok): before, a run start loaded the arena's chunks from disk and sent
them to the whole party at once, which made everyone stutter.
- **What the party sees:** pressing Start in a dungeon, raid or arena lobby (or a queued arena lobby reaching the
  front) now closes the lobby screens, posts one chat line and shows a countdown title, "Preparing… 10" … "1".
- **Preload:**
  - while the countdown runs, temporary chunk tickets load the run area and a square around the entrance (and the raid
    spawner) in the background;
  - these tickets load the chunks but don't tick them, so nothing in them runs;
  - the run starts when the countdown ends, or as soon as everything is loaded (at least 2 s in).
- **Instance:** the instance is held for the lobby during the countdown and isn't offered to anyone else. Lobbies that
  queue in the meantime wait behind it.
- **Cancel:** the party changing, a member going offline, the lobby going away, or (dungeon) a member un-readying
  cancels the countdown with a line saying why. The instance is free again, and the owner can press Start again.
- **Tickets:** they go 10 s after the start (the players and the run's own tickets hold what they need by then), or at
  once on a cancel. Nothing stays loaded, and nothing is saved: a restart during a countdown drops it.
- **Config:**
  - `run_prepare_seconds`: default 10; 0 brings back the old instant start;
  - `run_prepare_radius_chunks`: default 8, max 16.

## 4.28.0

Server-only; includes 4.27.1.

**Invite filter API** (TODO 19, for Eden_LD's privacy setting): `ArenasApi.setInviteFilter(InviteFilter)`, where
`InviteFilter.refuse(ServerPlayer inviter, UUID invitee, String activityId)` returns null to allow the invite or a
line to refuse it.
- It's asked at the start of every raid, dungeon and arena invite.
- On a refusal there is no invite, the inviter gets the line and the invitee hears nothing.
- Without a filter, nothing changes. See API.md.

## 4.27.1

Server-only (4.27.0 clients are fine).

**Fix (urgent, MCP eden A1): long raids broke chunk saving.** A run's stats go into its controller's save as JSON, and
since 4.27.0 (weapon pairs + timeline) a long raid's JSON passed 65,535 bytes, the most one NBT string can hold. C2ME
then failed to write the controller's chunk (`UTFDataFormatException … too long`) every save, so that chunk stopped
saving.
- Longer JSON is now saved as a list of parts of at most 16,000 chars. Short JSON stays one string, and both forms
  (and older saves) read back.
- The timeline keeps at most 240 entries per player (2 h of 30 s entries). Past that the oldest go, except the first.

**Respawn anchors can't be right-clicked in runs** (TODO 17). The new config list `run_blocked_use_blocks` (block ids
or `#tags`, default `minecraft:respawn_anchor`) is checked for raid, dungeon and arena players. They get "That can't be
used during a run."

## 4.27.0

Needs the client update (the run details screen has new player states).

**Run states "forfeited", "won", "lost"** (LeDok):
- A player who leaves a dungeon or raid with `/exit` (or the exit button) before it's over is **forfeited**, even if
  the run goes on and is won without them. Leaving after a win isn't a forfeit.
- When a run is won, everyone still in it (downed and hardcore-out players too) is **won**; in a failed or abandoned
  run they're **lost**.
- Shown in the run details and end screen with their own color and name (en: Forfeited / Won / Lost; uk: Покинув /
  Перемога / Поразка), saved in the run file's `state` (and `forfeited`), and in the staff view.

**Fight stats over the whole fight** (TODO 10), per player in the run file:
- `weapons`: every boss hit filed under the weapon pair in hand (main hand + offhand): hits, damage, and
  [min, mean, max, count] of attack speed, attack damage, crit chance and damage (melee and spell), and the spell power
  of the hit's school; plus the effects active while hitting (highest amplifier). E.g. "two daggers, attack speed 5.2
  on average, 6.1 at most, under Haste".
- `timeline`: a stats snapshot (attributes, effects, items in hand) every 30 s, and on a weapon change or a move of
  more than 10% in attack speed or damage (at most every 5 s).
- `statsStart` / `statsBestMinute` stay as they are; old files stay readable.
- Cost: a few attribute reads and an effect pass per boss hit; one snapshot per player per 30 s (or per change).

## 4.26.5

Needs the client update (the message fix is client-side).
- Fix: players in dungeons and raids kept getting "This block can't be broken here" while fighting. The client asks
  on every attack swing that hits a block, and holding attack asks again every tick, so missed swings in a fight
  counted as break attempts. The line now shows only when the player keeps attacking the same block for about a
  second, and at most once every 3 seconds.
- Rental builds:
  - after a restart, a re-begin that fails (the kit is gone) is tried at most 3 times, 10 s apart, instead of on
    every stats tick;
  - the preview leaves out a stack that can't be sent (e.g. an empty stack nested in a shared rune pouch) instead of
    breaking the player's connection.

## 4.26.4

Server-only.
- Fix: rental builds never began in **dungeons** (raids were fine). A dungeon removes its lobby when the run starts,
  and a raid keeps it as "in run"; the rental code required the lobby to still be there "in run", and its
  once-a-second lobby sync dropped the pick as soon as the lobby was gone. Now a pick is kept while its player is in a
  run and is taken by that run's first stats tick, whatever happened to the lobby; it's only held back while its own
  lobby is still waiting.

## 4.26.3

Server-only (records only). Healing by source in the run stats (LeDok: why does a berserker "heal" more than a water
mage?). Started by MCP eden, finished here. Per player in the run file:
- `healBySource`: effective healing (overheal left out) by source; for a caster it holds the healing they did on
  themselves and on others. `healOthersBySource`: the part on other players.
- Sources:
  - `spell:<id>`: Spell Engine heals (its heal event names the spell and the caster);
  - `effect:<id>`: effect ticks such as `minecraft:regeneration` and modded heal-over-time (wrapped at the
    `applyEffectTick` call in `MobEffectInstance.tick`), and vanilla instant health;
  - `other:<Class>`: anything else, e.g. lifesteal or relic passives, named after the first non-vanilla, non-Arenas
    class on the stack (a short StackWalker, only for run players).
- Natural food regeneration is still not counted.
- `topHeals`: the five biggest single heals (amount, source, seconds into the run, and the healed player's name for a
  heal on someone else).
- Cost: for anything but a run player, an effect tick costs one field check. For run players, a label per effect tick
  and a stack walk only for heals with no known source.

## 4.26.2

Server-only (4.26.1 clients stay compatible). Players revived in a dungeon or raid run come back with a full hunger
bar (food level 20); saturation is left as it is (LeDok 2026-11-09).

## 4.26.1

Needs the client update (lobby screens). Rental builds follow LeDok's preset layout (`falleneden:rental/<level>/<preset>`,
e.g. `rental/25/paladin_holy`, `rental/0/warrior`):
- The provider offers presets, not classes: `RentalProvider.presets(level)` (for each class, all its presets in the
  highest level folder at or below the player's `loot_stage` level that has that class, 0 included) and
  `displayName(kitId)`; `classes()` and `kitFor()` are gone. Needs Eden_LD built against 4.26.1.
- No fixed bands and no "level 5" minimum: a level-0 kit serves level 0.
- The preview lists every offered preset with its name, its own level ("— level 5") and items; the player picks a
  preset. The class (badge, records) is the preset name up to the first "_".
- Records: `loadoutId` = the kit id, `rentalClass` = the class, `rentalBand` = the preset's level folder.

## 4.26.0

Needs the client update (lobby screens). Design: Balance/RENTAL-BUILDS.md.

**Rental builds in dungeons and raids** (optional per player):
- Lobby: "Rental build" is a per-player run modifier (LeDok): a row under the lobby's run modifiers that each member
  switches only for themselves. Switching it on opens a preview of every rental kit at their level band (each kit's
  items as slots with vanilla tooltips); picking a class there confirms it ("Change" opens it again, Off goes back to
  own gear). A member's pick shows as a "Rental: <class>" badge on their member row. Shown only when a rental provider
  is registered (Eden_LD) and offers classes. The band is the highest of 5/10/15/20/25 at or below the player's level
  (`loot_stage`); below 5 the switch is off.
- Run start (the run's first stats tick): effects cleared, then the provider lends the loadout (`begin`, session
  `arenas:<instance>@<start tick>`). If it can't, the player runs with their own gear and full rewards.
- After each respawn in the run the loadout is handed out again (`reequip`). After a restart the rental is begun again
  a few ticks after the player's login (once the provider's own login check ran).
- The rental ends (`end`) within a second once the player is no longer in the running run (run over, left, removed).
- Rewards: the renter gets a per-player "Rental ×0.5" on loot, XP and currency (completion and room rewards). Reward
  commands are unchanged.
- Run records: per player `loadoutId`, `rentalClass`, `rentalBand`; `RunHistoryEntry.loadouts`; staff line
  "RENTAL rogue L20".
- API: `ArenasApi.RentalProvider` (classes, kitFor, begin, end, reequip, isRenting, preview) + `setRentalProvider` /
  `rentalProvider` (Arenas has no loadouts of its own).

## 4.25.1

Server-only.
- Respawn shield (player request): after a dungeon or raid respawn a player can't be hurt for
  `respawn_invulnerable_ticks` (new config, default 20 = 1 s; 0 = off). It ends early on their first melee swing or the
  first damage they deal (no free hits). Void and /kill damage still land.

## 4.25.0

Server-only. Data for class balance (LeDok's goal: a same-level player attacking non-stop for the whole time limit
deals 40% of the boss's HP); no gameplay change. Older run files stay readable (new fields are optional).
- Per run: `timeLimitSeconds`; `bosses` (entity id, name, max HP as spawned or the highest seen, HP left, killed)
  with the sums `bossMaxHealth` and `bossHealthLeft`.
- Per player: `bossActiveSeconds` (seconds in which they landed damage on a boss; "endless attack DPS" =
  `bossDealt / bossActiveSeconds`), `statsStart` (attributes and effects at their first boss hit, their first hit until
  then), `statsBestMinute` + `bestMinuteDealt` (the same at the first hit of their best damage minute), `skills` (every
  unlocked Pufferfish skill node, "<category>/<node>", at run start).
- Stats: attack damage and speed, max health, armor, toughness, `eden_ld:damage_reduction`,
  `spell_engine:evasion_chance`, `mrpg_lib:rage_modifier`, every `spell_power:*` and `critical_strike:*` above 0;
  effects as "id amplifier".
- Staff line adds "on boss 300s, 41/s".
- Cost: per credited hit a couple of counter updates; a stats snapshot only at a player's first (boss) hit and once
  per minute they deal damage.

## 4.24.2

Server-only.
- Fix: the player class (equipped spell book) was never found: 4.24.1 looked up `getSpellsOf` on Spell Engine's
  `SpellContainerHelper`; it is on `SpellContainerSource`. Every run fell back to the skill-tree branch.

## 4.24.1

Server-only.
- Run stats record each player's class at run start: the equipped Spell Engine spell book's item id and its spell
  pool (read by reflection), else `skills:<branch>`, the `falleneden:classes` skill-tree branch with the most
  unlocked nodes (node ids start with the branch). Shown in the staff line ("Lv 69 arcane_spell_book …"), saved in
  the run file and `RunHistoryEntry.classes` (per player; the 4.24.0 constructor stays).

## 4.24.0

Server-only (the new staff text is plain English, no client lang).

**Run levels and biggest hits in the run stats** (LeDok's balance request):
- Per player, at run start: level (`loot_stage:loot_stage`), gear power per slot (Dungeon Difficulty `dd.rsf`,
  head/chest/legs/feet/main hand/offhand, with average and max; crafted gear has none), Limit Break level per slot
  (`limit_break_level`), item ids. Per run: the difficulty's level, from its loot table (`…/level_40` → 40).
- Per player: the three biggest single hits (amount as it reached the target, target type, boss or not, time into
  the run, source, damage type) and damage per source. Source = the Spell Engine spell whose impact dealt it, else
  the weapon (main hand, an arrow's bow), else the pet/projectile type, else the damage type (poison, fire).
- Saved in the run file and the run's save; shown at the front of each player's staff line in the run details
  ("Lv 69 (run 40, +29) · gear avg 42 max 50 · LB … · top hit 6,012 spell:… on boss 1:23 · top sources …").
- `RunHistoryEntry` gains `runLevel`, `playerLevels`, `gearPower` (per player); the old constructor stays.
- Cost: one map update per credited hit (no allocation after a source's first hit), a few allocations only for a
  new top-3 hit; the gear snapshot once per player per run. Spell ids come from an optional mixin into Spell
  Engine's impact (`@Pseudo`, does nothing without Spell Engine).

## 4.23.1

- Hardcore dungeons and raids (LeDok): a player who dies (or, in a raid, disconnects) is out of the fight at once
  but no longer leaves the run. They spectate the party and never respawn; if the party wins they are revived at
  the end (prior game mode, full health) and get the rewards like everyone. The run is lost when every player in
  it is out (new dungeon outcome "the whole party fell"). Arenas' hardcore is unchanged.
- New messages need the client update (lang).

## 4.23.0

Needs the client update (new screens and the breakable-blocks client check).

**Run stats Phase B: end screen, saved history, more counters.**
- End screen: when a dungeon or raid run ends, every player of it gets the final run details (shown once the loot
  reveal is over) and a chat line with the result, time and highlights (top damage, most blocked, top healer) and a
  **[Details]** link (`/arenasld run last`).
- Saved history: one small gzip JSON file per finished run (`<world>/arenas_ld/runs/<runId>.json.gz`) plus an index,
  written once off the server thread and kept 30 days (pruned at server start).
- Staff: `/arenasld run history [player]` and `/arenasld run open <runId>`; API `ArenasApi.runHistory`,
  `openRunDetails(staff, runId)`, `openLastRunDetails(player)` (see API.md).
- New counters: damage blocked by source (shield, armor, Resistance, protection enchantments, absorption), boss
  damage, kills and boss kills, time alive, and for staff overkill, overheal and assists. Boss phases by HP
  (75/50/25%).
- Totals are saved with the run, so a restart mid-run keeps them.
- Fix: a hit soaked entirely by absorption hearts wasn't counted as blocked (Player.actuallyHurt returns early).
- Payloads to a player whose client lacks the channel are skipped instead of throwing (NeoForge threw, which
  broke the rest of a run's end).

**Breakable blocks in runs** (LeDok's design):
- Global `run_breakable_blocks` (block ids and `#tags`; first entries `bosses_of_mass_destruction:void_blossom` and
  `bosses_of_mass_destruction:vine_wall`).
- Per controller (dungeon and raid admin screens, General tab, or `/arenasld activity breakable <id> ...`, or
  `ArenasApi.setBreakableBlocks`): its own list, added to the global one or replacing it (empty = nothing).
- Per difficulty: **Block breaking** in each tier tab (`allowBlockBreaking`), **off by default for every tier**.
  Off switches both lists off.
- Only run players in Adventure (alive) can break; no drops, no XP; nothing is restored. Other blocks: an
  action-bar line "This block can't be broken here".

## 4.22.4

- Fix: the run details screen could show an old run instead of the one being played. A run's stats were closed
  only by the run's own once-a-second check seeing it over; a run its controller took away before that (abandoned,
  ended between two checks, cleaned up) stayed listed for good, and the lookup took the first (oldest) listed run of
  the player. Now a run that stops ticking is ended within 2 seconds, and the lookup takes the newest run.

## 4.22.3

- Raid respawns (LeDok's plan): downed raid players come back together at the next shared mark, on an interval set
  **per raid** in the raid controller's admin screen: its old "Respawn time" field is now "Respawn interval (s)"
  (seconds, default 60). The global `raid_respawn_wave_ticks` is only the fallback for a controller set to 0 (it
  used to override every controller).
- Migration: the interval is saved under a new key (`respawnWaveTicks`); the old per-player `respawnTimeTicks`
  (often the old 5 min default) isn't read, so every existing raid starts on 60 s. Set another interval in the
  admin screen.
- The death time penalty (time taken off the raid timer per death) is unchanged.

## 4.22.2

- Raids with `raid_respawn_together`: everyone downed now comes back TOGETHER at the next shared mark, so all
  countdowns and the party bar show the same "next wave in N s". Before, the per-death extra (`respawn_per_death_*`)
  delayed players with earlier deaths past the mark the others used, a whole interval later. New config
  `raid_per_death_extra` (default false) brings the old behavior back. Raids without the shared clock and dungeons
  keep the per-death extra.
- `resetCooldowns` (Moderation buttons, `/arenasld activity resetcooldown`) now logs refusals too ("<actor> tried to
  reset cooldowns of <id>: none on cooldown / no such activity"), so a click that changed nothing shows in the log.

## 4.22.1

- A downed player who respawns inside a dungeon or raid (respawn timer over: back at the room's respawn point or
  the entrance) loses every status effect, buffs too, as on leaving the run. The run modifiers' own effects aren't
  status effects (player speed is an attribute modifier, frozen ground the client's flag, damage multipliers are
  hooks), so they stay. Arenas' revive with the next wave is unchanged.

## 4.22.0

**Run details: live run stats (RunStats Phase A)** for dungeons and raids:
- A screen with each player's state (alive/downed/out/offline), deaths, respawn timer, HP, damage dealt and taken,
  and healing; the run's difficulty, hardcore, modifiers, room/phase line, time left/elapsed, boss HP, and the win's
  base rewards with reward modifiers. Updates about once a second while open, only when something changed.
- Opened by the **Run details** key (default `;`), `/arenasld run details`, `ArenasApi.openRunDetails(player)`, and
  for staff `ArenasApi.openRunDetails(staff, activity, instance)` (adds the HP nobody was credited for).
- Credit as the design says: hits, projectiles, pets and summons → their player; damage over time → whoever last put
  a harmful effect on the mob; no attacker → the last player who hit it; Spell Engine heals → the caster (its public
  heal event, by reflection); hunger regeneration isn't counted. Only HP really lost (no overkill).
- Cost: one field on living entities; outside runs a hit or heal costs one null check; totals only; config
  `run_stats.enabled` (default true).
- Not yet (Phase B): blocked damage by source, boss damage, kills, time alive, the end screen, saved history and
  the staff history tab. Totals aren't saved across a restart yet.

## 4.21.1

- Reward popup: items that went to the inbox (full inventory) or the ground no longer get a text tag each (with
  20 items the tags ran into one unreadable line). Such a card gets a small colored corner marker (blue inbox,
  amber ground), and one line under the cards counts them: "Sent to your inbox: 20 (inventory full)" /
  "Dropped at your feet: 3". Works with wrapped rows.
- Staff: reset instance cooldowns. `ArenasApi.resetCooldowns(server, id, instance|null, actor)` and
  `/arenasld activity resetcooldown <id> [pos [dimension]]` make an instance on cooldown (or all of an activity's)
  free at the next tick; the queue moves on as usual. Logged with who did it.

## 4.21.0

- **One lobby at a time:** creating or joining a lobby (an accepted invite or join request too) in any dungeon,
  raid or arena takes the player out of the lobby they were in, once the new lobby has accepted them (a full or
  refused lobby leaves them where they were). Ownership passes to another member, or the old lobby closes when they
  were alone; they get "You left the lobby <activity>." A run (or BusyLib busy) still blocks. Being in another
  lobby no longer refuses invites and join requests.
- **Lobby badge** on the HUD while in a lobby: "Lobby: <activity> (2/4, 1 ready)". Client config
  `config/arenas_ld-client.json`: `lobby_badge` = `top_left` (default), `top_right` (under the effect icons) or
  `hidden`. The server sends it only when it changes (checked twice a second).
- **Open your lobby from anywhere:** `/arenasld lobby open` (no arguments), clicking the badge with the chat open,
  and `ArenasApi.ownLobby` / `ArenasApi.openOwnLobby` for a hub button. The remote screen does everything the
  block's does (ready, leave, invite, run modifiers).
- `ActivityHost` gained `getLobbies`, `getMaxPartySize`, `leaveLobby(UUID)`; the unused raid-only
  `removePlayerFromOtherLobbies` is gone.

## 4.20.1

- Fix: a player sent home after a run (seen after a failed dungeon) could get no chunks until they rejoined. A
  move packet asked the chunk loader to remove them from a chunk they weren't registered in; vanilla throws there
  (`DistanceManager.removePlayer` NPE) after the player's section was already updated, so they were never
  registered again. That removal is now skipped (with a warning naming the player), so the move registers them
  and the chunks load. It covers every cause, also the one seen on live without a run.
- Going home (every way out of every mode): the old game mode is restored before the teleport, and the teleport
  holds the destination chunk loaded for the arrival (vanilla's post-teleport ticket), so a downed spectator
  arrives as a normal player.

## 4.20.0

**Hardcore is a run modifier** (LeDok): "Hardcore run" (`hardcore_run`, wither skeleton skull) sits in the lobby's
run modifier list; the separate hardcore switch is gone from the dungeon, raid and arena lobby screens.
- Gameplay unchanged: one life each, the same hardcore death/eject rules and respawn behavior.
- Rewards: loot rolls and currency more ×2 (as before); XP is now **increased +25%** (was ×2).
- New effect flag `effects.one_life`; the lobby/run is hardcore (`hardcoreEnabled()`, HUD tag, invite warning,
  stats, leaderboards) while a picked modifier has it. A `run_modifiers` entry that replaces a built-in without
  `effects` keeps the built-in's effects, so tuning `hardcore_run`'s rewards keeps the one life.
- Old saves: lobbies and runs (also runs still going across the restart) with the old hardcore flag load with
  `hardcore_run` picked, and get its rewards once. The old hardcore payloads and `Lobby.withHardcore` switch
  `hardcore_run`.
- uk/en texts for the modifier and the updated hardcore reward lines.

**Run modifier config, new modifiers, conflicts** (LeDok):
- Every built-in run modifier is written into the config's `run_modifiers` with all its values when missing
  (entries already there, e.g. the live `frozen_ground`, stay as they are). Edit the numbers there.
- Built-in Frozen ground now defaults to +15% XP, +25% currency (LeDok's live values).
- New: **XP to loot** (`xp_to_loot`, «Досвід у здобич»): no XP from the run, +2 loot rolls (flat). **Currency to
  loot** (`currency_to_loot`, «Монети у здобич»): no currency, +1 loot roll (flat).
- New `conflicts` per modifier (config-editable; either side listing the other counts). Built-in pairs:
  items_to_xp–xp_to_loot, items_to_xp–currency_to_loot, currency_to_xp–currency_to_loot, currency_to_xp–xp_to_loot.
  A conflicting row is strong red in the lobby with a dark red switch and "Conflict: …"; pressing it is refused
  with a chat line; a lobby holding a conflicting pair (config changed later) can't start, for all three modes
  (also when an arena lobby launches from the queue).
- Includes everything from 4.19.3 below (never deployed on its own).

## 4.19.3

- Creating or joining a lobby (from any tab, an accepted invite too) now opens your lobby tab in the dungeon, raid
  and arena screens instead of leaving you on the lobby list.
- The raid and arena screens' instance pills count a cooldown down every second while the screen is open, and
  turn FREE at 0 (they stayed at the number the screen opened with).
- Dungeon lobbies: pressing Create while busy (BusyLib, e.g. a tutorial) or already in a lobby now says "You are
  already in another lobby or game." like raids and arenas, instead of doing nothing.
- A `run_modifiers` entry that replaces a built-in with an empty `name`, `description` or `icon` keeps the
  built-in's (the lobby showed the raw id, e.g. `frozen_ground`).

## 4.19.2

- Leaving a raid now wipes every status effect, buffs too, like dungeons and arenas (4.19.1 cleared only the
  harmful ones). Every way out and every mode go through the same return: win, loss, timeout, staff end, /exit,
  hardcore death, disconnect expiry, and offline players on their next login.

## 4.19.1

- Leaving a raid (win, loss, timeout, staff end, /exit, hardcore death, disconnect expiry, offline players on their
  next login) now removes every harmful status effect (wither, poison, mod debuffs); beneficial and neutral ones
  stay. Dungeons and arenas already wipe all effects when a player leaves, as before.

## 4.19.0

**Lobby run modifiers** (dungeons, raids, arenas): the lobby owner switches them on under the hardcore switch;
everyone sees them; several stack. The built-in seven (config `run_modifiers` can replace or add):
| Modifier | Gameplay | Rewards |
|---|---|---|
| Frozen ground | run players slide as on ice | +25% XP, +25% currency |
| Glass cannon | run players deal and take ×4 damage | +2 loot rolls (flat) |
| Tanky mobs | run mobs ×2 health | +1 loot roll (flat) |
| Bloody mobs | run players take ×2 damage | +1 loot roll (flat) |
| Items to XP | — | no loot items; every final loot roll +10% XP (dungeons, raids) |
| Currency to XP | — | no currency; +25% XP |
| Speed run | run mobs and players ×1.5 speed | +10% XP |

- Reward model: `(base + Σ flat) × (1 + Σ increased) × Π more`, then conversions (loot/currency into increased
  XP, before XP's more factors). Flat bonuses apply to completions only, not to rooms or arena wave rows.
- Effects apply only to the run's players and mobs (minions too), and end when a player leaves or the run ends,
  also across restarts (refreshed by the run every half second).
- Damage multipliers stack between modifiers (Glass cannon + Bloody mobs = ×8 taken).
- Arena per-player wave rows roll `arena_player_row_rolls` times their rolls (config, default 1; live: 3). The old
  LuckPerms `arena_loot_rolls` is no longer read.
- Lobby data format changed again: clients need 4.19.0.

## 4.18.0

**Reward modifiers** (one formula for every payout):
- `final = base × (1 + Σ increased) × Π more` for loot rolls, currency and skill XP: dungeon completions, dungeon
  room rewards, raid wins, arena per-player wave rows and the arena summary. Hardcore is more ×2 (no per-mode
  hardcore code left). Currency and XP round half up; loot rolls are fractional (the fraction is the chance of
  one more roll).
- Dungeon and raid completions roll the tier's loot table `completion_loot_rolls` times (config, default 3)
  before modifiers.
- LuckPerms: new percent keys `arenas_ld.loot_bonus`, `xp_bonus`, `currency_bonus` (+ per-mode overrides,
  `arenas_ld.bonus_name`). The old keys are still read and converted until they are replaced.
  Prime with `loot_bonus 0.5` gets 4.5 rolls on average instead of 5.
- LuckPerms bonuses now also apply to dungeon room rewards.
- Data-driven run modifiers: `run_modifiers`, `activity_modifiers` and time-windowed `reward_events` in the config.
  New API `ArenasApi.registerRewardModifierSource`.
- Players get a per-stat breakdown in chat after a completion, a raid win and the arena summary. Lobby headers list
  the active run modifiers. The lobby data format changed, so clients need 4.18.0.

## 4.17.2

- Hardcore doubles items and skill XP too, not only currency, so it is 2× everything in every mode:
  - dungeon and raid wins: loot rolls ×2 after the LuckPerms roll clamp (3 → 6, Prime 5 → 10), skill XP ×2;
    the LuckPerms currency and XP multipliers apply on top, as before;
  - dungeon room rewards: loot rolls, currency and skill XP ×2 (effects stay single);
  - arenas already doubled wave loot, currency and XP.
- The hardcore descriptions now say "2× items, currency and XP" (en_us, uk_ua).

## 4.17.1

- Raids respawn downed players together every minute by default (`raid_respawn_wave_ticks` 1200, was 600).
  A config file that already has the key keeps its value.

## 4.17.0

**Respawn timer** (dungeons, raids, arenas):
- Downed players see a countdown on the action bar: "Respawn in 12 s · Watching Bob — sneak to switch"
  (arenas: "Respawn with the next wave"). Their party sees a red boss bar, "Downed: Bob 12 s · Alice 30 s",
  while anyone is downed. These are plain vanilla action-bar and boss-bar messages; older clients show the English fallback.
- Dungeons: a downed player waits at least `dungeon_respawn_min_ticks` (default 10 s), whatever the controller
  stores (live controllers often keep 2 s). The protect-room +50% stays on top.
- Dungeons and raids: every earlier death of the same player in the same run adds `respawn_per_death_ticks`
  (default +5 s), up to `respawn_per_death_max_ticks` (+60 s). The count is saved with the run.
- Raids: with `raid_respawn_together` (default on), downed players come back together every
  `raid_respawn_wave_ticks` (default 1 min, counted from the raid's start), once their own per-death wait is over.
  Off: each player on their own timer (controller respawn time + the per-death extra), as before.

## 4.16.2

**Fixes:**
- Crash: a room cleared while a party member was offline (inside the disconnect grace) crashed the server.
  The offline member's loot was rolled with GIFT parameters, which need a player. Offline rolls now use
  CHEST parameters, and a loot roll that fails for any reason is logged and gives nothing instead of
  throwing out of the controller tick. Arena floor loot goes through the same guarded roll.

## 4.16.1

**Fixes:**
- A player could keep a run's no-friendly-fire team after the run. If a run's end never ran for them (for
  example, the server stopped while a won raid was closing), the next run they started stored that team
  as their "previous team" and gave it back at its end, run after run. A run start now never stores
  another run's team (`ald_d_`/`ald_r_`/`ald_a_`). It keeps the real previous team if one is still
  stored, and otherwise none, and logs a warning.
- An old run ending late (for example one restored after a restart) no longer takes a player off the team
  of the run they are in now, or takes the previous team saved for it.

**New:**
- `/arenasld team release <player>` (ops): takes a player off a run team no run has them on, gives back
  their saved previous team, and deletes the team once it's empty. Refused while the player is in a run.

## 4.16.0

**Admin screens: several admins can edit at once** (issue #17):
- Saving one tab no longer resets everyone's open admin screens. A snapshot now merges field by
  field: a field nobody is editing takes the new value, and unsaved edits stay, in every tab and
  for every admin. Before, another admin saving "Easy" threw away unsaved edits on "General", and
  so did the same admin saving another tab.
- If another admin saved a field you are editing, your value stays and the footer warns about it.
  The warning names who saved and shows their value. The tab gets a yellow ⚠. Saving overwrites
  their value on purpose; **Use theirs** drops your edit instead.
- Tabs with unsaved edits show a "•". When another admin's save changes other tabs, the footer
  says who saved which tabs.
- Saving sends only the fields you changed. So a save can't revert a field another admin saved
  while you were editing a different one.
- Admin snapshots name the op who last saved through an admin screen (kept in memory only). The
  snapshot format changed, so clients and server need the same version.
- A resized dungeon admin screen kept its tab but lost unsaved edits; they now stay.

**Lobbies show the entry requirement when it is met** (issue #8):
- New API `ArenasApi.registerEntryRequirement`: a hub labels what entering needs ("Lv 25+"). Players who
  meet it see the label in green under each difficulty they may pick (dungeons, raids), and in the arena
  lobby header. A difficulty they don't meet still shows the gate's refusal, lock and all.
- Nothing shows until the hub (Eden_LD) registers the label.
- An invite to a player who doesn't meet the requirement was already refused with "<name> can't enter:
  <reason>"; that's unchanged.

## 4.15.0

**Security** (a modified client could do all of these):
- The boss mob id, attribute and equipment editors of spawners had no permission check on save, so
  any survival player could rewrite them. They now need creative or permission level 2, the same as
  opening those screens.
- Lobby and spawner-editor packets no longer load chunks at positions the client names. Spamming
  them with far-away coordinates generated chunks on the server thread.
- Client-sent list sizes are checked before anything is allocated. A forged size made the server
  try to allocate gigabytes.
- Room and spawner editor data (reward commands, loot tables, mob setups) is sent only to players
  who can open those editors, instead of to everyone online.
- Controllers no longer sync their saved state to nearby clients. That state included every run
  participant's return point (where they came from, often their base), plus lobbies, invites and
  tier configs with reward commands. Clients now get only the instance list, which is all the
  Dungeon Tool overlay uses.
- Reward commands skip a winner whose name isn't a plain Minecraft name, logging a warning. On
  offline-mode servers a player named `@a` could turn `give @dungeonplayer …` into a command for
  everyone. Floodgate's `.` prefix for Bedrock players is allowed.

**Fixes:**
- A player who left a raid (`/exit`, hardcore death, disconnect expiry) couldn't die until the raid
  ended. Raid damage scaling and item restrictions also kept applying to them anywhere.
- Raid instance cooldown used the "respawn time" setting instead of the cooldown setting.
- A death penalty larger than the time left in an arena wave switched the wave timer off, so the
  wave could no longer be lost on time. The wave now times out on the next tick.
- An arena player who came back after the disconnect grace ran out was sent home, then teleported
  back to their old entry point when the run ended.
- A dungeon with no rooms linked was won on its first tick, with full rewards. Starting one now
  fails with "this dungeon has no rooms", and if every room is unlinked mid-run it ends as a loss.
- Active runs now mark their controller's chunk for saving every tick. Before, a restart could bring
  back stale run state, such as a player who had left still listed as in the run.
- An Economy_LD build without the newer `give`/`deliverItem` API turned off all currency rewards.
  Economy_LD call failures are now logged instead of losing the reward silently.
- One failing event listener no longer skips the others. For example, an error in one activity's
  disconnect handling stopped the next activity from starting its grace timer.
- Config: a syntax error in `arenas_ld.json` crashed the game on startup. The defaults (or, on
  `/arenasld reload`, the previous settings) now stay in effect, and the file isn't overwritten.
  `/arenasld reload` reports when the file couldn't be read. `dungeon_damage_source_filter` and
  `puffish_skills_tree_id` weren't applied by a reload. Saves can no longer leave a truncated file.

**Runs survive disconnects and restarts:**
- Dungeon and arena runs were lost the moment nobody in them was online. A solo player's dropped
  connection, and every server restart, ended the run, even though disconnect grace exists for that.
  A run now ends as abandoned only once everyone still in it has stayed away past the grace.
- A dungeon run counted only players in the boss spawner's dimension as online, so a party standing
  at an entrance in another dimension was "gone".
- While everyone in an arena run is away, the run holds still: no wave timer, no wipe, no next wave.
  The first player back stands in it again, at a respawn point, instead of coming back downed. As the
  only one there, coming back downed counted as a wipe.
- Disconnects at server shutdown are now recorded (dungeon players were saved as still in the run),
  and disconnect grace carries over the restart. A player offline with no disconnect on record, after
  a crash, gets their grace from the first tick after the restart.
- The disconnect every player gets when the server stops no longer counts as a hardcore death (it
  wiped every hardcore raid and arena on restart). It also no longer costs a raid its death-time
  penalty once per player.

**Breaking a controller mid-run:** its runs are now ended as staff force-end does, and closed at
once. Before, they vanished with the block: the players stayed in the instance in Adventure or
Spectator, still marked busy and on the run's team, with the mobs alive and the chunks held until
the server stopped.

**Instances at the same coordinates in different dimensions** (copied instance dimensions):
- Raid and arena controllers tracked runs, and instances flagged for removal, by spawner position
  only. A second run at the same position in another dimension replaced the first, which then never
  ticked again, leaving its players stuck. Runs are now keyed by dimension and position. Older saves
  still load, and removal flags saved by position are matched to the running instance there.
- When a run ended on an arena instance flagged for removal, every instance at that position was
  unlinked, in all dimensions. Now only that instance is.
- Each run's no-friendly-fire team is now named from its instance's dimension and exact position.
  The old names came from the position's hash code, so two instances could share a team, and one
  run's end deleted it from under the other. A team left under the old name by a run that was going
  during the update is deleted when that run ends.

**Doors and chunk loading:**
- Doors are now always set as a whole, and the run loads a door's chunks before changing it. Before,
  at run start the run's chunks were still loading, so a door outside its Room Controller's own
  chunk could silently keep the previous run's state (the way in staying shut). A door that crossed
  an unloaded chunk border could end up half open, and one block already in the new state stopped
  the change from reaching the rest. Such doors now heal on their next change.
- Entering a room is noticed on every block of the doorway. The door list the run watches was built
  one tick after run start, while chunks were loading, so it could hold just one corner block of a
  doorway and the run sat on "Explore".
- Dungeon runs also keep their mobs' spawn positions and the rooms' respawn points loaded. Before,
  only the spawner blocks were kept loaded, and mobs could stand in other chunks.
- Arena runs now keep their battle area (the battle radius, up to 128 blocks) and entrance loaded,
  as dungeons and raids already did. Before, an arena unloaded as soon as its players left or
  disconnected, or the server restarted. Its mobs unloaded with it, counted as killed, and the wave
  was cleared and paid out with the mobs still there.
- Dungeon rooms and arena waves no longer count a mob that isn't loaded yet as dead (or a protect
  target as lost) in the first ticks after a run starts or the server restarts.
- `/exit` from a raid or arena whose spawner is in another dimension than its controller looked the
  spawner up at the same coordinates in the controller's dimension, loading a chunk there, and its
  end missed the spawner.

**Build:** VectorLib 0.7.2 (was 0.2.6). CI now builds VectorLib from its public repository first.
Every CI run since the VectorLib migration had failed on the missing dependency.

## 4.14.0

- **Downed players watch their party instead of flying free.** Downed run players (dungeon, raid,
  arena) are still spectators, but their camera is now put on the nearest standing teammate of their
  run (same dimension first), with an action-bar hint "Watching <name> — sneak to switch". Sneaking
  switches to the next teammate instead of leaving the camera. The spectator menu's teleport and
  clicking an entity only work on a standing teammate and only switch the camera; any other target
  (players outside the party, other instances, mobs) is refused. When the watched teammate goes down,
  leaves, logs out or changes dimension, another one is picked.
- **Tether.** With nobody left standing, a downed player is held near where they went down: farther
  than `downed_spectator_radius` blocks (new config key, default `16`; `0` or less pins them to the
  spot), or in another dimension, puts them back on it, checked every 5 ticks.
- The hold lasts from going down until respawn, `/exit`, hardcore removal, disconnect expiry or the
  run's finalize (so it covers the exit timer), and is applied again when a downed player reconnects
  (on the same spot). Normal spectators and admins are not affected. Server-side only: the two new
  messages have English fallbacks for clients without this version.
- Fixed: an arena player whose wave was cleared while they were offline came back as a spectator
  for the rest of the run (the wave revive had marked them standing while offline). They now come
  back downed and are revived with the next cleared wave. A participant who never left is no longer
  turned into a spectator by the join handling.

## 4.13.0

- **Run leftovers are cleaned up**, in dungeons, raids and arenas, without any setup: Arenas_LD
  follows the players rather than sweeping an area (instances have no bounds, and raid copies sit
  close together). Whatever a run player spawns during the run (Spell Engine summons, projectiles,
  pets, thrown items; a summon's projectiles count as its player's) is tagged with the run. When the
  run is over for a player (the run closes in any way, including staff force-end, or they `/exit`,
  die out in hardcore or stay away past the disconnect grace), they are sent home first, then their
  Spell Engine summons and projectiles are removed, their pets (tamed wolves, Saint's Dragons dragons,
  horses…) are teleported to them and never deleted, and items they threw (and a thrown trident) go
  back into their inventory. An offline owner's pets and items stay put and come over when they join or the next time
  they load. Leftovers in chunks that weren't loaded are handled when they load (one tick later, never
  inside the chunk load), unless their owner is in that very run again. Run mobs, minions and
  fixtures (armor stands, item frames, paintings, display entities, NPCs_LD NPCs) are never touched.
- At run start, the party's Spell Engine summons left by an earlier run within 48 blocks of where
  each player landed are removed; real pets stay.
- Each cleanup is logged with counts by entity type: `[Arenas] Run cleanup (<activity id> <instance>,
  <player>): removed {...}, returned N pet(s), returned M item stack(s)`.
- The fresh-entity check costs one map lookup per owned entity: Arenas_LD keeps an index of run
  players, updated where players enter and leave runs and rebuilt from the loaded controllers every
  5 s and after a join (a restart).

## 4.12.0

- **`/exit` works mid-run in raids and arenas**, as it already did in dungeons. It's a forfeit: the
  player goes home at once (game mode and health restored, busy cleared, off the party team), gets no
  rewards from that run, and the party is told. The run goes on for the others; if nobody is left, a
  raid is lost and an arena run ends as abandoned on the next tick. Before, `/exit` only worked once
  a raid or arena run was over.
- An arena run whose remaining players all left with `/exit` now ends as abandoned (a forfeiter who
  stays online no longer counts as still in it).
- Fixed: a raid could lose its boss when the boss's chunk unloaded ("Boss entity disappeared" with
  the boss alive and saved in its chunk), and the next party found two bosses. Raids now hold chunk
  tickets on the spawner, the entrance and the boss's chunk (following the boss, whose last position
  is saved with the run), wait for an unloaded boss to load instead of losing at once (lost only
  after 5 s with its chunk loaded and no boss, or 30 s in all; the timer pauses meanwhile), discard
  their boss whatever ends the run, and sweep the arena for stray run-spawned mobs of the boss's
  type at battle start and every 5 s while running.

## 4.11.0

- **Commands on win** for dungeon and raid tiers: each tier tab of the Dungeon and Raid Controller
  admin screens has a command list under the loot, currency and XP fields (✕ to remove, a text field
  and Add, up to 8 commands of up to 256 chars, saved with the tier's Apply). They run as the server
  after the tier's loot, currency and XP are paid, with the room reward rules: `@dungeonplayer` or
  `@s` runs once per loot-eligible winner with their name, anything else runs once. Hardcore and
  LuckPerms perks don't change them. Tiers saved before 4.11.0 load with none.
- **Offline winners get their commands on their next join**: their per-player commands are queued in
  `data/arenas_ld_pending_reward_commands.dat` and run once they are home (after any pending eject).
- The reward popup is unchanged: it shows loot, currency and XP; what a command gives is delivered by
  the command itself.
- Reward commands (rooms too) now accept `@s[...]`: it becomes `@a[name=<player>,...]` instead of a
  name with brackets, which never parsed. A command that fails is logged instead of failing silently.
  The server drops a leading `/` and blank entries from room commands as well.

## 4.10.0

- **"Scale minions" switch on every spawner** (Mob Spawner, Dungeon Boss Spawner, Raid Boss Spawner,
  Arena Spawner), on by default. Turn it off for a boss that already scales its own minions from its
  health or attack damage, so they aren't scaled twice. Its minions are still minions (its team, no
  loot or experience, gone with the boss); Arenas_LD just doesn't multiply their health and damage.
  Saved with the spawner; spawners from earlier versions count as on. Op only, like every spawner edit.

## 4.9.1

- Fix a server crash when a dungeon wave spawns: `RunMinions.recordScale` removed its scale tag
  through `getTags()`, which another mod in the pack returns as a read-only copy
  (`UnsupportedOperationException` in `ImmutableCollections.removeIf`). It goes through `removeTag` now.

## 4.9.0

Staff tools for hubs: an activity's live status, a teleport to its controller, its admin screen from
anywhere, renaming and ending runs, all by activity id, for moderation screens such as Eden_LD's.
Arenas_LD still depends on nobody.

### Admin API

- **`ArenasApi.status(server, id)`**: every instance (free, running, cooling down with the ticks
  left, waiting to be unlinked), the runs going on, open and queued lobbies and their players, and
  setup warnings:
  - a generated id, no name, no difficulty enabled (dungeons and raids), no instance linked,
    instances waiting to be unlinked;
  - the controller gone, or its chunk not loaded.

  A run shows its difficulty, players, time elapsed and left (-1 when nothing counts down, such as
  an untimed raid or an arena wave without a timer), whether it is closing, and a short phase:
  - dungeons: "Starting", "Room 3", "Room 2/5", "Entering room 2", "Exploring, 4 cleared", "Boss
    room";
  - raids: "Starting", "Boss 42%" ("Boss" while the boss isn't loaded);
  - arenas: "Prepare", "Wave 4/10", "Wave 5" (endless), "Wave 5 incoming";
  - all: "Closing (won)", "Closing (lost)", "Closing (ended by an admin)".

  It reads only the saved index and what the controller holds in memory and never loads a chunk,
  so a hub can poll it every couple of seconds for every activity.
- **`teleportToController(player, id)`** puts a player next to the controller, facing it, from any
  dimension: the closest free 2-high spot on solid ground within 3 blocks with no liquid, fire or
  other harmful block or portal, else on top of it. Refused during a run or while BusyLib marks the
  player busy (naming why), and reported when the teleport didn't happen (a refused dimension change).
- **`openAdmin(player, id)`** opens the controller's admin screen from any distance or dimension.
  Operators only; it closes if they are deopped.
- **`rename(server, id, name)`**: blank or over 48 characters is refused; hubs hear about it once.
- **`forceEnd(server, id, instance)`** ends the runs of an activity, or of one instance, as staff
  (see below). Run listeners get the new **`Outcome.FORCED`**: a `switch` over `Outcome` needs a
  case for it.
- **`forget(server, id)`** and **`location(server, id)`**.
- The activity index (`arenas_ld_activities.dat`) now records whether an id was generated. Entries
  saved before 4.9.0 count as generated only in the position form (`arenas_ld:dungeon_10_64_-3`).

### Ending runs as staff

- **`/arenasld activity stop <id>`** ends every run of the activity, and
  **`/arenasld activity stop <id> <pos> [dimension]`** only the run at that boss or arena spawner.
  Op level 2.
- A run still going ends as a loss marked as ended by staff:
  - its players get the defeat title and read "Dungeon ended by an admin." (or Raid / Arena; for
    an arena this replaces "Arena over at wave N.");
  - no rewards, except that an arena still pays for the waves reached, as on any loss;
  - the boss is removed as on a loss (a dungeon clears its boss room; the room being fought keeps
    its mobs until the players leave);
  - the usual exit timer follows, and `/exit` leaves at once.

  It is reported once, as `FORCED`, never as a win or a loss. Ordinary losses (out of time, wiped
  out, a missing boss or arena spawner) still report `LOSS`.
- A run already on its exit timer is closed on the spot: its players are returned now, it keeps its
  result, and nothing is reported or paid again. A won run is never turned into a forced one.
- A run stuck after a damaged save (a phase that can't be read) is cleaned up (its mobs removed) and
  its players are returned, without reporting anything. So is a raid run whose dimension is gone.
- A dungeon whose boss spawner is gone mid-run used to say "Dungeon ended by admin."; it now says its
  boss spawner is gone.

### Admin screens

- **Admin screens work from anywhere.** Each knows its controller's dimension and sends its edits
  to that controller, so `ArenasApi.openAdmin` can open one from any distance or dimension. The mob
  arena admin screen now gets its data from the server instead of the controller's copy on the
  client.
- **Open admin screens stay current.** They are updated when a run starts or ends, when an
  instance's cooldown runs out, and when another op edits the controller. Those run and cooldown
  updates never overwrite a field you are typing in; after Save, every field shows what the server
  accepted, so a refused or clamped value snaps back. On dungeon and raid screens, a settings change
  by another op re-reads every field (the arena screen keeps each field you are typing in).
- The arena admin screen shows an instance's cooldown in seconds and PENDING REMOVAL on a running
  instance unlinked during its run (pressing ✕ again cancels it).

### Commands

- **`/arenasld activity forget` is refused while the controller is still there.** It used to drop a
  live controller's entry too, which then vanished from hubs until the next restart. Break the block
  instead; `forget` is for entries whose block a tool removed without a block update.
- `/arenasld admin` opens through the same op check as the API; a refusal is shown as a command
  failure.

### Fixed

- **Admin data no longer goes to the wrong players.** A dungeon controller's admin data went to every
  online player, a raid controller's to every op. Now only ops with that controller's admin screen
  open get it, and it is built once, only when someone is looking.
- **Admin screens know their controller's dimension.** Two controllers at the same position in
  different dimensions could overwrite each other's open admin screen, and an admin edit went to the
  block at that position in the editor's own dimension.
- **Admin screens close when they should, and only then.** A dungeon or raid admin screen opened from
  9 or 10 blocks away (`/arenasld admin` reaches 10) closed at once; the reach is now 16 blocks, and
  there is no limit when it was opened remotely. A screen now also closes when its controller is
  broken or the player is deopped.
- **Arena admin and Arena Spawner edits are op-only**, as every other editor's are; creative mode
  alone no longer saves them. The Arena Spawner screen opens only for ops too; anyone else is told
  they lack permission.
- **A dungeon or raid run can no longer end twice.** A second win or loss on the same run (a run
  listener ending it again, say) could pay out or report again, or overwrite a win.
- **A finished dungeon run no longer pulls back players who moved on.** A player who had left a run
  and joined another dungeon run could be unregistered from the new one, lose its busy flag and team,
  or be teleported home when the old run finished.
- Renaming a dungeon now sends a block update, as raids and arenas do, and renaming any controller
  updates its open lobby and admin screens.
- **A raid instance unlinked during its run is now unlinked when that run ends.** It used to be
  marked PENDING REMOVAL forever and kept taking runs. Marks left over from earlier versions on
  instances that aren't running are dropped.
- An admin edit that arrives after its admin screen closed (its controller was broken, say) is
  ignored instead of landing on a controller at the same position in the editor's own dimension.

### Load

- The dungeon admin screen's loot table list is read once and kept until the next `/reload`, instead
  of being listed for every update.
- Apply on a raid admin screen's General tab sends it one update instead of seven.

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
