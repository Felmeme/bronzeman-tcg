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

## DEFERRED: Sailing test pass (owner lacks quick access to sailing content)
Implemented 2026-07-12, entirely untested in-game. When the owner can get on a
boat, run these and fix data accordingly (resource_nodes.json is hand-editable;
sailing rules were merged by scripts/merge_sailing_data.py from the audit in
docs/sailing_nodes_report.md):
1. **Workbench parts** (HIGH confidence): at a Shipwrights' Workbench, make
   "Oak hull parts" without owning that card -> expect blocked. Product names
   here are verified identical to card names; if this does NOT block, the
   workbench isn't using make-verb options — capture the exact menu option
   text and add it to MAKE_VERBS in BronzemanTcgPlugin.
2. **Install/Modify** (GUESSED strings): Boat schematics -> Modify -> install
   a hull/keel tier. The rules are keyed on names like "Oak hull"; if
   installing is NOT blocked, note the exact product string the menu target
   shows and rename the kind:"interface" node keys to match.
3. **Salvaging** (option string unverified): salvage a wreck without its
   salvage card -> expect blocked. Wiki infobox only lists "Inspect"; if the
   real option isn't "Salvage" (or it's hook-item-on-wreck), update the 8
   sailing-salvage nodes' options (or re-kind them to item-on-object).
4. **Modes**: walk the Sailing boat-upgrades dropdown: Parts (part card only),
   Parts+Materials (adds plank/bar), Everything (adds logs + Large part).
5. **Harpoonfish**: Tempoross Cove harpoon spot obeys the fishing mode with
   Raw harpoonfish now in the Harpoon union.

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
6. **Sailing**: implemented (2026-07-12), needs manual test pass. Boat
   upgrades: mode dropdown Off/Parts/Parts+Materials/Everything; material
   = plank card, log card only in Everything (owner's chain ruling); keels
   have no Everything-extra (bar-from-ore is smelting's job); each tier
   keyed under install name (guessed: "Oak hull") AND workbench part
   products (verified == card names). Salvaging: 8 wreck tiers -> salvage
   cards (mapping wiki-verified). Raw harpoonfish joined the Harpoon
   fishing union (Tempoross Cove shares the spot identity); Crystallised
   excluded. To verify in-game: install-side product strings, the wreck
   "Salvage" option text (infobox only lists Inspect — may be item-on-
   object with the hook), and Boat schematics "Modify" flow. Skipped (no
   cards/cosmetic/double-gating): masts, helms, cannons, cargo holds,
   chum, nets, port tasks, charting, crew, paints, dragon schematics.
   See docs/sailing_nodes_report.md.
7. Hub submission: **submitted 2026-07-12** — repo public at
   github.com/Felmeme/bronzeman-tcg, plugin-hub PR open with CI green
   (pinned commit 802e7c9; first CI run failed on a missing
   build=standard in runelite-plugin.properties, restored). Awaiting
   manual review; reviewer feedback lands as PR comments, fixes ship by
   pushing here and bumping the PR's commit= line.

## Post-launch backlog (agreed with owner)
- **Grey model recolor** as a third Visuals choice (dropdown: Outline /
  Grey model / Off, outline default): mutate NPC Model face color arrays
  (getFaceColors1/2/3, packed HSL) to greyscale for locked NPCs. Needs
  original-color snapshot/restore on unlock/toggle/shutdown, and in-game
  iteration for animation edge cases; models are shared per NPC type
  (coherent for us — lock state is per-name). Hub precedent exists but
  expect closer review.
- Side panel design iteration (ask owner what to change first).
- Time Tracking plugin interop: per-patch crop harvest restriction +
  compost-type discrimination by reading its persisted config state
  (same pattern as the osrs-tcg interop).
- Krystilia require-all difficulty revisit (wilderness bosses).
- Sailing test pass (see DEFERRED section above).
- Upstream card-gap report to osrs-tcg: Onyx ring; larupia/kyatt/kebbits/
  moths/Herbiboar hunter creatures; blurite/barronite ores.

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
