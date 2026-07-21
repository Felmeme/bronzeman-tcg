# Slayer task variant substitution — research report

Maps each Slayer task monster to the set of monster **cards** that also complete
that same assignment (variant substitution), so "Full Task List" completion can
accept ANY carded variant instead of the exact base card.

- **Output:** `slayer_variants.json` — 62 of the 147 distinct task monsters got a
  variant group (≥1 carded variant beyond themselves). The other 85 have no
  carded non-superior variant and are omitted (plugin keeps their single-card rule).
- **Total distinct variant names emitted:** 235.
- **Superiors deliberately EXCLUDED** everywhere (handled by the plugin's separate
  Bigger-and-Badder mechanic): Abhorrent/Repugnant spectre, Greater abyssal demon,
  Monstrous basilisk, Basilisk Sentinel, Insatiable (mutated) Bloodveld, Cave
  abomination, Cockathrice, Choke devil, Marble gargoyle, Vitreous/Vitreous
  warped/Vitreous chilled Jelly, King kurask, Nechryarch, Flaming pyrelord, Giant
  rockslug, Shadow wyrm, Night beast, Nuclear smoke devil, Spiked turoth, Chasm
  crawler, Crushing hand, Malevolent mage, Screaming (twisted) banshee, Dark ankou,
  Colossal hydra, Dreadborn araxyte, Elder aquanite, Guardian drake, King scorpion?
  (no — King scorpion is a regular scorpion, kept).
- **Validation:** every emitted name was checked against `monster_cards.json`
  (case-insensitive, with wiki-bracket stripping to mirror the plugin's own
  matching). Zero misses. See `build_slayer_variants.py` (re-runnable).
- **Wiki requests:** ~46 distinct plain-page fetches (WebFetch, edge-cached URLs,
  no api.php). The `Slayer_task` master page was queried several times but is one
  cached URL. Two 404s (Elf (Slayer), Shade (Mort'ton)) were routed through the
  cached Slayer_task page instead.

## Verified variant groups (individually wiki-confirmed)

| Task | Added carded variants | Note |
|---|---|---|
| Aberrant spectre | Deviant spectre | catacombs variant |
| Bloodveld | Mutated Bloodveld | Acidic Bloodveld EXCLUDED (see gaps) |
| Jelly | Warped Jelly, Chilled Jelly | Chilled = Ruins of Tapoyauik |
| Nechryael | Greater Nechryael | |
| Smoke devil | Thermonuclear smoke devil | boss, task-only |
| Cave kraken | Kraken | boss counts (confirmed on Kraken page) |
| Basilisk | Basilisk Knight | category Basilisks |
| Black demon | Demonic gorilla, Skotizo, Porazdir, Balfrug Kreeyath | Balfrug is category **Black** (not Greater) per its page |
| Greater demon | K'ril Tsutsaroth, Tstanon Karlak, Skotizo, Tormented Demon | Skotizo is BOTH black+greater |
| Lesser demon | Zakl'n Gritch | Zakl'n is category **Lesser** (surprising — its own page) |
| Hellhound | Cerberus | |
| Hill Giant | Obor | boss, category Hill Giants |
| Moss giant | Bryophyta | boss, category Moss Giants |
| Gargoyle | Dawn, Dusk | Grotesque Guardians count |
| Hydra | Alchemical Hydra | boss, task-only |
| Dagannoth | Dagannoth Prime, Rex, Supreme, Dagannoth fledgeling | see gaps re spawn |
| Aviansie | Kree'arra, Wingman Skree, Flockleader Geerin, Flight Kilisa | |
| Banshee | Twisted Banshee | |
| Cockatrice | Moonlight cockatrice | Varlamore variant |
| Lizardman | Lizardman brute, Lizardman shaman | |
| Black dragon | Brutal black dragon, King Black Dragon | KBD except when Krystilia-assigned |
| Blue dragon | Brutal blue dragon, Vorkath | Vorkath = blue dragon + zombie |
| Red dragon | Brutal red dragon | |
| Green dragon | (NONE) | Brutal green does NOT count — all in Ancient Cavern, outside Wildy |
| Callisto | Artio | |
| Venenatis | Spindel | |
| Vet'ion | Calvar'ion | |

## Grouped assignments (whole set shares one task)

- **Kalphite** (Guardian/Soldier/Worker keys) → union of Guardian, Soldier, Worker,
  **Kalphite Queen**.
- **Fossil Island Wyverns** (Ancient/Long-tailed/Spitting/Taloned keys) → union of
  all four. NOTE: **Skeletal Wyvern is a separate task** (Asgarnian Ice Dungeon) and
  is NOT in this group.
- **TzHaar** (Hur/Ket/Mej/Xil keys) → the four city TzHaar + **TzTok-Jad** + **TzKal-Zuk**
  (both confirmed category TzHaar; Jad via "Hot Stuff", Zuk unlockable). Fight-Cave
  adds (Tok-Xil, Ket-Zek, Tz-Kih, Tz-Kek) were NOT included — they don't tick task
  progress individually.
- **Vampyres** (Feral Vampyre / Vampyre Juvinate keys) → both + Vyrewatch +
  Vyrewatch Sentinel (all category Vampyres). "Vampyre juvenile" (a separate card,
  different from "Vampyre Juvinate") was NOT included.
- **Elves** (Elf Warrior / Elf Archer keys) → both + Iorwerth Warrior + Iorwerth
  Archer + Mourner (Iorwerth Warrior confirmed category Elves; Archer/Mourner by the
  same grouping).
- **Zygomites** (Zygomite ↔ Ancient Zygomite) — mutual.
- **Shade** → Loar/Phrin/Riyl/Asyn/Fiyr/Urium shade (Mort'ton shade tiers).

## Low-level generic categories — MODERATE confidence, owner may wish to trim

These come from the wiki's Slayer-category listings via the `Slayer_task` page.
They are faithful to the slayer *category* membership (which is what ticks task
progress), but the lists are broad and worth an eyeball:

- **Rat**: Giant/Dungeon/Crypt rat, Scurrius. (Brine rat is its own assignment —
  excluded despite category overlap.)
- **Spider**: Giant/Shadow/Jungle/Deadly red/Poison/Blessed/Crypt/Temple spider,
  Kalrag, Sarachnis, Spindel, Venenatis, Araxyte, Araxxor. (Big group — Araxxor &
  the wildy spider bosses genuinely count as spiders.)
- **Scorpion**: King/Poison/Pit scorpion, Scorpia, Lobstrosity.
- **Bird**: Chicken, Rooster, Seagull, Terrorbird, Vulture, Undead chicken, Chompy
  bird, Mounted terrorbird gnome.
- **Bat**: Giant bat, Albino bat.
- **Wolf**: Big/Dire/Jungle/Desert/Ice wolf.
- **Dog** (Guard dog / Wild dog / Jackal keys) → those three. ("Temple Guardian"
  appeared in the wiki list but is dubious as a dog — excluded.)
- **Skeleton**: Vet'ion, Calvar'ion (the wiki also notes "numerous dungeon skeleton
  variants" — not enumerated; base Skeleton card + the two wildy bosses only).
- **Zombie**: Vorkath (counts as zombie). "Any zombie-class except Zogres/Zombie
  monkeys" per wiki — other carded zombie-class NPCs (Armoured zombie, Zombie
  pirate/swab, etc.) also technically count but were not enumerated. UNVERIFIED which
  the owner wants.
- **Cow**: Cow calf, Undead cow. (Buffalo & "Brutus" appeared in the wiki list but
  are dubious — excluded.)
- **Goblin**: Sergeant Strongstack/Grimspike/Steelwill (Graardor's goblin bodyguards,
  category Goblins), Cave goblin.
- **Ghost**: Tortured soul.
- **Bear** (Black bear / Grizzly bear keys) → Black bear, Grizzly bear, Grizzly bear
  cub, **Callisto, Artio** (both category Bears — a Callisto card can finish a bear task).
- **Lizard**: Desert/Grimy/Small/Sulphur lizard. (Sulphur lizard borderline.)
- **Monkey (monster)**: Demonic gorilla (verified "counts as monkeys"). Low relevance;
  base card is bracket-stripped to "monkey" in the provided list.

## Card-GAPS / UNVERIFIED — flag for owner review

- **Acidic Bloodveld** (Karuulm) has a card but was **EXCLUDED**: its wiki page shows
  NO Slayer-category infobox field and the base Bloodveld page lists only Bloodveld +
  Mutated Bloodveld. Community lore often assumes it counts, but the wiki did not
  confirm it — needs an in-game check before adding.
- **Dagannoth spawn / Dagannoth mother**: "spawn" was listed by the Slayer_task page
  but is a fight-add of dubious task value; "mother" (Blood Runs Deep) not listed.
  Both cards exist — excluded pending confirmation. "Dagannoth fledgeling" (Waterbirth,
  a genuine low-level dagannoth task target) IS included.
- **Blue dagannoth** card exists but wasn't listed by the wiki as a distinct
  task-counter (regular dagannoths share the in-game name "Dagannoth"); excluded.
- **Green dragon**: confirmed to have NO carded variant (brutal greens don't count).
- **Skeleton / Zombie** generic lists are intentionally conservative — many category
  members have cards but enumerating every dungeon skeleton/zombie is out of scope;
  owner should decide whether to expand.
- No superior monster was included anywhere (per instructions).

## Method notes
- All facts sourced from OSRS Wiki plain `/w/` pages (edge-cached, ~1 req/sec,
  descriptive project User-Agent via WebFetch). No `api.php?action=parse` calls.
- WebFetch returns a summarised answer, not raw HTML; where the summariser was
  conservative or self-contradictory (e.g. it claimed Alchemical Hydra "doesn't
  count" while quoting text proving it does; missed Obor/Bryophyta on base pages),
  I re-queried the **boss's own page** for the authoritative Slayer-category line.
- Re-run `build_slayer_variants.py` to regenerate + re-validate `slayer_variants.json`.
