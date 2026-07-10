#!/usr/bin/env python3
"""Regenerate the plugin's card-catalog snapshots from osrs-tcg's Card.json.

Usage: python scripts/generate_tracked_monsters.py <path-to-osrs-tcg-Card.json>

Writes two resources, both shaped {"entityToCards": {lowercased in-game
name -> [card names]}}:
  - src/main/resources/tracked_monster_names.json  (Monster-category cards)
  - src/main/resources/tracked_item_names.json     (Resource-category cards)

Every card is exactly one of Monster or Resource (verified 2026-07-10:
partition is exact, 1,227 + 5,149 = 6,376, no overlap, no leftovers).

Why a map instead of a flat name list: 67 monster cards carry wiki-style
disambiguation suffixes ("Monkey (monster)", "Soldier (tier 3)", ...) that the
in-game NPC name never contains, and several distinct cards collapse to the
same NPC name (11 "Soldier (...)" cards). RuneLite only exposes the plain NPC
name at attack time, so the plugin unlocks an NPC when the player owns ANY of
its variant cards. Resource cards have no such suffixes today, but they share
the map shape so one catalog loader serves both and upstream suffixes would be
handled automatically.

Rules (from the 2026-07-10 full-catalog audit):
  - The two catalogs must stay separate: names like "Ferret" or "Manta ray"
    exist as both a Resource card and a "<name> (Hunter)"/"(monster)" monster
    card, and owning the item card must not unlock attacking the NPC (or vice
    versa).
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


def build_map(cards: list, category: str) -> tuple[dict[str, list[str]], int]:
	name_to_cards: dict[str, list[str]] = {}
	card_count = 0
	for card in cards:
		name = card.get("name", "").strip()
		if not name or category not in card.get("category", []):
			continue

		entity_name = name
		match = SUFFIX_RE.match(name)
		if match:
			if match.group(2).lower().startswith("unused"):
				continue
			entity_name = match.group(1)

		name_to_cards.setdefault(entity_name.lower(), []).append(name)
		card_count += 1
	return {k: sorted(v) for k, v in sorted(name_to_cards.items())}, card_count


def write_snapshot(path: str, mapping: dict, card_count: int) -> None:
	snapshot = {
		"schemaSource": "osrs-tcg Card.json snapshot",
		"schema": 2,
		"entityCount": len(mapping),
		"cardCount": card_count,
		"entityToCards": mapping,
	}
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(snapshot, f, indent="\t", ensure_ascii=False)
		f.write("\n")
	print(f"Wrote {path}: {len(mapping)} entities <- {card_count} cards")


def main() -> int:
	if len(sys.argv) != 2:
		print(__doc__)
		return 2

	with open(sys.argv[1], encoding="utf-8") as f:
		cards = json.load(f)

	monsters, monster_cards = build_map(cards, "Monster")
	items, item_cards = build_map(cards, "Resource")

	write_snapshot("src/main/resources/tracked_monster_names.json", monsters, monster_cards)
	write_snapshot("src/main/resources/tracked_item_names.json", items, item_cards)

	multi = {k: v for k, v in {**monsters, **items}.items() if len(v) > 1}
	print(f"{len(multi)} entities with multiple card variants:")
	for k, v in sorted(multi.items()):
		print(f"  {k}: {v}")
	return 0


if __name__ == "__main__":
	sys.exit(main())
