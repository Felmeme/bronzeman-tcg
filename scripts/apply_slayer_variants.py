"""Expand each slayer master's assignable-monster requirements into any-of variant groups.

"Full Task List" should accept ANY carded variant of a task (owning Mutated Bloodveld
satisfies the Bloodveld task), not force the exact base card. The enforcement already
treats a multi-card group as "own at least one", so this is a pure data reshape: each
single-card "monsters" group [X] becomes [X, <carded variants of X>] where X has any.

Variant map sourced by an Opus wiki-research pass (docs/slayer_variants_report.md;
superiors deliberately excluded - they are a separate opt-in mechanic), validated
name-by-name against the monster card catalog.

Run: py scripts/apply_slayer_variants.py
"""
import json
import re

RN = "src/main/resources/resource_nodes.json"
VARIANTS = "docs/slayer_variants.json"


def load_lenient(path):
    return json.loads(re.sub(r",(\s*[}\]])", r"\1", open(path, encoding="utf-8").read()))


def main():
    data = load_lenient(RN)
    variants = json.load(open(VARIANTS, encoding="utf-8"))
    tracked = {k.lower() for k in
               json.load(open("src/main/resources/tracked_monster_names.json",
                              encoding="utf-8"))["entityToCards"]}

    def norm(s):
        return re.sub(r"\s*\([^)]*\)", "", s).strip().lower()

    # validate the map up front
    for base, names in variants.items():
        for n in names:
            assert n.lower() in tracked or norm(n) in tracked, f"'{n}' not a monster card"
        assert names[0] == base, f"base '{base}' not first in its group"

    expanded = 0
    for node in data["nodes"]:
        if node.get("category") != "slayer":
            continue
        roles = node["groupRoles"]
        groups = node["requiredCardGroups"]
        for i, role in enumerate(roles):
            if role != "monsters":
                continue
            base = groups[i][0]
            if base in variants and len(variants[base]) > 1:
                groups[i] = list(variants[base])   # base + variants, any-of
                expanded += 1

    with open(RN, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent="\t", ensure_ascii=False)
        f.write("\n")
    print(f"expanded {expanded} slayer monster requirements into variant any-of groups")


if __name__ == "__main__":
    main()
