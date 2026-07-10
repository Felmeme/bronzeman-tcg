# Bronzeman TCG — RuneLite plugin

## What this is
A bronzeman-style restriction plugin: the player may only **attack NPCs whose card
they have already pulled** in the separate [OSRS TCG plugin](https://github.com/Azderi/osrs-tcg)
(a gacha card-collecting plugin on the RuneLite Plugin Hub). Goal is eventual
Plugin Hub publication, so all patterns must be hub-compliant (restriction/QoL
only, no automation).

## Architecture (read this before changing anything)
- **No compile-time dependency on osrs-tcg.** Interop is read-only via RuneLite's
  ConfigManager: osrs-tcg persists its state RSProfile-scoped under group
  `osrstcg`, key `state`.
- The stored value is `RLTCG_v2:` + base64( xor_salt( gzip(json) ) ).
  `TcgStateDecoder` mirrors that transform (salt copied from osrs-tcg's
  `TcgStateStorageEncoding`). The decode algorithm was verified by round-trip
  test against the real encoding.
- `TcgStateDto` declares only the JSON fields we read:
  `collectionState.instances[].cardName` (+ `foil`). Gson ignores the rest.
- `TcgCollectionReader` caches the owned-name set for 5s; invalidated on
  `RuneScapeProfileChanged`. **Fails closed** on decode failure (empty
  collection → all tracked NPCs blocked). This is deliberate: breakage should
  be loud for a challenge-mode plugin. Discuss before changing to fail-open.
- `TrackedMonsterCatalog` loads `src/main/resources/tracked_monster_names.json`
  — a snapshot of the 1,227 card names tagged `Monster` in osrs-tcg's
  `Card.json` (6,376 cards total, all names globally unique, no ID fields —
  cards link to game entities **by exact name only**).
  NPCs *not* in this list are never restricted (they could never be unlocked).
- `BronzemanTcgPlugin` consumes `MenuOptionClicked`:
  - `NPC_FIRST..FIFTH_OPTION` where option text == "Attack"
  - `WIDGET_TARGET_ON_NPC` (spell/item on NPC), config-gated
  - NPC name via `getTransformedComposition()` when present, `Text.removeTags`,
    exact case-insensitive match.

## Current status
- ✅ Decode round-trip verified; JSON resource validated; structural checks pass.
- ❌ **Never compiled** — written in a sandbox without network access to pull
  RuneLite deps. First task: `./gradlew build` and fix any API drift
  (candidates: `MenuEntry.getNpc()` availability, `MenuAction` enum names,
  `RuneScapeProfileChanged` package).
- ❌ Never run in-game. Test via `./gradlew run` (dev-mode launcher in
  `src/test/.../BronzemanTcgPluginTest.java`) with osrs-tcg also installed.

## Manual test plan (needs a logged-in account with osrs-tcg installed)
1. Attack an NPC that has a Monster card you don't own → blocked + chat message.
2. `::tcg-give <that npc name>` in osrs-tcg debug mode, wait ≤5s → attack works.
3. Attack an NPC with no card (check tracked_monster_names.json) → never blocked.
4. Cast a spell on an uncollected tracked NPC → blocked when the toggle is on.
5. Toggle config options off → restrictions lift.

## Roadmap (agreed with owner)
1. **This phase**: compile, run, fix API drift, manual test pass.
2. **Loot restriction**: block picking up ground items whose card isn't owned
   (maps directly — most of the 6,376 cards ARE items; reuse the same
   owned-name set, new catalog snapshot filtered to item categories).
3. Overlay/UI: visual indicator on locked NPCs; maybe a side panel of
   nearest unlocks.
4. Hub submission.

## Maintenance contracts with upstream osrs-tcg
- If its `Card.json` changes: regenerate `tracked_monster_names.json`
  (filter category contains "Monster", dedupe, keep the `count` field accurate).
- If its storage prefix/shape changes: update `TcgStateDecoder` / `TcgStateDto`.
- Owning normal **or** foil counts as collected.

## Conventions
- Java 11, tabs, RuneLite plugin-hub style (mirrors osrs-tcg's build.gradle).
- Owner is learning game dev; explain non-obvious RuneLite API choices briefly
  in PR descriptions/commit messages rather than silently applying them.
