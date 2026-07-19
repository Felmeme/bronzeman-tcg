# Changelog

Versioning: 0.MINOR.PATCH — MINOR for new behaviour, PATCH for fixes only.
The build.gradle version is bumped in the same commit as each release.

## v0.2.1 — 2026-07-19

### Fixes
- Coin Settings (Locked) now blocks shop buying and selling — both
  transactions handle Coins, so both require the Coins card (or an
  exemption). Shops only; the Grand Exchange is unchanged for now.

### Interface
- The plugin version now shows on the Plugin Hub and in the login greeting.

## Unversioned updates — 2026-07-16 to 2026-07-19 (shipped as 0.2.0)
- Welcome, plugin-conflict and collection-unreadable chat messages;
  enforcement stands down loudly when the TCG state can't be read.
- Locked-item marking: fade + bank-filler icon in inventory and bank.
- Exempt-list update-safety fix (Coins moved to its own setting).
- Settings overhaul: NPC Locks, Ground Items, Item Usage, Banking, Grand
  Exchange and Coin Settings dropdowns; option hiding made inherent; shops
  always refuse locked items; stricter defaults (no card, no permit).
- Thieving tiers (Coins+Pouch / +NPC card / All) with H.A.M. and Master
  Farmer Insanity toggles; full pickpocket loot data (479 loot groups).
- Quest-state NPC override: NPCs of started quests stay visible and
  talkable in every mode; CotS guard marking waives itself mid-quest; the
  CotS toggle retired.
- One-time settings migration preserving deliberate lenient choices.

## v0.2.0 — 2026-07-16

### New restrictions & options
- **Market stall thieving**: stealing from stalls requires cards from their
  loot table, with an Off / Any of / All items dropdown (26 stall rules).
- **Pickpocketing modes**: the toggle became a dropdown — Coins + Pouch
  (previous behaviour) or NPC + Loot, which also requires the target's own
  card.
- **Slayer "Include superiors"**: Require-monsters can additionally demand
  each master's superior variant cards (130 mapped); panel bars mirror the
  setting.
- **Cooking section**: Restrict cooking moved into its own section with a new
  "Require burnt food" toggle (19 foods have burnt cards; fish that burn into
  the card-less generic "Burnt fish" are unaffected).
- **Crafting "Require crushed gem"**: cutting opal/jade/red topaz can demand
  the Crushed gem card (precious gems never shatter and are unaffected).
- **Allow Last Man Standing** (default on): all restrictions lift inside a
  live LMS match, detected via the client's own in-game flag.
- **TCG stats overlay** (default off): movable plain-text overlay showing
  credits and cards collected out of 6,376, read from the OSRS TCG plugin's
  state with its creator's blessing.

### Fixes
- Chat feedback was invisible on standard clients (messages now route through
  the chat manager as unfiltered CONSOLE messages).
- The item exempt list (default: Coins) now applies to every restriction —
  forced drop, banking, equipping, buying — not just loot pickup, and the
  setting's description says so.
- Added Kuradal and Achtryn, the While Guthix Sleeps replacement slayer
  masters missing from the data; slayer restrictions now cover all 13.
- Cooking via the range-click "Cook" interface flow no longer bypasses the
  restriction (the make-interface now consults cooking rules).
- Children of the Sun correctly lists its guard-marking Guard card in the
  quest panel.
- Nine classic pickpocket targets briefly lost their Coins + Coin pouch
  requirement during data restructuring; restored and verified.

### Interface
- Config panel reorganised into per-skill sections (General, Visuals,
  Resource nodes, Skill Options, Cooking, Crafting, Farming, Firemaking,
  Hunter, Smithing, Slayer, Sailing, Thieving).
- README gains Support links; blocked-action chat lines list missing cards
  with alternatives.

## v0.1.0 — 2026-07-11

Initial Plugin Hub release: combat, loot, equipping, buying, processing-skill
recipes, gathering, hunter, slayer, runecrafting, farming, thieving and
sailing restrictions driven by the OSRS TCG card collection; locked-NPC
outlines and hiding; sidebar panel with card lookup, nearby lock states,
collection progress, and quest & PvM readiness checklists.
