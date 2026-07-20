# Bronzeman TCG — RuneLite plugin

## HANDOFF NOTES (2026-07-12, written for the next assistant taking over)
- **Stall thieving + LMS bypass: BUILT (2026-07-15), need in-game verify.**
  Stalls: 26 nodes (category "thieving-stalls") + StallThievingMode dropdown
  (Off/Any of/All items, default Any of) in the Thieving section — mirrors
  fishing's mode handling in checkNodeRule. Verify the "steal-from" vs
  "steal from" (space) option strings in-client (data carries both); TzHaar
  gem+ore stalls unavoidably merge into one generic "Shop Counter" node.
  See docs/stall_nodes_report.md. LMS: "Allow Last Man Standing" toggle
  (General Settings, default ON) lifts ALL restrictions via isLmsBypassed()
  = BR_INGAME varbit (5314) OR the 15 LMS map regions. Owner MUST confirm
  in a real match that restrictions lift on entry and (harmlessly) that
  they don't linger-off. See docs/lms_detection_research.md.
- **PvM content data: INTEGRATED** (was in flight at handoff time; the agent
  completed and the data shipped — 7 contents, 80 roster cards, see
  docs/content_cards_report.md). Owner still needs to eyeball the panel
  section in the dev client. Notable: CoX resolves to only 2 cards (Great
  Olm, Lizardman shaman — most of its roster has no cards, which per the
  untracked-never-restricted rule means those rooms are freely fightable);
  Fortis "Passionate Supporter" has a card but is a non-combat spectator,
  deliberately excluded. Next agreed feature: the card browser grouped by
  skill/type (see backlog).
- **Operational knowledge the hard way**:
  - Build: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-11.0.31.11-hotspot" && ./gradlew build --no-daemon`;
    dev client via `./gradlew run` or the owner's local launch-client.bat
    (untracked, on disk).
  - Research agents die on session limits: ALWAYS instruct them to write
    deliverable files BEFORE their final message; if one dies, its
    generation scripts/raw fetches usually survive in the scratchpad and
    can be finished locally.
  - Bulk wiki data (corrected per wiki staff, github issue #1, 2026-07-18):
    request plain page URLs with NO query params - those hit the wiki's
    edge cache, which is what their infrastructure optimizes for. NEVER
    api.php?action=parse (forces an uncached server-side parse each call).
    Parse the returned HTML locally. Always send a descriptive User-Agent
    naming this project, pace at ~1 req/sec, and cache every raw fetch in
    the repo/scratchpad so re-runs hit the wiki zero times. The shipped
    plugin makes no wiki requests at runtime - this governs dev-time data
    generation only.
  - Hub releases (versioned since 0.2.1, 2026-07-19): EVERY release commit
    bumps the `version=` line in runelite-plugin.properties (0.MINOR.PATCH -
    MINOR features, PATCH fixes; OWNER RULING 2026-07-19: stay on 0.2.x
    patches until the full skills sweep is complete, then 0.3.0) AND adds a
    CHANGELOG.md entry. That line is
    the SINGLE source of truth: the hub displays it directly, and
    build.gradle reads it in for the jar name. (The plugin no longer shows
    its own version anywhere at runtime - the old processResources-stamped
    version.txt shipped its raw ${version} token on hub builds because the
    hub PACKAGER does NOT run a plugin's custom processResources/expand
    logic; removed in 0.2.4. Lesson: build-time resource templating is
    invisible to the hub packager - never rely on it.) Then: push, take the hash
    from `git log -1 --format=%H` AFTER the final push (never earlier -
    amends invalidate hashes), PR to runelite/plugin-hub bumping
    plugins/bronzeman-tcg `commit=`. runelite-plugin.properties MUST keep
    `build=standard`. Config keyNames are a public contract now. Assistant
    handovers for release-bound work must state the suggested next version.
  - **VERIFY EVERY MERGE'S CONTENT, not just its conflict markers (learned
    the hard way 2026-07-21).** `f041097` ("Merge remote-tracking branch
    'origin/main'") auto-resolved with NO conflict and a clean `git status`,
    yet silently discarded the owner's newer Ground Items / Item Usage
    config descriptions in favour of the other side's older text. A clean
    merge can still drop a side's content. After ANY merge, run
    `git diff <parent1> <merge>` AND `git diff <parent2> <merge>` and read
    every deletion, asking "did the other side legitimately replace this,
    or did we just lose it?". Recovery is easy if caught (the text lives in
    the parent commit) and invisible if not. Pure-addition merges (only
    `+` lines vs both parents) are safe by inspection.
  - The shell-permission service occasionally drops for a few minutes
    ("temporarily unavailable... classifier"); Write/Edit still work — route
    file changes through them and commit when it recovers.
- **Plan first (2026-07-18 onward)**: for any feature request, write a plan
  grounded in the existing codebase (files/methods touched, reuse vs new,
  config impact) and discuss with the owner BEFORE implementing. He wants
  less settings bloat and no spaghetti; consolidation refactors are welcome.
- **Owner commits himself (2026-07-16 onward)**: the assistant edits files
  and build-verifies, then hands over the changed-file list + a suggested
  commit message; the owner stages, commits and pushes in IntelliJ. Do not
  run git add/commit/push for repo changes unless explicitly asked.
- **Owner working style**: see the memory files (Opus agents for wiki/data
  research with exact-match validation against Card.json; every difficulty
  knob is a config dropdown; owner decides design calls from concise
  option lists; he manual-tests in-game and reports tersely; explain
  non-obvious RuneLite choices — he's learning; commit at every green
  milestone). Cross-project study notes live in
  `D:\ClaudeFolder\RuneLite Research\`.

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
  `RuneScapeProfileChanged`. On decode failure it reports state UNAVAILABLE
  and **enforcement stands down entirely** (isEnforcementBypassed) with a
  repeating chat warning — changed 2026-07-18 from the old fail-closed
  behaviour, which would have locked every user out of the game on any
  upstream format change. New-player-with-zero-cards still restricts
  normally (state present but empty = available).
- **PluginMessage API (MERGED to main 2026-07-20, in the staged 0.2.3;
  ACTIVATION pending Az's next hub release):** osrs-tcg commit 977f2ae adds
  an event-bus API: post `PluginMessage("osrstcg", "query-owned-names")`,
  receive "owned-names" reply + "owned-names-changed" pushes carrying
  `data.ownedNames` (List<String>, foil folded). Feeds TcgCollectionReader
  as the preferred source (instant unlocks, no polling); the config-decode
  path stays as fallback, so ANY combination of upgrade timing is safe.
  VERIFIED END-TO-END 2026-07-20 against osrs-tcg 0.17.3 (the git/main API
  build, sideloaded beside the Bronzeman dev build): handshake log line
  fires on login AND `::tcg-give` unlocks INSTANTLY with no relog (the
  push path). Contract confirmed matching on both sides (namespace
  "osrstcg"; query-owned-names / owned-names / owned-names-changed /
  ownedNames). Also tested 2026-07-18 against 977f2ae. CAUTION
  (learned 2026-07-20): the hub's LIVE osrs-tcg (132fe59, "reduce config
  writes") is cut from an old base 43 commits BEHIND the API commit - the
  API is NOT live yet (a GitHub forward-compare's ahead-count hid this;
  Az's word + the reverse compare settled it), AND its write-throttling
  makes our fallback read stale/absent state (the 2026-07-20 user-facing
  breakage; unfixable from our side, resolves when Az's next hub release
  lands and the API answers). The TCG stats overlay (credits+cards) was
  REMOVED same day (osrs-tcg displays its own stats now; the credits
  config-decode plumbing went with it - getCredits/cachedCredits/
  TcgStateDto.credits deleted, showTcgStatsOverlay config item retired).
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
    (resources/resource_nodes.json — hand-curated, 400+ (kind|name|option)
    keys spanning gathering, hunter, slayer, runecrafting, farming, thieving
    and sailing): trees need their logs card, rocks their ore, fishing spots
    fish they can yield, pickpocketing needs the loot cards (Coins + Coin
    pouch; the target's own card too in NPC+Loot mode via role "npc"
    groups), and using raw food on a fire/range needs the cooked card
    (parsed from the "item -> object" menu target). Config in a "Resource nodes" section:
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
7. Hub submission: **APPROVED — LIVE ON THE PLUGIN HUB** (2026-07-11).
   Repo public at github.com/Felmeme/bronzeman-tcg. Release flow for all
   future updates: commit+push here, then PR to runelite/plugin-hub
   bumping plugins/bronzeman-tcg's commit= line to the new hash. The
   plugin now has real users: keep config keyNames stable (renames wipe
   players' settings), keep the fail-safe defaults conservative, and
   prefer the dev client only for testing - play on the hub build.
   **NEVER rename a keyName that holds user-entered data**; display
   names/descriptions are safe to change, keyNames are not.
   **RuneLite config gotcha (learned the hard way, 2026-07-18):** RuneLite
   RE-INJECTS a @ConfigItem's non-empty `default` whenever the stored
   value is cleared/unset (and auto-unsets values equal to the default).
   So a user-editable field must NOT have a non-empty default the user
   might want to remove - that is why `lootExemptNames` kept re-adding
   "Coins" after updates (real player bug). Fix: list default is now `""`
   (nothing to re-inject); Coins exemption moved to its own `exemptCoins`
   toggle (default true; a boolean set to false != default, so it
   persists). One-time `migrateExemptList()` (guarded by hidden
   `exemptListMigrated`) reads the raw stored list and turns exemptCoins
   off for players whose list didn't already contain Coins, preserving
   their effective behaviour. Never reintroduce a non-empty default on
   any editable list field.

## ACTIVE: Skills sweep to 0.3.0 (2026-07-19 onward)
**Session 1 (Fletching): data rebuild DONE 2026-07-19, needs owner in-game
test pass, then ship as 0.2.3.** FletchingMode dropdown (Off/Product/
Product+Materials, default P+M) was already wired pre-rebuild. The 35 old
recipes (all mis-keyed item-on-item on the knife) were replaced by 111
recipes via `scripts/rebuild_fletching_data.py` (rerun it if
docs/fletching_actions.json's verdicts change - it fully regenerates the
"fletching" category, nothing there is hand-edited). Also deleted one
corrupted `"feltching"` (typo) entry found during the rebuild - a garbled
duplicate of the Earth battlestaff crafting recipe with the wrong output;
unrelated to fletching but was silently mis-gating that item, worth
mentioning if the owner asks why battlestaff crafting behavior changed.
  - **Knife-on-logs family -> `kind:"interface"`** (owner-verified): Arrow
    shaft (bare Logs only), all 8 crossbow stocks (Wooden..Magic, wiki-
    verified log->stock pairing), Ogre arrow shaft (Achey tree logs,
    uncarded so output-only gate). Two-stage bow chain, GROUND-TRUTHED via
    the owner's debug-log capture (2026-07-20): the knife menu fires PLAIN
    product names ("Willow shortbow", NOT "(u)" - two earlier guesses were
    both wrong), so the plain interface key = STAGE-1 CARVING, tier's Logs
    only, output null (owner ruling: strung card never gates the carve).
    STAGE-2 STRINGING is keyed item-on-item instead (Bow string on
    "<bow> (u)" - the unstrung item name is reliable there): logs + Bow
    string + strung card output. No "(u)" interface keys remain.
    Owner-test rulings/facts (2026-07-20): arrow shafts come from EVERY
    log tier and the menu string is "45 arrow shafts" (leading count,
    log-verified) - shaft rule is output-only, keyed singular+plural, and
    stripProductQuantity removes leading counts ("45 ", "45 x ") and
    trailing "xN" from interface product strings. WOODEN SHIELDS
    (Forestry 2023, all 7 tiers Wooden..Redwood carded) were missing from
    the research matrix entirely - owner spotted them in the knife menu;
    added as interface rules (tier logs + shield card). RECIPE-FIRST
    ordering in handleWidgetOnWidget (owner ruling): item-on-item recipe
    checks now run BEFORE the generic itemUsageMode lock, so block
    messages name every missing card instead of just the first locked
    material item (previously a locked Feather masked the bolts card and
    made stringing/bolts/darts results look like data bugs). Bow names
    are first-word-capitalised only ("Magic longbow"); all card
    references exact-match tracked_item_names (validated per regen).
    Stocks keys look correct ("Willow stock") but the owner saw no block -
    if the retest still doesn't block, capture the stock click's
    node-lookup line. WorldView migration done same day: getNpcs() ->
    getTopLevelWorldView().npcs() (overlay), getMapRegions() -> WorldView
    (LMS check), both null-guarded; deprecation warnings resolved. Avoid
    deprecated API in new code (owner instruction 2026-07-20).
  - **Verification sweep (2026-07-20 night, wiki plain-page fetches per
    etiquette; 125 fletching recipes final):** shields START AT OAK (no
    plain-logs shield exists - the guessed "Wooden shield" tier removed;
    2 logs each, Oak_shield page); JAVELIN SHAFTS are another missed
    knife-menu product (15/plain log, Fletching 3 - added output-only like
    arrow shafts); atlatl chain verified end-to-end (knife on Ent branch
    [CARDED] -> 100 shafts -> +Feathers -> headless -> +tips -> dart; Ent
    branch singular/plural unverified so both targets keyed); "Amethyst
    broad bolt tips" DOES NOT EXIST - real item is "Amethyst bolt tips"
    (trigger fixed); RUNE/RUNITE TRAP: unstrung = "Runite crossbow (u)"
    (matches card "Runite crossbow") but finished item = "Rune crossbow"
    (untracked!) - finished-name interface twin added, card stays Runite.
    Adamant has no such trap ("Adamant crossbow (u)" confirmed).
  - **OWNER'S OWN FIX PASS (2026-07-21) - the lessons, learn these:**
    (a) **Menu labels are not item names, and may be GENERIC.** The knife
    menu labels every crossbow stock tier "Crossbow stock" - never "Willow
    stock" - which is why the tier-keyed rules never fired. Same family as
    "45 arrow shafts" (leading count). Assume nothing about the label.
    (b) **Bow carving takes an output gate after all**: the knife menu fires
    the STRUNG name, so gating on that card is what makes the carve block
    (revises the 2026-07-20 logs-only ruling).
    (c) A generic label carries no tier, so no amount of data fixes it -
    the answer is `MenuEntry/MenuOptionClicked.getWidget().getItemId()`,
    which identifies the product outright. `logInterfaceProduct()` now logs
    raw/stripped/itemId/itemName at debug on every interface product click
    (permanent, owner ruling) so this question is answerable without a code
    change. **PHASE C (pending owner's in-game capture):** if product
    widgets carry item ids, resolve the product by id and restore
    tier-specific stock keys; if not, remember the log from the item-on-item
    click that opened the menu and key stocks with `targets:["willow logs"]`
    (RecipeCatalog already supports targets + ANY_TARGET fallback, no
    catalog change needed either way).
    **PHASE C RESOLVED (0.2.5).** Owner capture settled it: product widgets
    carry NO item id (`widgetItemId=-1` on every interface click), so the
    label can't be disambiguated on its own. Instead the plugin remembers the
    item-on-item pair that opened the menu (`lastUsedItemA/B`, ~100-tick
    window) and `resolveInterfaceMaterial()` passes whichever half has an
    EXACT rule (`RecipeCatalog.findExact`, no any-target fallback - a
    fallback would match every candidate and prove nothing). The 8 stock rows
    are keyed `targets:[<tier logs>]`. Crucially, `RecipeCatalog.load()` now
    withholds the ANY_TARGET catch-all from interface names shared by more
    than one recipe - counted automatically at load, so any future
    generic-label family is safe by default. This had to be conditional:
    206 interface recipes (smithing-forge 147, crafting 51, smelt 8) declare
    explicit targets yet are looked up with a null target, so removing the
    catch-all outright would have silently disabled Smithing and Crafting.
    Unresolvable material = no rule = no block (owner ruling: never a false
    block). Verified by simulation: Yew logs -> Yew stock, the Knife does not
    resolve, and bars/platebodies/bows/shields still match unchanged.
  - **SYSTEMIC collision outside fletching: FIXED 2026-07-21 (0.2.6),
    needs owner verification.** Same shape as Crossbow stock: one
    tool-on-material trigger, many products chosen in a follow-up interface,
    all sharing one lookup key so only the LAST was reachable - and since
    crafting enforces the output, that product's card was demanded for ALL
    of them (e.g. crafting Leather gloves demanded *Leather chaps*: a false
    block) while the other five were never gated at all.
    Fix = TWO-LAYER split (owner ruling), mirroring the bow carve/string
    split, applied by `scripts/fix_multiproduct_collisions.py`:
    layer 1 = the tool-on-material click keeps only the CERTAIN inputs
    (needle+thread+leather) with `output:null`, so no product card can be
    wrongly demanded and the collision disappears; layer 2 = one `interface`
    twin per product carrying that product's card. 24 colliding rules ->
    6 material rules + 24 twins, covering leather (6), green/blue/red/black
    d'hide (3 each) and glassblowing (6). Smelting differed: the ambiguous
    `item-on-object iron ore|furnace` pair (Iron vs Steel bar) was DELETED
    outright because `interface|Iron bar` and `interface|Steel bar` already
    gate both correctly at the product click.
    DATA-ONLY - no code change needed (the machinery landed in 0.2.5).
    Verified by simulation: no multi-product collisions remain anywhere in
    the file, all card refs exact-match, every leather product now gated,
    and bars/platebodies/bows/bolts/Ball of wool unaffected.
    UNVERIFIED: the 24 twin menu strings follow the product-name precedent
    of the existing crafting interface rules; a wrong one merely never fires
    (layer 1 carries no product card, so it cannot false-block). Owner to
    confirm via `logInterfaceProduct` on a Crafting run.
  - **STANDING DOCTRINE (owner instruction, 2026-07-20): never key rules
    on guessed menu/item strings.** Acceptable sources ONLY: (a) the
    owner's node-lookup debug capture (exact in-game strings), (b) wiki
    item pages fetched per the etiquette rules. Where a string is
    genuinely unobtainable ahead of time, key ALL plausible variants,
    label them UNVERIFIED in notes, and put them on the owner's test
    list. Every data regen must re-run exact-match validation against
    tracked_item_names + the collision check (both scripted in
    scripts/rebuild_fletching_data.py's validation snippet pattern).
  - **Bolts got interface twins** (both `item-on-item` AND `interface`
    registered, same product/inputs) per the 31 Jul 2024 Make-X wiki note -
    default-vs-toggle is unverified so both fire. Added Blurite bolts as a
    7th tier (was missing; needed as Jade bolts' base). Darts stayed
    item-on-item only (owner-verified default-instant; the opt-in Make-X
    toggle is an accepted honor-system gap, no twin).
  - **MISSING families added, all wiki-verified this pass (not guessed):**
    javelins (8 tiers, heads uncarded -> gate on Javelin shaft only);
    crossbows (8 tiers - stringing registered under BOTH item-on-item and
    interface since even the lean-toward-interface guess is unverified;
    stock/limbs/crossbow tier pairing confirmed via oldschool.runescape.wiki,
    e.g. Teak stock + Steel limbs -> Steel crossbow, Mahogany + Adamantite
    limbs -> Adamant crossbow); gem-tipped bolts (10 gems, base metal-bolt
    tier confirmed PER GEM via individual wiki pages - it varies, e.g. Opal
    -> Bronze bolts, Sapphire/Emerald -> Mithril bolts, Onyx/Dragonstone ->
    Runite bolts; the old matrix's "Bolts (metal base)" placeholder was not
    reliable enough to code from directly); ogre/brutal arrows (7 nail
    tiers, nearest-carded requires Ogre arrow shaft since Flighted ogre
    arrow is uncarded; plain "Ogre arrow" itself has NO card and can never
    be gated - card-gap report); broad ammo (broad arrowheads/unfinished
    broad bolts ARE carded, unlike other tip families, so both recipe sides
    enforce); Varlamore atlatl darts (headless + tipped, both carded).
  - Everything not owner-verified is still UNVERIFIED-lean-instant/interface
    per the matrix (docs/fletching_actions.json, docs/fletching_report.md) -
    the owner's in-game test pass is what promotes leans to verified facts,
    same as it did for knife-on-logs and bow-stringing originally.
  - Build is green (`./gradlew build`); recipe_nodes.json validated for
    zero key collisions (111 unique fletching trigger keys, zero clashes
    against the other 344 non-fletching recipes).

**Locked-tool gathering fix (2026-07-20, rides in the 0.2.3 release, needs
in-game test):** rocks/trees were minable/choppable with a LOCKED pickaxe/axe
carried - the node rules only checked the ore/logs card, and itemUsageMode
never fires because gathering doesn't click the tool. Fix (owner quiz'd):
`restrictMining`/`restrictWoodcutting` toggles became dropdowns `miningMode`
("Mining Options": Card Required/Ore Only/Tool Only/No Card Needed, default
Card Required) and `woodcuttingMode` (same with Logs Only) - new keyNames,
old ones mapped in migrateSkillToggles() (explicit false -> No Card Needed).
Tool half is STRICT: blocks while ANY carried (inv+equipped) pickaxe/axe is
locked, because the client silently uses the best tool carried. Mechanics:
carried tool NAMES cached on ItemContainerChanged (lock state evaluated per
check so unlocks apply instantly); mining/woodcutting left excludedRolesFor
for a dedicated evaluateGatheringRule(); WOODCUTTING_AXES allowlist avoids
combat axes (Zombie/Soulreaper/Morrigan's are carded ' axe' names!) and
pre-lists uncarded felling axes (inert until carded). Exempt list applies
to tools; untracked variants (e.g. 'Dragon pickaxe (or)') never block.
ALSO: migrateSkillToggles() is deliberately UNGUARDED (self-disarming) and
the fletching toggle migration MOVED into it - it previously sat inside
guard-flagged migrateNpcVisibility, where 0.2.1 upgraders (flag already
set) would never have run it; real latent bug, mention to owner. Owner
plans to reword the two new config descriptions himself.
docs/plan_skills_sweep.md is the governing roadmap: broken skills first
(Fishing -> Sailing -> Fletching), then missing rules (Varlamore thieving,
Agility, Construction, Farming-harvest), then owner test passes. Assistant
implements everything this sweep; owed lessons resume after 0.3.0. Standing
card-gap policy: requirements fall back to nearest CARDED items. Each
session: mini-plan + quiz first, 0.2.x release after owner test.

## Post-launch backlog (agreed with owner)
- **Force-smelt / single-product bypass (owner-reported 2026-07-21, owner has
  an idea, NOT urgent):** at a furnace (and any make-interface) a player who
  carries materials for exactly ONE product skips the selection interface
  entirely, so the product-click gate never runs. Same class as the
  spacebar/Make-X honour-system gaps. Owner is well aware it is deliberate
  cheating if used; revisit when he raises his approach.
- **D'hide crafting not blocking (owner test 2026-07-21, UNRESOLVED):** needle
  on <colour> dragon leather opens the menu but picking a product does not
  block. Wiki CONFIRMS "Green dragon leather" (our trigger target) is correct
  and layer 1 correctly stays quiet when the material cards are owned, so the
  suspect is the 24 d'hide/leather TWIN names ("Green d'hide body" etc).
  Needs one `interface product raw='...'` debug capture from the d'hide menu
  before changing anything - do NOT guess the strings.
- **Locked-item marking: SHIPPED + VERIFIED 2026-07-18** — Visuals dropdown
  "Mark locked items" (Off/Transparent/Transparent + icon, default
  Transparent). Widget-opacity fade (140) on inventory/bank/bank-side
  containers re-applied on INVENTORY_DRAWITEM + BANKMAIN_BUILD scripts and a
  5-tick sweep; bank-filler badge at 60% via LockedItemIconOverlay
  (WidgetItemOverlay). Same lock rule as blocking incl. exempt list and
  dose folding; stands down with LMS/unreadable state.
- **Cooking via range-click "Cook": FIXED 2026-07-16, VERIFIED in-game 2026-07-18** — the
  SKILLMULTI/SMITHING interface branch now consults node rules first (it
  previously went recipe-only AND didn't log, which is why the owner's
  interface click produced no "kind=interface" line), and every cooking
  rule gained interface-kind twins under BOTH its raw and cooked names
  (58 twins; the interface's product string is unverified so both are
  covered — no collisions exist). Re-test: range -> Cook -> click product
  should now block and log. Residual honor-system: materials-for-exactly-
  one skips the interface (same class as single-bar smelt).
- **Raw cavefish / Camdozaal (open owner decision, from the 2026-07-16 burnt
  research)**: Raw cavefish is prepared with a KNIFE at a Preparation Table
  (Cooking 20), not on a fire/range — so the existing cooking node for it
  ("item-on-object" onto fire/range) probably never fires and needs
  verifying. Its failure item is `Ruined cavefish` (card EXISTS), not a
  burnt item, so it is excluded from burntMapping. Options: (a) leave
  unrestricted, (b) fix the gate to intercept knife-on-cavefish and have the
  burnt toggle require Ruined cavefish, (c) fix the gate but only require
  the cooked card. Check whether other Camdozaal prep-table foods share this
  shape. See docs/burnt_and_crushed_report.md.
- **Burnt food coverage ceiling (informational)**: only 19 of 30 cooking
  rules can ever gain a burnt requirement — the other 11 (trout, salmon,
  tuna, cod, bass, pike, herring, sardine, anchovies, mackerel) all burn
  into the generic `Burnt fish`, which has NO card. If osrs-tcg ever adds
  one, revisit. Beef/bear/rat/ugthanki all share one `Burnt meat` card.
- **Firemaking: game-object "Light" gap (owner-reported, confirmed in code)**:
  the firemaking gate only catches the INVENTORY "Light" item-op and
  Tinderbox-on-logs (item-on-item). Permanently-spawning log OBJECTS with
  their own "Light" option (e.g. at Lumbridge castle) route through
  handleGameObjectInteraction, which only consults node rules — no such
  rule exists, so they are unrestricted. Fix = add object node(s) keyed
  (object name, "light") requiring the Tinderbox + Logs cards, reusing the
  existing firemaking rules; NO new toggles (owner's call). Needs the exact
  in-game object name(s) verified first — check whether they're literally
  "Logs" and whether other locations have the same setup. Note the
  firemaking category currently lives in RecipeCatalog, not
  ResourceNodeCatalog, so decide whether to add a node rule (simplest, but
  the FiremakingMode Just-logs/Both dial lives in checkRecipe) or extend the
  object path to consult recipes.
- **Quest walkthrough-mining pass**: the quest data only captures infobox
  "Items required" + "kills"; interactions hidden in walkthrough prose
  (CotS guard-marking) get patched case-by-case as reported. A systematic
  pass = parse quest walkthrough sections for item-on-NPC / marked-NPC
  steps against tracked names. Until then, treat player reports as data
  bugs: patch quest_cards.json + note in docs/quest_cards_report.md.
- **Card browser in the side panel (NEXT after PvM content — owner spec
  in progress, "we'll work on it as we go")**: grouped view of available
  cards for easier discovery — resources grouped by skill/type, e.g. all
  logs under "Logs" since every skill uses them, but Construction shows
  planks and not logs. Grouping taxonomy is the owner's design call;
  expect iteration.
- **Collection backup + regression detection**: periodically (and via a
  side-panel button) export the DECODED collection as plain JSON to
  .runelite/bronzeman-tcg/collection-backup.json (timestamped; card names
  are the universal key, translatable to any future upstream format). On
  each cache refresh, diff owned names against the last snapshot: if
  previously-owned cards vanish, warn loudly in chat ("N cards missing vs
  backup from <date>"). WARN ONLY, never auto-restore and never write to
  osrs-tcg's storage (we stay read-only on their data) — the collection
  can shrink legitimately (osrs-tcg has debug commands and may grow
  trade/sacrifice mechanics), so make the warning dismissible.
- **Grey model recolor** as a third Visuals choice (dropdown: Outline /
  Grey model / Off, outline default): mutate NPC Model face color arrays
  (getFaceColors1/2/3, packed HSL) to greyscale for locked NPCs. Needs
  original-color snapshot/restore on unlock/toggle/shutdown, and in-game
  iteration for animation edge cases; models are shared per NPC type
  (coherent for us — lock state is per-name). Hub precedent exists but
  expect closer review.
- Side panel design iteration (ask owner what to change first). Rendering
  study notes: "D:\ClaudeFolder\RuneLite Research\osrs-tcg-overlay-techniques.md".
- Time Tracking plugin interop: per-patch crop harvest restriction +
  compost-type discrimination by reading its persisted config state
  (same pattern as the osrs-tcg interop).
- Krystilia require-all difficulty revisit (wilderness bosses).
- **Fishing gear-card edits (owner hand-edit 2026-07-17, incomplete)**: the
  cage "Fishing spot" union is now [Raw lobster, Lobster pot] — Raw dark
  crab was REMOVED pending the owner's plan to split lobster vs dark-crab
  spots by NPC ID (his note in the node). Until then dark-crab spots
  unlock via lobster cards. Big-net union gained "Big fishing net" as a
  gear alternative. Re-add dark crabs when doing the ID split.
- Sailing test pass (see DEFERRED section above).
- **Tracked-name encoding defect (found 2026-07-19)**: four tracked names
  (rosé wines, "grubs à la mode") carry a literal U+FFFD replacement char
  from the snapshot generator; consumables.json preserves them byte-for-byte
  so matching holds. Real fix belongs in scripts/generate_tracked_monsters.py
  encoding handling; regenerate both snapshots + consumables when fixed.
- **Quest enemy variant-name aliases (held from the 2026-07-16 re-derivation)**:
  Cuthbert (Ribbiting Tale, fought as "Cuthbert, Lord of Dread") and Metzli
  (The Final Dawn, fought as "Augur Metzli") have cards but the fought NPC's
  right-click name differs from the card/snapshot key, so adding them would
  gate nothing. To ship: capture the exact menu name in-game (node-lookup
  log), add a snapshot alias (fought name -> card), then add the quest
  monsterCards. Same class as the Kuradal/troll naming traps. See
  docs/quest_enemy_rederivation_report.md.
- Upstream card-gap report to osrs-tcg: Onyx ring; larupia/kyatt/kebbits/
  moths/Herbiboar hunter creatures; blurite/barronite ores; and required
  quest kills with no card (Naiatli, Solus Dellagar, Koschei, the five
  Blood Moon Rises bosses, Red Reef pirate captains).

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
