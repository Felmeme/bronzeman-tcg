# Changelog

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
