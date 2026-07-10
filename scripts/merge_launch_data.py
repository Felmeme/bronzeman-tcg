#!/usr/bin/env python3
"""One-time merge of the launch-hardening agent data into plugin resources.

Inputs (scratchpad): hunter_nodes.json, slayer_nodes.json, rc_farming_nodes.json,
recipe_nodes.json. Outputs: merges nodes into src/main/resources/resource_nodes.json
and copies recipes to src/main/resources/recipe_nodes.json.

Normalizations:
- Hunter categories -> stable keys (hunter-birds, hunter-butterflies, ...).
- Hunter "modeGroups" -> ordinary requiredCardGroups with roles ("extra" = jar-type
  additions, "creature" = the hunted animal), matching the engine's role-exclusion
  model. Trap-lay nodes become kind "inventory" (laying is an inventory item op).
- Bird snare / box trap get an any-of creature group (catch unknowable at lay time;
  owner-approved).
- Slayer: the master node and monsters node for the same NPC collide on the
  (kind,name,option) key, so they merge into ONE node with role "master" on the
  master's own card and role "monsters" on each assignable monster card. The two
  config toggles include/exclude those roles.
- Farming splits into farming-rake / farming-plant / farming-compost by shape.
"""

import json
import sys

SCRATCH = r"C:\Users\ocari\AppData\Local\Temp\claude\D--ClaudeFolder\7ce6b1e2-8f99-4e53-b8e5-25eebd1548d5\scratchpad"
RES = "src/main/resources"
CARD_JSON = r"D:\ClaudeFolder\osrs-tcg-main\osrs-tcg-main\src\main\resources\Card.json"

CARDS = {c["name"] for c in json.load(open(CARD_JSON, encoding="utf-8"))}

HUNTER_CATEGORY = {
	"Hunter - Bird snaring": "hunter-birds",
	"Hunter - Butterflies": "hunter-butterflies",
	"Hunter - Implings": "hunter-implings",
	"Hunter - Chinchompas": "hunter-chins",
	"Hunter - Salamanders": "hunter-salamanders",
	"Hunter - Pitfall": "hunter-pitfalls",
	"Extreme Hunter - Rumours": "hunter-rumours",
}
MODEGROUP_ROLE = {"with jar": "extra", "both": "extra", "items+sally": "creature", "all": "creature"}
SNAREABLE_BIRD_CARDS = ["Crimson swift", "Golden warbler", "Tropical wagtail"]
CHIN_CARDS = ["Chinchompa", "Red chinchompa", "Black chinchompa"]


def to_groups(node):
	"""Return (groups, roles) unified from requiredCards/requiredCardGroups/modeGroups."""
	groups, roles = [], []
	if node.get("requiredCardGroups"):
		src_roles = node.get("groupRoles") or []
		for i, g in enumerate(node["requiredCardGroups"]):
			groups.append(g)
			roles.append(src_roles[i] if i < len(src_roles) else None)
	elif node.get("requiredCards"):
		if node.get("requireAll", True):
			for c in node["requiredCards"]:
				groups.append([c])
				roles.append(None)
		else:
			groups.append(node["requiredCards"])
			roles.append(None)
	for mode, extra in (node.get("modeGroups") or {}).items():
		role = MODEGROUP_ROLE.get(mode.lower(), "extra")
		for g in extra:
			groups.append(g)
			roles.append(role)
	return groups, roles


def emit(nodes_out, category, kind, name, options, groups, roles, notes=""):
	for g in groups:
		for c in g:
			assert c in CARDS, f"not a card: {c!r} ({name})"
	nodes_out.append({
		"category": category, "kind": kind, "name": name, "options": options,
		"requiredCardGroups": groups, "groupRoles": [r or "" for r in roles],
		"requireAll": True, "notes": notes,
	})


def main():
	out = []

	# ---- hunter ----
	hunter = json.load(open(f"{SCRATCH}/hunter_nodes.json", encoding="utf-8"))["nodes"]
	for n in hunter:
		cat = HUNTER_CATEGORY[n["category"]]
		kind = n["kind"]
		groups, roles = to_groups(n)
		if cat in ("hunter-birds", "hunter-chins") and n["options"] == ["lay"]:
			kind = "inventory"  # laying a trap is an inventory item op
			creature = SNAREABLE_BIRD_CARDS if cat == "hunter-birds" else CHIN_CARDS
			groups.append(creature)
			roles.append("creature")
		emit(out, cat, kind, n["name"], n["options"], groups, roles, n.get("notes", ""))

	# ---- slayer: merge master+monsters per NPC ----
	slayer = json.load(open(f"{SCRATCH}/slayer_nodes.json", encoding="utf-8"))["nodes"]
	masters = {}
	for n in slayer:
		entry = masters.setdefault(n["name"], {"options": n["options"], "master": None, "monsters": []})
		if n["category"] == "slayer-master":
			entry["master"] = n["requiredCards"][0]
		else:
			entry["monsters"] = n["requiredCards"]
	for name, e in sorted(masters.items()):
		groups = [[e["master"]]] + [[m] for m in e["monsters"]]
		roles = ["master"] + ["monsters"] * len(e["monsters"])
		emit(out, "slayer", "npc", name, [o.lower() for o in e["options"]], groups, roles,
			f"master card + {len(e['monsters'])} assignable-monster cards; toggles include/exclude roles")

	# ---- runecrafting + farming (split farming by shape) ----
	rcf = json.load(open(f"{SCRATCH}/rc_farming_nodes.json", encoding="utf-8"))["nodes"]
	for n in rcf:
		cat = n["category"]
		if cat == "farming":
			if n["kind"] == "item-on-object":
				cat = "farming-plant"
			elif "rake" in n["options"]:
				cat = "farming-rake"
			else:
				cat = "farming-compost"
		groups, roles = to_groups(n)
		emit(out, cat, n["kind"], n["name"], n["options"], groups, roles, n.get("notes", ""))

	# ---- merge into resource_nodes.json ----
	path = f"{RES}/resource_nodes.json"
	data = json.load(open(path, encoding="utf-8"))
	existing = {(n["kind"], n["name"].lower(), tuple(sorted(o.lower() for o in n["options"])))
		for n in data["nodes"]}
	added = 0
	for n in out:
		key = (n["kind"], n["name"].lower(), tuple(sorted(o.lower() for o in n["options"])))
		if key not in existing:
			data["nodes"].append(n)
			added += 1
	data["nodes"].sort(key=lambda n: (n["category"], n["name"].lower()))
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent="\t", ensure_ascii=False)
		f.write("\n")

	# ---- recipes straight copy (validated upstream; re-verify here) ----
	recipes = json.load(open(f"{SCRATCH}/recipe_nodes.json", encoding="utf-8"))
	for r in recipes["recipes"]:
		for g in r["inputs"]:
			for c in g:
				assert c in CARDS, f"recipe input not a card: {c!r}"
		if r["output"]:
			assert r["output"] in CARDS, f"recipe output not a card: {r['output']!r}"
	with open(f"{RES}/recipe_nodes.json", "w", encoding="utf-8", newline="\n") as f:
		json.dump(recipes, f, indent="\t", ensure_ascii=False)
		f.write("\n")

	print(f"merged {added} nodes into resource_nodes.json (total {len(data['nodes'])}); "
		f"copied {len(recipes['recipes'])} recipes")
	return 0


if __name__ == "__main__":
	sys.exit(main())
