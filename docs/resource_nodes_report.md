# Resource Nodes Data — Coverage Report

Data file: `resource_nodes.json`. Every `requiredCards` entry and every cooking `name`
(used item) was validated by exact string match against `Card.json` — **0 unmapped**.

## Node counts

| Category | Nodes |
|---|---|
| woodcutting | 18 |
| mining | 17 |
| fishing | 12 |
| pickpocketing | 12 |
| cooking | 30 |
| **Total** | **89** |

---

## Woodcutting (18)
Option is `chop down` for all. Mapped species: Tree/Dead tree/Evergreen tree -> `Logs`;
Oak/Willow/Yew/Teak/Mahogany/Maple/Magic/Redwood -> their logs; Hollow tree -> `Bark`.

**Key uncertainty — object name suffix.** OSRS is inconsistent about whether a tree object
is named `Oak` or `Oak tree`. For Oak, Willow, Yew, Teak, Mahogany, Redwood I emitted **both
variants as separate nodes** (identical requiredCards) so at least one matches; delete the
wrong one after in-game testing. Maple/Magic use `Maple tree`/`Magic tree` (confident).

**Hollow tree:** yields `Bark`; the menu option may be `Chop-up` rather than `Chop down` — verify.

## Mining (17)
Option is `mine`. Rocks named per-ore per the spec (`Iron rocks` etc.).

**Global caveat:** if in-game these objects are actually the generic name `Rocks` (they may be,
depending on client menu text), all per-ore names will fail to match and must be revisited.
Flagged individually for `Clay rocks`, `Sandstone rocks`, `Granite rocks` where I'm least sure.

- `Gem rocks` -> union of 7 uncut gems, `requireAll=false` (random gem).
- `Rune Essence` -> `Rune essence` + `Pure essence`, `requireAll=false` (level-gated yield).
- `Amethyst crystals` -> `Amethyst`.
- Sandstone/Granite: Desert Quarry objects are generically `Rocks` in-game — names are best-guess.

## Fishing (12)
Spots are NPCs named `Fishing spot` (9 nodes, one per option) plus `Rod Fishing spot`
(3 nodes: bait/lure/use-rod). Distinguished ONLY by option.

**requireAll=false for every fishing node** (recommended). Same name+option yields different fish
by location and we have no location awareness, so each node lists the UNION of common yields and
owning ANY one unlocks the spot. Requiring all would over-restrict (e.g. a low-level shrimp spot
would demand a shark card).

Option -> union used:
- `net` -> Raw shrimps, Raw anchovies, Raw monkfish
- `small net` -> Minnow
- `bait` -> Raw sardine, Raw herring, Raw pike, Raw anglerfish
- `lure` -> Raw trout, Raw salmon
- `cage` -> Raw lobster, Raw dark crab
- `harpoon` -> Raw tuna, Raw swordfish, Raw shark
- `big net` -> Raw mackerel, Raw cod, Raw bass
- `fish` -> Raw karambwan
- `use-rod` -> Leaping trout, Leaping salmon, Leaping sturgeon (Barbarian)

**Verify option text** for Barbarian (`Use-rod`?) and Karambwan (`Fish`?) — these are the least
certain option strings.

## Pickpocketing (12)
kind `npc`, option `pickpocket`. Per owner spec: requiredCards = `Coins` + `Coin pouch`,
`requireAll=true`. (`Coin pouch` is the exact card name; no other coin-pouch card exists.)

NPCs: Farmer, Guard, H.A.M. Member, Hero, Knight of Ardougne, Man, Master Farmer, Paladin,
Rogue, Vyrewatch Sentinel, Warrior woman, Woman.

Loot-table simplifications (noted per-node): Master Farmer really yields seeds + pouch (no single
`Seeds` card exists, so simplified to Coins+pouch — and Master Farmer does not actually drop
Coins); Vyrewatch Sentinel really yields Blood shard/coins; Hero/Paladin/Rogue/Farmer have richer
tables all reduced to Coins+pouch.

## Cooking (30)
kind `item-on-object`. `name` = raw item card, `requiredCards` = [cooked card],
`options` = target object names. Default options: `clay oven`, `cooking range`, `fire`, `range`,
`stove`. Range-only items (Bread dough, Pitta dough) omit `fire`.

Covered: all raw fish shrimps->anglerfish incl. Raw karambwan, Raw dark crab, Raw cavefish,
Raw cave eel, Raw slimy eel; meats (Raw beef/bear meat/rat meat -> `Cooked meat`; Raw chicken ->
`Cooked chicken`; Raw ugthanki meat -> `Ugthanki meat`); bakeables (Bread dough, Pitta dough,
Potato -> Baked potato, Sweetcorn -> Cooked sweetcorn).

---

## Unmappable / excluded

**No matching card in Card.json (excluded):**
- Woodcutting: **Arctic pine** (no `Arctic pine logs` — only `Arctic pyre logs`),
  **Achey tree** (no `Achey tree logs`), **Blisterwood tree** (no `Blisterwood logs`).
- Mining: **Blurite rocks** (no `Blurite ore` — only `Blurite bar`),
  **Barronite deposit** (Camdozaal; no `Barronite shards` card).

**Coverable but deliberately excluded (card exists; name/mechanic uncertainty):**
- Mining minerals with generic in-game object names: `Basalt`, `Volcanic ash`, `Daeyalt ore`/
  `Daeyalt essence`, and Volcanic Mine `Pyrophosphite`/`Saltpetre`/`Calcite` — all have cards but
  the source objects are niche/generically named; add later if desired.
- Fishing: `Infernal eel` (Harpoon, Mor Ul Rek) and `Sacred eel` — very niche; omitted from unions.
- Pickpocketing: **Elf** — Prifddinas pickpocketable elves have varied/ambiguous NPC names; omitted.
- Cooking: Raw rabbit / Raw beast meat / Raw oomlie — require iron spit or pastry wrap, not a
  direct item-on-fire action; omitted to avoid false rules.

## Decisions the owner should confirm
1. Tree object suffix (`Oak` vs `Oak tree`, etc.) — dual nodes provided; prune after testing.
2. Whether ore rocks appear in-game as per-ore names or generic `Rocks` (affects all 15 ore nodes).
3. Fishing option strings for Barbarian (`Use-rod`) and Karambwan (`Fish`).
4. Hollow tree option string (`Chop-up` vs `Chop down`).
5. Comfort with fishing `requireAll=false` union semantics (owning any yielded fish unlocks).
