# Changelog

Versioning: 0.MINOR.PATCH — staying on 0.2.x until the full skills sweep is
complete, then 0.3.0. The runelite-plugin.properties version line is bumped in
the same commit as each release.

## v0.2.4 — 2026-07-21

### New
- While OSRS TCG's live-update API isn't connected yet, the login message
  now adds: "Not Connected to OSRS TCG - Please relog if you are missing
  unlocks (waiting on OSRS TCG update)." It disappears automatically once
  the API update is live on both plugins.

### Changes
- Simplified the login welcome message to "Plugin is active - Good luck
  on the pulls!" — dropped the card count and the version number.

### Fixes
- The welcome message no longer shows a literal `${version}` placeholder.
  The build-time resource stamp it relied on isn't run by the plugin-hub
  packager, so hub builds always shipped the raw token. Removed the unused
  version-reading code along with it.

## v0.2.3 — 2026-07-21

### New
- **OSRS TCG API integration**: the collection is now read via OSRS TCG's
  PluginMessage API when available — unlocks apply instantly (pushed, not
  polled). The API ships in OSRS TCG's next update and activates
  automatically on both sides; until then the existing decode path is
  used, which OSRS TCG's latest update can leave stale (state may lag or
  read as unavailable — resolved the moment their next update lands).
- **Mining Options / Woodcutting Options** dropdowns (replacing the old
  toggles): Card Required / Ore(Logs) Only / Tool Only / No Card Needed.
  Card Required now also blocks gathering while a locked pickaxe/axe is
  carried — previously a locked tool could still mine and chop.
- **Fletching families added**: crossbows (stocks, stringing), javelins
  and javelin shafts, gem-tipped bolts, ogre/brutal arrows, broad ammo,
  Forestry wooden shields (Oak–Redwood), and Varlamore atlatl darts.

### Fixes
- Fletching's knife-on-logs products (arrow shafts, bows, stocks,
  shields) now block at the make-menu product click with the correct
  in-game names — carving an unstrung bow needs only the logs card;
  stringing needs logs + Bow string + the bow card.
- Item-on-item crafting blocks now list every missing card instead of
  stopping at the first locked material item.
- A corrupted recipe entry that wrongly gated Earth battlestaff crafting
  was removed.
- Deprecated RuneLite API calls replaced (WorldView migration).

### Removed
- The TCG stats overlay (credits / cards collected) — OSRS TCG now has
  its own built-in display, and the latest OSRS TCG update means the
  saved state this overlay read from can lag behind the live values.

## v0.2.2 — 2026-07-19

### New
- **Food Settings** dropdown (under Item Usage): Locked / Pots Only /
  Food Only / Unlocked. An allowed class is treated as owned everywhere,
  like the exempt list. 315 food and 108 potion items classified
  (wiki-derived, owner-curated: wines, beers and teas count as food;
  utility consumables like purple sweets stay locked).

### Fixes
- Shop Buy options are now hidden on items whose card is missing, and all
  Buy/Sell options hide when Coins are locked without their card —
  matching the click blocking added in 0.2.1.
- The locked-item fade and icon now apply inside shop windows (stock and
  shop-side inventory).

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
