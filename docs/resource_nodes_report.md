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

---

## Addendum (2026-07-10, second research pass)

Owner decisions applied: fishing became a three-way config mode (Off/Any of/
Require ALL); Master Farmer became a dedicated mode (Off/Coins+Pouch/Insanity).

**Master Farmer seed table (Insanity mode)**: all 45 seeds on his wiki drop
table exist as cards with exact names; shipped as `masterFarmerSeedCards`.

**Minerals added (8)**: Ash pile -> Volcanic ash; Basalt/Efh/Te/Urt salt rocks
-> respective salt cards (Weiss Salt Mine); Daeyalt rocks -> Daeyalt ore
(Meiyerditch quest mine); "Daeyalt Essence" rock -> Daeyalt shard (Darkmeyer;
object name confirmed via wiki infobox); Saltpetre -> Saltpetre (Hosidius,
option is **Dig**, not Mine).

**Still excluded**: Volcanic Mine Calcite/Pyrophosphite — the minable object
is boulder stages with ambiguous names and mixed yields; revisit if wanted.

**Elves added (53)**: 4 Lletya (Arvel, Goreu, Kelyn, Mawrth) + 49 Prifddinas
named elves from the wiki's pickpocketable list. Crystal shard has NO card,
so they use the Coins+Coin pouch convention like other pickpocket targets.

---

## Addendum 2 (2026-07-12, post-implementation corrections)

Changes made after in-game testing that supersede parts of this report:
- **Tree names confirmed "Oak tree" style** — the six bare-name fallback nodes
  (Oak/Willow/Teak/Mahogany/Yew/Redwood) shipped for the naming uncertainty
  were pruned. Plain "Tree" remains (genuinely the in-game name).
- **Master Farmer left the generic pickpocket rules** — he has a dedicated
  config mode (Off / Coins+Pouch / Insanity) implemented as a code path with
  his 45-seed table shipped as `masterFarmerSeedCards`; see Addendum 1.
- **Fishing requireAll became a config mode** (Any of / Require ALL), and the
  Harpoon union later gained `Raw harpoonfish` (Tempoross Cove deep-sea spots
  share the spot identity; see docs/sailing_nodes_report.md).
- Substantial later additions (hunter, slayer, runecrafting, farming, sailing
  rules) are covered by their own reports and the git history, not this file.
