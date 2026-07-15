# Burnt food + crushed gem research

Scope: the plugin's 30 cooking inputs and 10 gem-cutting recipes.
Card validation: exact string match against `D:\ClaudeFolder\osrs-tcg-main\osrs-tcg-main\src\main\resources\Card.json` (6376 cards).
Wiki: oldschool.runescape.wiki (authoritative). Game state ~2025/2026.

---

## Headline finding: "Burnt fish" is real, and it kills 10 of the 30

The wiki article **[Burnt fish](https://oldschool.runescape.wiki/w/Burnt_fish)** is a **disambiguation page**, not an item page. That is the crux. In-game there are several *distinct item IDs that all share the literal display name `Burnt fish`*. The wiki disambiguates them with parenthetical suffixes — `Burnt fish (herring)`, `Burnt fish (trout)`, `Burnt fish (tuna)`, `Burnt fish (anchovies)`, `Burnt fish (sardine)` — but **those parenthetical names do not exist in game**. The item is just `Burnt fish`.

Card.json has **no `Burnt fish` card** (verified: `'Burnt fish' in names == False`). So every raw fish that burns into a generic `Burnt fish` gets **no burnt requirement**. That is 10 of the plugin's 30 inputs.

Verbatim from the disambiguation page's wikitext (`?action=parse&prop=wikitext`), the burnt→raw mapping:

| Burnt item (wiki name) | Raw sources | In-game name | Card? |
|---|---|---|---|
| `Burnt fish (herring)` | Raw herring, **Raw mackerel** | `Burnt fish` | NO |
| `Burnt fish (sardine)` | Raw sardine | `Burnt fish` | NO |
| `Burnt fish (anchovies)` | Raw anchovies | `Burnt fish` | NO |
| `Burnt fish (trout)` | **Raw cod, Raw pike, Raw salmon, Raw trout**, Raw giant carp | `Burnt fish` | NO |
| `Burnt fish (tuna)` | **Raw bass, Raw tuna** | `Burnt fish` | NO |
| `Burnt shrimp` | Raw shrimps | `Burnt shrimp` | YES |
| `Burnt lobster` | Raw lobster | | YES |
| `Burnt swordfish` | Raw swordfish | | YES |
| `Burnt monkfish` | Raw monkfish, Fresh monkfish | | YES |
| `Burnt shark` | Raw shark | | YES |
| `Burnt anglerfish` | Raw anglerfish | | YES |
| `Burnt dark crab` | Raw dark crab | | YES |
| `Burnt karambwan` | Raw karambwan | | YES |
| `Burnt cave eel` | **Raw cave eel** | | YES |

Note the many-to-one collapsing: mackerel shares herring's burnt item; cod/pike/salmon/trout all share one; bass/tuna share one. Even if a `Burnt fish` card existed, it would be a single shared card across 10 raw inputs — worth knowing if the owner ever adds one.

---

## Per-item evidence table (all 30)

| Raw input | Burnt item | Card? | Source |
|---|---|---|---|
| Bread dough | `Burnt bread` | YES | [Burnt bread](https://oldschool.runescape.wiki/w/Burnt_bread) recipe: `mat1 = Bread dough`, `output1 = Burnt bread`, lvl 1, Cooking range |
| Pitta dough | `Burnt pitta bread` | YES | [Burnt pitta bread](https://oldschool.runescape.wiki/w/Burnt_pitta_bread): "the result of accidentally burning pitta dough while attempting to make pitta bread" |
| Potato | `Burnt potato` | YES | [Burnt potato](https://oldschool.runescape.wiki/w/Burnt_potato) recipe: `mat1 = Potato`, lvl 7, Cooking range |
| Sweetcorn | `Burnt sweetcorn` | YES | [Burnt sweetcorn](https://oldschool.runescape.wiki/w/Burnt_sweetcorn) recipe: `mat1 = Sweetcorn`, lvl 28, Cooking range |
| Raw chicken | `Burnt chicken` | YES | [Burnt chicken](https://oldschool.runescape.wiki/w/Burnt_chicken) recipe: `mat1 = Raw chicken` |
| Raw beef | `Burnt meat` | YES | [Burnt meat](https://oldschool.runescape.wiki/w/Burnt_meat) recipe tabber: `mat1 = Raw beef` |
| Raw bear meat | `Burnt meat` | YES | Burnt meat tabber: `mat1 = Raw bear meat` |
| Raw rat meat | `Burnt meat` | YES | Burnt meat tabber: `mat1 = Raw rat meat` |
| Raw ugthanki meat | `Burnt meat` | YES | Burnt meat tabber: `mat1 = Raw ugthanki meat` (**not** "Burnt ugthanki meat" — no such item) |
| Raw shrimps | `Burnt shrimp` | YES | Burnt fish disambig. Note singular **shrimp**, raw is plural **shrimps** |
| Raw lobster | `Burnt lobster` | YES | Burnt fish disambig |
| Raw swordfish | `Burnt swordfish` | YES | Burnt fish disambig |
| Raw monkfish | `Burnt monkfish` | YES | Burnt fish disambig |
| Raw shark | `Burnt shark` | YES | Burnt fish disambig |
| Raw anglerfish | `Burnt anglerfish` | YES | Burnt fish disambig |
| Raw dark crab | `Burnt dark crab` | YES | Burnt fish disambig |
| Raw karambwan | `Burnt karambwan` | YES | Burnt fish disambig |
| Raw cave eel | `Burnt cave eel` | YES | Burnt fish disambig |
| Raw slimy eel | `Burnt eel` | YES | [Burnt eel](https://oldschool.runescape.wiki/w/Burnt_eel) recipe: `mat1 = Raw slimy eel`, lvl 28 |
| Raw anchovies | `Burnt fish` | **NO** | Burnt fish disambig |
| Raw bass | `Burnt fish` | **NO** | Burnt fish disambig (shares tuna's) |
| Raw cod | `Burnt fish` | **NO** | Burnt fish disambig (shares trout's) |
| Raw herring | `Burnt fish` | **NO** | Burnt fish disambig |
| Raw mackerel | `Burnt fish` | **NO** | Burnt fish disambig (shares herring's) |
| Raw pike | `Burnt fish` | **NO** | Burnt fish disambig (shares trout's) |
| Raw salmon | `Burnt fish` | **NO** | Burnt fish disambig (shares trout's) |
| Raw sardine | `Burnt fish` | **NO** | Burnt fish disambig |
| Raw trout | `Burnt fish` | **NO** | Burnt fish disambig |
| Raw tuna | `Burnt fish` | **NO** | Burnt fish disambig |
| Raw cavefish | *none — `Ruined cavefish`* | see below | [Cavefish](https://oldschool.runescape.wiki/w/Cavefish) |

**Mapped: 19. Excluded: 11.** Coverage verified programmatically: all 30 accounted for, no extras.

### The ambiguous ones, resolved

- **Raw cave eel vs Raw slimy eel** — cleanly separate. `Raw cave eel` → **`Burnt cave eel`** (Burnt fish disambig). `Raw slimy eel` → **`Burnt eel`** (Burnt eel recipe, `mat1 = Raw slimy eel`). Both cards exist. No overlap, no shared item.
- **Raw ugthanki meat** → **`Burnt meat`**, same as beef/bear/rat. There is no "Burnt ugthanki meat" item. Confirmed by the Burnt meat recipe tabber.
- **Raw bear meat / Raw rat meat / Raw beef** → all **`Burnt meat`**. Yes, all three share one card, alongside ugthanki. (The same page also lists boar/yak/impaler meat and cooked meat/cooked mystery meat as Burnt meat sources — out of scope but relevant if the plugin's cooking rules ever expand.)
- **Potato** → **`Burnt potato`** (lvl 7, range only — potatoes cannot be cooked on a fire, worth noting for the gate).
- **Bread dough** → **`Burnt bread`**; **Pitta dough** → **`Burnt pitta bread`**. Both straightforward.

---

## Judgment call: Raw cavefish (needs an owner decision)

**`Burnt cavefish` does not exist and has no card.** But this one is not a simple exclusion, because the mechanic is different from every other item on the list.

`Raw cavefish` is **Ruins of Camdozaal** content (the F2P Imcando dwarf area below Ice Mountain), alongside `Raw guppy`, `Raw tetra`, `Raw catfish`. Per the [Cavefish](https://oldschool.runescape.wiki/w/Cavefish) recipe wikitext:

```
|skill1 = Cooking
|skill1lvl = 20
|tools = Knife
|facilities = Preparation Table
|members = No
|mat1 = Raw cavefish
|output1 = Cavefish
```

It is **prepared with a knife at a Preparation Table**, not cooked on a fire/range. The failure product is **`Ruined cavefish`** — and **that card DOES exist** in Card.json (alongside `Ruined guppy`, `Ruined tetra`, `Ruined catfish`).

I have excluded it from `burntMapping` because it is not a burnt item and the toggle is described as a *burnt*-item gate. But the owner may reasonably want the toggle to cover it, since a card exists and it is mechanically the same idea (failure byproduct of a Cooking-gated action). Flagging rather than deciding.

Secondary note: if the plugin gates `Raw cavefish` as "raw food on a fire/range", that gate may not fire at all in Camdozaal — the interaction is knife-on-fish at a preparation table. Worth a separate look at the existing rule; outside this research task's scope.

---

## Crushed gems — verdict

**Confirmed: only opal, jade, and red topaz can crush. Sapphire, emerald, ruby, diamond, dragonstone, onyx, and zenyte NEVER crush.**

The [Gem](https://oldschool.runescape.wiki/w/Gem) article is explicit and definitive on both halves:

> "For the semi-precious gems - opal, jade and red topaz - there is a chance that the gem will be smashed into a crushed gem."

> "For the precious gems, this cutting always succeeds."

Corroborated by [Crushed gem](https://oldschool.runescape.wiki/w/Crushed_gem), which describes the item as created when "a player fails to cut a semi-precious gem," and whose creation table lists exactly three sources:

| Gem | Crafting level | XP |
|---|---|---|
| Uncut opal | 1 | 3.7 |
| Uncut jade | 13 | 5 |
| Uncut red topaz | 16 | 6.3 |

The owner's belief is correct as stated. Two independent wiki pages agree; the precious-gem claim is stated affirmatively ("always succeeds") rather than merely being an absence of evidence, which is the strong form of confirmation.

**Does cutting opal/jade/red topaz produce the item literally named `Crushed gem`?** Yes. [Uncut opal](https://oldschool.runescape.wiki/w/Uncut_opal): "There is a chance to unsuccessfully cut the opal, resulting in a `crushed gem` and 3.8 experience." Singular, exactly `Crushed gem`. Card verified present.

So: `crushableGems = ["Uncut opal", "Uncut jade", "Uncut red topaz"]` — 3 of the plugin's 10 recipes get the optional requirement; the other 7 never do.

Minor note: the crush chance decreases with Crafting level but **never reaches zero** for these three (unlike precious gems, which are 100% from the level requirement onward). Uncut opal's success formula from the wiki is `((level-1)*122/98+129)/256` — at level 99 that is ~245/256, so ~4% crush even at max. The requirement is therefore never fully "outgrown," which is arguably a point in favour of the toggle being useful.

### `Crushed infernal shale` — irrelevant, but adjacent

Per [Crushed infernal shale](https://oldschool.runescape.wiki/w/Crushed_infernal_shale): it is made by deliberately "crushing infernal shale with a hammer and chisel," a material-prep step for infernal nuggets → oathplate armour. **Not a gem, not a failure product, no relation to gem cutting.** Correctly out of scope here.

One caveat worth passing on: it *is* a chisel activity. If the plugin's chisel gate is keyed on "Chisel used on X" broadly rather than on the 10 uncut-gem recipes specifically, infernal shale could interact with it. That is a code question, not a data question, so I did not investigate the plugin source.

---

## Uncertainties / confidence

- **High confidence** on all 19 mapped items and all 10 excluded fish — every one traced to a recipe block or the disambiguation table in raw wikitext, and every card name exact-matched programmatically.
- **High confidence** on the gem verdict — two pages, explicit affirmative statement for both the crushable and non-crushable sets.
- **Open decision** on `Raw cavefish` only (see above). Nothing else is unresolved.
- Method note: initial WebFetch summaries paraphrased and in two cases *misreported* the data (one claimed the Burnt fish page proved each fish has a uniquely-named burnt item — the exact opposite of what it says). All findings above were re-derived from raw wikitext via `api.php?action=parse&prop=wikitext`. Do not trust the prose-summary layer for this kind of question.
