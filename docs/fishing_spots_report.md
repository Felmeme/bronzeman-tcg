# Fishing spot rebuild report

## Result

- Regenerated the fishing category in place as 35 RuneLite FishingSpot action rows (35 rows were present before this run).
- Covered 26 of RuneLite's 27 FishingSpot groups with 38 exact menu-option keys.
- Explicitly unrestricted 1 groups whose inputs and catches have no OSRS TCG cards.
- Validated 81 exact card references against `tracked_item_names.json`.

## Sources and method

- NPC identity is resolved at runtime by RuneLite's maintained `net.runelite.client.game.FishingSpot.findSpot(npc.getId())` mapping.
- Menu actions were dumped from the owner's local RuneLite NPC cache with `scripts/FishingSpotCacheDump.java`; no wiki requests were needed for IDs or option strings.
- Tools, bait and catches were derived from the OSRS Wiki's plain `/w/Fishing_spots` page and matched exactly to the OSRS TCG card snapshot.
- Uncarded requirements are deliberately omitted and therefore never block.

## Mode semantics

- **Tools Only:** applicable carded equipment, bait and consumables actually carried must be unlocked.
- **Tools + Any Fish (default):** Tools Only plus any one carded catch.
- **Tools + Fish:** Tools Only plus every carded catch.
- Fishing options are never hidden; locked selections are blocked on click.

## Explicitly unrestricted RuneLite groups

- **QUEST_RUM_DEAL** — The RuneLite group is mapped, but its quest catches have no matching OSRS TCG cards.

## Card-relevant fishing outside RuneLite FishingSpot

- **The Stranglewood and event-specific fishing NPCs** — Present in the local NPC cache but absent from RuneLite FishingSpot; needs a reviewed explicit NPC-ID supplement.
- **Chambers of Xeric Bubbles** — A game object (cache action Fish), not an NPC represented by RuneLite FishingSpot.
- **The Gauntlet fishing spots** — Game objects (cache action Fish), not NPCs represented by RuneLite FishingSpot.

## Manual test targets

1. Draynor `Small Net` and `Bait` resolve to different SHRIMP rules.
2. Catherby `Cage`, `Harpoon`, and `Big Net` use their own catch groups.
3. Wilderness `Cage` resolves to DARK_CRAB rather than LOBSTER.
4. `Lure` checks carried feathers; `Bait` does not.
5. Tempoross `Harpoon`, aerial `Catch`, Civitas `Cast`, and squid `Harpoon` all log their named RuneLite group and rule.
6. Locked fishing options remain visible but are consumed with the normal missing-card message.
