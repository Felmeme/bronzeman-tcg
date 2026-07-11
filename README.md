# Bronzeman TCG

A card-collection challenge mode for RuneLite, driven by the
[OSRS TCG](https://github.com/Azderi/osrs-tcg) plugin's gacha collection:
**almost everything you do is locked until you pull the matching card.**

Install both plugins from the Plugin Hub; this one reads the TCG collection
and enforces the restrictions. Every restriction is individually toggleable,
so you tune the difficulty to taste.

## What gets restricted

- **Combat** — attacking an NPC (and casting spells / using items on it) is
  blocked until you own its card. Cards with location variants ("Soldier
  (Yanille)") all unlock the plain NPC name; owning any variant counts.
- **Loot** — picking up (or telegrabbing) a ground item needs its card, with
  a configurable exempt list (default: Coins).
- **Items** — equipping locked items can be blocked; an optional *forced
  drop* mode reduces locked inventory items to Drop/Examine/Destroy only
  (with an "allow banking" variant where deposits work but withdrawals
  stay locked); shop buying and Grand Exchange search selection can be
  blocked; potion drinking can be gated (all doses map to the one card).
- **Processing skills** — firemaking, smelting, smithing, crafting,
  enchanting, fletching and herblore recipes require input and/or output
  cards, with per-skill modes.
- **Gathering** — woodcutting, mining, fishing (spot types list every fish
  they can yield; "any of" or "require ALL" modes), cooking raw food, and
  pickpocketing (Master Farmer has his own dial, including an *Insanity*
  mode requiring all 45 seed cards on his table).
- **Hunter** — birds, butterflies, implings, chinchompas, salamanders and
  pitfalls need their gear cards (Magic butterfly net counts); an *Extreme
  Hunter* option locks each rumour master until you own every creature card
  they can assign.
- **Slayer** — optionally require each master's own card, and/or every card
  of the monsters they can assign, before using them.
- **Runecrafting & Farming** — altars need essence/talisman (tiara counts)
  and optionally the rune card; patches need tools, seeds and optionally the
  produce card at planting time; compost bins need a compost card.
- **Sailing** — hull and keel upgrades need their part cards (modes add the
  material and Large-part cards); salvaging each shipwreck tier needs its
  salvage card.

## Visuals

Locked NPCs get a configurable grey outline (colour/width/feather), or can be
hidden entirely. A sidebar panel (upside-down bronze med helm icon) offers
card lookup with lock state, the nearest tracked NPCs around you, and
collection progress meters per slayer/rumour master.

## Design principles

- **No card = no restriction.** Anything absent from the TCG catalog can
  never be unlocked, so it is never restricted.
- **Fails safe and loud.** If the TCG collection can't be read (format drift,
  plugin missing), everything tracked locks and a debug log explains why —
  breakage should be obvious in a challenge mode, not silent.
- **Menu-click enforcement only.** Restrictions consume menu clicks, the
  standard restriction-plugin pattern. Keyboard-driven interface defaults
  (spacebar "make") bypass the menu pipeline and are honor-system.

## Interop details

The OSRS TCG plugin persists its collection RSProfile-scoped under config
group `osrstcg`, key `state` (`RLTCG_v2:` + base64(xor(gzip(json)))). This
plugin decodes that read-only with a 5-second cache — no compile-time
dependency, so both plugins install independently. Card catalogs are bundled
snapshots generated from osrs-tcg's `Card.json` by `scripts/` (regenerate
when upstream updates; see script docstrings).

## Dev

```
./gradlew build          # requires network for RuneLite deps
./gradlew run            # RuneLite developer mode with the plugin loaded
```
