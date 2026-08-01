#!/usr/bin/env python3
"""Apply the owner-approved card-backed Quest Helper review batch."""

import argparse
import json
from pathlib import Path


CAT_CARDS = ["Cat", "Kitten", "Overgrown cat", "Lazy cat", "Wily cat", "Hellcat"]
BRUTAL_CARDS = [
    "Bronze brutal", "Iron brutal", "Steel brutal", "Black brutal",
    "Mithril brutal", "Adamant brutal", "Rune brutal",
]


def load(path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def item_section(quest):
    return next(section for section in quest["sections"] if section["label"] in {"Items", "Shared requirements"})


def top_level(quest):
    return [requirement for section in quest["sections"] for requirement in section.get("requirements") or []]


def find(quest, label):
    matches = [requirement for requirement in top_level(quest) if requirement.get("label") == label]
    if len(matches) != 1:
        raise AssertionError(f"{quest['name']} / {label}: found {len(matches)} top-level requirements")
    return matches[0]


def add(quest, requirement):
    label = requirement["label"]
    if any(existing.get("label") == label for existing in top_level(quest)):
        raise AssertionError(f"{quest['name']} already contains {label}")
    item_section(quest)["requirements"].append(requirement)


def leaf(label, cards):
    return {"label": label, "cards": cards, "type": "item"}


def all_group(label, cards):
    return {
        "label": label,
        "logic": "ALL",
        "children": [leaf(card, [card]) for card in cards],
        "displayCardsOnly": True,
    }


def spell_route(label, element):
    spell_element = label.split()[0]
    return {
        "label": label,
        "logic": "ANY",
        "children": [
            all_group(f"{spell_element} Bolt", [element, "Chaos rune"]),
            all_group(f"{spell_element} Blast", [element, "Death rune"]),
            all_group(f"{spell_element} Wave", [element, "Blood rune"]),
            all_group(f"{spell_element} Surge", [element, "Wrath rune"]),
        ],
    }


def replace_card_requirement(quest, old_label, new_label, cards):
    requirement = find(quest, old_label)
    requirement.clear()
    requirement.update(leaf(new_label, cards))


def apply(quest_data):
    quests = {quest["name"]: quest for quest in quest_data["quests"]}

    akd = quests["A Kingdom Divided"]
    find(akd, "Defence potion")["label"] = "Defence potion (3) or (4)"
    add(akd, spell_route("Fire Bolt or better", "Fire rune"))

    find(quests["A Tail of Two Cats"], "Cat / Kitten").update(label="Any cat", cards=CAT_CARDS)
    add(quests["Bone Voyage"], leaf("Marrentill potion (unf)", ["Marrentill potion"]))
    add(quests["Desert Treasure I"], leaf("Garlic powder", ["Garlic"]))
    add(quests["Eadgar's Ruse"], leaf("Ranarr potion (unf)", ["Ranarr potion"]))

    enakhra = quests["Enakhra's Lament"]
    add(enakhra, all_group("Crumble Undead Runes", ["Air rune", "Earth rune", "Chaos rune"]))
    add(enakhra, spell_route("Fire Bolt or stronger", "Fire rune"))
    add(enakhra, spell_route("Wind Bolt or stronger", "Air rune"))

    family = quests["Family Crest"]
    add(family, leaf("Antipoison or Superantipoison", [
        "Anti-venom", "Anti-venom+", "Antidote+", "Antidote++",
        "Antipoison", "Antipoison potion", "Superantipoison",
        "Relicym's balm", "Sanfew serum",
    ]))
    add(family, all_group("Four Blast Spell Runes", [
        "Air rune", "Water rune", "Earth rune", "Fire rune", "Death rune",
    ]))

    replace_card_requirement(quests["Grim Tales"], "Tarromin", "Tarromin potion (unf)", ["Tarromin potion"])
    add(quests["Heroes' Quest"], leaf("Harralander potion (unf)", ["Harralander potion"]))
    add(quests["Heroes' Quest"], leaf("Smiths gloves", ["Smiths gloves"]))

    horror = quests["Horror from the Deep"]
    horror_items = item_section(horror)["requirements"]
    elemental_labels = {"Fire rune", "Air rune", "Water rune", "Earth rune"}
    removed = [requirement for requirement in horror_items if requirement.get("label") in elemental_labels]
    if len(removed) != 4:
        raise AssertionError(f"Horror from the Deep: expected four elemental rune leaves, found {len(removed)}")
    horror_items[:] = [requirement for requirement in horror_items if requirement.get("label") not in elemental_labels]
    add(horror, all_group("20+ casts of each elemental spell", [
        "Air rune", "Water rune", "Earth rune", "Fire rune", "Mind rune",
    ]))

    find(quests["Icthlarin's Little Helper"], "Cat / Overgrown cat / Hellcat / Kitten").update(
        label="Any cat or kitten", cards=CAT_CARDS
    )
    add(quests["Land of the Goblins"], leaf("Toadflax potion (unf)", ["Toadflax potion"]))
    replace_card_requirement(quests["Shades of Mort'ton"], "Tarromin", "Tarromin potion (unf)", ["Tarromin potion"])
    add(quests["Tears of Guthix"], all_group("Sapphire lantern materials", ["Bullseye lantern", "Sapphire"]))
    add(quests["The Fremennik Exiles"], leaf("Smiths gloves", ["Smiths gloves"]))
    add(quests["The Great Brain Robbery"], leaf("Holy symbol material", ["Unblessed symbol"]))
    add(quests["While Guthix Sleeps"], leaf("Fire runes", ["Fire rune"]))

    zogre = quests["Zogre Flesh Eaters"]
    add(zogre, {
        "label": "Brutal arrows or Crumble Undead",
        "logic": "ANY",
        "children": [
            leaf("Any brutal arrows", BRUTAL_CARDS),
            all_group("Crumble Undead Runes", ["Air rune", "Earth rune", "Chaos rune"]),
        ],
    })


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--quest-cards", type=Path, required=True)
    args = parser.parse_args()
    quest_data = load(args.quest_cards)
    apply(quest_data)
    args.quest_cards.write_text(json.dumps(quest_data, indent="\t") + "\n", encoding="utf-8")
    print("Applied approved Quest Helper card-review batch.")


if __name__ == "__main__":
    main()
