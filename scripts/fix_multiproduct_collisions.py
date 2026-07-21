"""One-off transform: split multi-product crafting/smithing triggers into two layers.

THE BUG (found 2026-07-21, same class as the fletching "Crossbow stock" one):
several triggers are a tool used on a material that can yield MANY products, with
the actual product chosen afterwards in a make interface. Each product had its own
rule sharing one lookup key (kind|name|target), and RecipeCatalog keeps the LAST -
so only one product was reachable, and it was enforced for ALL of them. Two harms:

  * FALSE BLOCK: crafting Leather gloves demanded the *Leather chaps* card.
  * SILENT HOLE: the other 5 leather products were never gated at all.

THE FIX (owner-approved 2026-07-21) - two layers, mirroring the fletching bow
carve/string split:

  1. Tool-on-material click -> ONE rule requiring only the certain inputs
     (needle+thread+leather), output=None. No product card, because the product
     genuinely isn't known yet. Kills the false block; no collision remains.
  2. Product click in the make interface -> an "interface" twin per product,
     keyed on the product name, carrying that product's card.

Smelting is different: `Iron ore -> Furnace` is ambiguous (iron bar vs steel bar)
but interface rules for BOTH bars already exist, so the ambiguous item-on-object
pair is simply removed rather than replaced.

UNVERIFIED: the interface product strings follow the precedent of the existing
crafting interface rules (product name) and of fletching. If one turns out wrong
the twin merely never fires - it cannot false-block, because layer 1 no longer
carries a product card. Confirm via logInterfaceProduct on a Crafting run.

Run: py scripts/fix_multiproduct_collisions.py
"""
import json
from collections import defaultdict

PATH = "src/main/resources/recipe_nodes.json"

# (kind, trigger name, target) groups to split. Everything else is left alone.
GROUPS = [
    ("item-on-item", "Needle", "leather"),
    ("item-on-item", "Needle", "green dragon leather"),
    ("item-on-item", "Needle", "blue dragon leather"),
    ("item-on-item", "Needle", "red dragon leather"),
    ("item-on-item", "Needle", "black dragon leather"),
    ("item-on-item", "Glassblowing pipe", "molten glass"),
]
# Ambiguous and already covered by interface rules -> delete outright.
DROP = [("item-on-object", "Iron ore", "furnace")]


def group_key(recipe):
    t = recipe["trigger"]
    targets = t.get("targets") or [None]
    return (t["kind"], t["name"], (targets[0] or "").lower() or None)


def main():
    with open(PATH, encoding="utf-8") as f:
        data = json.load(f)

    wanted = {(k, n, t.lower()) for k, n, t in GROUPS}
    dropped = {(k, n, t.lower()) for k, n, t in DROP}

    buckets = defaultdict(list)
    kept = []
    for r in data["recipes"]:
        key = group_key(r)
        norm = (key[0], key[1], key[2]) if key[2] else None
        if norm in dropped:
            continue  # ambiguous smelting shortcut; interface rules cover both bars
        if norm in wanted:
            buckets[norm].append(r)
        else:
            kept.append(r)

    added = []
    for key, group in buckets.items():
        kind, name, target = key
        sample = group[0]
        # Layer 1: the certain inputs only. Every rule in a group shares its
        # inputs (the materials), so the first is representative.
        added.append({
            "category": sample["category"],
            "inputs": sample["inputs"],
            "output": None,
            "trigger": {"kind": kind, "name": name, "targets": [target]},
            "notes": f"{name} on {target} opens a make menu with "
                     f"{len(group)} possible products, so only the materials are "
                     "certain at this click - the product's own card is enforced "
                     "by the interface twins below. Previously each product had a "
                     "rule sharing this key, so only the last was reachable and it "
                     "was demanded for all of them (false block).",
        })
        # Layer 2: one interface twin per product.
        for r in group:
            added.append({
                "category": r["category"],
                "inputs": r["inputs"],
                "output": r["output"],
                "trigger": {"kind": "interface", "name": r["output"], "targets": []},
                "notes": f"Make-menu product click for {r['output']} ({name} on "
                         f"{target}). UNVERIFIED menu string - follows the product-name "
                         "precedent of the other crafting interface rules; if wrong it "
                         "simply never fires (layer 1 carries no product card, so this "
                         "cannot cause a false block).",
            })

    data["recipes"] = kept + added

    with open(PATH, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent="\t", ensure_ascii=False)
        f.write("\n")

    collapsed = sum(len(v) for v in buckets.values())
    print(f"Collapsed {collapsed} colliding rules into {len(buckets)} material rules")
    print(f"Added {len(added) - len(buckets)} interface twins")
    print(f"Dropped ambiguous groups: {sorted(dropped)}")
    print(f"Total recipes: {len(data['recipes'])}")


if __name__ == "__main__":
    main()
