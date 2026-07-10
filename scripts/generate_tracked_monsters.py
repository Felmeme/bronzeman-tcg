#!/usr/bin/env python3
"""Regenerate src/main/resources/tracked_monster_names.json from osrs-tcg's Card.json.

Usage: python scripts/generate_tracked_monsters.py <path-to-osrs-tcg-Card.json>

Output shape (schema 2):
  {
    "schemaSource": "osrs-tcg Card.json snapshot",
    "schema": 2,
    "npcCount": <distinct in-game NPC names>,
    "cardCount": <monster cards mapped>,
    "npcToCards": { "<npc name, lowercased>": ["<card name>", ...], ... }
  }

Why a map instead of a flat name list: 67 monster cards carry wiki-style
disambiguation suffixes ("Monkey (monster)", "Soldier (tier 3)", ...) that the
in-game NPC name never contains, and several distinct cards collapse to the
same NPC name (11 "Soldier (...)" cards). RuneLite only exposes the plain NPC
name at attack time, so the plugin unlocks an NPC when the player owns ANY of
its variant cards.

Rules (from the 2026-07-10 full-catalog audit):
  - Monster-category cards only. Item cards must never be indexed: names like
    "Ferret" or "Manta ray" exist as both a Resource card and a
    "<name> (Hunter)"/"(monster)" monster card, and owning the item card must
    not unlock attacking the NPC.
  - Cards whose bracket suffix starts with "unused" are skipped entirely
    (non-attackable content; "Golem (unused NPC)" would otherwise pollute the
    real Golem entry).
  - Keys are lowercased; this also folds casing duplicates like
    "Moss Giant (Iorwerth Dungeon)" into plain "Moss giant".
"""

import json
import re
import sys

SUFFIX_RE = re.compile(r"^(.*?) \(([^)]*)\)$")


def main() -> int:
	if len(sys.argv) != 2:
		print(__doc__)
		return 2

	with open(sys.argv[1], encoding="utf-8") as f:
		cards = json.load(f)

	npc_to_cards: dict[str, list[str]] = {}
	card_count = 0
	for card in cards:
		name = card.get("name", "").strip()
		if not name or "Monster" not in card.get("category", []):
			continue

		npc_name = name
		match = SUFFIX_RE.match(name)
		if match:
			if match.group(2).lower().startswith("unused"):
				continue
			npc_name = match.group(1)

		npc_to_cards.setdefault(npc_name.lower(), []).append(name)
		card_count += 1

	snapshot = {
		"schemaSource": "osrs-tcg Card.json snapshot",
		"schema": 2,
		"npcCount": len(npc_to_cards),
		"cardCount": card_count,
		"npcToCards": {k: sorted(v) for k, v in sorted(npc_to_cards.items())},
	}

	out_path = "src/main/resources/tracked_monster_names.json"
	with open(out_path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(snapshot, f, indent="\t", ensure_ascii=False)
		f.write("\n")

	print(f"Wrote {out_path}: {len(npc_to_cards)} NPCs <- {card_count} monster cards")
	multi = {k: v for k, v in npc_to_cards.items() if len(v) > 1}
	print(f"{len(multi)} NPCs with multiple card variants:")
	for k, v in sorted(multi.items()):
		print(f"  {k}: {v}")
	return 0


if __name__ == "__main__":
	sys.exit(main())
