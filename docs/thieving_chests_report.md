# Thievable chest data report

Generated on 2026-07-26 by `scripts/rebuild_thieving_chest_data.js`.

## Result

- 16 official thievable chest types covered.
- 20 exact object-ID and menu-option combinations.
- Every included loot card validates against `tracked_item_names.json`.
- Rules use category `thieving-chests` and the existing Stalls & Chests
  setting: Off / Any Of / All.
- Object IDs are authoritative. This keeps chests with the same in-game name
  but different loot tables separate.

## Coverage

| Chest | Object ID(s) | Option | Card-backed loot |
| --- | --- | --- | ---: |
| Underwater | 30971 | Search | 7 |
| 10 coin | 11735 | Search for traps | 1 |
| Nature rune | 11736 | Search for traps | 2 |
| Isle of Souls | 40739 | Picklock | 19 |
| Rusty pirate | 60511, 60512 | Picklock | 19 |
| Aldarin Villas | 54773 | Picklock | 35 |
| 50 coin | 11737 | Search for traps | 1 |
| Steel arrowtips | 11742 | Search for traps | 1 |
| Dorgesh-Kaan average | 22697, 22698 | Pick-lock | 42 |
| Tarnished pirate | 60514, 60515 | Picklock | 18 |
| Blood rune | 11738 | Search for traps | 2 |
| Stone | 34429 | Picklock | 31 |
| Ardougne Castle | 11739 | Search for traps | 4 |
| Reinforced pirate | 60517, 60518 | Picklock | 18 |
| Dorgesh-Kaan rich | 22681 | Pick-lock | 15 |
| Rogues' Castle | 26757 | Search for traps | 17 |

## Data decisions

- Only successful loot actions are restricted. Classic trapped chests use
  `Search for traps`; their damaging, no-loot `Open` action is not gated.
- The underwater rule uses searchable open-state object ID 30971. The closed
  state has no relevant player option.
- Exact Wiki menu spelling is retained: modern chests use `Picklock`, while
  Dorgesh-Kaan uses `Pick-lock`.
- Loot without a matching card is ignored: clue scrolls, Steel arrowtips and
  the Stone chest's bolt tips.
- Item states are folded to their card where appropriate: Medallion
  fragment#1, inert Xeric's talisman and Prayer potion(2).
- Storage, bank, raid, reward and Pyramid Plunder chests are intentionally
  outside this feature.

## Sources

- Plain OSRS Wiki pages for Thieving and each listed chest, fetched through
  the edge cache and parsed locally.
- RuneLite `ObjectID` source for the classic trapped chests and searchable
  underwater chest state.

No menu string was guessed.

## Suggested manual pass

1. Compare the 10 coin and Nature rune chests to confirm same-name object
   separation.
2. In Any Of, own one listed loot card and confirm the chest unlocks.
3. In All, leave one listed loot card locked and confirm the click is blocked
   with the missing-card message.
4. Test one `Picklock` chest and one Dorgesh-Kaan `Pick-lock` chest.
5. If accessible, test the underwater and Sailing pirate chests.
6. Confirm an unrelated storage or reward chest remains unaffected.
