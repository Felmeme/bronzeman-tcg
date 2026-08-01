#!/usr/bin/env python3
"""Apply the final concrete groups approved during the quest audit."""

import argparse
import json
from pathlib import Path


EXTRA_GROUPS = {
    ("Regicide", "Bow (not crossbow)"),
    ("Spirits of the Elid", "Arrows for bow"),
}


def load(path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def leaves(requirements):
    for requirement in requirements:
        children = requirement.get("children") or []
        if children:
            yield from leaves(children)
        else:
            yield requirement


def apply_row(quest, row):
    if row["coverage"] == "partial":
        covered = {card.casefold() for card in row["coveredCards"]}
        matches = [
            requirement
            for section in quest["sections"]
            for requirement in leaves(section.get("requirements") or [])
            if covered & {card.casefold() for card in requirement.get("cards") or []}
        ]
        if len(matches) != 1:
            raise AssertionError(f"{row['quest']} / {row['label']}: partial match count {len(matches)}")
        matches[0]["label"] = row["label"]
        matches[0]["cards"] = row["resolvedCards"]
        return

    section = next(section for section in quest["sections"] if section["label"] == "Items")
    section["requirements"].append(
        {"label": row["label"], "cards": row["resolvedCards"], "type": "item"}
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--quest-cards", type=Path, required=True)
    parser.add_argument("--audit", type=Path, required=True)
    args = parser.parse_args()

    quest_data = load(args.quest_cards)
    audit = load(args.audit)
    quests = {quest["name"]: quest for quest in quest_data["quests"]}
    selected = [
        row for row in audit["groups"]
        if row["category"] == "Other concrete groups"
        or (row["quest"], row["label"]) in EXTRA_GROUPS
    ]
    for row in selected:
        apply_row(quests[row["quest"]], row)

    args.quest_cards.write_text(json.dumps(quest_data, indent="\t") + "\n", encoding="utf-8")
    print(f"Applied {len(selected)} final concrete groups.")


if __name__ == "__main__":
    main()
