# Bronzeman TCG — Code Tour

*Everything a new contributor needs to work on this repo: what every directory is for, how the
pieces fit together, how to add a restriction, and how a release goes out.*

Companion to [`HOW_IT_WORKS.md`](HOW_IT_WORKS.md), which explains **what** the plugin does and why.
This one explains **where things live and how to change them**. No prior RuneLite knowledge assumed;
Java is assumed.

Written against `0.3.0` (post-v0.2.13). Line references are `file:line` and drift — the method names
around them are the durable anchors.

---

## Contents

1. [Orientation — read this first](#1-orientation--read-this-first)
2. [The repo map — what every directory is for](#2-the-repo-map--what-every-directory-is-for)
3. [The data pipeline — why half the repo exists](#3-the-data-pipeline--why-half-the-repo-exists)
4. [Core concepts — the four things to understand](#4-core-concepts--the-four-things-to-understand)
5. [How a catalog is created, declared and called](#5-how-a-catalog-is-created-declared-and-called)
6. [The rule data model](#6-the-rule-data-model)
7. [Ownership — what `effectiveOwnedCards()` contains](#7-ownership--what-effectiveownedcards-contains)
8. [The four enforcement surfaces](#8-the-four-enforcement-surfaces)
9. [`onMenuOptionClicked` — the router](#9-onmenuoptionclicked--the-router)
10. [Rule evaluation — the dispatch table](#10-rule-evaluation--the-dispatch-table)
11. [Case study: a fishing spot, end to end](#11-case-study-a-fishing-spot-end-to-end)
12. [Case study: the generic-label trap](#12-case-study-the-generic-label-trap)
13. [The other event subscribers](#13-the-other-event-subscribers)
14. [Config — the keyName contract and migrations](#14-config--the-keyname-contract-and-migrations)
15. [The side panel](#15-the-side-panel)
16. [**Adding a restriction — the contributor's guide**](#16-adding-a-restriction--the-contributors-guide)
17. [Build, run, test](#17-build-run-test)
18. [Releasing to the Plugin Hub](#18-releasing-to-the-plugin-hub)
19. [House rules — things that have drawn blood](#19-house-rules--things-that-have-drawn-blood)
20. [Known boundaries](#20-known-boundaries)
21. [Glossary](#21-glossary)
22. [Appendix: quick reference](#22-appendix-quick-reference)

---

## 1. Orientation — read this first

**What the plugin is.** A challenge-mode RuneLite plugin. The player may only do things whose
matching *card* they have pulled in a separate plugin, [OSRS TCG](https://github.com/Azderi/osrs-tcg)
— a gacha pack-opening plugin. Attacking, looting, equipping, mining, smithing, fishing, thieving,
buying: all gated behind card ownership. It's live on the RuneLite Plugin Hub and has real users.

**The one decision.** There is exactly one question in this codebase:

> *Is this interaction allowed?*

Answered by comparing **a set of card names you own** against **a set of card names this interaction
requires**. Everything else is plumbing — how the owned set is obtained (§7), how the required set is
looked up (§5, §6), which RuneLite events ask the question (§9), and what happens when the answer is
no (§8).

**The two rules that explain most of the code's shape:**

1. **No card exists → never restrict.** If OSRS TCG has no card for something, it can never be
   unlocked, so it must never be locked. `isUnlocked` returns `true` the moment a lookup finds
   nothing. This is what stops the plugin bricking content the TCG doesn't cover.
2. **Ambiguity resolves toward the challenge — except where that would break the game.** Unknown
   data category? Restrict. Can't read the collection? *Stand down loudly* (a special case: silently
   locking everything on an upstream format change would be worse). A false lock is a visible
   annoyance; a false *unlock* is an invisible hole in the whole point of the mode.

**If you only read one section**, make it §16 — that's how you actually add things.

---

## 2. The repo map — what every directory is for

```
bronzeman-tcg/
├── src/main/java/com/bronzemantcg/    52 Java files — the plugin
├── src/main/resources/                11 data files — baked into the jar, read at startup
├── src/test/java/com/bronzemantcg/    11 files — 10 real test classes + 1 dev-client launcher
├── docs/                              32 files — research matrices, generated reports, plans
├── scripts/                           14 files — data generators and cache dumps (dev-time only)
├── build.gradle                       Gradle build; reads the version out of the properties file
├── runelite-plugin.properties         THE version source of truth + Hub manifest
├── CHANGELOG.md                       One entry per release
├── CLAUDE.md / AGENTS.md              Project instructions + handover notes (near-identical)
├── README.md                          Player-facing feature list
└── launch-client.bat                  Owner's local dev-client launcher
```

### `src/main/java/` — the plugin (52 files, ~11,300 lines)

**Three files are 90% of the line count:**

| File | Lines | Role |
|---|---|---|
| `BronzemanTcgPlugin.java` | 3,396 | The only `Plugin` subclass. All event subscribers, all routing, all enforcement. |
| `BronzemanTcgPanel.java` | 3,296 | The side panel. Pure Swing. **Reads catalogs; enforces nothing.** |
| `BronzemanTcgConfig.java` | 888 | A RuneLite config **interface** — 18 `@ConfigSection`s. No logic at all. |

**Ownership — what you have:**

| File | Role |
|---|---|
| `TcgCollectionReader` | The owned-card set. Two input paths (API preferred, config-decode fallback). |
| `TcgStateDecoder` / `TcgStateDto` | The `RLTCG_v2:` base64 → XOR → gzip → JSON chain, and the two fields we read out. |
| `SharedUnlockStore` | Extra unlocks offered by sibling plugins (party/group modes), kept per source. |
| `RecentUnlocksTracker` | Diffs the owned set tick-to-tick to feed the panel's recent-unlocks list. |
| `ExemptionList` | Parses the user's comma-separated exempt names (with `*` wildcards) into a cached snapshot. |
| `ConsumablesCatalog` | Food/potion name lists, merged into the owned set when Food Settings allows. |

**Existence — what has a card at all:**

| File | Resource | Contents |
|---|---|---|
| `CardNameCatalog` (abstract base) | — | `entityName → [cardNames]`, plus dose-suffix folding. |
| `TrackedMonsterCatalog` | `tracked_monster_names.json` | 1,198 NPCs ← 1,225 Monster cards. |
| `TrackedItemCatalog` | `tracked_item_names.json` | 5,149 items, 1:1. |

**Rules — what an interaction requires:**

| File | Resource | Scale |
|---|---|---|
| `ResourceNodeCatalog` | `resource_nodes.json` | 431 nodes / 22 categories — gathering, thieving, hunter, slayer, farming, sailing, cooking. |
| `RecipeCatalog` | `recipe_nodes.json` | 638 recipes — herblore 164, fletching 157, smithing-forge 147, crafting 102, enchanting 39, firemaking 14, smelt 14, cooking 1. |
| `QuestCatalog` / `QuestNpcIndex` | `quest_cards.json` | Quest requirements; the index also drives the "don't hide a started quest's NPC" override. |

**Panel-only data (never consulted by enforcement):**

| File | Resource |
|---|---|
| `ContentCatalog` | `content_cards.json` — raid/boss rosters |
| `MonsterAreaCatalog` | `monster_areas.json` — monsters grouped by area |
| `ImportantUnlocksCatalog` | `important_unlocks.json` — milestone-card checklist |
| `CardKnowledgeCatalog` | `card_knowledge.json.gz` — per-card wiki knowledge for the card browser |

**Enums — one per config dropdown** (24 of them): `MiningMode`, `WoodcuttingMode`,
`FishingRestrictionMode`, `HunterMode`, `HerbloreMode`, `CookingMode`, `CraftingMode`,
`SmeltingMode`, `SmithingMode`, `FletchingMode`, `SlayerMode`, `ThievingMode`, `StallThievingMode`,
`RunecraftingMode`, `SailingUpgradeMode`, `FarmingRakeMode`, `BurntFoodMode`, `BankingMode`,
`LockState`, `NpcVisibilityMode`, `LockedItemMarkMode`, `FoodSettingsMode`, `CardRequirement`,
`HerbloreRecipeStage`, plus `HunterTrapType` / `HunterAreaSpecies` (lookup tables, not settings).

The pattern is deliberate and worth internalising: **every difficulty knob is an enum + a
`@ConfigItem` returning it**, consumed in exactly one place in the plugin. Adding a difficulty
option never touches the data files.

**Odds and ends:** `BronzemanTcgOverlay` (grey outline on locked NPCs), `LockedItemIconOverlay`
(bank-filler badge), `WrapLayout` (a standard Swing `FlowLayout` fix that wraps properly inside a
scroll pane — vendored utility, not project logic).

### `src/main/resources/` — the shipped data

Everything here is copied into the jar and read once at startup by its catalog. **Nothing is written
at runtime** — the plugin never modifies its own resources and never makes network requests.

| File | Size | Consumer |
|---|---|---|
| `resource_nodes.json` | 287 KB | `ResourceNodeCatalog` |
| `recipe_nodes.json` | 252 KB | `RecipeCatalog` |
| `tracked_item_names.json` | 247 KB | `TrackedItemCatalog` |
| `quest_cards.json` | 78 KB | `QuestCatalog` |
| `important_unlocks.json` | 74 KB | `ImportantUnlocksCatalog` |
| `tracked_monster_names.json` | 52 KB | `TrackedMonsterCatalog` |
| `card_knowledge.json.gz` | 650 KB | `CardKnowledgeCatalog` — the only gzipped one |
| `monster_areas.json` | 14 KB | `MonsterAreaCatalog` |
| `consumables.json` | 9 KB | `ConsumablesCatalog` |
| `content_cards.json` | 3 KB | `ContentCatalog` |
| `panel_icon.png` | 236 B | Toolbar nav button |

### `docs/` — three distinct kinds of file

This is the directory that looks like clutter until you know the taxonomy. There are three types,
and the naming tells you which:

| Pattern | What it is | Read it when |
|---|---|---|
| `*_actions.json` | **Input.** A hand-reviewed research matrix — the source of truth a generator consumes. Editable. | You're changing what a skill requires. |
| `*_report.md` | **Output.** Written *by* a generator. Coverage, exclusions, gaps. | You want to know what's covered and what was deliberately left out. |
| `plan_*.md` | **Decision record.** The plan agreed with the owner before a feature was built, including rejected alternatives. | You're wondering *why* something is shaped the way it is. |

Plus a few standalone research documents (`lms_detection_research.md`,
`burnt_and_crushed_report.md`, `quest_enemy_rederivation_report.md`) that captured a one-off
investigation.

**The important ones:**
- `plan_skills_sweep.md` — the governing roadmap for the current 0.3.0 push.
- `fishing_actions.json` + `fishing_spots_report.md` — the cleanest example of the pipeline (§11).
- `resource_nodes_report.md` — coverage and deliberate exclusions for the biggest data file.
- `slayer_rebuild_report.md`, `thieving_chests_report.md`, `sailing_nodes_report.md` — same, per area.
- `api_merge_checklist.md` — the osrs-tcg PluginMessage API integration.

**A `*_report.md` is never hand-edited.** If it's wrong, the generator or its matrix is wrong.

### `scripts/` — the generators

Dev-time only. **None of this ships**; the jar contains no scripts and makes no network calls.
Mixed Python and Node by history, not by design. Four categories:

| Category | Files | What they do |
|---|---|---|
| **Upstream sync** | `generate_tracked_monsters.py` | Regenerates *both* tracked-name snapshots from osrs-tcg's `Card.json`. Run when upstream ships new cards. This is the maintenance contract with the other plugin. |
| **Category rebuilds** | `rebuild_fishing_data.js`, `rebuild_herblore_data.js`, `rebuild_hunter_data.js`, `rebuild_slayer_data.js`, `rebuild_thieving_chest_data.js`, `rebuild_fletching_data.py`, `rebuild_cooking_roles.py` | Each regenerates **exactly one category** inside a resource file from its matrix, validates every card name, and rewrites its report. Idempotent — safe to re-run. |
| **Panel data builds** | `build_card_knowledge.js`, `build_important_unlocks.js`, `build_monster_areas.js` | Generate the panel-only resources. `build_monster_areas.js` deliberately writes a *review seed* into `docs/` and never overwrites the live owner-curated resource. |
| **Cache dumps** | `FishingSpotCacheDump.java`, `SlayerCacheDump.java` | **Developer-only, run inside a RuneLite client.** They read the game's local cache / DB tables to extract ground truth (fishing spot menu options; slayer master task tables). Not part of the build. |

`fix_multiproduct_collisions.py` is a one-off historical fixer (the 2026-07-21 crafting collision
fix). It has already been applied; it's kept as a record of what was done.

`scripts/wiki_cache/` holds cached raw wiki fetches so re-running a generator hits the wiki zero
times.

### `src/test/` — and one significant gotcha

Ten real JUnit classes, ~26 test methods. They're not unit tests of the plugin (which needs a live
client); they're **data-integrity and pure-logic tests**:

| Test | Guards |
|---|---|
| `HerbloreRecipeDataTest`, `HunterResourceDataTest`, `ThievingChestDataTest`, `QuestCatalogTest`, `CardKnowledgeCatalogTest` | The shipped JSON parses, has no trigger collisions, and every card name resolves. |
| `HerbloreModeTest`, `HunterMappingsTest`, `LockedItemMenuPolicyTest`, `ExemptionListTest`, `SharedUnlockStoreTest` | Pure decision logic — mode → enforcement, wildcard matching, store semantics. |

**The gotcha:** `BronzemanTcgPluginTest.java` is **not a test.** It has no `@Test` methods. It's a
`main()` that sideloads the plugin and starts RuneLite:

```java
ExternalPluginManager.loadBuiltin(BronzemanTcgPlugin.class);
RuneLite.main(args);
```

It lives in `src/test/` because that's where the RuneLite client dependency is available, and
`build.gradle`'s `run` task points at it. **That's the dev client.** Don't delete it as dead code.

---

## 3. The data pipeline — why half the repo exists

This is the single idea that makes the repo legible. Almost every restriction in the plugin was
produced by the same four-stage pipeline:

```
  docs/<skill>_actions.json          ← 1. MATRIX   hand-reviewed, the source of truth
              │
              ▼
  scripts/rebuild_<skill>_data.js    ← 2. GENERATOR  validates + transforms; throws on error
              │
      ┌───────┴────────┐
      ▼                ▼
 src/main/resources/   docs/<skill>_report.md   ← 3. SHIPPED DATA + 4. REPORT
```

**Why not just hand-edit the JSON?** Because stage 1 — finding out what the game actually calls
things — is the expensive, error-prone step, and a mistake there is *silent*. A rule keyed on a
wrong menu string doesn't error; it just never fires, which is indistinguishable from "restrictions
are off". So the research gets captured once in a reviewable matrix, and a generator mechanically
turns it into rules **while validating every card name against the tracked snapshots** and rejecting
duplicate keys.

The generators are strict on purpose. `rebuild_fishing_data.js` hard-codes RuneLite's 27
`FishingSpot` constants and **throws** if the matrix reviews a spot that doesn't exist *or* fails to
review one that does. You cannot silently forget a spot.

**Consequences for you as a contributor:**
- If a category has a matrix + generator, **edit the matrix and re-run the generator.** Hand-editing
  the resource file works until someone re-runs the generator and silently reverts you.
- If a category has no generator (much of `resource_nodes.json` is hand-curated), edit the JSON
  directly — but validate card names yourself against `tracked_item_names.json`.
- Which is which? Check the node's `notes` field and the report files. Generated categories say so.

---

## 4. Core concepts — the four things to understand

Everything else follows from these.

**1. The three-part lookup key.** Every rule is found by `kind | name | option`, all lower-cased.
*Kind* is what you clicked (an object, an NPC, an item-on-object, an inventory op, a make-interface
product). *Name* is the entity. *Option* is the menu verb. A miss returns `null`, and **`null` means
unrestricted**.

**2. The group algebra.** Requirements are lists of card groups:

> **Cards inside a group are OR. Groups are AND.**

"A pickaxe (any tier) plus this ore" is two groups. That one primitive expresses every rule in the
codebase.

**3. Roles + modes.** A group can carry a `role` label (`"tool"`, `"catch"`, `"loot"`, `"monsters"`,
`"material"`…). A config mode returns a set of roles to *skip*. That's how one dropdown changes
difficulty without touching data.

**4. `event.consume()` is the entire enforcement mechanism.** Consuming a `MenuOptionClicked` tells
RuneLite "this click is handled, don't pass it to the game" — the packet is never sent. One method
call. Everything else in the plugin is deciding *whether* to call it.

---

## 5. How a catalog is created, declared and called

You never construct a catalog. Here's the full chain, using `ResourceNodeCatalog`.

### Step 1 — the class declares itself injectable

```java
@Slf4j
@Singleton                                    // Guice: build exactly ONE, share it
public class ResourceNodeCatalog
{
    @Inject                                   // Guice: use THIS constructor
    public ResourceNodeCatalog(Gson gson)     // Guice: and supply a Gson for me
    {
        load(gson);                           // read the JSON immediately, once
    }
```
`ResourceNodeCatalog.java:37-61`

- `@Singleton` — Guice (the dependency-injection container RuneLite uses) creates one instance for
  the whole client. Matters because `load()` parses a 287 KB file; you want that once, not per caller.
- `@Inject` on the constructor — marks it as the one Guice should call.
- The `Gson` parameter — RuneLite already has a configured `Gson` bound, so Guice passes it in.

**There is no `new ResourceNodeCatalog(...)` anywhere in the codebase.** Grep for it; there are no
call sites. The object exists because something declared it wanted one.

### Step 2 — loading, in the constructor

`load()` at `ResourceNodeCatalog.java:130-201`:

1. `getClass().getResourceAsStream("/resource_nodes.json")` reads **out of the jar**, not off disk.
   Anything under `src/main/resources/` is jar-packed at build time and addressed by leading-slash
   path. Missing file → warning logged, catalog stays empty → everything unrestricted, not a crash.
2. Gson deserialises into the private `Snapshot` / `NodeDto` classes at the bottom of the file
   (`:351-369`). **Those DTOs are the schema** — they're where you look to find out what fields the
   JSON supports. Fields not named there are silently ignored; DTO fields absent from the JSON stay
   null.
3. Each node is flattened into `HashMap<String, Rule>` keyed `"kind|name|option"` (`:120-128`). One
   node with three options becomes three entries pointing at the *same* `Rule` object.
4. Nodes carrying `objectIds` are keyed by ID instead — `"kind|#12345|option"` (`:171-180`). Exists
   for Thieving chests, which are all literally named "Chest".
5. The map is wrapped unmodifiable. After the constructor returns the catalog is **immutable**,
   which is why it's safe to read from the render thread with no locks.

### Step 3 — the plugin declares it wants one

```java
@Inject
private ResourceNodeCatalog nodeCatalog;
```
`BronzemanTcgPlugin.java:226-227`

Field injection. Guice walks every `@Inject` field when the plugin is constructed and assigns them.
By the time `startUp()` runs, they're populated. There are 26 such fields (`:187-272`).

The panel gets the *same instances* by a different route — passed as constructor arguments in
`installPanel()` (`:422-437`). Deliberate: the panel is created on the Swing thread and isn't itself
a Guice object, so the plugin hands its own singletons through.

### Step 4 — the call site

```java
ResourceNodeCatalog.Rule rule = nodeCatalog.find(kind, name, option, targetId);
```
`BronzemanTcgPlugin.java:2024`, inside `evaluateNodeRule`.

`find` (`ResourceNodeCatalog.java:86-104`) is three lines: try the ID key, then lower-case +
dose-strip the name and try the name key. Miss → `null` → unrestricted.

### Nine catalogs, one pattern

`RecipeCatalog`, `QuestCatalog`, `ContentCatalog`, `MonsterAreaCatalog`, `ImportantUnlocksCatalog`,
`CardKnowledgeCatalog`, `ConsumablesCatalog`, `TrackedMonsterCatalog`, `TrackedItemCatalog` are
identical in shape. Learn one, you've learned all nine. `CardKnowledgeCatalog` is the only one
reading a gzipped resource (`GZIPInputStream`, `:37`) — its uncompressed JSON is several MB and the
jar ships to every Hub user.

---

## 6. The rule data model

Defined in `ResourceNodeCatalog.CardGroup` (`:294-349`) and shared by both rule catalogs:

> **A group is satisfied by owning ANY ONE of its cards. EVERY group must be satisfied.**

A group may also carry:
- **`role`** — how config modes drop whole requirements (§10).
- **`label`** — display text only, used by the panel to render Konar's task/location rows readably.
  Never used by enforcement.

`Rule.missingRequirements(owned, excludedRoles, forceAllInGroups)` (`:265-291`) evaluates it: skip
excluded roles, then check each group any-of — or, with `forceAllInGroups`, demand every card in the
group (used by "All items" stall thieving and "All catches" fishing). It returns **display strings**;
an any-of group renders as `"Raw shrimps / Raw anchovies"` so the chat message shows the player their
alternatives.

`RecipeCatalog.Recipe` (`RecipeCatalog.java:191-236`) is the same idea split differently:
`inputGroups` + a single `output`, evaluated by `missingRequirements(owned, enforceInputs,
enforceOutput)`. That boolean pair is how every processing-skill dropdown works — Smelting's
*Ore / Both* is literally `enforceInputs = true; enforceOutput = (mode == BOTH)`.

### Lookup keys by kind

| Catalog | Kind | Key is |
|---|---|---|
| Node | `object` | game object name + menu option (`"oak tree" + "chop down"`) |
| Node | `npc` | NPC name + option (pickpocketing, slayer masters) |
| Node | `fishing-spot` | **RuneLite `FishingSpot` enum name** + option (`"SHRIMP" + "net"`) |
| Node | `item-on-object` | used item + target object (`"raw shrimps" + "fire"`) |
| Node | `inventory` | item name + item op (`"bird snare" + "lay"`) |
| Node | `interface` | product name + `"*"` |
| Recipe | `item-on-item` | used item + target item (`"tinderbox" + "oak logs"`) |
| Recipe | `item-on-object` | used item + object (`"iron ore" + "furnace"`) |
| Recipe | `interface` | product name + optional declared target |
| Recipe | `spell-on-item` | target jewellery alone |

---

## 7. Ownership — what `effectiveOwnedCards()` contains

Every lock check funnels through one method, `BronzemanTcgPlugin.java:2880-2925`. It's the union of
**five** sources:

```
effectiveOwnedCards() =
      collectionReader.getOwnedCardNamesLowerCase()   // your real TCG collection
    + exemptions().getCardNamesLowerCase()            // the user's exempt list
    + sharedUnlockStore.getSharedCardNamesLowerCase() // sibling-plugin party unlocks (if enabled)
    + {"coins"}                                       // if Coin Settings = unlocked
    + consumablesCatalog food / potion names          // if Food Settings allows them
```

**Exemptions are modelled as ownership, not as a bypass.** Earlier versions checked the exempt list
only at loot pickup, so an exempt item still failed *recipe* requirements. Folding exempt names into
the owned set means every check honours them, in one place.

**The result is cached by reference identity** (`:2898-2900`) — it compares with `!=`, not
`.equals()`. This works because `TcgCollectionReader` and `SharedUnlockStore` both hand back *the
same immutable Set instance* until their contents change. The menu-hiding path calls this many times
per frame; a `HashSet` rebuild per call would be a real frame-rate cost. Same trick powers
`lockedItemCache` (`:2762-2780`).

### The two ways in

`TcgCollectionReader` has a preferred path and a fallback:

1. **PluginMessage API.** The plugin posts `PluginMessage("osrstcg", "query-owned-names")` from a
   game tick (`:506-515`) — *from a tick, not from `startUp()`, because RuneLite only registers
   `@Subscribe` methods after `startUp` returns and `EventBus.post` is synchronous, so a reply to a
   query sent from `startUp` would arrive before anything is listening.* osrs-tcg replies with
   `owned-names` and pushes `owned-names-changed` on every collection change. Once any payload
   lands, polling stops forever (`:514`) and unlocks are instant.
2. **Config decode.** `getRSProfileConfiguration("osrstcg", "state")` → `TcgStateDecoder.decode` →
   Gson → `cardInstances[].cardName`. Cached 5 seconds. Carries anyone on a pre-API osrs-tcg build.

`isStateAvailable()` returning false is the **stand-down signal**: `isEnforcementBypassed()`
(`:2626-2629`) makes every enforcement path return early, and a repeating chat warning tells the
player. Changed 2026-07-18 from fail-closed, which would have locked every user out of the game on
any upstream format change.

### The outbound API

The plugin also *exposes* one, under its own `bronzemantcg` namespace (`:130-137`): a sibling plugin
posts `shared-unlocks` with `source` + `cardNames`, and those names count as owned. Gated behind
`acceptSharedUnlocks` so nothing external can loosen restrictions behind the player's back. The
constants are copied, not imported — Hub plugins can't see each other's classes.

---

## 8. The four enforcement surfaces

The plugin blocks things in four places, and they can't be collapsed into one.

| Hook | Method | Timing | Effect |
|---|---|---|---|
| `RenderCallback.addEntity` | `:640-659` | Per frame, per entity | Return `false` → NPC never enters the scene (no model, **no clickbox**). NPCs only — ground items don't route through here (verified in-game). |
| `MenuEntryAdded` | `:681-699` | As each menu entry is created | Remove blocked options, so a locked tree simply has no "Chop down". |
| `PostMenuSort` (priority −1.0) | `:1746-1770` | After the menu is assembled and sorted | Inventory options only. Runs *after* Menu Entry Swapper's subscriber, so a user's left-click "Drop" promotion survives before blocked ops are stripped. |
| `MenuOptionClicked` | `:1251-1301` | On the click | `event.consume()`. The final guard. |

**Why the split.** `MenuEntryAdded` firing early is what makes hiding possible, but it's *too* early
for inventory items: removing "Wear"/"Drink"/"Use" there stops Menu Entry Swapper from reliably
promoting a Drop entry. So inventory entries are explicitly exempted from the early pass
(`isInventoryMenuVisibilityExempt`, `:1728-1737`) and handled late instead. A real bug fix encoded as
an architectural split.

**Why they can't drift.** Every hiding path calls the *same* `evaluateNodeRule` /
`isBlockedItemName` helpers the clicking path uses (`:2010-2016` says so explicitly). "It's hidden
but clicking it works" is structurally impossible.

---

## 9. `onMenuOptionClicked` — the router

`:1251-1301`. Two tiers:

```java
if (isEnforcementBypassed()) return;              // LMS, or collection unreadable

NPC npc = event.getMenuEntry().getNpc();
if (npc != null) { handleNpcInteraction(event, npc); return; }   // fast path

switch (event.getMenuAction()) { ... }            // everything else
```

| MenuAction | Handler | Line | Notes |
|---|---|---|---|
| *(entry has an NPC)* | `handleNpcInteraction` | 1305 | Attack, spell/item-on-NPC, pickpocket, fishing, slayer, Master Farmer |
| `GROUND_ITEM_*`, `WIDGET_TARGET_ON_GROUND_ITEM` | `handleGroundItemInteraction` | 1395 | Plain clicks only count for "Take"; telegrab always counts |
| `GAME_OBJECT_*` | `handleGameObjectInteraction` | 1511 | Node lookup, ID-aware |
| `WIDGET_TARGET_ON_GAME_OBJECT` | `handleItemOnGameObject` | 1530 | Splits `"Raw shrimps -> Fire"`; nodes first, then recipes |
| `CC_OP`, `CC_OP_LOW_PRIORITY` | `handleWidgetOp` | 1551 | The busiest — bank, shop, make-menus, GE, inventory |
| `WIDGET_TARGET` | `handleUseSelected` | 1826 | "Use" on an inventory item |
| `WIDGET_TARGET_ON_WIDGET` | `handleWidgetOnWidget` | 1841 | Item-on-item and spell-on-item |

### `handleWidgetOp` in detail (`:1551-1695`)

Discriminates on `WidgetUtil.componentToInterface(entry.getParam1())` — the interface **group ID** —
plus the option text, in this order:

1. **Sailing menus** — prefers the widget's real item ID over its label; emits focused debug
   evidence when no ID is available, rather than guessing.
2. **`withdraw*` / `deposit*`** — by option text, not group, because bank-side panels aren't the
   inventory group. Withdraw gated unless Banking = Full; Deposit only when Banking = Off.
   ("Deposit Only" treats the bank as a holding pen.)
3. **`SHOPMAIN` buy** — needs unlocked Coins *and* the item's card. **`SHOPSIDE` sell** — only Coins
   (disposal is always allowed).
4. **`SKILLMULTI` / `SMITHING`** — the make-X product click. Node rules first (cooking lives there),
   then recipes.
5. **`INVENTORY`** → `handleInventoryOp`.
6. **`CHATBOX` while the GE is open** — best-effort GE search blocking; keyboard flows bypass it.
7. **Fallback: any option starting with a make-verb** (`MAKE_VERBS`, `:156-158`) — covers production
   interfaces we don't match by group ID (furnace, shipwright's bench). Prefix-matched, so `Make-5`
   and `Smelt-1` are covered.

---

## 10. Rule evaluation — the dispatch table

`evaluateNodeRule(kind, name, option, targetId)` at `:2022-2086` turns a `Rule` into a decision.
It's a dispatch on `rule.category`:

```java
Rule rule = nodeCatalog.find(kind, name, option, targetId);
if (rule == null) return null;                       // no rule = allowed

if (mining/woodcutting)           → evaluateGatheringRule      :2241
if (fishing)                      → evaluateFishingRule        :2295
if (hunter-salamanders)           → evaluateSalamanderRule     :2088
if (hunter-birds / -chins)        → evaluateAreaTrapRule       :2105
if (hunter-butterflies/-implings) → evaluateHunterNetRule      :2124
if (runecrafting)                 → evaluateRunecraftingRule   :2138
if (thieving-stalls/-chests)      → forceAllInGroups from StallThievingMode
else                              → excludedRolesFor(category) :2343
```

The **default branch** is the generic one. Six categories need bespoke evaluators because they
consult *game state*, not just config:

- **Gathering** (`:2241`) needs carried tools. The tool half is deliberately strict — it blocks if
  **any** carried pickaxe/axe is locked, because the client silently uses the best tool you carry,
  so one unlocked bronze pickaxe must not alibi a locked dragon one.
- **Fishing** (`:2295`) — see §11.
- **Salamanders** (`:2088`) uses the clicked object's ID to distinguish the five young trees, whose
  names are identical.
- **Bird/chin traps** (`:2105`) consult `currentRegionId()` for which species are catchable here.
- **Runecrafting** (`:2138`) inspects the actual carried essence variant, falls back to any valid
  essence when it's all inside pouches, and special-cases GotR via `VarbitID.GOTR_IS_PLAYING`.
- **Stalls/chests** flip `forceAllInGroups` for "All items".

### `excludedRolesFor` (`:2343-2452`) — read this to understand any dropdown

Returns one of three things:
- `null` → category switched off → **no restriction**
- a set of role names → skip those groups
- `Collections.emptySet()` → enforce everything

The default case (`:2447-2450`) returns `emptySet()` — **an unknown category restricts fully.** If a
data file ships a category this build has no toggle for, you find out by hitting the restriction, not
by silently losing enforcement.

Slayer, as an example (`:2417-2436`):
```java
SlayerMode.OFF    → null                              // no restriction
SlayerMode.MASTER → excludes {"monsters","superiors"} // master card only
SlayerMode.FULL   → excludes {"superiors"} unless the superiors opt-in is on
```
One dropdown, zero data changes.

### `checkRecipe` (`:2455-2586`)

Same shape for processing skills: dispatch on `recipe.category` to compute `enforceInputs` /
`enforceOutput` from that skill's dropdown, plus three special layers:
- **Firemaking** enforces inputs to *compute* the Tinderbox requirement, then filters down to
  Tinderbox alone (`:2567-2571`) — the logs were already gated when you obtained them.
- **Crushed gem** rides on top of crafting for gems that can shatter (`:2572-2577`).
- **Herblore** is the only one consulting per-recipe data:
  `mode.enforcesOutput(recipe.herbloreStage)` lets *Require Unfinished* gate the output for
  unfinished-potion recipes only.

---

## 11. Case study: a fishing spot, end to end

The best example of the full pipeline — research matrix, generator, resource file, a RuneLite core
mapping, and a bespoke evaluator.

### Stage 1 — the matrix: `docs/fishing_actions.json`

```json
{
  "spot": "SHRIMP",
  "options": ["Net", "Small Net"],
  "toolGroups": [["Small fishing net"]],
  "catches": ["Raw shrimps", "Raw anchovies"]
}
```

`spot` is a **RuneLite `FishingSpot` enum constant**, not an NPC name. `options` are exact menu
strings, audited from the local NPC cache by `scripts/FishingSpotCacheDump.java`. Card names are
exact OSRS TCG names; uncarded tools and catches are omitted, never guessed.

### Stage 2 — the generator: `scripts/rebuild_fishing_data.js`

- Hard-codes RuneLite 1.12.33's 27 `FishingSpot` values and **throws** if the matrix reviews an
  unknown spot *or* misses a real one (`:41-47`).
- Turns `toolGroups` into `role: "tool"` groups and `catches` into one `role: "catch"` group.
- Rejects duplicate `spot|option` keys.
- Validates every card against `tracked_item_names.json`.
- Regenerates **only** the `"fishing"` category and rewrites `docs/fishing_spots_report.md`.

### Stage 3 — the shipped data (34 fishing nodes)

```json
{
  "category": "fishing",
  "kind": "fishing-spot",
  "name": "SHRIMP",
  "options": ["Net", "Small Net"],
  "requiredCardGroups": [["Small fishing net"], ["Raw shrimps", "Raw anchovies"]],
  "groupRoles": ["tool", "catch"],
  "notes": "RuneLite FishingSpot.SHRIMP; options verified from the local RuneLite NPC cache."
}
```

`load` turns this into two map entries — `fishing-spot|shrimp|net` and
`fishing-spot|shrimp|small net` — both pointing at one `Rule` with two groups.

### Stage 4 — the click

Fishing spots **are** NPCs, so they arrive on the NPC path. But their display names are useless —
dozens of unrelated spots share the name "Fishing spot". So before the generic NPC lookup:

```java
FishingSpot fishingSpot = FishingSpot.findSpot(npc.getId());
if (fishingSpot != null) {
    checkNodeRule(event, KIND_FISHING_SPOT, fishingSpot.name(), cleanOption);
    return;
}
```
`:1346-1352`

`net.runelite.client.game.FishingSpot` is **RuneLite core's** maintained NPC-ID → spot-group mapping
(150 IDs → 27 groups). Using it rather than copying IDs means RuneLite's own updates keep us current
for free, and `DARK_CRAB` stays distinct from `LOBSTER` instead of collapsing into a name-keyed union.

### Stage 5 — evaluation: `evaluateFishingRule` (`:2295-2335`)

Bespoke because tools are conditional on what you're *carrying*:

```java
FishingRestrictionMode.OFF → null

// 1. Tools: for each role:"tool" group, check only the inputs actually carried.
for each tool group:
    for each carried item (carriedFishingInputs):
        if the group lists it AND it's locked AND not exempt → missing

// 2. Catches: skipped in TOOLS_ONLY.
CARD_REQUIRED → any-of the catch group
ALL_CATCHES   → forceAllInGroups: every carded catch in the union
```

The "only check what's carried" rule stops the plugin inventing a Fishing bait requirement for
someone net-fishing. `carriedFishingInputs` is rebuilt on `ItemContainerChanged` (`:1430-1455`) —
**names only, not lock states**, so a card pulled mid-session applies immediately without waiting for
an inventory event.

### Stage 6 — the deliberate exception

Fishing options are **never hidden** from menus:

```java
// Fishing restrictions remain click-only. Fishing spots have several valid
// methods and the owner wants those choices visible even while their cards
// are locked; selecting one still runs the normal blocking path below.
if (FishingSpot.findSpot(npc.getId()) != null) return false;
```
`:749-755`, inside `shouldHideEntry`. An owner design ruling, encoded as three lines with the
reasoning inline.

---

## 12. Case study: the generic-label trap

Worth understanding because it shaped the whole interface-recipe design.

**The problem.** The knife menu labels *every* crossbow stock tier "Crossbow stock" — never "Willow
stock". So eight recipes all wanted the key `interface|crossbow stock|*`, `HashMap.put` meant only
the last survived, every tier was gated on Magic's card, and seven rules silently never fired. The
obvious fix — read the product widget's item ID — doesn't work: an in-game capture proved
`widgetItemId == -1` on every interface click.

**The fix, in two parts:**

1. **`RecipeCatalog.load` counts interface names up front** (`RecipeCatalog.java:120-130`) and
   withholds the `ANY_TARGET` catch-all key from any name used by more than one recipe (`:172-179`).
   Uniquely-named products still match by name alone; shared names must match on their declared
   target. Automatic — any future generic-label family is safe by default, without anyone
   remembering this trap.
2. **The plugin remembers what opened the menu.** `handleWidgetOnWidget` stores the item-on-item pair
   in `lastUsedItemA/B` with a tick stamp (`:1854-1856`), and `resolveInterfaceMaterial`
   (`:1920-1935`) asks `recipeCatalog.findExact` — *exact, no any-target fallback, because a
   fallback would match every candidate and prove nothing* — which half has a real rule for this
   product. A ~100-tick (60 s) window stops a remembered material leaking into an unrelated menu
   opened later.

**Unresolvable material = no rule = no block.** An owner ruling: a false block is worse than a
missed one here.

`logInterfaceProduct` (`:1937-1949`) is the permanent debug instrument from this episode — every
interface product click logs `raw`, `stripped`, `widgetItemId`, `itemName` at debug level, so the
next "why doesn't this fire" is answerable from a log capture rather than a code change.

---

## 13. The other event subscribers

Fourteen `@Subscribe` methods. The ones in §8/§9 are the interesting ones; here's the rest:

| Subscriber | Line | Purpose |
|---|---|---|
| `onGameStateChanged` | 457 | Arms the login greeting; re-arms on `LOGIN_SCREEN` so world-hopping doesn't re-greet. |
| `onGameTick` | 884 | The heartbeat. Recent-unlock diffing, welcome/reminder countdowns, item-mark sweep every 5 ticks, quest-state capture every 25, panel refresh every 5. |
| `onConfigChanged` | 941 | Cross-setting reactions: unlocking item usage turns item marking off; Duelist City toggling sweeps players; shared-unlocks toggling clears or re-queries. |
| `onRuneScapeProfileChanged` | 1142 | Invalidates the collection, clears shared unlocks, re-arms the API query. Stops one account's collection leaking into another. |
| `onPluginMessage` | 1198 | Two APIs in one: our `bronzemantcg` namespace for shared unlocks, osrs-tcg's `osrstcg` for owned names. |
| `onItemContainerChanged` | 1430 | Rebuilds carried pickaxe / axe / fishing-input / inventory-name caches. Watches `INV` **and** `WORN`. |
| `onScriptPostFired` | 2663 | `INVENTORY_DRAWITEM` / `BANKMAIN_BUILD` reset widget opacity, so re-apply the locked-item fade. |
| `onWidgetLoaded` | 2674 | Shops build stock on open; fade it immediately. |
| `onPlayerSpawned/Changed/Despawned` | 993-1021 | Duelist City Mode only. |

**Duelist City Mode** (`:991-1140`) is the odd one out — cosmetic, not a restriction. It fakes Mystic
cards (item 27645) as a 2h weapon on every player, client-side. It must snapshot each player's real
weapon/shield **and** their eight weapon-stance animation IDs before overwriting, keyed by scene
index, because `PlayerComposition.setHash()` rebuilds the model but not the pose.
`logLocalStanceOnChange` (`:1030-1054`) is the capture tool that produced `MYSTIC_STANCE`.

---

## 14. Config — the keyName contract and migrations

`BronzemanTcgConfig` is an interface with no implementation. RuneLite reads the annotations,
generates the settings UI, and manufactures an object answering the methods. `@Provides` at
`:3391-3395` is how the plugin gets one. 18 `@ConfigSection`s: General, Visuals, External Plugins,
then one per skill alphabetically.

**Two rules that have both drawn blood:**

**1. Never rename a `keyName`.** It's the storage key. Renaming wipes real players' settings. Display
names and descriptions are safe to change; keyNames are a public contract now the plugin is on the
Hub.

**2. Never give a user-editable field a non-empty `default`.** RuneLite **re-injects** a non-empty
default whenever the stored value is cleared, and auto-unsets values equal to the default. That's why
`lootExemptNames` kept re-adding "Coins" after every update — a real player-reported bug. The list
default is now `""` and Coins lives in its own toggle.

### The migration methods (`:2940-3275`)

Five one-shot migrations run in `startUp()`:

| Method | Guard | Covers |
|---|---|---|
| `migrateExemptList` | `exemptListMigrated` flag | Coins out of the free-text list into its own toggle |
| `migrateNpcVisibility` | `npcVisibilityMigrated` flag | 3 NPC toggles → dropdown, plus the item-settings pass |
| `migrateSkillToggles` | **unguarded, self-disarming** | Fletching, mining, woodcutting, crafting, compost, smelt/smith, firemaking, slayer, cooking, herblore |
| `migrateFishingMode` | `fishingModeMigrated` flag | `ANY_OF`/`REQUIRE_ALL` → the exact-spot modes |
| `migrateHunterMode` | `hunterModeMigrated` flag | Six Hunter controls → one dial |

Two patterns. **Flag-first**: guarded migrations write their flag *before* doing any work, so a crash
mid-way can't re-run against half-migrated data. **Self-disarming**: `migrateSkillToggles`
deliberately has no flag — each mapping reads a retired key then unsets it, so it can run repeatedly
and only ever acts once. Not laziness: the fletching mapping originally sat inside
`migrateNpcVisibility`'s guard, where players who had already run 0.2.1 would never have received it.

**The governing principle: only an explicit lenient choice carries over.** If a stored value is
literally `"false"`, map it to the new off value. Everyone else gets the new (stricter) default.
Forcing a restriction back on that a player deliberately disabled could brick their gameplay;
under-restricting is one click to fix.

---

## 15. The side panel

`BronzemanTcgPanel` is 3,296 lines of Swing and **enforces nothing**. It reads the same catalogs and
renders progress. Tabs: Quests / Slayer / PvM / Rumours / Recent / Shared / Important / Settings,
switched by `CardLayout` so selection changes rebuild nothing.

**The threading contract** — the one piece of concurrency discipline in the project:

- **Client thread** reads game state. `captureCompletedQuests()` (`:924-939`) walks every `Quest`
  enum on the client thread and hands the panel a frozen `Set<String>`.
- **Shared executor** builds snapshots. `requestRefresh()` (`Panel:520-546`) runs `buildSnapshot()`
  off-thread — walking 6,376 cards against the owned set is not EDT work.
- **Swing EDT** renders. `finishRefresh` is `invokeLater`'d.

Refreshes coalesce: an `AtomicBoolean` pair (`refreshRunning` / `refreshAgain`) collapses every
request arriving during an in-flight build into exactly one follow-up, so game ticks and
PluginMessage pushes can't flood either thread.

Two more details:
- **`panelGeneration`** (`Plugin:292`, `:415-420`) guards the enable-then-immediately-disable race.
  Queued Swing work checks its generation before installing, so stale work can't resurrect a panel
  after `shutDown()`.
- **Lazy children.** Collapsed groups create no Swing rows. Konar alone would otherwise build
  hundreds of hidden components.

**Sorting is strictly alphabetical** — an owner ruling. The old progress-based comparator reshuffled
the list every time you pulled a card. Don't reintroduce it.

Constraints worth knowing: `PluginPanel` is a plain `JPanel` with a fixed 225px width, already inside
a `JScrollPane`. Vertical space is free; **horizontal is the binding constraint.**

---

## 16. Adding a restriction — the contributor's guide

**Adding a rule is 90% data, 10% code.** You write a JSON entry saying *"when the player clicks this
thing with this menu option, they need these cards."* You only write Java when you invent a new
**category** or a new **difficulty mode**.

### The four questions, in order

**1. What does the player physically click?** → picks the `kind`.

A tree is `object`. A fishing spot is `fishing-spot`. Dropping raw food on a fire is
`item-on-object`. Laying a trap is `inventory`. Choosing a product in a make-menu is `interface`.
Using one item on another is a recipe's `item-on-item`. See the key table in §6 — there are about
eight, and that's the whole vocabulary.

**2. What exact strings does that click produce?** → gives you `name` and `option`.

**This is the hard part, and it is not a programming problem — it's an observation problem.** The
menu says "Chop down", not "chop". The knife menu says "Crossbow stock" for every tier. Arrow shafts
arrive as "45 arrow shafts". You cannot reason your way to these.

Two acceptable sources, and only two:
- **An in-game debug capture.** Run the dev client with `--debug`, do the interaction, and read the
  log. Two permanent log lines exist for exactly this: `node lookup kind=... name=... option=... ->
  NO RULE` (`:1993-1999`) and `interface product raw=... stripped=... widgetItemId=...`
  (`:1937-1949`).
- **A wiki item/object page**, fetched per the etiquette rules in `CLAUDE.md` (plain page URLs, no
  query params, ~1 req/sec, descriptive User-Agent, cache every fetch).

Where a string genuinely can't be obtained ahead of time: key **all** plausible variants, label them
UNVERIFIED in the node's `notes`, and add them to the owner's test list.

**3. What cards should it need?** → the group algebra.

**Cards inside a group are OR; groups are AND.** "Any pickaxe, plus this ore" is two groups. Every
card name must **exact-match** an entry in `tracked_item_names.json` or `tracked_monster_names.json`.
If something has no card, leave it out — untracked things are never restricted (§1, rule 1).

**4. Should a setting be able to relax it?** → roles.

Tag groups with a `role`, then add one case to `excludedRolesFor` saying which roles that dropdown
drops. This is why "add a difficulty option" never touches data.

### The failure mode to internalise

**A wrong rule doesn't error — it silently never fires.** A typo'd option string is
indistinguishable from "restrictions are off". This is why:
- the generators validate every card name and throw on duplicates;
- `node lookup` logging exists at all;
- the standing doctrine is *never key a rule on a guessed menu string*.

If you're testing and nothing blocks, the first move is always to turn on debug logging and read the
`node lookup` line. Nine times in ten it says `NO RULE` and shows you the string you actually needed.

### Concretely

**A new gathering / activity node** (no code change):
1. Add an entry to `resource_nodes.json` with `category`, `kind`, `name`, `options`,
   `requiredCardGroups`, and `groupRoles` if a mode needs to drop any of them.
2. If `category` is new, add a case to `excludedRolesFor` — otherwise it restricts fully by default,
   which is loud but probably not what you want.
3. Validate card names against the tracked snapshots.

**A new recipe:** add to `recipe_nodes.json` with `trigger.kind`, `trigger.name`, optional
`trigger.targets`, `inputs` (groups), `output`. New category → a case in `checkRecipe`.

**A new difficulty mode:** add an enum, a `@ConfigItem` returning it, and one case in
`excludedRolesFor` (or a bespoke `evaluateXRule` if it needs game state). Add a migration mapping in
`migrateSkillToggles` if it replaces something.

**A whole new skill area:** follow the pipeline (§3) — write a matrix in `docs/<skill>_actions.json`,
a generator in `scripts/rebuild_<skill>_data.js` modelled on `rebuild_fishing_data.js`, and let it
write both the resource category and the report.

### Before you hand it over

- `./gradlew build` is green (it runs the data-integrity tests).
- Card names validated against the tracked snapshots.
- No duplicate trigger keys.
- Anything unverified is labelled in `notes` and listed for the owner's in-game test pass.
- The owner commits. **Contributors edit and build-verify, then hand over a changed-file list plus a
  suggested commit message** — don't run `git add`/`commit`/`push` unless asked.

---

## 17. Build, run, test

**Java 11 (Temurin), Gradle 8.10 wrapper.** RuneLite's client is a `compileOnly` dependency — it's
provided by the host client at runtime, not bundled.

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-11.0.31.11-hotspot"
./gradlew build --no-daemon
```

**Run the dev client:**

```bash
./gradlew run
```

That executes `BronzemanTcgPluginTest.main()` (the launcher, §2) with `--developer-mode --debug`,
sideloading the plugin into a real RuneLite. The owner also has an untracked `launch-client.bat`.

**Enable debug logging** — this is how you diagnose almost everything. `--debug` gives you:
- `node lookup kind=... name=... option=... targetId=... -> NO RULE | rule[category]`
- `interface product raw=... stripped=... widgetItemId=... itemName=...`
- `sailing object click ...` / `sailing interface ...`
- `local stance: ...` (Duelist City capture aid)

**Tests:** `./gradlew test`, or they run as part of `build`. They're data-integrity and pure-logic
tests (§2) — there's no way to unit-test the click pipeline without a live client, so the in-game
test pass is the real verification step, and it's the owner who does it.

**Regenerating data:**
```bash
node scripts/rebuild_fishing_data.js          # or herblore / hunter / slayer / thieving_chest
python scripts/generate_tracked_monsters.py <path-to-Card.json>   # upstream card sync
```

---

## 18. Releasing to the Plugin Hub

The plugin is live on the Hub, so releases have a fixed procedure:

1. **Bump `version=` in `runelite-plugin.properties`.** That line is the **single source of truth** —
   the Hub displays it and `build.gradle` reads it for the jar name. `0.MINOR.PATCH`: MINOR for
   features, PATCH for fixes.
2. **Add a `CHANGELOG.md` entry** in the same commit.
3. Keep `build=standard` in the properties file.
4. Commit and **push**.
5. Take the hash from `git log -1 --format=%H` **after the final push** — never earlier; amends
   invalidate hashes.
6. Open a PR against [`runelite/plugin-hub`](https://github.com/runelite/plugin-hub) bumping
   `plugins/bronzeman-tcg`'s `commit=` line to that hash.

**Do not rely on build-time resource templating.** The Hub packager does **not** run a plugin's
custom `processResources`/`expand` logic — a version-stamped resource shipped its raw `${version}`
token to real users in 0.2.3. That's why the plugin no longer displays its own version anywhere at
runtime.

---

## 19. House rules — things that have drawn blood

Each of these is a scar, not a preference.

1. **Never key a rule on a guessed menu/item string.** Acceptable sources: an in-game debug capture,
   or a wiki page fetched per the etiquette rules. A guess produces a rule that silently never fires.
2. **Never rename a `keyName`** that holds user data. It wipes real players' settings.
3. **Never give a user-editable config field a non-empty default.** RuneLite re-injects it.
4. **Verify every merge's *content*, not just its conflict markers.** A commit auto-resolved with no
   conflict and a clean `git status`, and silently discarded newer config descriptions. After any
   merge, run `git diff <parent1> <merge>` **and** `git diff <parent2> <merge>` and read every
   deletion, asking "did the other side legitimately replace this, or did we just lose it?"
   Pure-addition merges (only `+` lines vs both parents) are safe by inspection.
5. **Wiki etiquette** (agreed with wiki staff): plain page URLs, **no** query params — those hit the
   edge cache. Never `api.php?action=parse`. Descriptive User-Agent naming this project, ~1 req/sec,
   and cache every raw fetch so re-runs hit the wiki zero times. Dev-time only; the shipped plugin
   makes no network requests.
6. **Plan first.** For any feature, write a plan grounded in the existing code — files/methods
   touched, reuse vs new, config impact — and agree it with the owner before implementing. The
   `docs/plan_*.md` files are the record of this.
7. **The owner commits.** Contributors edit and build-verify, then hand over the changed-file list
   and a suggested commit message.
8. **Avoid deprecated RuneLite API in new code.** (`getNpcs()` → `getTopLevelWorldView().npcs()`,
   `getMapRegions()` → `WorldView`, both already migrated.)
9. **Hub compliance.** Restriction and QoL only. No automation, ever.

---

## 20. Known boundaries

Not bugs — documented, owner-accepted limits:

- **Keyboard `make` flows bypass everything.** Spacebar on a "how many?" dialog, and the case where
  you carry materials for exactly one product (the game skips the menu entirely), never generate a
  `MenuOptionClicked`. There is nothing to consume. This is a platform boundary, not a fixable defect.
- **GE search blocking is leaky.** Consuming the chatbox selection is the best hook available;
  keyboard flows slip past.
- **Ground items can't be hidden.** The `addEntity` render callback only fires for NPCs — verified
  in-game. Loot relies on Take-blocking instead.
- **Some in-game strings remain unverified** — sailing install/salvage options, several crafting
  interface twin names. Labelled in `notes`, listed in the relevant `docs/*_report.md`.
- **Upstream card gaps.** Some content has no card at all (Onyx ring, several hunter creatures,
  blurite/barronite ores, some required quest kills). Per rule 1, those are never restricted. Listed
  in `CLAUDE.md`'s backlog for reporting upstream.

---

## 21. Glossary

RuneLite terms a newcomer will hit in the first hour:

| Term | Meaning |
|---|---|
| **Plugin Hub** | RuneLite's third-party plugin repository. Publishing = a PR pointing at a commit hash in your repo. |
| **Event bus** | RuneLite's internal pub/sub. You subscribe with `@Subscribe` on a method whose parameter is the event type. `EventBus.post` is **synchronous**. |
| **`MenuOptionClicked`** | Fired for every menu option the player activates, *before* the game acts. `consume()` cancels it. |
| **`MenuAction`** | An enum saying what kind of interaction a click is (`NPC_FIRST_OPTION`, `GAME_OBJECT_*`, `CC_OP`, `WIDGET_TARGET_ON_WIDGET`…). The primary routing key. |
| **Widget** | Any interface element. A **group ID** identifies which interface (inventory, bank, shop); `WidgetUtil.componentToInterface` extracts it. |
| **`ItemComposition`** | The game's item definition. `itemManager.getItemComposition(id).getName()` is how an item ID becomes a name. |
| **Varbit** | A packed game state variable. Used here for LMS detection and GotR. |
| **Client thread** | Where the game runs and game state is safe to read. |
| **EDT** | Swing's Event Dispatch Thread — where all UI must be built and updated. Never read game state from it. |
| **Overlay** | A class RuneLite calls once per frame to paint over the game. |
| **`ConfigManager`** | Shared key-value storage, divided into named groups. Doesn't police who reads what — which is how this plugin reads osrs-tcg's data. |
| **RSProfile-scoped** | Config scoped to the current RuneScape account, not the client install. |
| **`PluginMessage`** | An event-bus message with a namespace and a name — how two Hub plugins talk without a compile-time dependency. |
| **Game tick** | 600ms. All the plugin's countdowns are measured in these. |

---

## 22. Appendix: quick reference

**"Where is X decided?"**

| Question | Answer |
|---|---|
| Do I own this card? | `effectiveOwnedCards()` — `Plugin:2880` |
| Is this entity tracked at all? | `isUnlocked(catalog, name)` — `Plugin:2782` (empty variants = untracked = allowed) |
| Should the whole plugin stand down? | `isEnforcementBypassed()` — `Plugin:2626` |
| Is this node interaction allowed? | `evaluateNodeRule` — `Plugin:2022` |
| Does this config mode drop a requirement? | `excludedRolesFor` — `Plugin:2343` |
| Is this recipe allowed? | `checkRecipe` — `Plugin:2455` |
| Which menu options get hidden? | `shouldHideEntry` — `Plugin:716`; `shouldHideInventoryEntryAfterSort` — `Plugin:1772` |
| What does a rule require? | `Rule.missingRequirements` — `ResourceNodeCatalog:265` |
| How is a rule keyed? | `ResourceNodeCatalog.key` / `idKey` — `:120-128` |
| What fields does the JSON support? | The private DTO classes at the bottom of each catalog |

**Commands**

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-11.0.31.11-hotspot" && ./gradlew build --no-daemon
```
```bash
./gradlew run
```
```bash
node scripts/rebuild_fishing_data.js
```

**Further reading, in order:** `HOW_IT_WORKS.md` (conceptual overview) → this file →
`docs/plan_skills_sweep.md` (current roadmap) → the `docs/*_report.md` for whatever area you're
touching → `CLAUDE.md` (project instructions, backlog, and the full history of decisions).
