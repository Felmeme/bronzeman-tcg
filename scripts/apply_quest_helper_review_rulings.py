#!/usr/bin/env python3
"""Apply the owner's second Quest Helper outlier-review rulings."""

import argparse
import json
from pathlib import Path


REPORT_GROUPS = {
    ("Beneath Cursed Sands", "Any cooked or raw meat"),
    ("The Final Dawn", "Any type of bone or raw meat"),
    ("Throne of Miscellania", "Cake (if courting Brand)"),
    ("Horror from the Deep", "Any sword you're willing to lose"),
    ("The Fremennik Exiles", "Rune thrownaxe, or a friend to help enter Waterbirth Isle Dungeon"),
    ("Mountain Daughter", "A staff or a pole"),
    ("Throne of Miscellania", "Any non-silver ring you are willing to lose"),
    ("A Porcine of Interest", "A knife or slash weapon"),
    ("Horror from the Deep", "Any arrow"),
    ("Regicide", "Arrows (metal, unpoisoned)"),
    ("Spirits of the Elid", "Any bow"),
    ("The Curse of Arrav", "Any crossbow"),
    ("The Path of Glouphrie", "Any crossbow"),
    ("Throne of Miscellania", "Any normal/oak/willow/maple/yew shortbow or longbow (if courting Astrid)"),
    ("Underground Pass", "Arrows (metal, unpoisoned)"),
    ("Underground Pass", "Bow (not crossbow)"),
    ("In Aid of the Myreque", "Silver weapon (including Silverlight + varieties), blessed axe or Efaritay's Aid to damage vampyres"),
}

TEMPLE_GROUPS = {
    ("Temple of Ikov", "Throwable Weapon"),
    ("Temple of Ikov", "Yew, magic, or dark bow"),
}

EXCLUDED_CLOTHING = {
    "Ferocious gloves", "Granite gloves", "Antisanta gloves", "Cow gloves",
    "Santa gloves", "Ancient ceremonial gloves", "Bloodbark gauntlets",
    "Dragonstone gauntlets", "Holy wraps", "Ornate gloves", "Ranger gloves",
    "Samurai gloves", "Splitbark gauntlets", "Swampbark gauntlets",
    "Zaryte vambraces", "Black partyhat", "Antisanta boots",
    "Antisanta jacket", "Antisanta mask",
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


def item_section(quest):
    for section in quest["sections"]:
        if section["label"] == "Items":
            return section
    section = {"label": "Items", "requirements": []}
    quest["sections"].insert(0, section)
    return section


def find_leaf(quest, label):
    matches = [
        requirement
        for section in quest["sections"]
        for requirement in leaves(section.get("requirements") or [])
        if requirement.get("label") == label
    ]
    if len(matches) != 1:
        raise AssertionError(f"{quest['name']} / {label}: found {len(matches)} leaves")
    return matches[0]


def all_requirements(requirements):
    for requirement in requirements:
        yield requirement
        yield from all_requirements(requirement.get("children") or [])


def find_requirement(quest, label):
    matches = [
        requirement
        for section in quest["sections"]
        for requirement in all_requirements(section.get("requirements") or [])
        if requirement.get("label") == label
    ]
    if len(matches) != 1:
        raise AssertionError(f"{quest['name']} / {label}: found {len(matches)} requirements")
    return matches[0]


def add_leaf(quest, label, cards, quantity=None):
    requirement = {"label": label, "cards": cards, "type": "item"}
    if quantity and quantity > 1:
        requirement["quantity"] = quantity
    item_section(quest)["requirements"].append(requirement)


def apply_report_row(quest, row, cards=None):
    cards = cards or row["resolvedCards"]
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
        matches[0]["cards"] = cards
    else:
        add_leaf(quest, row["label"], cards)


def add_temple_route(quest, rows):
    by_label = {row["label"]: row for row in rows}
    item_section(quest)["requirements"].append(
        {
            "label": "Yew/magic bow or throwable weapon",
            "logic": "ANY",
            "children": [
                {
                    "label": "Yew, magic, or dark bow",
                    "cards": by_label["Yew, magic, or dark bow"]["resolvedCards"],
                    "type": "item",
                },
                {
                    "label": "Optional throwable weapon",
                    "cards": by_label["Throwable Weapon"]["resolvedCards"],
                    "type": "item",
                },
            ],
        }
    )


def add_constant_backed_requirements(quests):
    # Existing leaves that were present but lacked quantities or complete
    # alternatives are updated in place.
    find_leaf(quests["Wanted!"], "Any essence").update(quantity=20)
    find_requirement(quests["Wanted!"], "A law rune, an enchanted gem and some molten glass")["children"].insert(
        0, {"label": "A law rune", "cards": ["Law rune"], "type": "item"}
    )
    find_leaf(quests["Dragon Slayer I"], "Plank").update(quantity=3)
    find_leaf(quests["Dragon Slayer II"], "Oak plank").update(quantity=8)
    find_leaf(quests["Dragon Slayer II"], "Machete").update(
        label="Any machete",
        cards=["Red topaz machete", "Jade machete", "Opal machete", "Machete"],
    )

    add_leaf(quests["Watchtower"], "Guam potion (unf)", ["Guam potion"])
    add_leaf(quests["Waterfall Quest"], "Air runes", ["Air rune"])
    add_leaf(quests["Waterfall Quest"], "Earth runes", ["Earth rune"])
    add_leaf(quests["Waterfall Quest"], "Water runes", ["Water rune"])
    add_leaf(quests["What Lies Below"], "Chaos runes", ["Chaos rune"])
    add_leaf(
        quests["Dragon Slayer II"], "Any nails",
        ["Steel nails", "Iron nails", "Bronze nails", "Black nails", "Mithril nails", "Adamantite nails", "Rune nails"],
        12,
    )

    myreque = quests["In Aid of the Myreque"]
    find_leaf(myreque, "Bucket").update(label="Buckets", quantity=5)
    add_leaf(
        myreque, "Any nails",
        ["Steel nails", "Iron nails", "Bronze nails", "Black nails", "Mithril nails", "Adamantite nails", "Rune nails"],
        44,
    )
    raw_mackerel = find_leaf(myreque, "Raw mackerel")
    item_section(myreque)["requirements"].remove(raw_mackerel)
    item_section(myreque)["requirements"].append(
        {
            "label": "Raw mackerel or Raw snail",
            "logic": "ANY",
            "children": [
                {"label": "Raw mackerel", "cards": ["Raw mackerel"], "type": "item", "quantity": 10},
                {"label": "Raw snail", "cards": ["Thin snail", "Lean snail", "Fat snail"], "type": "item"},
            ],
        }
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--quest-cards", type=Path, required=True)
    parser.add_argument("--audit", type=Path, required=True)
    args = parser.parse_args()

    quest_data = load(args.quest_cards)
    audit = load(args.audit)
    quests = {quest["name"]: quest for quest in quest_data["quests"]}
    rows = {(row["quest"], row["label"]): row for row in audit["groups"]}

    selected = [row for row in audit["groups"] if row["category"] == "Altar-access groups"]
    selected.extend(rows[key] for key in REPORT_GROUPS)
    for row in selected:
        apply_report_row(quests[row["quest"]], row)

    for row in audit["groups"]:
        if row["category"] == "Clothing groups":
            cards = [card for card in row["resolvedCards"] if card not in EXCLUDED_CLOTHING]
            apply_report_row(quests[row["quest"]], row, cards)

    add_temple_route(quests["Temple of Ikov"], [rows[key] for key in TEMPLE_GROUPS])
    add_constant_backed_requirements(quests)

    args.quest_cards.write_text(json.dumps(quest_data, indent="\t") + "\n", encoding="utf-8")
    print(f"Applied {len(selected)} report groups, 4 trimmed clothing groups, Temple route, and constant-backed items.")


if __name__ == "__main__":
    main()
