# Quest Walkthrough Mining Report

Goal: find quest-critical, card-gated interactions hidden in walkthrough **prose** that the
INFOBOX-only `quest_cards.json` missed, restricted to the three interaction shapes the plugin
actually blocks:

1. item-on-NPC / spell-on-NPC where the NPC is card-tracked (in `tracked_monster_names.json`)
2. using/possessing a card-tracked ITEM obtained mid-quest that the infobox omitted
3. attacking a card-tracked NPC not listed in the quest's kills field

## Method

1. Enumerated all 206 quest names from `quest_cards.json`.
2. Batch-fetched every wikitext via the MediaWiki revisions API (50 titles/request, ~5 calls,
   `redirects=1`). 206/206 resolved, 0 missing. Cached to `wikicache/all_wikitext.json` (3.4 MB).
   No per-page WebFetch was used.
3. Scripted extraction: sliced each page's `== Walkthrough ==` section (level-2 to next level-2
   header; 14 quests with no such header fell back to "everything before Rewards" — flagged below).
   Extracted every `[[wiki link]]`, cross-referenced link targets against (a) `Card.json` item
   names and (b) the monster-snapshot NPC keys, then subtracted every card already in that quest's
   `cardGroups` + `monsterCards`.
4. Ranked surviving candidates by action-verb proximity, with a +3 boost for `use ... on` /
   `cast ... on` phrasing and penalties for teleport/reward/recommended/optional wording.
5. I hand-classified only the ranked candidate sentences (small text), keeping GENUINE only where
   the prose shows an interaction the quest cannot proceed without.

## Candidate funnel

- Raw walkthrough link matches against cards/NPCs: **2,572**
- Unique (quest, card) candidates after de-dup: **1,799**
- Candidates surfaced at score >= 2 for human review: **230**, across **189** quests
- Quests with zero surviving candidates (**17**, no card/NPC link in the walkthrough outside data
  already in the infobox): Architectural Alliance, Biohazard, Enter the Abyss, Ethically Acquired
  Antiquities, Fallen From Grace, His Faithful Servants, Holy Grail, Lost Lover, Murder Mystery,
  Pirate's Treasure, Spirit of Adventure, The Graveyard, The Hand in the Sand, The Ides of Milk,
  The Queen of Thieves, The Restless Ghost, Witch's House.
- 14 quests used the fallback walkthrough slice (no `== Walkthrough ==` header): Barbarian Training,
  Desert Treasure II, Dragon Slayer II, Grim Tales, Monkey Madness I & II, Observatory Quest,
  Rag and Bone Man II, Recipe for Disaster, Rogue Trader, Secrets of the North, Song of the Elves,
  The Blood Moon Rises, While Guthix Sleeps. These pull from `== Details ==`/reward tables too, so
  their candidates carry more noise.

## SHIPPED (GENUINE) — see quest_mining_additions.json

| Quest | Added | Shape | Why |
|-------|-------|-------|-----|
| Rag and Bone Man I | Giant rat, Unicorn, Black bear, Ram, Goblin, Big frog, Giant bat, Monkey (monster) | 3 (attack) | The quest's core loop is killing these 8 creatures for their bones; `monsterCards` was completely empty. |
| Watchtower | Ogre shaman, Enclave guard | 1 (item-on-NPC) | "use the magic ogre potion on each of the six ogre shamans" (quest climax) and "use one of your nightshades on an enclave guard ... to distract them ... and enter the cave" (only way into the Ogre Enclave). Both NPCs card-tracked; neither in existing data. |
| Legends' Quest | Ungadulu | 1 (item-on-NPC) | "use your binding book on Ungadulu to release the demon, Nezikchened." Required story beat; Ungadulu is card-tracked and absent from the quest's monster list. |

All 11 cards exact-matched against `Card.json`. Watchtower/Legends targets placed in `cardGroups`
to mirror the patched Children of the Sun "Guard" (item-on-NPC → whitelist the NPC card). RBM I
kills placed in `monsterCards`.

## Classified NOISE (high volume, representative reasons)

The bulk of the 230 review candidates were rejected as non-gating:

- **Lore / cutscene mentions** of a tracked NPC (no player interaction): e.g. Dragon Slayer II
  "player's defeat of Elvarg" (recap of DS1), Defender of Varrock "the now-undead Arrav", A Tail of
  Two Cats "a conversation between Bob and the King Black Dragon".
- **Optional / alternative kills**: "you can kill", "you may kill", "for example", diary-task asides
  — Lair of Tarn Razorlor terror dog (Medium Morytania task), Legends' Quest death wing (Karamja
  Hard task), Olaf's Quest brine rat, Jungle Potion jogre.
- **Food / convenience kills**: Regicide rabbit, Swan Song chicken/highwayman, Waterfall Quest
  "attack a lower level skeleton to bait the Moss Guardian".
- **Pathing hazards** ("run past the X", "avoid the aggressive Y"): Monkey Madness II spiders,
  The Heart of Darkness frost crabs, The Path of Glouphrie warped tortoise.
- **Crafting intermediates from already-listed ingredients** (bread dough, soft clay, bronze bar,
  bucket of water, etc.): Learning the Ropes, Merlin's Crystal, Monk's Friend, Prince Ali Rescue,
  One Small Favour. Treated as noise here — see the "made mid-quest items" question below.
- **Teleport-jewellery mentions** captured as items: Games necklace, Skills necklace,
  Xeric's talisman.

## UNCERTAIN — owner to rule on (NOT shipped)

### A. Item obtained mid-quest, then required for a step (shape 2)
The plugin's shape-2 concern ("a locked item can't be used under forced-drop") may or may not bite,
since obtaining an item mid-quest normally *unlocks* it in the same action. Left for the owner:

- **At First Light — Toy mouse.** "Wolf will give you a toy mouse; wind it up and then use it on
  Kiko." Given mid-quest, must be used. Card `Toy mouse` exists; Kiko is NOT card-tracked (so no
  shape-1 NPC add). Ship only if force-drop can strip a just-given quest item before use.
- **Song of the Elves — Ode to eternity.** "he will hand you a book, Ode to eternity, with a riddle,
  that requires you to use items on the pillars." Auto-given book; card exists.

### B. Required kills inside high-complexity / alternative-heavy quests
"Must kill" language is present but tangled with alternative sources, so the *specific* card-tracked
NPC isn't strictly forced — owner should decide the canonical required set:

- **Rag and Bone Man II** — `monsterCards` is empty, yet the quest is a large collect-bones/ashes
  loop. Candidates with kill language: Basilisk ("though not necessary"), Zogre, Lizard ("You must
  kill level 42 lizard"), Undead cow, Zombie, Rat, Vulture, Jackal, Bunny, plus Warped/Terrorbird
  variants. Many have alternative sources or diary framing; a curated required set would need a pass
  against the quest's two display item lists.
- **Desert Treasure II — Scarred imp.** "players must kill all the scarred imps, who are providing
  protection Prayers to the lesser demons." Reads required, but sits inside a boss-approach section
  of a very large quest; unclear if the infobox boss list is considered sufficient.
- **Witch's Potion — Giant rat OR Cow.** "Kill a giant rat ... or kill a cow ... and cook the meat."
  Burnt meat is a genuine ingredient, but the rat/cow choice makes neither NPC individually forced.
- **Zogre Flesh Eaters — Zogre / Skogre.** "Brutal arrows are the best way to kill Zogres ... in the
  final fight." Final fight is against Slash Bash; unclear whether generic Zogre kills are required.
- **Shilo Village — Zombie / Skeleton / Ghost.** These are the three transformation forms of the
  boss Nazastarool ("he will turn into a level 68 skeleton and then a level 93 ghost"). If the boss
  NPC is named "Nazastarool" in-game the generic cards won't match, so likely not gating.

## Caveats

- Extraction keys on `[[wiki links]]`; a required interaction written in plain text with no link
  would be missed. Spot-checks suggest walkthroughs link the relevant NPC/item nearly always.
- Card-name collisions: a few NPCs also exist as unrelated item cards (e.g. "Mayor of catherby" is
  a Card.json *item* but the Mayor NPC is not in the monster snapshot) — these were rejected because
  the plugin only gates NPCs present in the snapshot.
- The 17 "zero candidate" quests and the fallback-slice list are programmatic; treat as directional.

---

## Owner rulings on the UNCERTAIN cases (2026-07-16)

- **SHIPPED**: At First Light (Toy mouse) and Song of the Elves (Ode to
  eternity) — mid-quest given items DO gate under forced drop (obtaining an
  item is not card ownership); labels use the owner's wording "Card
  required: Item is used mid-quest". Desert Treasure II (Scarred imp).
  Witch's Potion as an any-of Giant rat / Cow group. Rag and Bone Man II
  via a dedicated wiki pass: all 27 bones -> 26 monster kills resolved
  (troll bone mapped to Mountain troll; the snapshot's bare "troll" is the
  POH Construction pet) plus a Rabbit / Bunny any-of group.
- **SKIPPED**: Zogre Flesh Eaters (generic zogre kills not clearly forced;
  the fight is Slash Bash) and Shilo Village (the Zombie/Skeleton/Ghost
  forms belong to the boss NPC Nazastarool, whose name won't match generic
  cards).
