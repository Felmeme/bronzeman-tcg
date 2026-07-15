# Thievable Stall Restriction Data — Report

Source: OSRS Wiki `Stall/Thievable` consolidated table + per-stall `Infobox Scenery` (object name & options) and `DropsLineSkill` loot, cross-checked via MediaWiki API. Game state ~2025/2026. Every card exact-matched against `Card.json` (assertion passed in build script).

- **Stall types enumerated:** 22 (21 tracked, 1 fully excluded)
- **Output nodes:** 26 (some stall types expand to multiple distinct in-game object-name variants)
- **Menu option:** canonical `Steal-from`; stored lowercase `steal-from`. Ape Atoll stalls + Seed stall + one Crafting variant show `Steal from` (space, no hyphen) in the wiki infobox, so those nodes carry BOTH `steal-from` and `steal from` for safety.

## Per-stall table

| Stall (location) | Object name(s) → node(s) | Option | Loot table | Cards kept | Excluded loot |
|---|---|---|---|---|---|
| Vegetable stall (Miscellania/Etceteria/Port Roberts) | `Veg stall`  | steal-from | Potato, Cabbage, Onion, Tomato, Garlic | Potato, Cabbage, Onion, Tomato, Garlic | — |
| Baker's / Bakery stall (Ardougne, Keldagrim, Kourend, Varlamore) | `Baker's stall` / `Bakery stall` / `Baker's Stall`  | steal-from | Bread, Cake, Chocolate slice | Bread, Cake | Chocolate slice |
| Tea stall (Varrock, Kourend) | `Tea stall`  | steal-from | Cup of tea | Cup of tea | — |
| Monkey food stall (Ape Atoll) | `Food Stall`  | steal-from , steal from | Banana | Banana | — |
| Crafting stall (Ape Atoll, Keldagrim) | `Crafting Stall` / `Crafting stall`  | steal-from , steal from | Chisel, Ring mould, Necklace mould, Amulet mould, Bracelet mould, Gold bar | Chisel, Ring mould, Necklace mould, Amulet mould, Bracelet mould, Gold bar | — |
| Monkey general stall (Ape Atoll) | `General Stall`  | steal-from , steal from | Pot, Hammer, Tinderbox | Pot, Hammer, Tinderbox | — |
| Counter (Gu'Tanoth) | `Counter` **EXCLUDED (no cards)** | steal-from | Rock cake | — | Rock cake |
| Silk stall (Ardougne, Kourend, Prifddinas, Varlamore, Port Roberts) | `Silk stall`  | steal-from | Silk | Silk | — |
| Wine stall (Draynor) | `Market stall`  | steal-from | Bottle of wine, Grapes, Jug, Jug of water, Jug of wine | Grapes, Jug, Jug of water, Jug of wine | Bottle of wine |
| Fruit stall (Kourend) | `Fruit Stall`  | steal-from | Cooking apple, Banana, Strawberry, Redberries, Jangerberries, Strange fruit, Lime, Lemon, Pineapple, Papaya fruit, Golovanova fruit top | Cooking apple, Banana, Strawberry, Redberries, Jangerberries, Strange fruit, Lime, Lemon, Pineapple, Papaya fruit, Golovanova fruit top | — |
| Seed stall (Draynor) | `Seed Stall`  | steal-from , steal from | Hammerstone seed, Potato seed, Marigold seed, Barley seed, Onion seed, Asgarnian seed, Cabbage seed, Yanillian seed, Rosemary seed, Nasturtium seed, Tomato seed, Jute seed, Sweetcorn seed, Krandorian seed, Strawberry seed, Wildblood seed, Watermelon seed | Hammerstone seed, Potato seed, Marigold seed, Barley seed, Onion seed, Asgarnian seed, Cabbage seed, Yanillian seed, Rosemary seed, Nasturtium seed, Tomato seed, Jute seed, Sweetcorn seed, Krandorian seed, Strawberry seed, Wildblood seed, Watermelon seed | — |
| Fur stall (Ardougne, Rellekka, Varlamore, Port Roberts) | `Fur stall`  | steal-from | Fur, Grey wolf fur, Bear fur | Fur, Grey wolf fur, Bear fur | — |
| Fish stall (Miscellania/Etceteria/Rellekka/Warrens/Port Roberts) | `Fish stall`  | steal-from | Raw salmon, Raw tuna, Raw lobster, Raw swordtip squid, Raw giant krill, Raw haddock | Raw salmon, Raw tuna, Raw lobster, Raw swordtip squid, Raw giant krill, Raw haddock | — |
| Crossbow stall (Keldagrim) | `Crossbow stall`  | steal-from | Bronze bolts, Bronze limbs, Mithril bolts, Mithril limbs, Wooden stock | Bronze bolts, Bronze limbs, Mithril bolts, Mithril limbs, Wooden stock | — |
| Silver stall (Ardougne, Keldagrim, Kourend, Prifddinas, Port Roberts) | `Silver stall`  | steal-from | Silver ore, Silver bar, Tiara | Silver ore, Silver bar, Tiara | — |
| Spice stall (Ardougne, Prifddinas, Varlamore, Port Roberts) | `Spice stall`  | steal-from | Spice | Spice | — |
| Magic stall (Ape Atoll) | `Magic Stall`  | steal-from , steal from | Air rune, Earth rune, Fire rune, Nature rune, Law rune | Air rune, Earth rune, Fire rune, Nature rune, Law rune | — |
| Scimitar stall (Ape Atoll) | `Scimitar Stall`  | steal-from , steal from | Iron scimitar, Steel scimitar, Mithril scimitar, Adamant scimitar | Iron scimitar, Steel scimitar, Mithril scimitar, Adamant scimitar | — |
| Gem stall (Ardougne, Keldagrim, Kourend, Prifddinas, Varlamore, Port Roberts) | `Gem stall` / `Gem Stall`  | steal-from | Uncut sapphire, Uncut emerald, Uncut ruby, Uncut diamond, Sapphire, Emerald, Ruby, Diamond | Uncut sapphire, Uncut emerald, Uncut ruby, Uncut diamond, Sapphire, Emerald, Ruby, Diamond | — |
| Gem + Ore stall (Mor Ul Rek / TzHaar) - both objects share name 'Shop Counter' | `Shop Counter`  | steal-from | Uncut sapphire, Uncut emerald, Uncut ruby, Uncut diamond, Uncut dragonstone, Uncut onyx, Sapphire, Emerald, Ruby, Diamond, Dragonstone, Onyx, Iron ore, Coal, Silver ore, Gold ore, Mithril ore, Adamantite ore, Runite ore | Uncut sapphire, Uncut emerald, Uncut ruby, Uncut diamond, Uncut dragonstone, Uncut onyx, Sapphire, Emerald, Ruby, Diamond, Dragonstone, Onyx, Iron ore, Coal, Silver ore, Gold ore, Mithril ore, Adamantite ore, Runite ore | — |
| Ore stall (Port Roberts) | `Ore stall`  | steal-from | Coal, Iron ore, Silver ore, Gold ore, Mithril ore, Adamantite ore, Runite ore | Coal, Iron ore, Silver ore, Gold ore, Mithril ore, Adamantite ore, Runite ore | — |
| Cannonball stall (Port Roberts) | `Cannonball stall` / `Cannonball Stall`  | steal-from | Bronze cannonball, Iron cannonball, Steel cannonball, Mithril cannonball, Adamant cannonball, Rune cannonball, Dragon cannonball | Bronze cannonball, Iron cannonball, Steel cannonball, Mithril cannonball, Adamant cannonball, Rune cannonball, Dragon cannonball | — |

## Excluded whole nodes (untracked = unrestricted)

- **['Counter']** (Counter (Gu'Tanoth)) — entire loot table ['Rock cake'] has no card. Node omitted so stealing stays unrestricted.

## Excluded individual loot (item exists in-game but no card)

- `Chocolate slice` — from Baker's / Bakery stall
- `Rock cake` — from Counter
- `Bottle of wine` — from Wine stall

## Name-variant ambiguities & judgment calls

**Multi-name objects (one stall type → several distinct in-game object-name strings, each its own node with the same loot group, per task spec):**
- **Baker's / Bakery stall** → 3 nodes: `Baker's stall`, `Bakery stall`, `Baker's Stall`. Infobox lists name1=`Baker's stall`, name2=`Bakery stall`, name3=`Baker's Stall` (regional wording/casing differences). All ship the same group `[Bread, Cake]`.
- **Gem stall** → 2 nodes: `Gem stall`, `Gem Stall` (name1/name2 casing).
- **Crafting stall** → 2 nodes: `Crafting Stall` (option `Steal from`) and `Crafting stall` (option `Steal-from`). Both carry both option strings anyway.
- **Cannonball stall** → 2 nodes: `Cannonball stall`, `Cannonball Stall`.

**Vegetable stall:** in-game object name is `Veg stall` (the wiki article `Vegetable stall` redirects there; infobox name=`Veg stall`). Node uses `Veg stall`. If the client actually reports `Vegetable stall`, add that string.

**Wine stall:** object name is the generic `Market stall` (Draynor), option `Steal-From` → `steal-from`. Flag: `Market stall` is a generic string; verify no non-thievable Draynor market object collides (option gate `steal-from` should prevent false matches).

**Mor Ul Rek / TzHaar (Karamja):** BOTH the gem stall and the ore stall are in-game objects named `Shop Counter` with option `Steal-from`. Because (object name, option) is identical, the plugin cannot distinguish them, so their loot tables are **UNIONED into one `Shop Counter` node** (gems + ores, cut & uncut incl. dragonstone/onyx). Caveat: `Shop Counter` is a very generic name — confirm no unrelated `Shop Counter` object offers a `Steal-from` menu entry elsewhere.

**Prifddinas (Tirannwn):** the wiki pages `Prifddinas Gem/Silver/Spice Stall` are **shop interfaces** (Infobox Shop, run by Glenda/Osian/Caerwyn), NOT separate thievable objects. The thievable objects in Prifddinas are the ordinary `Gem stall` / `Silver stall` / `Spice stall` (same object names, same steal loot). No separate Prifddinas nodes created. (Their shop stock — Unstrung symbol, Knife — is irrelevant to thieving loot.)

**Port Roberts (Sailing content):** every Port Roberts stall shares its base object name (`Veg stall`, `Silk stall`, `Fur stall`, `Fish stall`, `Silver stall`, `Spice stall`, `Gem stall`, `Ore stall`) — infobox names confirmed identical to the mainland versions. Where Port Roberts loot is a superset, the extra items are folded into the base node:
  - `Fur stall`: base `Fur, Grey wolf fur` + Port Roberts `Bear fur` → group `[Fur, Grey wolf fur, Bear fur]`.
  - `Fish stall`: base 3 fish + Port Roberts `Raw swordtip squid, Raw giant krill, Raw haddock` → all 6 (all cards).
  - `Gem stall`: base uncut gems + Port Roberts cut gems (`Sapphire/Emerald/Ruby/Diamond`) → all 8.
  - `Ore stall` node covers the Port Roberts ore stall (distinct object name `Ore stall`, separate from TzHaar `Shop Counter`).
  - `Cannonball stall` is Port Roberts-only.

**Option string caveat:** wiki infoboxes are hand-entered; casing/spacing (`Steal-from` / `Steal-From` / `Steal from`) is inconsistent. The plugin is assumed to case-fold (the spec example uses lowercase `steal-from`), so only hyphen-vs-space matters — handled by carrying both forms on the affected nodes. Recommend the owner confirm in-client for Seed, Magic, Scimitar, Crafting, Food, General stalls.

**Fossil Island:** no thievable stall exists there in current OSRS — nothing to add (task raised it as a possibility).

**Fur stall base loot:** wiki `DropsLineSkill` only tagged `Grey wolf fur`, but the summary/table text and the Ardougne stall drop plain `Fur` too; both kept (both are cards).
