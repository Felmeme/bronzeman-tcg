# Quest nested `ANY` groups audit

Audit date: 2026-08-03. Source: `src/main/resources/quest_cards.json`.

The audit looked for `ANY` requirements whose children could be merged into one
flat `cards` list. A group is only an exact flattening candidate when every child
is a simple card alternative with no quantity, selector, nested `ALL` logic or
special display behaviour.

## Flattened in this pass

- **King's Ransom — Air runes or staff**: 10 item-card alternatives.
- **Temple of Ikov — Yew+ bow or throwable weapon**: 29 item-card alternatives.
- **The Feud — Gloves (check wiki for more)**: 21 item-card alternatives.
- **While Guthix Sleeps — Catalyst Runes**: 5 item-card alternatives.

These four retain the same ownership rule: any one listed card satisfies the
requirement.

## Keep nested: alternative-specific quantities

Flattening these would discard useful quantity information.

- **A Taste of Hope — Enchant Emerald Runes/Tablet > Emerald enchant runes > Air runes**: Air rune x3 or an air-providing staff.
- **At First Light — Jerboa tails or Box trap**: Jerboa tail x2 or Box trap.
- **Eadgar's Ruse — Climbing boots or Coins**: Coins x12 alternative.
- **Icthlarin's Little Helper — Linen or Coins**: Coins x30 alternative.
- **In Aid of the Myreque — Raw mackerel or Raw snail**: Raw mackerel x10 alternative.
- **Legends' Quest — Charge Orb Runes > Elemental runes**: 30 of the selected elemental rune.
- **Monkey Madness II — Monkey talisman or Coins**: Coins x1,000 alternative.
- **Sins of the Father — Enchant Ruby Runes/Tablet > Ruby enchant runes > Fire runes**: Fire rune x5 or a fire-providing staff.
- **The Fremennik Exiles — Kegs of beer or Coins**: Keg of beer x2 or Coins x650.
- **Throne of Miscellania — A tool or coins to acquire favour on Miscellania**: Coins x1,875 alternative.
- **Vampyre Slayer — Beer or Coins**: Coins x2 alternative.
- **While Guthix Sleeps — Charge Orb Runes > Elemental runes**: 30 of the selected elemental rune.

## Keep nested: combinations, routes or deeper alternatives

Flattening these would change `ALL`/`ANY` semantics, remove route selection or
make a spell/tablet alternative count incorrectly.

- **A Taste of Hope — Enchant Emerald Runes/Tablet**: complete rune combination OR tablet.
- **Dragon Slayer II — Fire Wave/Surge Runes**: two complete spell alternatives.
- **Enakhra's Lament — Fire Bolt or stronger**: four complete spell alternatives.
- **Enakhra's Lament — Wind Bolt or stronger**: four complete spell alternatives.
- **Heroes' Quest — Your Shield of Arrav gang route**: route-selected requirements.
- **King's Ransom — Telegrab Runes/Lockpick**: complete Telegrab combination plus Lockpick.
- **Shield of Arrav — Your Shield of Arrav gang route**: route-selected requirements.
- **Sins of the Father — Vyrewatch outfit or Coins**: complete outfit OR coin alternative.
- **Sins of the Father — Enchant Ruby Runes/Tablet**: complete rune combination OR tablet.
- **The Great Brain Robbery — Wooden cats or Materials**: nested material combination.
- **Underground Pass — Arrows (unpoisoned) and fire arrows**: each valid alternative requires a matching normal and fire-arrow pair.
- **Wanted! — Materials or Coins**: complete material combination OR coin alternative.
- **Zogre Flesh Eaters — Brutal arrows or Crumble Undead**: arrow alternative OR complete spell combination.

## Separate findings

- **Sins of the Father — Air runes** was a handwritten label mismatch: its
  children require Fire rune or a fire-providing staff. It was renamed to
  **Fire runes**.
- `Rune crossbow` remains referenced by three Any crossbow groups but does not
  match the current TCG card name `Runite crossbow`. This requires an owner
  ruling and was not changed in this pass.
