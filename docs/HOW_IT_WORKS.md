# Bronzeman TCG, Under the Hood

*An engineering walkthrough of your own plugin — how the code actually enforces the challenge, layer by layer.*

You designed every feature in here. What this document does is show you how those design decisions became running Java: what a RuneLite plugin is at the machine level, how the code learns what you own and what exists, how a single mouse click gets inspected and sometimes cancelled, and why the whole thing is built to break loudly rather than quietly cheat in your favour. No prior Java or RuneLite knowledge assumed — jargon gets defined the first time it shows up — but nothing here is dumbed down either.

---

## 1. The big picture: a plugin that eats mouse clicks

**What a RuneLite plugin is.** RuneLite is the third-party OSRS client, written in Java. It's built to be extended: instead of a single monolithic program, it's a host that loads dozens of small modules called *plugins*, and it hands each one a controlled window into the running game. Three mechanisms make that possible, and this plugin uses all three.

- **Event subscribers.** RuneLite has an internal *event bus* — a public address system that announces things as they happen: "a game tick elapsed," "the player clicked a menu option," "the account profile changed." Your code subscribes to the announcements it cares about by writing a method and tagging it `@Subscribe`. The `@` symbol is a Java *annotation*: a label you stick on code that tools (here, RuneLite's event bus) read and act on. When RuneLite sees `@Subscribe` above a method whose argument is a `MenuOptionClicked`, it files that method away and calls it every time a menu option is clicked. You never call it yourself; RuneLite does.

- **Dependency injection.** Look at the top of `BronzemanTcgPlugin` and you'll see a stack of fields each marked `@Inject` — `Client`, `ItemManager`, `ConfigManager`, plus your own catalogs. Injection means you *declare* "I need a Client" and the framework *supplies* a fully-built one at startup. You never write `new Client()`. This matters because there's exactly one real game client, one item database, one config store, and everyone must share the same instances; injection is how RuneLite guarantees that. `@Singleton` on your catalog classes is the other half of the deal — it tells the framework "build exactly one of these and give everyone the same copy."

- **The config interface.** `BronzemanTcgConfig` is not a class — it's a Java *interface*, a list of method signatures with no bodies (`boolean restrictAttacks();`). You never implement it. RuneLite reads the annotations (`@ConfigItem`, `@ConfigSection`, `@Range`) and *generates* the settings panel you see in-game, wires each toggle to saved storage, and manufactures an object that answers those methods with the player's current choices. When your plugin code calls `config.restrictAttacks()`, it's reading a checkbox — no file I/O in sight.

**This plugin's one-sentence job:** watch every menu click, and if it's an action on something whose OSRS-TCG card you haven't pulled yet, cancel the click and tell you why.

Everything downstream is detail on those two verbs: *recognise* the restricted click, and *cancel* it. Cancelling is one method call — `event.consume()` — which we'll come back to. Recognising is where nearly all the code lives, because "did you click Attack on a monster you can't fight yet" requires knowing two independent things: **what you own**, and **what exists to be owned**. Those are the next two sections.

---

## 2. How it knows what you own: reading the TCG collection

The catch: the card collection lives in a *different* plugin — [OSRS TCG](https://github.com/Azderi/osrs-tcg), the gacha pack-opening plugin. Bronzeman TCG has to read that plugin's data without depending on it, because both ship separately on the Plugin Hub and either can be installed without the other.

**ConfigManager as a shared notice board.** Every RuneLite plugin gets to persist small key-value data through a shared service called `ConfigManager`. Think of it as one big notice board the whole client shares, divided into named *groups*. Your plugin's settings live under group `bronzemantcg`; OSRS TCG stores its saved collection under group `osrstcg`, key `state`. Crucially, ConfigManager doesn't police who reads what. So Bronzeman TCG simply asks for someone else's pinned note:

```java
String raw = configManager.getRSProfileConfiguration("osrstcg", "state");
```

`getRSProfileConfiguration` means "the value scoped to the current RuneScape account profile" — so a different account sees a different collection, which is exactly right. This read-only borrowing is the standard interop pattern for unrelated Hub plugins: no shared code, no compile-time link, just an agreed-upon group and key. If OSRS TCG is not installed, the read returns nothing and we carry on gracefully.

**Why the value is scrambled, and the decode chain.** The string that comes back isn't JSON — it's `RLTCG_v2:` followed by a wall of base64. OSRS TCG deliberately obfuscates its save so players can't trivially hand-edit their collection. The transform, applied in this order when *saving*, is:

1. Take the collection as JSON text.
2. **gzip** it — standard compression, shrinks the text.
3. **XOR** every byte against a fixed 15-byte "salt" (`RLTCG|osrs-tcg!` in ASCII) — a reversible byte-scramble.
4. **base64**-encode the result so it's safe to store as text, and prefix `RLTCG_v2:`.

`TcgStateDecoder` runs that film backwards: strip the prefix, base64-*decode*, XOR again (XOR is its own inverse — applying the same salt twice returns the original bytes), then gzip-*decompress* back to JSON. The whole thing is about 40 lines. It's worth being clear-eyed about what this is *not*: it's not cracking encryption. There's no secret key. The salt is copied straight from OSRS TCG's own public source. This is just faithfully reading another plugin's documented on-disk format — the CLAUDE.md notes the algorithm was verified by a round-trip test (encode something, decode it, confirm you get the original back).

**Only reading the fields we need.** The decoded JSON is large — the full TCG state, schema version 3. `TcgStateDto` declares just two fields:

```java
public List<OwnedCardInstanceDto> cardInstances;   // each has: String cardName; boolean foil;
```

("DTO" = *Data Transfer Object*, a plain container class whose only job is to receive parsed data.) The parsing is done by **Gson**, Google's JSON library, and Gson's convenient rule is: *fields in the JSON with no matching Java field are silently ignored*. So this tiny class quietly skips dozens of fields we don't care about. There's a scar in the code's history here worth noting: the DTO originally assumed the collection lived under a nested `collectionState.instances[]`, but a real save captured from a live client revealed it's actually a top-level `cardInstances[]`. That's the kind of thing you can only learn by looking at real data, and the fix is recorded in CLAUDE.md.

**The 5-second cache.** Decoding gzip on *every single menu click* would be wasteful — you might click several times a second. So `TcgCollectionReader` decodes at most once every 5 seconds and hands back a cached `Set<String>` of owned card names (lower-cased, so comparison is case-insensitive) the rest of the time. The staleness this introduces is harmless and one-directional: worst case, you pull a card and have to wait up to five seconds before the game lets you act on it. It never wrongly *unlocks* something. The cache is also force-invalidated when the account profile changes (`onRuneScapeProfileChanged`), so one account's collection can never leak into another's.

**Why it fails closed.** This is the single most important design decision in the whole plugin, so it's worth stating plainly. If anything goes wrong reading the collection — OSRS TCG not installed, the save format changed in a future update, corrupt data, a decode exception — the reader does not guess and does not crash. It returns an **empty set**: you own nothing. And because (as the next section shows) "you don't own the card" means "you're blocked," a decode failure locks *everything tracked* and floods the debug log with the reason. For a challenge-mode plugin that's the honest behaviour: breakage should be loud and obvious, never a silent free pass. The alternative — fail *open*, unlock everything on error — would let a quiet format drift secretly disable your entire challenge, and you'd never know. CLAUDE.md explicitly flags this as a decision to discuss before ever reversing.

---

## 3. How it knows what exists: the bundled catalogs

Knowing what you *own* is only half the question. The other half: does a card even exist for this monster / item / recipe? Because of the plugin's first design principle — *no card means no restriction* — anything with no card can never be unlocked, so it must never be locked either. To answer "does a card exist for X," the plugin ships **snapshot catalogs**: static data files baked into the plugin jar, generated from OSRS TCG's master `Card.json` by scripts in `scripts/`.

Why snapshots instead of reading OSRS TCG live, like we do for ownership? Three reasons, from the code comments: the card *catalog* changes rarely (only when upstream ships new cards), "does a card exist" doesn't need to be second-fresh the way ownership does, and bundling it means the plugin works even before OSRS TCG has loaded in a session. Ownership is live; existence is a snapshot.

**Why the catalogs are maps, not lists — the bracketed-suffix story.** The obvious design would be a flat list of card names. It doesn't work, and the reason is a genuinely subtle data problem you (or a prior session) discovered by auditing all 6,376 cards. 67 of the ~1,225 monster cards carry wiki-style *disambiguation suffixes* in brackets: `Monkey (monster)`, `Soldier (Yanille)`, `Ferret (Hunter)`. These brackets exist because the OSRS wiki needs to tell apart two things sharing a name. But **the game never shows you the bracket** — when you right-click a monkey, the menu just says "Monkey." So a naive exact-match on card names would never fire: the game says "Monkey," the card says "Monkey (monster)," they don't match, the monkey stays unlocked forever.

The fix is to make each catalog a **map from in-game entity name to a list of card variants**:

```json
"soldier": ["Soldier (Al Kharid)", "Soldier (Yanille)", "Soldier (Burthorpe)", ...11 total...],
"monkey":  ["Monkey (monster)"],
"abyssal demon": ["Abyssal demon"]
```

At lookup time, the plugin takes the plain name the game gives it, finds the entry, and unlocks the entity if you own **any one** of the variant cards. Owning a single "Soldier (Yanille)" card unlocks *all* soldiers — which is the only sane behaviour given the game can't tell you which soldier you're clicking. Fifteen NPCs have multiple variants; Soldier has eleven. `TrackedMonsterCatalog` holds 1,198 NPCs mapping from 1,225 cards. `TrackedItemCatalog` is a thin subclass over the same `CardNameCatalog` base with a 1:1 item map (5,149 items, no bracket problems). One landmine the catalog design has to respect: item and monster cards can *collide* by name (`Ferret` the item-resource card versus `Ferret (Hunter)` the monster card), which is exactly why the monster and item maps are kept as two separate catalogs rather than one merged pile.

`CardNameCatalog` also quietly handles one more normalisation: **potion doses**. In-game an item is `Attack potion(3)` but the card is dose-less `Attack potion`, so a lookup that misses strips a trailing `(1)`–`(4)` and tries again (`CardNames.stripDoseSuffix`). All four dose variants collapse to the one card.

**Resource nodes and recipes: the same idea, richer rules.** Attacking a monster is a simple "do you own the card" question. Gathering and crafting aren't — chopping an oak needs the *oak logs* card, smelting needs *ore and/or bar*, fishing a spot could yield any of several fish. Two more catalogs handle these, and they share a small rule vocabulary.

`ResourceNodeCatalog` loads `resource_nodes.json` — 89-plus hand-curated rules. Each rule is keyed by `(kind, name, option)`: *kind* is what you clicked (an `npc`, an `object`, an `item-on-object`, an `inventory` item, or a make-`interface` product), *name* is the entity, *option* is the menu verb. Requirements are expressed as **card groups**:

> Every group must be satisfied; a group is satisfied by owning **any one** card in it.

So "requires A **and** (B **or** C)" is two groups: `[A]` and `[B, C]`. That single primitive expresses everything from "pickpocketing needs Coins **and** Coin pouch" (two single-card groups) to "this fishing spot yields salmon **or** trout" (one two-card group).

**Group roles and config modes.** Here's the clever part that makes the difficulty dials work. A group can carry a *role* label — `"seed"`, `"produce"`, `"material"`, `"rune"`, `"master"`, `"monsters"`. Roles let a config mode **drop entire groups at evaluation time** without touching the data. When the rule is checked, the plugin passes in a set of "excluded roles," and any group whose role is excluded is skipped. That one mechanism powers nearly every dropdown:

- **Fishing** (`Off / Any of / Require ALL`): a special case, since spots everywhere share one NPC name and a rule can only list the *union* of every fish that option yields anywhere. The mode overrides the group logic directly — `Any of` needs one fish card, `Require ALL` needs every card in the union.
- **Master Farmer** (`Off / Coins+Pouch / Insanity`): its own dedicated code path, not a generic node, because he's unique. Insanity requires all 45 seed cards on his table.
- **Farming** (`Tools / Tools+Seeds / All`): Tools mode excludes the `seed` and `produce` roles; Tools+Seeds excludes only `produce`; All excludes nothing.
- **Sailing** (`Parts / Parts+Materials / Everything`): Parts excludes `material` and `large`; Parts+Materials excludes only `large`; Everything enforces all.
- **Runecrafting** (`Talisman / Talisman+Runes`): Talisman-only excludes the `rune` role.

`RecipeCatalog` (`recipe_nodes.json`, 378 recipes) is the same shape for processing skills, but splits requirements into **input groups** and an **output card**. Each skill's config mode picks whether inputs, output, or both are enforced — smelting's `Ore / Bars / Both`, smithing's `Bars / Items / Both`, and so on — by passing `enforceInputs`/`enforceOutput` booleans into the same evaluation method.

---

## 4. The interception pipeline: one click, many paths

Everything converges on a single subscriber:

```java
@Subscribe
public void onMenuOptionClicked(MenuOptionClicked event) { ... }
```

RuneLite fires this for *every* menu option the player activates — left-click default action or right-click selection alike. The `event` carries the option text ("Attack"), the target ("Giant spider"), a `MenuAction` enum saying what *kind* of interaction it is, and IDs for the entities involved. The method's whole job is to route the event to the right handler and, if the handler decides to block, call `event.consume()`.

**`event.consume()` — the actual enforcement.** This is the entire teeth of the plugin. Consuming a menu event tells RuneLite: *this click is handled, do not pass it to the game.* The packet that would have told the server "attack that spider" is never sent. From your seat it looks like the click did nothing (plus a chat message appears). One method call, and that's the whole restriction mechanism — everything else is deciding *whether* to call it.

The routing works in two tiers. First, a fast check: does the clicked menu entry have an NPC attached? If so it's the NPC path. Otherwise, switch on the `MenuAction` enum:

- **NPC path** (`handleNpcInteraction`). Resolves the NPC's name — preferring `getTransformedComposition()` so shape-shifting NPCs report their *current* form, then stripping any colour tags. Two checks run. First, attack-style interactions: if the option is "Attack" (or a spell/item-on-NPC, config permitting) and you don't own the card, consume and message. Second, node rules on NPCs cover pickpocketing, fishing spots, and slayer/rumour masters — with Master Farmer peeled off to his own dial first.

- **Ground items** (`GROUND_ITEM_*` and telegrab `WIDGET_TARGET_ON_GROUND_ITEM`). Gated by the `restrictLoot` toggle. Plain clicks only count when the option is "Take"; telegrab always counts (it's always looting). The item's true name comes from `itemManager.getItemComposition(event.getId())`. A user-editable comma-separated **exempt list** (default "Coins") keeps universal drops pickup-able — because in the TCG *everything*, Coins and Bones included, has a card, so blocking with no exemptions would brick a fresh account.

- **Game objects** (`GAME_OBJECT_*`) run through the node catalog: trees need their logs card, rocks their ore, and so on.

- **Item-on-object** (`WIDGET_TARGET_ON_GAME_OBJECT`). The menu target arrives as a single string, `"Raw shrimps -> Fire"`, so the code splits on `" -> "` into used-item and target-object and checks node rules (cooking) then recipes (e.g. ore on furnace).

- **Widget ops** (`CC_OP` / `CC_OP_LOW_PRIORITY`) are clicks *inside* interface panels — inventory, bank, shop, make-menus. This is the busiest handler because it has to figure out *which* interface you clicked from the widget group ID (`WidgetUtil.componentToInterface`). Bank clicks discriminate on the option text: "Withdraw" is gated (in either forced-drop mode), "Deposit" only in strict *Drop* mode — because "allow banking" treats the bank as a holding pen, so deposits are always fine but withdrawals stay locked. Shop "Buy" checks the item card. Inventory ops cover equip verbs, potion "Drink", and forced-drop's inverse rule: in forced-drop mode, anything *except* Drop/Examine/Destroy/Release on a locked item is blocked. Grand Exchange search is *best-effort* — it consumes the chatbox search-result selection, the best hook available, and the code and CLAUDE.md are both honest that keyboard flows can slip past. And a general **make-verb fallback**: production screens the plugin doesn't recognise by group (the furnace, the shipwright's bench) still name their product in the menu target, so any click whose verb starts with `smelt`/`make`/`craft`/`cook`/`fire`/… gets a recipe lookup by product name (prefix-matched, so `Make-5` and `Smelt-1` are covered).

- **Item-on-item and spell-on-item** (`WIDGET_TARGET_ON_WIDGET`). Same `" -> "` split. If the source is a spell (`Cast …`, or a non-item selected widget) it routes to enchanting, keyed on the target jewellery alone since each item has exactly one enchant. Otherwise it's a crafting recipe, tried both directions because the data keys tool→material but you might click either way round.

**The honour-system caveat.** The whole design rests on one hard limit: RuneLite can only consume clicks that go *through the menu*. Some actions don't. The classic is a keyboard "make" — pressing spacebar on the "how many?" dialog, or the case where you're holding materials for exactly one product so the game skips the menu entirely. Those never generate a `MenuOptionClicked`, so there's nothing to consume. This isn't a bug to be fixed; it's a boundary of the platform. You accepted it as an honour-system gap, and the code comments say so out loud rather than pretending otherwise.

---

## 5. The visuals: two rendering paths and a threading rule

Blocking is invisible until you click. Two overlay features make locked things *visible* first, and they use different hooks because they answer different needs.

**The grey outline** (`BronzemanTcgOverlay`). This is an `Overlay` — a class RuneLite calls once per frame to paint on top of the game. It walks every on-screen NPC, checks lock state (two map lookups: variants for this name, do you own any), and for locked ones calls `modelOutlineRenderer.drawOutline(npc, width, color, feather)`. `ModelOutlineRenderer` is the same silhouette-hugging renderer the popular NPC Indicators plugin uses; here it's driven in "disabled grey" rather than a highlight colour, with colour/width/feather all config-exposed under the Visuals section. It runs on the *client thread* every frame, which is why the per-NPC work is kept to cheap map lookups — anything heavy here would drop your frame rate.

**Hiding entirely** (`RenderCallbackManager` + `addEntity`). Fully hiding a locked NPC needs a lower-level hook than painting over it. The plugin registers itself as a `RenderCallback` and implements `addEntity(Renderable, boolean)`, which RuneLite calls as it assembles the scene each frame. *Returning `false` keeps that entity out of the scene entirely* — and, importantly, removes its clickbox too, so a hidden monster can't even be clicked. The comment records a real experiment: this hook only fires for NPCs, **not** ground items (verified in-game on the predecessor hook), which is why "hide locked ground items" was tried and removed — the code simply can't do it, so loot relies on the Take-blocking from Section 4 instead. Note the two visual modes are mutually exclusive by design: the outline overlay bails out if hide-mode is on, since there'd be nothing to outline.

**The threading rule.** This is the one piece of concurrency discipline in the plugin, and it's non-negotiable in RuneLite. There are two threads that matter: the **client thread** (where the game runs and where game state is safe to read) and the **Swing EDT** (Event Dispatch Thread, where all UI panels must be built and updated). Reading live game state from the EDT, or touching Swing from the client thread, causes intermittent corruption that's miserable to debug. The sidebar panel obeys the split cleanly: every few game ticks, `onGameTick` runs on the client thread and *gathers* the nearby tracked-NPC snapshot (names and distances) into a plain list; then it hands that inert list to the panel via `SwingUtilities.invokeLater(...)`, which *renders* it on the EDT. Game state is read only on the client thread; the panel only ever sees a frozen snapshot. The panel's own comment states the contract: the catalogs are immutable after load and the reader is synchronized, so reading *those* from the EDT is fine — the one forbidden thing is touching live game state, and that's exactly what the client-thread gather step keeps out.

---

## 6. The fail-safe philosophy, tying it together

Three defaults recur across every layer, and together they define the plugin's character. Worth seeing them side by side:

1. **Untracked means never restricted.** No card exists → the entity can never be unlocked → it must never be locked. `isUnlocked` returns `true` the instant a lookup finds no variants. This is what keeps the plugin from bricking things the TCG doesn't cover.

2. **Decode failure means everything locks, loudly.** Can't read the collection → owned set is empty → everything tracked is blocked and the debug log says why. Breakage is a wall, not a silent unlock. (Section 2.)

3. **Unknown data categories restrict by default.** If `resource_nodes.json` ever ships a category this build has no config toggle for, `excludedRolesFor` returns "restrict fully" rather than "ignore." New data is loud, not inert — you find out the plugin needs a new toggle by hitting the restriction, not by silently losing enforcement.

Notice the through-line: **every ambiguity resolves toward the challenge, never away from it** — *except* the one place where resolving toward the challenge would break the base game (untracked entities), where it correctly steps back. That's not three unrelated rules; it's one philosophy applied consistently. In a challenge-mode plugin, a false lock is a minor annoyance you can see and diagnose; a false *unlock* is an invisible hole in the whole point of the mode. The code is built to prefer the annoyance every time.

---

## 7. Life of a click: attacking a locked Giant spider

Let's trace one real interaction end to end. You left-click a Giant spider whose card you don't own, with the attack restriction on.

1. **The game raises the menu event.** RuneLite constructs a `MenuOptionClicked` — option `"Attack"`, target `"Giant spider"`, `MenuAction.NPC_FIRST_OPTION`, the NPC object attached — and posts it to the event bus.

2. **The event bus calls your subscriber.** Because `onMenuOptionClicked` is `@Subscribe`d to that event type, RuneLite invokes it. This happens *before* the game acts on the click.

3. **Routing.** `event.getMenuEntry().getNpc()` returns the spider (non-null), so control goes straight to `handleNpcInteraction`.

4. **Name resolution.** `resolveNpcName` prefers `getTransformedComposition().getName()` (a plain spider isn't shape-shifting, so this is just "Giant spider"), then `Text.removeTags` strips any styling. Result: `"Giant spider"`.

5. **Is this a restricted interaction?** `isRestrictedNpcInteraction` sees `NPC_FIRST_OPTION`, checks `config.restrictAttacks()` is on, and confirms the option text lower-cases to `"attack"`. Yes.

6. **Do you own the card?** `isUnlocked(monsterCatalog, "Giant spider")` looks up `"giant spider"` in the monster catalog → finds the variant set `["giant spider"]` (non-empty, so it *is* tracked). It fetches your owned set from `TcgCollectionReader` — served from the 5-second cache, or freshly gzip-decoded from OSRS TCG's `osrstcg`/`state` blob if the cache is stale — and asks: is `"giant spider"` in it? No.

7. **Block.** Both conditions met (`isRestrictedNpcInteraction` true, `isUnlocked` false), so:
   - `event.consume()` — the attack packet is never sent to the server. The spider is untouched; your character doesn't move.
   - `sendBlockedMessage("Giant spider")` — subject to the 1.2-second chat throttle (so mashing the spider doesn't spam), prints to your chatbox:

     > `[Bronzeman TCG] You haven't collected the Giant spider card yet - open more packs!`

8. **You pull the card.** You open packs in OSRS TCG and get the Giant spider. It writes the new collection to `osrstcg`/`state`. Within 5 seconds the Bronzeman cache expires; the next attack click re-runs steps 1–6, step 6 now finds `"giant spider"` in your owned set, `isUnlocked` returns `true`, and the event is left alone. The click sails through to the game and you attack normally.

That's the entire system in miniature: an event you didn't ask for, routed by kind, matched against a snapshot of what exists and a live read of what you own, resolved by a single `consume()` — and every unclear branch along the way tilting toward keeping the challenge honest.
