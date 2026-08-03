# Bronzeman TCG

A card-collection challenge mode for RuneLite, driven by the
[OSRS TCG](https://runelite.net/plugin-hub/show/osrs-tcg) plugin's gacha collection:
**almost everything you do is locked until you pull the matching card.**

Install both plugins from the Plugin Hub; this one reads the TCG collection
and enforces the restrictions. Every restriction is individually toggleable,
so you tune the difficulty to taste.

## What gets restricted
 
- **Combat** — attacking an NPC (and casting spells / using items on it) is
  blocked until you own its card. Cards with location variants ("Soldier
  (Yanille)") all unlock the plain NPC name; owning any variant counts.
- **Loot** — picking up (or telegrabbing) a ground item needs its card. Coins
  have their own default-on exemption, and the separate exempt list supports
  case-insensitive `*` wildcards.
- **Items** — equipping locked items can be blocked; an optional *forced
  drop* mode reduces locked inventory items to Drop/Examine/Destroy only
  (with an "allow banking" variant where deposits work but withdrawals
  stay locked); shop buying and Grand Exchange search selection can be
  blocked; potion drinking can be gated (all doses map to the one card).
- **Processing skills** — firemaking, smelting, smithing, crafting,
  enchanting, fletching and herblore recipes require input and/or output
  cards, with per-skill modes.
- **Gathering** — woodcutting, mining, fishing (spot types list every fish
  they can yield; "any of" or "require ALL" modes), and cooking raw food.
- **Thieving** — pickpocketing needs the loot cards (Coins + Coin pouch),
  or in *NPC + Loot* mode the target's own card too; Master Farmer has his
  own dial, including an *Insanity* mode requiring all 45 seed cards on
  his table.
- **Hunter** — birds, butterflies, implings, chinchompas, salamanders and
  pitfalls need their gear cards (Magic butterfly net counts); an *Extreme
  Hunter* option locks each rumour master until you own every creature card
  they can assign.
- **Slayer** — optionally require each master's own card, and/or every card
  of the monsters they can assign, before using them; an *Include superiors*
  checkbox stacks the superior variant cards on top. (Superiors are always
  separately combat-locked by their own cards regardless.)
- **Runecrafting & Farming** — altars need essence/talisman (tiara counts)
  and optionally the rune card; patches need tools, seeds and optionally the
  produce card at planting time; compost bins need a compost card.
- **Sailing** — hull and keel upgrades need their part cards (modes add the
  material and Large-part cards); salvaging each shipwreck tier needs its
  salvage card.

## Visuals & panel

Locked NPCs get a configurable grey outline or can be hidden entirely. Locked
inventory items can also be marked without changing their chosen visual mode
when Item Usage is temporarily unrestricted.

The sidebar panel provides overall progress, recent unlocks, Collection and
TCG Locked Shared Cards views, plus readiness tabs for Quests, Miniquests,
Slayer, PvM and Rumours. Quest requirements are grouped into Items and Enemies,
support nested alternatives and route-specific requirements, and count accepted
shared cards. Quests, Collection and Shared Cards each have focused search.

## Support

If you would like to support then please firstly support the OSRS TCG Devs through their [Patreon](https://www.patreon.com/Azderi).

If you would like to support the Bronzeman TCG plugin you can donate through [Ko-fi](https://ko-fi.com/felmeme)
