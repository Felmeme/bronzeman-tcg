"""Give every cooking node explicit input/output/burnt roles.

Cooking used to gate only the COOKED card (role "") plus optionally the BURNT
card (role "burnt"); the raw food was never a requirement. The new CookingMode
dropdown (No restrictions / Input Only / Output Only / Also Need Burnt Food Card)
needs to enforce the RAW card on its own, and the catalog can only skip a group
that has a NON-EMPTY role - a blank/absent role is always enforced. So this
rewrites all 88 cooking nodes to three explicitly-named groups:

    input  = the raw food card      (new)
    output = the cooked food card   (was role "" / requiredCards)
    burnt  = the burnt food card    (was role "burnt"; absent for the 11 fish
                                     that burn into the card-less "Burnt fish")

The raw<->cooked pairing is read from the 30 item-on-object rules (their name is
the raw food, their non-burnt group is the cooked card), then applied to every
node - including the interface twins keyed on the cooked name - by looking each
node's existing cooked card up in that map.

Run: py scripts/rebuild_cooking_roles.py
"""
import json
import re
from collections import defaultdict

PATH = "src/main/resources/resource_nodes.json"


def load():
    txt = open(PATH, encoding="utf-8").read()
    # Gson tolerates a stray trailing comma; strict json does not.
    return json.loads(re.sub(r",(\s*[}\]])", r"\1", txt))


def groups_of(node):
    """Return {role -> [cards]} for a cooking node, normalising both shapes."""
    out = {}
    if "requiredCardGroups" in node:
        roles = node.get("groupRoles", [])
        for i, cards in enumerate(node["requiredCardGroups"]):
            role = roles[i] if i < len(roles) else ""
            out[role or "output"] = cards          # blank role == the cooked card
    elif "requiredCards" in node:
        out["output"] = list(node["requiredCards"])  # cooked-only fish (no burnt)
    return out


def main():
    data = load()
    tracked = json.load(open("src/main/resources/tracked_item_names.json",
                              encoding="utf-8"))["entityToCards"]
    cooking = [n for n in data["nodes"] if n.get("category") == "cooking"]

    # 1. From the item-on-object rules (name = raw food): the raw names, the raws that
    # produce each cooked card (usually one; Cooked meat has three), and each cooked
    # card's burnt card. A node's OWN name is the authoritative raw where it has one.
    raw_names = set()
    cooked_to_raws = defaultdict(list)
    cooked_to_burnt = {}
    for n in cooking:
        if n["kind"] != "item-on-object":
            continue
        g = groups_of(n)
        cooked = g["output"][0]
        raw_names.add(n["name"])
        cooked_to_raws[cooked].append(n["name"])
        if "burnt" in g:
            cooked_to_burnt[cooked] = g["burnt"][0]

    def raws_for(node, cooked):
        # item-on-object and raw-named twins: their own name is the raw.
        if node["name"] in raw_names:
            return [node["name"]]
        # a twin keyed on the cooked name: any raw that yields it (any-of; only Cooked
        # meat is multi, where the interface can't tell beef from rat anyway).
        return list(cooked_to_raws.get(cooked, []))

    # 2. rewrite every cooking node to input/output/burnt.
    unmapped = []
    for n in cooking:
        g = groups_of(n)
        cooked = g["output"][0]
        raws = raws_for(n, cooked)
        if not raws:
            unmapped.append((n["name"], cooked))
            continue
        burnt = g.get("burnt", [cooked_to_burnt[cooked]] if cooked in cooked_to_burnt else None)

        req_groups, roles = [list(raws)], ["input"]
        req_groups.append([cooked]);           roles.append("output")
        if burnt:
            req_groups.append(list(burnt));     roles.append("burnt")

        n.pop("requiredCards", None)
        n["requiredCardGroups"] = req_groups
        n["groupRoles"] = roles

    # 3. validate.
    assert not unmapped, f"cooked cards with no raw mapping: {unmapped}"
    bad = []
    for n in cooking:
        for card in (c for grp in n["requiredCardGroups"] for c in grp):
            if card.lower() not in tracked:
                bad.append((n["name"], card))
        if any(not r for r in n["groupRoles"]):
            bad.append((n["name"], "BLANK ROLE"))
    assert not bad, f"invalid cards/roles: {bad}"

    with open(PATH, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent="\t", ensure_ascii=False)
        f.write("\n")

    with_burnt = sum(1 for n in cooking if "burnt" in n["groupRoles"])
    print(f"rewrote {len(cooking)} cooking nodes -> input/output roles "
          f"({with_burnt} also carry a burnt role, {len(cooking) - with_burnt} have no burnt card)")
    print(f"cooked cards: {len(cooked_to_raws)} | all cards exact-match tracked catalog")


if __name__ == "__main__":
    main()
