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
- `CardNameCatalog` (abstract) loads a `{entityToCards: {name -> [cards]}}`
  snapshot resource; `TrackedMonsterCatalog` (1,198 NPCs <- 1,225 Monster
  cards) and `TrackedItemCatalog` (5,149 items <- 5,149 Resource cards, 1:1)
  are thin subclasses. Card.json's 6,376 cards partition exactly into
  Monster/Resource with globally unique names and no ID fields — cards link
  to game entities **by exact name** (after stripping wiki-style bracket
  disambiguators; owning any variant unlocks the entity). Entities *not* in
  a snapshot are never restricted (they could never be unlocked).
- `BronzemanTcgPlugin` consumes `MenuOptionClicked`:
  - `NPC_FIRST..FIFTH_OPTION` where option text == "Attack"
  - `WIDGET_TARGET_ON_NPC` (spell/item on NPC), config-gated
  - `GROUND_ITEM_FIRST..FIFTH_OPTION` where option text == "Take", plus
    `WIDGET_TARGET_ON_GROUND_ITEM` (telegrab), gated by `restrictLoot`;
    item name via `ItemManager.getItemComposition(event.getId())`; a
    user-editable comma-separated exempt list (default "Coins") keeps
    universal drops lootable
  - NPC name via `getTransformedComposition()` when present, `Text.removeTags`,
    exact case-insensitive match.
  - `GAME_OBJECT_FIRST..FIFTH_OPTION`, NPC options, and
    `WIDGET_TARGET_ON_GAME_OBJECT` are checked against `ResourceNodeCatalog`
    (resources/resource_nodes.json, 89 hand-curated rules): trees need their
    logs card, rocks their ore, fishing spots any fish they can yield
    (locations are indistinguishable, so `requireAll=false` union per menu
    option), pickpocketing needs Coins+Coin pouch (`requireAll=true`), and
    using raw food on a fire/range needs the cooked card (parsed from the
    "item -> object" menu target). Five per-skill config toggles in a
    "Resource nodes" section; unknown categories restrict by default so new
    data is loud, not inert. See docs/resource_nodes_report.md for coverage,
    exclusions (no card exists: Arctic pine, Blurite ore, ...), and items
    deliberately omitted pending owner decisions.

## Current status
- ✅ Compiles green (`./gradlew build`, Gradle 8.10 wrapper, Temurin JDK 11).
  None of the suspected API-drift candidates had drifted.
- ✅ Full manual test pass completed in-game (2026-07-10): block + chat
  message, `::tcg-give` unlock within 5s, untracked NPC unaffected,
  spell/item-on-NPC gating, config toggles.
- ✅ Fixed during testing: `TcgStateDto` assumed `collectionState.instances[]`
  but osrs-tcg (schemaVersion 3) stores a top-level `cardInstances[]` —
  verified against a real decoded state blob from a live client.
- ✅ **Bracketed card names handled** (full-catalog audit, 2026-07-10): 67 of
  the 1,227 monster cards carry wiki-style disambiguation suffixes
  (`Monkey (monster)`, `Soldier (Yanille)`, ...) that in-game NPC names never
  contain — and only monster cards do (0 of the other 5,149 cards). The
  snapshot is now a `npcName -> [cardNames]` map (1,198 NPCs <- 1,225 cards;
  the 2 `(unused)` cards are excluded as non-attackable); owning ANY variant
  unlocks the NPC, since RuneLite only exposes the plain NPC name at attack
  time. 15 NPCs have multiple variants (Soldier has 11). Item-card name
  collisions (e.g. `Ferret` the Resource card vs `Ferret (Hunter)` the
  monster) are why the map must stay Monster-only — mind this in the Phase-2
  loot catalog, which reuses the same owned-name set.
- ✅ Suffix-matching verified in-game (2026-07-10): Monkey blocked without
  the card, unlocked after `::tcg-give Monkey (monster)`. Phase 1 complete.

## Manual test plan (needs a logged-in account with osrs-tcg installed)
1. Attack an NPC that has a Monster card you don't own → blocked + chat message.
2. `::tcg-give <that npc name>` in osrs-tcg debug mode, wait ≤5s → attack works.
3. Attack an NPC with no card (check tracked_monster_names.json) → never blocked.
4. Cast a spell on an uncollected tracked NPC → blocked when the toggle is on.
5. Toggle config options off → restrictions lift.

## Roadmap (agreed with owner)
1. **This phase**: compile, run, fix API drift, manual test pass.
2. **Loot restriction**: implemented (2026-07-10) — blocks Take/telegrab on
   ground items whose Resource card isn't owned, with a config exempt list
   (default "Coins": every common drop has a card, including Coins and Bones,
   so pure blocking would brick the early game). Needs its manual test pass:
   drop-blocked item Take, telegrab, exempt list entry, `::tcg-give` unlock.
3. **Resource nodes**: implemented (2026-07-10) — woodcutting/mining/fishing/
   pickpocketing/cooking gated behind yielded-item cards, per-skill toggles.
   Needs its manual test pass; specific things the data audit could not
   verify without a client (fix resource_nodes.json + regenerate nothing —
   it's hand-curated, just edit it):
   - tree object names: both "Oak" and "Oak tree" style nodes shipped —
     prune whichever doesn't match after testing
   - ore rocks assumed per-ore names ("Iron rocks"); if the client shows
     generic "Rocks", the 15 ore nodes need object-ID work instead
   - option strings to confirm in-game: "Use-rod" (barbarian fishing),
     "Fish" (karambwan), "Small Net" (minnows)
4. Overlay/UI: visual indicator on locked NPCs; maybe a side panel of
   nearest unlocks.
5. Hub submission.

## Maintenance contracts with upstream osrs-tcg
- If its `Card.json` changes: regenerate both snapshot resources with
  `python scripts/generate_tracked_monsters.py <path-to-Card.json>` (splits
  Monster/Resource categories, strips bracket suffixes into the
  entity->cards maps, skips `(unused)` cards).
- If its storage prefix/shape changes: update `TcgStateDecoder` / `TcgStateDto`.
- Owning normal **or** foil counts as collected.

## Conventions
- Java 11, tabs, RuneLite plugin-hub style (mirrors osrs-tcg's build.gradle).
- Owner is learning game dev; explain non-obvious RuneLite API choices briefly
  in PR descriptions/commit messages rather than silently applying them.
