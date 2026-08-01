#!/usr/bin/env python3
"""Find Quest Helper item groups missed by the first name-only audit.

This is report-only. It resolves every leaf from both its display label and
the exported RuneLite ItemID constant names, then compares the complete group
with schema-2 quest_cards.json. It never rewrites plugin data.
"""

import argparse
import json
import re
from pathlib import Path


CONSTANT_ALIASES = {
    "MYSTIC_AIR_STAFF": "Mystic air staff",
    "MYSTIC_SMOKE_BATTLESTAFF": "Mystic smoke staff",
    "MYSTIC_DUST_BATTLESTAFF": "Mystic dust staff",
    "MYSTIC_MIST_BATTLESTAFF": "Mystic mist staff",
    "POH_TABLET_ENCHANTEMERALD": "Enchant emerald or jade",
    "POH_TABLET_ENCHANTRUBY": "Enchant ruby or topaz",
    "_3A_AXE": "3rd age axe",
    "_3A_PICKAXE": "3rd age pickaxe",
    "TRAIL_GILDED_AXE": "Gilded axe",
    "TRAIL_GILDED_PICKAXE": "Gilded pickaxe",
    "GUAMVIAL": "Guam potion",
    "AIRRUNE": "Air rune",
    "EARTHRUNE": "Earth rune",
    "WATERRUNE": "Water rune",
    "CHAOSRUNE": "Chaos rune",
    "LAWRUNE": "Law rune",
    "BLANKRUNE": "Rune essence",
    "BLANKRUNE_HIGH": "Pure essence",
    "WOODPLANK": "Plank",
    "PLANK_OAK": "Oak plank",
    "MACHETTE_REDTOPAZ": "Red topaz machete",
    "MACHETTE_JADE": "Jade machete",
    "MACHETTE_OPAL": "Opal machete",
    "MACHETTE": "Machete",
    "NAILS": "Steel nails",
    "NAILS_IRON": "Iron nails",
    "NAILS_BRONZE": "Bronze nails",
    "NAILS_BLACK": "Black nails",
    "NAILS_MITHRIL": "Mithril nails",
    "NAILS_ADAMANT": "Adamantite nails",
    "NAILS_RUNE": "Rune nails",
    "BUCKET_EMPTY": "Bucket",
    "SNAIL_CORPSE1": "Thin snail",
    "SNAIL_CORPSE2": "Lean snail",
    "SNAIL_CORPSE3": "Fat snail",
}

OWNER_EXCLUDED_CARDS = {
    ("Mountain Daughter", "Almost any gloves"): {
        "Ferocious gloves", "Granite gloves",
    },
    ("Regicide", "Gloves which fully cover your hand"): {
        "Ferocious gloves", "Granite gloves", "Antisanta gloves", "Cow gloves",
        "Santa gloves", "Ancient ceremonial gloves", "Bloodbark gauntlets",
        "Dragonstone gauntlets", "Holy wraps", "Ornate gloves", "Ranger gloves",
        "Samurai gloves", "Splitbark gauntlets", "Swampbark gauntlets",
        "Zaryte vambraces",
    },
    ("Shadow of the Storm", "pieces of black clothing"): {
        "Antisanta boots", "Antisanta gloves", "Antisanta jacket",
        "Antisanta mask", "Black partyhat",
    },
}

OWNER_OMITTED_UNRESOLVED = {
    ("Dragon Slayer I", "Combat equipment"),
    ("Dragon Slayer I", "Telekinetic grab"),
    ("Dragon Slayer II", "Catspeak amulet (e)"),
    ("Dragon Slayer II", "Ghostspeak amulet"),
    ("Dragon Slayer II", "Goutweed"),
    ("Dragon Slayer II", "Seal of passage"),
}

OWNER_EXCLUDED_CATEGORIES = {"Food/combat-supply review"}


def load(path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def unique(values):
    return list(dict.fromkeys(value for value in values if value))


def name_variants(raw_name):
    name = re.sub(r"\s+", " ", (raw_name or "").strip())
    variants = [name]
    variants.append(re.sub(r"\s*\([1-4]\)$", "", name))
    variants.append(re.sub(r"^\d+\s*(?:x\s*)?", "", name, flags=re.IGNORECASE))
    return unique(variants)


def constant_variants(constant):
    result = [CONSTANT_ALIASES.get(constant)]
    result.append(constant.replace("_", " ").strip().lower())
    if constant.startswith("MYSTIC_") and constant.endswith("_BATTLESTAFF"):
        element = constant[len("MYSTIC_") : -len("_BATTLESTAFF")]
        result.append("mystic " + element.replace("_", " ").lower() + " staff")
    return unique(result)


def card_catalog(tracked_items):
    return {
        card.casefold(): card
        for cards in tracked_items["entityToCards"].values()
        for card in cards
    }


def resolve_leaf(leaf, cards):
    candidates = []
    for candidate in name_variants(leaf.get("name")):
        card = cards.get(candidate.casefold())
        if card:
            candidates.append(card)
    for constant in leaf.get("constantNames") or []:
        for candidate in constant_variants(constant):
            card = cards.get(candidate.casefold())
            if card:
                candidates.append(card)
    return unique(candidates)


def walk_leaves(requirement, inside_alternative=False):
    children = requirement.get("children") or []
    if not children:
        yield requirement, inside_alternative
        return
    alternative = inside_alternative or requirement.get("logic") in {
        "OR", "XOR", "NAND", "NOR"
    }
    for child in children:
        yield from walk_leaves(child, alternative)


def collect_cards(requirement):
    result = list(requirement.get("cards") or [])
    for child in requirement.get("children") or []:
        result.extend(collect_cards(child))
    return result


def current_cards(quest):
    return {
        card.casefold()
        for section in quest.get("sections") or []
        for requirement in section.get("requirements") or []
        for card in collect_cards(requirement)
    }


def category(label):
    folded = label.casefold()
    if "pickaxe" in folded:
        return "Pickaxe groups"
    if "axe" in folded and "thrownaxe" not in folded:
        return "Axe groups"
    if any(word in folded for word in ("light", "torch", "candle")):
        return "Light-source groups"
    if "hammer" in folded:
        return "Hammer alternatives"
    if any(word in folded for word in ("food", "meat", "fish", "cake", "eating")):
        return "Food/combat-supply review"
    if any(word in folded for word in ("bow", "arrow", "weapon")):
        return "Weapon/ammo groups"
    if any(word in folded for word in ("altar", "talisman")):
        return "Altar-access groups"
    if any(word in folded for word in ("glove", "clothing", "outfit")):
        return "Clothing groups"
    if any(word in folded for word in ("log", "branch")):
        return "Logs/branches groups"
    return "Other concrete groups"


def build_report(qh_data, quest_data, tracked_items):
    cards = card_catalog(tracked_items)
    current_by_name = {quest["name"]: quest for quest in quest_data["quests"]}
    groups = []
    unresolved = []
    compared = 0

    for qh_quest in qh_data["quests"]:
        quest = current_by_name.get(qh_quest["name"])
        if not quest:
            continue
        compared += 1
        present = current_cards(quest)
        for root in qh_quest.get("requirements") or []:
            for leaf, alternative in walk_leaves(root):
                resolved = resolve_leaf(leaf, cards)
                resolved = [
                    card for card in resolved
                    if card not in OWNER_EXCLUDED_CARDS.get(
                        (qh_quest["name"], leaf.get("name")), set()
                    )
                ]
                if not resolved:
                    if (leaf.get("name") and
                            (qh_quest["name"], leaf["name"]) not in OWNER_OMITTED_UNRESOLVED):
                        unresolved.append(
                            {
                                "quest": qh_quest["name"],
                                "label": leaf["name"],
                                "constantNames": leaf.get("constantNames") or [],
                            }
                        )
                    continue
                covered = [card for card in resolved if card.casefold() in present]
                missing = [card for card in resolved if card.casefold() not in present]
                if not missing:
                    continue
                row_category = category(leaf.get("name") or resolved[0])
                if row_category in OWNER_EXCLUDED_CATEGORIES:
                    continue
                coverage = "missing" if not covered else "partial"
                groups.append(
                    {
                        "quest": qh_quest["name"],
                        "label": leaf.get("name") or resolved[0],
                        "category": row_category,
                        "coverage": coverage,
                        "insideAlternative": alternative,
                        "resolvedCards": resolved,
                        "coveredCards": covered,
                        "missingCards": missing,
                    }
                )

    groups.sort(key=lambda row: (row["category"].casefold(), row["quest"].casefold(), row["label"].casefold()))
    unresolved.sort(key=lambda row: (row["quest"].casefold(), row["label"].casefold()))
    categories = {}
    for row in groups:
        summary = categories.setdefault(
            row["category"], {"groupCount": 0, "quests": set(), "missing": 0, "partial": 0}
        )
        summary["groupCount"] += 1
        summary["quests"].add(row["quest"])
        summary[row["coverage"]] += 1
    for summary in categories.values():
        summary["questCount"] = len(summary["quests"])
        summary["quests"] = sorted(summary["quests"], key=str.casefold)

    return {
        "source": {
            "questHelperCommit": qh_data.get("sourceCommit"),
            "questHelperVersion": qh_data.get("sourceVersion"),
            "questEntriesCompared": compared,
        },
        "summary": {
            "candidateGroups": len(groups),
            "affectedQuests": len({row["quest"] for row in groups}),
            "fullyMissingGroups": sum(row["coverage"] == "missing" for row in groups),
            "partialGroups": sum(row["coverage"] == "partial" for row in groups),
            "remainingUnresolvedLabels": len(unresolved),
        },
        "categories": categories,
        "groups": groups,
        "unresolved": unresolved,
    }


def markdown(report):
    source = report["source"]
    summary = report["summary"]
    lines = [
        "# Quest Helper generic-group outlier audit",
        "",
        "Report only. No changes were made to `quest_cards.json`.",
        "",
        "## Why the first audit missed these",
        "",
        "The first audit matched Quest Helper display labels directly to TCG card names. "
        "Generic labels such as `An axe`, `Any pickaxe`, or `A light source` therefore "
        "stayed unresolved even though the pinned export already contained their RuneLite "
        "ItemID constants. This pass resolves both sources and compares the whole alternative "
        "group against the current schema-2 quest data.",
        "",
        "## Baseline",
        "",
        f"- Quest Helper `{source['questHelperCommit']}` (version `{source['questHelperVersion']}`).",
        f"- Exact quest-title entries compared: {source['questEntriesCompared']}.",
        f"- Candidate groups: {summary['candidateGroups']} across {summary['affectedQuests']} quests.",
        f"- Fully absent groups: {summary['fullyMissingGroups']}.",
        f"- Partially represented alternative groups: {summary['partialGroups']}.",
        f"- Still unresolved labels: {summary['remainingUnresolvedLabels']}.",
        "",
        "A candidate is not automatically a correction. Food, combat supplies, optional "
        "routes, quest-obtainable tools, and broad equipment recommendations need a mechanics "
        "ruling before generation.",
        "",
        "## Category summary",
        "",
    ]
    for name, data in report["categories"].items():
        lines.append(
            f"- **{name}:** {data['groupCount']} groups across {data['questCount']} quests "
            f"({data['missing']} missing, {data['partial']} partial)."
        )

    lines.extend(["", "## Candidate groups", ""])
    current_category = None
    for row in report["groups"]:
        if row["category"] != current_category:
            current_category = row["category"]
            lines.extend([f"### {current_category}", ""])
        covered = f"; already present: {', '.join(row['coveredCards'])}" if row["coveredCards"] else ""
        alternative = "; inside an OR group" if row["insideAlternative"] else ""
        lines.append(
            f"- **{row['quest']} — {row['label']}** ({row['coverage']}{alternative}): "
            f"missing {', '.join(row['missingCards'])}{covered}."
        )

    lines.extend(["", "## Still unresolved after constant matching", ""])
    for row in report["unresolved"]:
        constants = ", ".join(row["constantNames"]) or "no exported constants"
        lines.append(f"- **{row['quest']} — {row['label']}**: {constants}.")
    lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("quest_helper_export", type=Path)
    parser.add_argument("--quest-cards", type=Path, default=Path("src/main/resources/quest_cards.json"))
    parser.add_argument("--tracked-items", type=Path, default=Path("src/main/resources/tracked_item_names.json"))
    parser.add_argument("--json-output", type=Path, required=True)
    parser.add_argument("--markdown-output", type=Path, required=True)
    args = parser.parse_args()
    report = build_report(load(args.quest_helper_export), load(args.quest_cards), load(args.tracked_items))
    args.json_output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    args.markdown_output.write_text(markdown(report), encoding="utf-8")
    print(json.dumps(report["summary"], indent=2))


if __name__ == "__main__":
    main()
