#!/usr/bin/env python3
"""Apply the owner-approved, low-ambiguity Quest Helper outlier groups.

The broader audit intentionally remains report-only. This script applies an
explicit allow-list of concrete tools and materials, merging partial groups
instead of creating duplicate requirements.
"""

import argparse
import json
from pathlib import Path


APPROVED_CATEGORIES = {
    "Hammer alternatives",
    "Light-source groups",
    "Logs/branches groups",
    "Pickaxe groups",
}

APPROVED_OTHER = {
    ("Between a Rock...", "Ammo mould"),
    ("Dragon Slayer II", "Antifire shield"),
    ("Mourning's End Part I", "Bear fur"),
    ("Pirate's Treasure", "Bananas"),
    ("Ratcatchers", "Clean kwuarm"),
    ("The Dig Site", "Opal"),
    ("Throne of Miscellania", "One of: a harpoon or lobster pot."),
    ("Wanted!", "A rope"),
}


def load(path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def approved(row):
    if row["category"] == "Axe groups":
        # The Myreque silver-weapon row was classified by the word "axe" in
        # its label but is not an any-axe tool group.
        return row["label"].casefold() in {"an axe", "any axe"}
    return (
        row["category"] in APPROVED_CATEGORIES
        or (row["quest"], row["label"]) in APPROVED_OTHER
    )


def leaf_requirements(requirements):
    for requirement in requirements:
        children = requirement.get("children") or []
        if children:
            yield from leaf_requirements(children)
        else:
            yield requirement


def item_section(quest):
    for section in quest.get("sections") or []:
        if section.get("label") == "Items":
            return section
    section = {"label": "Items", "requirements": []}
    quest.setdefault("sections", []).insert(0, section)
    return section


def merge_partial(quest, row):
    covered = {card.casefold() for card in row["coveredCards"]}
    matches = [
        requirement
        for section in quest.get("sections") or []
        for requirement in leaf_requirements(section.get("requirements") or [])
        if covered & {card.casefold() for card in requirement.get("cards") or []}
    ]
    if len(matches) != 1:
        raise AssertionError(
            f"{row['quest']} / {row['label']}: expected one partial leaf, found {len(matches)}"
        )
    matches[0]["label"] = row["label"]
    matches[0]["cards"] = row["resolvedCards"]


def add_missing(quest, row):
    section = item_section(quest)
    existing = {
        card.casefold()
        for requirement in leaf_requirements(section.get("requirements") or [])
        for card in requirement.get("cards") or []
    }
    overlap = existing & {card.casefold() for card in row["resolvedCards"]}
    if overlap:
        raise AssertionError(
            f"{row['quest']} / {row['label']}: missing group overlaps existing cards {sorted(overlap)}"
        )
    section["requirements"].append(
        {
            "label": row["label"],
            "cards": row["resolvedCards"],
            "type": "item",
        }
    )


def apply(quest_data, audit):
    quests = {quest["name"]: quest for quest in quest_data["quests"]}
    selected = [row for row in audit["groups"] if approved(row)]
    for row in selected:
        quest = quests[row["quest"]]
        if row["coverage"] == "partial":
            merge_partial(quest, row)
        else:
            add_missing(quest, row)
    return selected


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--quest-cards", type=Path, required=True)
    parser.add_argument("--audit", type=Path, required=True)
    args = parser.parse_args()

    quest_data = load(args.quest_cards)
    selected = apply(quest_data, load(args.audit))
    args.quest_cards.write_text(
        json.dumps(quest_data, indent="\t") + "\n", encoding="utf-8"
    )
    print(f"Applied {len(selected)} approved outlier groups.")


if __name__ == "__main__":
    main()
