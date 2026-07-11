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
    logs card, rocks their ore, fishing spots fish they can yield,
    pickpocketing needs Coins+Coin pouch (`requireAll=true`), and using raw
    food on a fire/range needs the cooked card (parsed from the
    "item -> object" menu target). Config in a "Resource nodes" section:
    toggles for woodcutting/mining/pickpocketing/cooking, a three-way
    fishing mode (Off / Any of / Require ALL — spot locations are
    indistinguishable so rules hold the union per menu option and the mode
    overrides requireAll), and a dedicated Master Farmer mode (Off /
    Coins+Pouch / Insanity = all 45 seed cards from
    `masterFarmerSeedCards`, code path in the plugin, not a generic node).
    Unknown categories restrict by default so new data is loud, not inert.
    See docs/resource_nodes_report.md (+ addendum) for coverage, exclusions
    (no card: Arctic pine, Blurite ore, Crystal shard...; ambiguous object:
    Volcanic Mine), and deliberate omissions.

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
3. **Resource nodes**: implemented and manually tested (2026-07-10) —
   woodcutting/mining/fishing/pickpocketing/cooking gated behind
   yielded-item cards, per-skill config. Tree names confirmed "Oak tree"
   style (bare-name duplicates pruned); per-ore rock names confirmed
   working. resource_nodes.json is hand-curated — just edit it. Still
   unverified in-game (niche): option strings "Use-rod" (barbarian
   fishing), "Fish" (karambwan), "Small Net" (minnows); the Weiss salt /
   Daeyalt / Saltpetre / elf nodes from the second research pass.
4. **Launch hardening**: implemented (2026-07-11), needs manual test pass —
   equip blocking, forced-drop mode (Drop/Allow-banking/Off; deposit-only
   banking), shop buying, best-effort GE search blocking (owner-accepted
   leaky; keyboard flows may bypass), potion-drink blocking (dose folding),
   378 recipes (firemaking/smelt/smith/craft/enchant/fletch/herblore via
   RecipeCatalog + recipe_nodes.json; make-X = mouse-only — spacebar AND
   the materials-for-exactly-one case bypass the menu pipeline, both
   owner-accepted honor system; quantity options prefix-matched), hunter (5 methods, any-of gear+creature groups),
   Extreme Hunter rumours, slayer masters+monsters (merged rules with
   master/monsters roles), runecrafting altars, farming rake/plant/compost.
   Deferred: Sailing (needs deep dive); Time Tracking plugin interop for
   per-patch harvest + compost-type discrimination (read its ConfigManager
   state like we do osrs-tcg's); Krystilia difficulty revisit; upstream
   card gaps (Onyx ring; larupia/kyatt/kebbits/moths/Herbiboar creatures).
5. Overlay/UI: implemented and in-game tested (2026-07-11) — grey model
   outline on locked NPCs (colour/width/feather configurable, Visuals
   section), hide-locked-NPCs option (ground items can't be hidden by the
   renderable draw hook — verified in-game, removed), sidebar panel
   (search / nearby / progress; upside-down bronze med helm icon).
   **Panel needs design iteration** — owner wants to revisit its layout
   and content later; ask what's wrong with it before restyling.
6. Hub submission.

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
