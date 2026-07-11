#!/usr/bin/env python3
"""One-time merge of the Sailing agent data into resources/resource_nodes.json.

Owner rulings applied here:
- Hull material for Parts+Materials mode = the PLANK card (what the game consumes);
  the LOG card moves to role "extra", enforced only in Everything mode (the chain
  start: logs -> sawmill -> planks).
- Keels keep their bar/sheet material with no "extra" - bar-from-ore is already the
  smelting restriction's job, double-gating it here would be redundant.
- Each tier's rule is keyed under THREE interface product names: the install-side
  build name ("Oak hull", agent-guessed) plus the two workbench part products
  ("Oak hull parts", "Large oak hull parts", verified == card names), so at least
  one interception point per tier is guaranteed.
- Harpoon fishing union gains Raw harpoonfish only (Crystallised excluded).
- The agent's two non-gating station-marker nodes are not merged (empty groups).
"""

import json
import sys

SCRATCH = r"C:\Users\ocari\AppData\Local\Temp\claude\D--ClaudeFolder\7ce6b1e2-8f99-4e53-b8e5-25eebd1548d5\scratchpad"
RES_PATH = "src/main/resources/resource_nodes.json"
CARD_JSON = r"D:\ClaudeFolder\osrs-tcg-main\osrs-tcg-main\src\main\resources\Card.json"

CARDS = {c["name"] for c in json.load(open(CARD_JSON, encoding="utf-8"))}


def plank_for(log_card):
	return "Plank" if log_card == "Logs" else log_card.replace(" logs", " plank")


def main():
	sailing = json.load(open(f"{SCRATCH}/sailing_nodes.json", encoding="utf-8"))
	data = json.load(open(RES_PATH, encoding="utf-8"))
	nodes = data["nodes"]
	new_nodes = []

	# ---- salvage nodes: as delivered (skip empty-group markers) ----
	for n in sailing["nodes"]:
		if n["category"] == "sailing-salvage" and n.get("requiredCardGroups"):
			new_nodes.append(n)

	# ---- boat upgrade tiers from interfaceProducts ----
	for install_name, spec in sailing["interfaceProducts"].items():
		groups, roles = [], []
		for group, role in zip(spec["groups"], spec["roles"]):
			if role == "material" and "hull" in install_name.lower():
				log_card = group[0]
				groups.append([plank_for(log_card)])
				roles.append("material")
				groups.append([log_card])
				roles.append("extra")
			else:
				groups.append(group)
				roles.append(role)
		part_name = next(g[0] for g, r in zip(groups, roles) if r == "part")
		large_name = next((g[0] for g, r in zip(groups, roles) if r == "large"), None)
		keys = [install_name, part_name] + ([large_name] if large_name else [])
		for card in (c for g in groups for c in g):
			assert card in CARDS, f"not a card: {card!r}"
		for key in keys:
			new_nodes.append({
				"category": "sailing-upgrades", "kind": "interface", "name": key,
				"options": ["*"], "requiredCardGroups": groups, "groupRoles": roles,
				"requireAll": True,
				"notes": f"{install_name} tier; keyed on install name (guessed) and workbench "
					"part products (verified) so one path always intercepts.",
			})

	# ---- harpoonfish joins the existing Harpoon union (Raw only, owner ruling) ----
	for n in nodes:
		if n["kind"] == "npc" and n["name"].lower() == "fishing spot" \
			and [o.lower() for o in n["options"]] == ["harpoon"]:
			if "Raw harpoonfish" not in n["requiredCards"]:
				n["requiredCards"].append("Raw harpoonfish")
				n["notes"] = n.get("notes", "") + " Raw harpoonfish added (Tempoross Cove deep-sea spots share this identity)."
			break
	else:
		sys.exit("harpoon union node not found")

	existing = {(n["kind"], n["name"].lower(), tuple(sorted(o.lower() for o in n["options"])))
		for n in nodes}
	added = 0
	for n in new_nodes:
		key = (n["kind"], n["name"].lower(), tuple(sorted(o.lower() for o in n["options"])))
		if key not in existing:
			nodes.append(n)
			added += 1
	nodes.sort(key=lambda n: (n["category"], n["name"].lower()))
	with open(RES_PATH, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent="\t", ensure_ascii=False)
		f.write("\n")
	print(f"merged {added} sailing nodes (total {len(nodes)}); harpoon union updated")
	return 0


if __name__ == "__main__":
	sys.exit(main())
