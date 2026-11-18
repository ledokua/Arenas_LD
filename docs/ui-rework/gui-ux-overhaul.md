# Arenas_LD GUI and UX overhaul

Local worktree on `gui-ux-overhaul` from `65224eb` (`1.21.1`). The review includes standalone Arenas screens and the views embedded in the FallenEden hub. The branch uses the existing VectorLib widgets, theme palettes, and menu actions.

## Screen inventory

| Surface | Review and implementation |
| --- | --- |
| Raid, dungeon, and wave-arena lobbies | Shared shell, tabs, party and readiness, queue and instance status, leaderboard, invites, difficulty, visibility, entry requirements, and modifiers. Tabs wrap, titles and status stay within their rows, and instance states are localized. Check crowded own lobbies and the Eden-hosted layout with a live menu. |
| Rental build selection | Optional and required rental rows, kit preview, item tooltips, spell choices, and selection confirmation. The dialog sizes to the canvas, wraps kit items, and has a clear empty state and keyboard focus. Reconcile dialog sizing with the active icon-based spell picker. |
| Run Details, result, and Timeline | Party and combat rows, class icons, HP, highlights, rewards and breakdown, Timeline player and event filters, older pages, and live updates. The title and stats have distinct rows. Controls wrap, common focus survives refresh, and missing Timeline replies offer a retry. |
| HUD and overlays | Ally HP and absorption, downed/offline state, classic and current run timers, boss HP, loot reveal, lobby badge, and the dungeon tool's world selection/link overlay. HUD bounds respond to GUI size. The world overlay continues to use the existing color and shape legend. Check overlap with resource-pack effects, minimaps, chat, and other HUD mods. |
| Activity administration | Raid, dungeon, and arena controller settings, instances, tiers/waves, leaderboards, live status and editing feedback. These editors retain their design width on narrow windows and keep the body in a scrollable viewport. Tab and Shift+Tab reveal fields inside the viewport. |
| Instance and room configuration | Raid/dungeon boss spawners, arena and mob spawners, room controller, room rewards, mob attributes and equipment, summon NBT, ID suggestions, reward commands, breakable-block lists, and dungeon tool modes/linked spawners. Shared close labels and clipped-text tooltips, scrollable mob/equipment forms, room rewards back navigation, and keyboard opening/dismissal of ID suggestions. |
| Run and client configuration | Dungeon/raid rental mode and loot-roll settings in admin General, plus client settings for ally placement, timers, loot reveal, and lobby badge exposed through the Eden settings panel and existing JSON. Run settings name the action, accept Enter to apply rolls, and explain invalid values. Existing option keys, values, and access paths are retained. |

## Reconciliation and integration

- The original checkout has 4.40.1–4.40.4 work in progress. Its `LobbyView` removes the public lobby list in favor of Eden and replaces inline invitations with an invite picker. Merge the header, status, and footer layout into that navigation and retain the invite flow from development.
- The active `RentalPick` work adds Spell Engine icon slots, hover details, and grouped spell choices, with changes to the preview payload. Retain those changes and apply the responsive dialog and focus behavior around the new rows.
- The active `RunDetailsScreen` shows the base-roll text for completed runs. Retain that condition when integrating the layout changes. Merge the EN and UK keys by key.
- Eden owns the hosted lobby frame, mouse and keyboard dispatch, and the client settings panel through `ArenasClientApi`. Test handoff, Esc, modal focus, and returning to the hub with both mods loaded. The standalone Fabric client cannot exercise this integration.
- Test with the FallenEden font and theme resource packs in English and Ukrainian. The standalone client reported missing raid-controller block/item models and mob-spawner animation frame warnings already present in the base resources.

## Verification

- Fabric remapped JAR and NeoForge JAR built with Java 21. Both locale files have matching keys.
- A disposable Fabric client rendered sample lobby, rental, and Run Details data at 1366×768 (scale 3 and 4) and 1920×1080 (scale 3). The Ukrainian pass confirmed the open rental dialog stays visible when changing scale, and showed the compact Timeline controls and focus in the raid-admin instance list. The probe did not connect to a game server.
- The committed base does not compile the unit-test suite. `RunEventsTest.StubHost` lacks current `ActivityHost` methods, starting with `rewardLootTables`. The original checkout already contains edits to those tests.
- Checked the original local `1.21.1` ref before branching and again at build, visual review, and handoff. No commits appeared after `65224eb`. The overlapping working-tree edits stayed in the original checkout.

## Visual checks on TEST

Use 1920×1080 and 1366×768 at GUI scale 3, plus scale 4 at 1366×768. Include long activity and player names, full parties, queued and empty lobbies, and a large reward reveal.

1. Open each standalone and hub-hosted lobby. Check tab order and visible focus, invite and rental dialogs, ready/start status, and closing during a server snapshot update.
2. Inspect required and optional rental selection, item and spell tooltips, and resize with a dialog open. Check that closing returns focus to the lobby and keeps the menu open.
3. Open live and completed Run Details. Try Tab, Shift+Tab, and Page Up/Down. Change Timeline player and filter, load older rows, and check a successful retry after a missed response.
4. Resize controller, spawner, room, rewards, attributes and equipment editors with unsaved fields. Check the inventory controls, save/validation feedback, and nested scrolling. Dismiss ID suggestions with Esc before leaving the editor.
5. Check both HUD styles, ally left/right/hidden and vertical offset, the lobby badge from chat, and the dungeon tool's link colors and mode picker.
