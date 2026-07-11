# Quest Cards Build Report

Informational data for the Bronzeman TCG panel: for every OSRS quest, the cards a player
must own (required item cards + resolvable enemy cards). Generated 2026-07-11.

## Output
- `quest_cards.json` — `{"quests":[{name, miniquest, cardGroups, groupLabels, monsterCards, notes}]}`, 206 quests, sorted by name.
- Every card string is asserted to exact-match `Card.json` (6376 cards) at build time; the emitted JSON is re-parsed to confirm validity. **Validation passed.**

## Sources & parsing approach
The consolidated "Quest item requirements" page redirects to `Quest items/Need for quest`,
which only lists items carried *between* quests — not per-quest required items. No single
per-quest items table exists; the authoritative data lives in each quest's `{{Quest details}}`
infobox (`items=` and `kills=` params).

Rather than fetch ~170 pages, I used the MediaWiki API's batch capability:
1. `list=embeddedin&eititle=Template:Quest details&einamespace=0` → 206 mainspace quest/miniquest pages (subpages like RFD subquests filtered out).
2. `prop=revisions&rvprop=content` with 50 titles/request → **all 206 wikitexts in ~5 API calls**.
3. `Category:Miniquests` members → miniquest flag.

Total network: ~10 API calls (well under budget). **Both items and monsters shipped — no second pass needed.**

### Item parsing (`items=` field, a wiki bullet list)
- Only **top-level bullets** (single `*`) are parsed. Nested `**` bullets are sub-notes
  (where to buy / how to craft / alternatives) and are skipped — a deliberate conservative
  choice (we require the primary/finished item the wiki lists at top level).
- Prose parentheticals (those containing a space, e.g. `(obtainable during the quest)`) are
  stripped; short item-name parens are preserved (`(i)`, `(unf)`, `(nettle)`, dose `(4)`).
- Item = the `[[wikilink]]` target (before `|` and `#`), resolved case-insensitively to the
  exact Card.json name. Dose suffixes `(N)` and trailing plural `s` are folded to the base card.
- Links not matching any card are dropped silently (untracked = unrestricted).
- **Cards whose category is/contains `Monster` are excluded from item resolution** — this removes
  NPC links that appear in item prose (e.g. Fremennik Trials council names Lanzig/Borrokar).

### Any-of (OR) groups
Alternatives are detected by the connector text *between* adjacent links: a link run joined only
by `or` / `/` / comma-lists ending in `or` becomes one any-of group; `and`/other connectors break
the run into separate required items. 47 OR-groups across 35 quests. Group labels: `"Any <common
suffix>"` when the alternatives share a trailing noun (`Any pickaxe`, `Any spear`, `Any fishing
rod`), otherwise a `" / "`-joined label (`Yellow dye / Onion`, `Bucket of water / Jug of water`).

### Monster parsing (`kills=` field)
Each `[[enemy]]` target resolved against `tracked_monster_names.json` `entityToCards`
(lowercased, bracket-variant-folded). Added underscore→space and hyphen→space normalization to
recover e.g. `Vanstrom_Klause`, `Agrith-Naar`, `Armoured_zombie_(...)`. 130 quests carry monster
cards, 320 distinct monster cards.

## Coverage stats
| Metric | Count |
|---|---|
| Quests total | 206 (183 full, 23 miniquest) |
| Quests with ≥1 item-card group | 168 |
| Quests with ≥1 monster card | 130 |
| Quests with neither (always-completable, empty arrays) | 18 |
| Total item groups | 768 (47 any-of) |
| Distinct item cards referenced | 314 |
| Distinct monster cards referenced | 320 |

Miniquest flag is derived from `Category:Miniquests`. The full-quest count (183) reflects the
current ~2025/2026 game, including recent Varlamore/Sailing-era quests.

## Judgment calls
- **Top-level bullets only.** For "make-it-yourself" items (e.g. Cook's Assistant's bucket/pot
  sub-bullet alternatives, or "Hangover cure *or the ingredients*"), we list the finished/primary
  item and do **not** parse the sub-bullet component alternatives. Conservative and defensible;
  documented limitation.
- **Coins included.** `Coins` is a Card.json card and is emitted wherever a quest links coins as a
  requirement. Kept for fidelity; the panel may wish to treat it as always-owned.
- **Parenthetical alternatives not parsed** (e.g. `Ice gloves (obtainable during quest) or Smiths
  gloves (i)` where the alt sits after a prose paren is caught, but `Harralander potion (unf) (or a
  harralander and a vial of water)` keeps only the potion).
- **Monster-category cards excluded from items** to prevent NPC prose links leaking into item lists.

## Excluded / unresolved
### Items with no card
Silently dropped (untracked). By design, not enumerated — any required item lacking an exact
Card.json match is simply absent from a quest's groups.

### Enemies not resolved (per quest in `unresolved_enemies.json`)
Two classes:
1. **Non-enemy prose noise** correctly excluded — mechanics/links inside the kills field:
   `safespot`, `Protect from Melee/Missiles`, `protection prayers`, `Multicombat area`, skill links
   (`Magic`, `Melee`, `Hitpoints`), item links (`bronze axe`, `bat bones`, `dusty key`, `ice
   gloves`, `pickaxe`), locations (`Glarial's Tomb`, `Theatre of Blood`).
2. **Genuine quest bosses absent from the monster snapshot** (no card, or a name mismatch vs the
   snapshot). Notable: `Koschei the deathless` & `Draugen` (Fremennik Trials), `Solus Dellagar`
   (Wanted!), `Kolodion` (Mage Arena I), `King rat` (Ratcatchers), `Zombie monkey`/`Ninja monkey`
   (RFD — snapshot uses "Monkey zombie"), plus new-content bosses `Augur Metzli` (The Final Dawn),
   `Sanguidae`/`Venator`/`Nylocas`/`Wyrd`/`Ancient feral vyre`/`Webbed-winged Crow` (The Blood Moon
   Rises), and the pirate NPCs of `The Red Reef`. These are listed for a possible snapshot-update
   pass but are correctly omitted from `monsterCards` (no exact card match).

## Ambiguous / worth a manual glance
- Quests with `kills = None` or no `items` param (18 have neither card-backed requirement) are
  emitted with empty arrays — the panel shows them always-completable.
- Dragon Slayer I `Law rune`/`Air rune` are emitted as separate required items; on the wiki they're
  part of a "telegrab OR 10,000 coins" alternative that is too nested to model as a clean group.
