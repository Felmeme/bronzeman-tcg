# Slayer rebuild report

Generated from RuneLite's local Slayer master/task/area DB tables. Konar's
location-specific alternatives come from the cached plain Wiki page; the rebuild
script performs no network requests.

## Generated coverage

| Master | Cache assignments | Generated requirements | Aliases |
|---|---:|---:|---|
| Turael | 24 | 24 | Turael, Aya |
| Spria | 25 | 25 | Spria |
| Mazchna | 30 | 30 | Mazchna, Achtryn |
| Vannaka | 46 | 46 | Vannaka |
| Chaeldar | 40 | 40 | Chaeldar |
| Nieve | 46 | 45 | Nieve, Steve |
| Duradel | 43 | 42 | Duradel, Kuradal |
| Krystilia | 37 | 35 | Krystilia |
| Konar quo Maten | 39 | 108 | Konar quo Maten |

- Final master nodes: **13**
- Source DB rows: **330**
- Deliberately excluded assignment rows: **5**
- Konar task/location pairs left unrestricted: **1**

## Deliberate policy

- Early masters receive base/common variants only; higher masters can use reviewed boss substitutes.
- Krystilia uses only base cards plus explicitly Wilderness-valid boss substitutes.
- Boss and Revenant assignment categories remain excluded. Bosses can still be valid substitutes for a normal high-master task.
- Superiors remain separate opt-in requirements and are preserved from the existing data.
- Sergeant Strongstack, Sergeant Grimspike, and Sergeant Steelwill are explicitly excluded from Goblin groups.

## Excluded assignment rows

- Nieve: Boss
- Duradel: Boss
- Krystilia: Boss
- Krystilia: Revenants
- Konar quo Maten: Boss

## Konar areas intentionally left unrestricted

- Vampyres — Vampyrium (an allowed alternative has no card)

An area is left unrestricted when the Wiki lists a valid alternative which has no
TCG card; blocking the area would otherwise reject a monster the plugin can never unlock.

## Unclassified candidate variants (excluded, not guessed)

- Birds: Duck → Duck
- Birds: Penguin → Penguin (monster)
- Trolls: Berry → Berry
- Wyrms: Wyrmling → Wyrmling

These are candidates from RuneLite's broad task matcher or the earlier research file
which were not explicitly classified by the reviewed policy. They are reported and
excluded rather than silently widening a master.
