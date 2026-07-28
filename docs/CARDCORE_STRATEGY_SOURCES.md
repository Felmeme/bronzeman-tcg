# Cardcore strategy sources

The optional planner separates rules from strategy. Rules remain player-selected; strategy
is advisory and may combine creator evidence with community-tested routes.

## Additional series discovered

- SoupRS — *My Runescape Account is Locked behind Cards (#1)*
- Alfie — *TCG HCIM - NEW Trading Cards Controls My Destiny OSRS #1*
- wzrd — *Meet My Cardcore Account - TCG Hardcore Ironman #1*
- JordanMG osrs — *Runescape, but I have to Open Packs to Progress | Pack Locked (#1)*
- HerbalLeaf — *This F2P TCG account should have been bricked...*
- TCG Chaos — series linked from Shadow Lock

YouTube rate-limited caption retrieval during this research pass. These titles are recorded
for future transcript analysis but are **not** treated as evidence for implemented route
rules yet.

## Community strategy evidence used

- [First-hour starter route](https://www.osrstcgexchange.com/blog/getting-started):
  Draynor Agility to 20, Varrock dummies to 8 Attack, free XP quests, then Thieving/combat.
- [Skilling starter guide](https://www.osrstcgexchange.com/blog/skilling-starter-guide):
  Thieving ladder, Wintertodt readiness, and no-tool training workarounds.
- [Combat starter guide](https://www.osrstcgexchange.com/blog/combat-starter-guide):
  prioritize the highest safe carded combat-level target because kills, not combat XP,
  produce steady combat credits.
- [Credit mechanics](https://www.osrstcgexchange.com/blog/how-credits-work):
  early level bonuses favor broad low-level progression; fast non-combat XP is reliable
  pack income.
- [CardMan no-card methods](https://www.osrstcgexchange.com/blog/training-the-cardman-way):
  specimen trays, Digsite panning, cactus cutting, permanent-fire Firemaking, H.A.M.
  thieving, Blast Furnace pumping and card-specific combat pivots.
- [Varrock Museum Natural History Quiz](https://oldschool.runescape.wiki/w/Kudos):
  no requirements; 1,000 Hunter and 1,000 Slayer XP, taking both skills from 1 to 9.
- High-value early quest rewards used for pack/goal scoring:
  [Waterfall Quest](https://oldschool.runescape.wiki/w/Waterfall_Quest),
  [The Knight's Sword](https://oldschool.runescape.wiki/w/The_Knight%27s_Sword),
  [early melee quest rewards](https://oldschool.runescape.wiki/w/Pay-to-play_melee_training), and
  [early Fishing quest rewards](https://oldschool.runescape.wiki/w/Ironman_Guide/Fishing).

Community rates are treated as approximate. The planner uses route ordering and readiness
conditions, not claimed exact XP or credit rates.

## Pack-first optimizer order

1. Spend any complete 2,500-credit packs immediately.
2. Take one-time, requirement-free level bursts (Varrock Museum first).
3. Finish the Draynor Agility bootstrap at 20; do not default to 30.
4. Take free/quest XP that also advances Fire Cape or Barrows Gloves.
5. Prefer a card-ready high-XP quest over a low-value conventional quest.
6. Select the strongest legal repeatable engine: Thieving, fast non-combat XP,
   Wintertodt, or the highest safe carded combat-level target.
7. Use no-tool bridge methods only when they activate an already-owned higher-tier card.

## Card-gate classification used by the planner

The planner does not treat "no skill requirement" and "no card requirement" as the
same thing:

- **Cardless actions:** Draynor rooftops, Varrock dummies, the Natural History quiz,
  Digsite specimen trays, H.A.M. pickpocketing, cake/fruit stalls, and the Blast Furnace
  pump itself. Locked outputs must still be banked, dropped, or destroyed rather than used.
- **One-card bridges:** Digsite panning needs Cup of tea; cactus cutting needs an owned
  Knife or slash-weapon card; Civitas big-net fishing needs Big fishing net.
- **Multi-card methods:** Motherlode Mine repairs need a Hammer plus a pickaxe;
  Wintertodt needs an axe plus legally usable 4+ healing food; combat needs the target's
  monster card and every item actually used.
- **No-level quests are still card-gated:** for example, X Marks the Spot has no skill
  requirement but needs a Spade, so it is only recommended when the quest catalogue says
  every required card group is satisfied.

Further free-XP findings:

- [X Marks the Spot](https://oldschool.runescape.wiki/w/X_Marks_the_Spot) gives a
  selectable 300-XP lamp with no target-skill level requirement, but needs a Spade.
- [Varrock Museum cleaning tools](https://oldschool.runescape.wiki/w/Tools_%28Varrock_Museum%29)
  are supplied in the museum, but the cleaning area requires The Dig Site; cleaning can
  produce 500-XP lamps for skills at level 10+.
- Museum kudos thresholds later offer 1,000 Mining XP at 51 kudos and 2,500 Mining plus
  2,500 Crafting XP at 101 kudos. These are not shown as immediately ready because the
  current planner cannot yet observe kudos directly.
