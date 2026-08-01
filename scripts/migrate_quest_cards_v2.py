#!/usr/bin/env python3
"""Build quest_cards.json schema 2 from the legacy snapshot and Quest Helper audit.

The migration is deliberately assertion-heavy: every existing quest and card
relationship must survive. Quest Helper additions are limited to the candidates
approved in docs/quest_helper_requirements_audit.json, except for the explicitly
split Recipe for Disaster and Heroes' Quest sections.
"""

import argparse
import json
import re
from copy import deepcopy
from pathlib import Path

from audit_quest_helper_requirements import name_variants, resolve_card_name


RFD_SECTIONS = [
    ("RFD - Start", "Another Cook's Quest"),
    ("RFD - Dwarf", "Freeing the Mountain Dwarf"),
    ("RFD - Wartface & Bentnoze", "Freeing the Goblin generals"),
    ("RFD - Pirate Pete", "Freeing Pirate Pete"),
    ("RFD - Lumbridge Guide", "Freeing the Lumbridge Guide"),
    ("RFD - Evil Dave", "Freeing Evil Dave"),
    ("RFD - Monkey Ambassador", "Freeing King Awowogei"),
    ("RFD - Sir Amik Varze", "Freeing Sir Amik Varze"),
    ("RFD - Skrach Uglogwee", "Freeing Skrach Uglogwee"),
    ("RFD - Finale", "Defeating the Culinaromancer"),
]

RFD_MANUAL_ITEM_SECTIONS = {
    "Premade fr' blast": "RFD - Start",
    "Fishbowl": "RFD - Pirate Pete",
    "Cat": "RFD - Evil Dave",
    "Overgrown cat": "RFD - Evil Dave",
    "Machete": "RFD - Monkey Ambassador",
}

CONSTANT_ALIASES = {
    "MYSTIC_AIR_STAFF": "Mystic air staff",
    "MYSTIC_SMOKE_BATTLESTAFF": "Mystic smoke staff",
    "MYSTIC_DUST_BATTLESTAFF": "Mystic dust staff",
    "MYSTIC_MIST_BATTLESTAFF": "Mystic mist staff",
    "POH_TABLET_ENCHANTEMERALD": "enchant emerald or jade",
    "POH_TABLET_ENCHANTRUBY": "enchant ruby or topaz",
}

# Quest Helper labels are written as inventory instructions. The side panel is
# a card-ownership checklist, so keep its wording compact while preserving the
# original quantities and logic in the generated nodes.
DISPLAY_LABEL_OVERRIDES = {
    "Runes or tablet for Enchant Emerald": "Enchant Emerald Runes/Tablet",
    "Runes for shadow, smoke, blood, and ice burst": "Burst Spell Runes",
    "3 casts of Fire Wave or Fire Surge": "Fire Wave/Surge Runes",
    "3 casts of Fire Wave": "Fire Wave",
    "3 casts of Fire Surge": "Fire Surge",
    "Runes for telekinetic grab or a lockpick": "Telegrab Runes/Lockpick",
    "Runes for any charge orb spell you have the level to cast": "Charge Orb Runes",
    "Runes or tablet for Enchant Ruby": "Enchant Ruby Runes/Tablet",
    "3 air runes": "Air runes",
    "2 Jerboa tails, or a box trap to get some": "Jerboa tails or Box trap",
    "Climbing boots or 12 coins": "Climbing boots or Coins",
    "3 colours of dyes. Which you'll need is random. To be prepared, bring 3 red/blue/yellow dyes": "Dyes",
    "1 x Linen or 30 coins to buy some": "Linen or Coins",
    "Monkey talisman or 1000 coins": "Monkey talisman or Coins",
    "1000 coins": "Coins",
    "Vyrewatch outfit or 1950 coins": "Vyrewatch outfit or Coins",
    "2x kegs of beer or 650 coins": "Kegs of beer or Coins",
    "10 Wooden cats, or 10 planks and 10 furs to make them": "Wooden cats or Materials",
    "A beer, or 2 coins to buy one": "Beer or Coins",
    "A law rune, an enchanted gem and some molten glass OR 10k gp": "Materials or Coins",
    "10k gp": "Coins",
}


def display_label(requirement, fallback):
    label = requirement.get("name") or fallback
    return DISPLAY_LABEL_OVERRIDES.get(label, label)


def load(path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def folded_catalog(snapshot):
    return {
        card.casefold(): card
        for cards in snapshot["entityToCards"].values()
        for card in cards
    }


def constant_variants(constant):
    result = []
    if constant in CONSTANT_ALIASES:
        result.append(CONSTANT_ALIASES[constant])
    result.append(constant.replace("_", " ").lower())
    if constant.startswith("MYSTIC_") and constant.endswith("_BATTLESTAFF"):
        element = constant[len("MYSTIC_") : -len("_BATTLESTAFF")]
        result.append("mystic " + element.replace("_", " ").lower() + " staff")
    return result


def resolve_leaf_cards(requirement, resource_cards):
    cards = []
    label_card = resolve_card_name(requirement.get("name"), resource_cards)
    if label_card:
        cards.append(label_card)
    for constant in requirement.get("constantNames") or []:
        for candidate in constant_variants(constant):
            card = resource_cards.get(candidate.casefold())
            if card and card not in cards:
                cards.append(card)
    return cards


def convert_qh_requirement(requirement, resource_cards):
    children = [
        converted
        for child in requirement.get("children") or []
        if (converted := convert_qh_requirement(child, resource_cards)) is not None
    ]
    if children:
        result = {
            "label": display_label(requirement, "Requirement"),
            "logic": "ANY" if requirement.get("logic") in {"OR", "XOR"} else "ALL",
            "children": children,
        }
        if result["label"] == "Fire Wave/Surge Runes":
            result["displayCardsOnly"] = True
        return result

    cards = resolve_leaf_cards(requirement, resource_cards)
    if not cards:
        return None
    result = {
        "label": display_label(requirement, cards[0]),
        "cards": cards,
        "type": "item",
    }
    quantity = requirement.get("quantity")
    if isinstance(quantity, int) and quantity > 1:
        result["quantity"] = quantity
    return result


def requirement_cards(requirement):
    result = list(requirement.get("cards") or [])
    for child in requirement.get("children") or []:
        result.extend(requirement_cards(child))
    return result


def section_cards(section):
    return {
        card
        for requirement in section.get("requirements") or []
        for card in requirement_cards(requirement)
    }


def existing_item_requirements(quest, resource_cards, monster_cards):
    requirements = []
    labels = quest.get("groupLabels") or []
    for index, cards in enumerate(quest.get("cardGroups") or []):
        if not cards:
            continue
        label = labels[index] if index < len(labels) and labels[index] else cards[0]
        folded = {card.casefold() for card in cards}
        if folded <= set(resource_cards):
            requirement_type = "item"
        elif folded <= set(monster_cards):
            requirement_type = "card"
        else:
            requirement_type = "card"
        requirements.append({"label": label, "cards": list(cards), "type": requirement_type})
    return requirements


def existing_enemy_requirements(quest, monster_cards=None):
    return [
        {"label": card + " (enemy)", "cards": [card], "type": "enemy"}
        for card in quest.get("monsterCards") or []
        if monster_cards is None or card.casefold() in monster_cards
    ]


def section(label, requirements):
    return {"label": label, "requirements": requirements}


def remove_fully_nested_groups(requirements, nested_cards):
    result = []
    for requirement in requirements:
        cards = set(requirement_cards(requirement))
        if cards and cards <= nested_cards:
            continue
        result.append(requirement)
    return result


def approved_candidates(audit):
    approved = {}
    for row in audit["comparisons"]:
        cards = set(row.get("questHelperOnlyMandatory") or [])
        cards.update(row.get("questHelperOnlyAlternatives") or [])
        if cards:
            approved[row["quest"]] = cards
    return approved


def root_contains_candidate(root, candidates, resource_cards):
    for leaf in walk_qh_leaves(root):
        if set(resolve_leaf_cards(leaf, resource_cards)) & candidates:
            return True
    return False


def walk_qh_leaves(requirement):
    children = requirement.get("children") or []
    if not children:
        yield requirement
        return
    for child in children:
        yield from walk_qh_leaves(child)


def dedupe_requirements(requirements):
    result = []
    seen = set()
    for requirement in requirements:
        key = json.dumps(requirement, sort_keys=True)
        if key not in seen:
            seen.add(key)
            result.append(requirement)
    return result


def default_sections(quest, qh_quest, candidates, resource_cards, monster_cards):
    items = existing_item_requirements(quest, resource_cards, monster_cards)
    enemies = existing_enemy_requirements(quest, monster_cards)
    additions = []
    if qh_quest and candidates:
        for root in qh_quest.get("requirements") or []:
            if not root_contains_candidate(root, candidates, resource_cards):
                continue
            converted = convert_qh_requirement(root, resource_cards)
            if converted:
                additions.append(converted)
    additions = dedupe_requirements(additions)
    nested_cards = {card for root in additions for card in requirement_cards(root)}
    items = remove_fully_nested_groups(items, nested_cards)

    sections = []
    items = dedupe_requirements([*items, *additions])
    if items:
        sections.append(section("Items", items))
    if enemies:
        sections.append(section("Enemies", enemies))
    return sections


def rfd_sections(legacy, qh_by_name, resource_cards, monster_cards):
    old_items = existing_item_requirements(legacy, resource_cards, monster_cards)
    old_enemies = existing_enemy_requirements(legacy, monster_cards)
    sections = []
    assigned_item_indexes = set()
    assigned_enemies = set()

    for qh_name, display_name in RFD_SECTIONS:
        qh = qh_by_name[qh_name]
        qh_requirements = dedupe_requirements(
            converted
            for root in qh.get("requirements") or []
            if (converted := convert_qh_requirement(root, resource_cards)) is not None
        )
        qh_cards = {
            card for requirement in qh_requirements for card in requirement_cards(requirement)
        }
        requirements = []
        for index, existing in enumerate(old_items):
            if index in assigned_item_indexes:
                continue
            manually_assigned = RFD_MANUAL_ITEM_SECTIONS.get(existing["label"]) == qh_name
            if manually_assigned or set(requirement_cards(existing)) & qh_cards:
                requirements.append(deepcopy(existing))
                assigned_item_indexes.add(index)
        combat_text = " ".join(qh.get("combatRequirements") or []).casefold()
        for enemy in old_enemies:
            card = enemy["cards"][0]
            aliases = [card.casefold()]
            if card == "Monkey Guard":
                aliases.append("guard monkey")
            if any(alias in combat_text for alias in aliases):
                requirements.append(deepcopy(enemy))
                assigned_enemies.add(card)
        sections.append(section(display_name, requirements))

    leftovers = [
        requirement
        for index, requirement in enumerate(old_items)
        if index not in assigned_item_indexes
    ]
    leftovers.extend(
        enemy for enemy in old_enemies if enemy["cards"][0] not in assigned_enemies
    )
    if leftovers:
        sections.append(section("Existing additional requirements", leftovers))
    return sections


def shield_sections(legacy):
    black_arm = {
        "label": "Black Arm Gang route",
        "logic": "ALL",
        "selectorValue": "BLACK_ARM",
        "children": [
            {"label": "Weaponsmaster (enemy)", "cards": ["Weaponsmaster"], "type": "enemy"}
        ],
    }
    phoenix = {
        "label": "Phoenix Gang route",
        "logic": "ALL",
        "selectorValue": "PHOENIX",
        "children": [
            {"label": "Coins", "cards": ["Coins"], "type": "item"},
            {"label": "Jonny the Beard (enemy)", "cards": ["Jonny the Beard"], "type": "enemy"},
        ],
    }
    return [
        section(
            "Gang routes",
            [
                {
                    "label": "Your Shield of Arrav gang route",
                    "logic": "ANY",
                    "selector": "SHIELD_GANG",
                    "children": [black_arm, phoenix],
                }
            ],
        )
    ]


def heroes_sections(legacy, qh, resource_cards, monster_cards):
    old_items = existing_item_requirements(legacy, resource_cards, monster_cards)
    old_enemies = existing_enemy_requirements(legacy, monster_cards)
    black_cards = {"Black full helm", "Black platebody", "Black platelegs"}
    route_enemy = "Grip"

    qh_shared = dedupe_requirements(
        converted
        for root in qh.get("requirements") or []
        if (converted := convert_qh_requirement(root, resource_cards)) is not None
        and not (set(requirement_cards(converted)) & black_cards)
    )
    represented = {card for root in qh_shared for card in requirement_cards(root)}
    shared = remove_fully_nested_groups(
        [req for req in old_items if not (set(requirement_cards(req)) & black_cards)],
        represented,
    )
    shared.extend(qh_shared)
    shared.extend(
        enemy for enemy in old_enemies if enemy["cards"][0] != route_enemy
    )

    black = {
        "label": "Black Arm Gang route",
        "logic": "ALL",
        "selectorValue": "BLACK_ARM",
        "children": [
            req for req in old_items if set(requirement_cards(req)) & black_cards
        ],
    }
    phoenix = {
        "label": "Phoenix Gang route",
        "logic": "ALL",
        "selectorValue": "PHOENIX",
        "children": [
            enemy for enemy in old_enemies if enemy["cards"][0] == route_enemy
        ],
    }
    return [
        section("Shared requirements", dedupe_requirements(shared)),
        section(
            "Gang route",
            [
                {
                    "label": "Your Shield of Arrav gang route",
                    "logic": "ANY",
                    "selector": "SHIELD_GANG",
                    "children": [black, phoenix],
                }
            ],
        ),
    ]


def collect_v2_cards(quest):
    return {
        card
        for current_section in quest.get("sections") or []
        for requirement in current_section.get("requirements") or []
        for card in requirement_cards(requirement)
    }


def migrate(legacy, qh, audit, tracked_items, tracked_monsters):
    resources = folded_catalog(tracked_items)
    monsters = folded_catalog(tracked_monsters)
    qh_by_name = {quest["name"]: quest for quest in qh["quests"]}
    candidates = approved_candidates(audit)
    migrated = []

    for quest in legacy["quests"]:
        name = quest["name"]
        legacy_notes = [
            card
            for card in quest.get("monsterCards") or []
            if card.casefold() not in monsters
        ]
        if name == "Recipe for Disaster":
            sections = rfd_sections(quest, qh_by_name, resources, monsters)
        elif name == "Shield of Arrav":
            sections = shield_sections(quest)
        elif name == "Heroes' Quest":
            sections = heroes_sections(quest, qh_by_name[name], resources, monsters)
        else:
            sections = default_sections(
                quest,
                qh_by_name.get(name),
                candidates.get(name, set()),
                resources,
                monsters,
            )
        migrated.append(
            {
                "name": name,
                "miniquest": bool(quest.get("miniquest", False)),
                "sections": sections,
                "notes": "; ".join(
                    part for part in [quest.get("notes") or "", *legacy_notes] if part
                ),
            }
        )

    output = {
        "schema": 2,
        "source": {
            "questHelperCommit": qh.get("sourceCommit"),
            "questHelperVersion": qh.get("sourceVersion"),
        },
        "quests": migrated,
    }

    assert len(migrated) == len(legacy["quests"])
    assert [quest["name"] for quest in migrated] == [quest["name"] for quest in legacy["quests"]]
    migrated_by_name = {quest["name"]: quest for quest in migrated}
    for old in legacy["quests"]:
        old_cards = {
            card
            for group in old.get("cardGroups") or []
            for card in group
        } | {
            card
            for card in old.get("monsterCards") or []
            if card.casefold() in monsters
        }
        missing = old_cards - collect_v2_cards(migrated_by_name[old["name"]])
        assert not missing, f"{old['name']} lost existing cards: {sorted(missing)}"

    all_known = set(resources) | set(monsters)
    for quest in migrated:
        for card in collect_v2_cards(quest):
            assert card.casefold() in all_known, f"Unknown card {card!r} in {quest['name']}"

    for quest_name, approved in candidates.items():
        present = collect_v2_cards(migrated_by_name[quest_name])
        missing = approved - present
        assert not missing, f"{quest_name} missing approved candidates: {sorted(missing)}"

    rfd = migrated_by_name["Recipe for Disaster"]
    assert len(rfd["sections"]) >= 10
    return output


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("legacy", type=Path)
    parser.add_argument("quest_helper_export", type=Path)
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--tracked-items", type=Path, required=True)
    parser.add_argument("--tracked-monsters", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = migrate(
        load(args.legacy),
        load(args.quest_helper_export),
        load(args.audit),
        load(args.tracked_items),
        load(args.tracked_monsters),
    )
    args.output.write_text(json.dumps(result, indent="\t") + "\n", encoding="utf-8")
    print(f"Wrote {len(result['quests'])} schema-2 quest entries to {args.output}")


if __name__ == "__main__":
    main()
