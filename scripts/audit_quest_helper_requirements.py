#!/usr/bin/env python3
"""Compare a pinned Quest Helper requirement export with quest_cards.json.

This is a report-only developer tool. It never rewrites plugin data. Quest
Helper display labels are resolved to TCG resource-card names only when a
normalization produces one unique catalogue match; unresolved labels remain
visible in the report instead of being guessed.
"""

import argparse
import json
import re
from pathlib import Path


def load_json(path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def unique(values):
    return list(dict.fromkeys(value for value in values if value))


def name_variants(raw_name):
    name = re.sub(r"\s+", " ", (raw_name or "").strip())
    variants = [name]
    variants.append(re.sub(r"\s*\([1-4]\)$", "", name))
    variants.append(re.sub(r"^\d+\s*(?:x\s*)?", "", name, flags=re.IGNORECASE))

    words = name.split(" ")
    if words:
        first = words[0]
        if first.lower().endswith("ies"):
            variants.append(" ".join([first[:-3] + "y"] + words[1:]))
        elif first.lower().endswith("s") and len(first) > 3:
            variants.append(" ".join([first[:-1]] + words[1:]))

        last = words[-1]
        if last.lower().endswith("ies"):
            variants.append(" ".join(words[:-1] + [last[:-3] + "y"]))
        elif last.lower().endswith("ves"):
            variants.append(" ".join(words[:-1] + [last[:-3] + "fe"]))
        elif last.lower().endswith("s") and len(last) > 3:
            variants.append(" ".join(words[:-1] + [last[:-1]]))
    return unique(variants)


def resolve_card_name(label, cards_by_folded_name):
    matches = {
        cards_by_folded_name[variant.casefold()]
        for variant in name_variants(label)
        if variant.casefold() in cards_by_folded_name
    }
    return next(iter(matches)) if len(matches) == 1 else None


def walk_leaves(requirement, inside_alternative=False):
    children = requirement.get("children") or []
    if children:
        child_is_alternative = inside_alternative or requirement.get("logic") in {
            "OR",
            "XOR",
            "NAND",
            "NOR",
        }
        for child in children:
            yield from walk_leaves(child, child_is_alternative)
        return
    yield requirement, inside_alternative


def qh_card_names(quest, cards_by_folded_name):
    mandatory = []
    alternatives = []
    unresolved = []
    for requirement in quest.get("requirements") or []:
        for leaf, inside_alternative in walk_leaves(requirement):
            card = resolve_card_name(leaf.get("name"), cards_by_folded_name)
            if card:
                (alternatives if inside_alternative else mandatory).append(card)
            elif leaf.get("name"):
                unresolved.append(leaf["name"])
    mandatory = set(mandatory)
    alternatives = set(alternatives) - mandatory
    return (
        sorted(mandatory, key=str.casefold),
        sorted(alternatives, key=str.casefold),
        sorted(set(unresolved), key=str.casefold),
    )


def bronzeman_card_names(quest, cards_by_folded_name):
    return sorted(
        {
            cards_by_folded_name[card.casefold()]
            for group in quest.get("cardGroups") or []
            for card in group
            if card.casefold() in cards_by_folded_name
        },
        key=str.casefold,
    )


def compare(qh_data, quest_data, tracked_items):
    cards_by_folded_name = {
        card.casefold(): card
        for cards in tracked_items["entityToCards"].values()
        for card in cards
    }
    qh_quests = {quest["name"]: quest for quest in qh_data["quests"]}
    bm_full = {
        quest["name"]: quest
        for quest in quest_data["quests"]
        if not quest.get("miniquest", False)
    }
    exact_names = sorted(set(qh_quests) & set(bm_full), key=str.casefold)

    comparisons = []
    for name in exact_names:
        qh_mandatory, qh_alternatives, unresolved = qh_card_names(
            qh_quests[name], cards_by_folded_name
        )
        qh_cards = sorted(set(qh_mandatory) | set(qh_alternatives), key=str.casefold)
        bm_cards = bronzeman_card_names(bm_full[name], cards_by_folded_name)
        qh_by_folded = {card.casefold(): card for card in qh_cards}
        qh_mandatory_by_folded = {card.casefold(): card for card in qh_mandatory}
        qh_alternatives_by_folded = {card.casefold(): card for card in qh_alternatives}
        bm_by_folded = {card.casefold(): card for card in bm_cards}
        comparisons.append(
            {
                "quest": name,
                "questHelperCardBackedItems": qh_cards,
                "questHelperMandatoryItems": qh_mandatory,
                "questHelperAlternativeItems": qh_alternatives,
                "bronzemanItemCards": bm_cards,
                "questHelperOnlyMandatory": sorted(
                    (
                        qh_mandatory_by_folded[key]
                        for key in set(qh_mandatory_by_folded) - set(bm_by_folded)
                    ),
                    key=str.casefold,
                ),
                "questHelperOnlyAlternatives": sorted(
                    (
                        qh_alternatives_by_folded[key]
                        for key in set(qh_alternatives_by_folded) - set(bm_by_folded)
                    ),
                    key=str.casefold,
                ),
                "bronzemanOnly": sorted(
                    (bm_by_folded[key] for key in set(bm_by_folded) - set(qh_by_folded)),
                    key=str.casefold,
                ),
                "unresolvedQuestHelperLabels": unresolved,
            }
        )

    return {
        "source": {
            "questHelperCommit": qh_data.get("sourceCommit"),
            "questHelperVersion": qh_data.get("sourceVersion"),
            "questHelperFullQuestEntries": len(qh_quests),
            "bronzemanFullQuestEntries": len(bm_full),
            "exactNameMatches": len(exact_names),
        },
        "catalogueDifferences": {
            "questHelperOnlyNames": sorted(set(qh_quests) - set(bm_full), key=str.casefold),
            "bronzemanOnlyNames": sorted(set(bm_full) - set(qh_quests), key=str.casefold),
            "knownRepresentationDifferences": {
                "Recipe for Disaster": sorted(
                    name for name in qh_quests if name.startswith("RFD -")
                ),
                "Shield of Arrav": sorted(
                    name for name in qh_quests if name.startswith("Shield of Arrav -")
                ),
            },
        },
        "summary": {
            "questsWithQuestHelperOnlyMandatoryCandidates": sum(
                bool(row["questHelperOnlyMandatory"]) for row in comparisons
            ),
            "questHelperOnlyMandatoryCandidateCount": sum(
                len(row["questHelperOnlyMandatory"]) for row in comparisons
            ),
            "questsWithQuestHelperOnlyAlternativeCandidates": sum(
                bool(row["questHelperOnlyAlternatives"]) for row in comparisons
            ),
            "questHelperOnlyAlternativeCandidateCount": sum(
                len(row["questHelperOnlyAlternatives"]) for row in comparisons
            ),
            "questsWithBronzemanOnlyItems": sum(
                bool(row["bronzemanOnly"]) for row in comparisons
            ),
            "bronzemanOnlyItemCount": sum(
                len(row["bronzemanOnly"]) for row in comparisons
            ),
            "unresolvedQuestHelperLabelCount": sum(
                len(row["unresolvedQuestHelperLabels"]) for row in comparisons
            ),
        },
        "comparisons": comparisons,
    }


def markdown(report):
    source = report["source"]
    summary = report["summary"]
    differences = report["catalogueDifferences"]
    mandatory_rows = [
        row for row in report["comparisons"] if row["questHelperOnlyMandatory"]
    ]
    alternative_rows = [
        row for row in report["comparisons"] if row["questHelperOnlyAlternatives"]
    ]
    bronzeman_rows = [row for row in report["comparisons"] if row["bronzemanOnly"]]

    lines = [
        "# Quest Helper requirement audit",
        "",
        "Report-only comparison. No changes were made to `quest_cards.json`.",
        "",
        "## Baseline",
        "",
        f"- Quest Helper `{source['questHelperCommit']}` "
        f"(build version `{source['questHelperVersion']}`): "
        f"{source['questHelperFullQuestEntries']} full-quest helper entries.",
        f"- Bronzeman: {source['bronzemanFullQuestEntries']} full quests.",
        f"- Exact title matches compared: {source['exactNameMatches']}.",
        "- Quest Helper splits `Recipe for Disaster` into ten helpers and "
        "`Shield of Arrav` into two route helpers; Bronzeman stores one entry for each quest.",
        "- `The Blood Moon Rises` exists only in Bronzeman in this comparison; the pinned "
        "Quest Helper revision has no matching helper.",
        "",
        "## Interpretation",
        "",
        "A Quest Helper-only item is a review candidate, not an automatic correction. "
        "Quest Helper models what its guide wants available, while Bronzeman models card-backed "
        "requirements. Bronzeman-only items are not removal candidates: several came from the "
        "existing walkthrough-mining pass and can represent interactions Quest Helper does not "
        "list in its top-level item requirements.",
        "",
        "Only Quest Helper labels that resolve uniquely to an existing TCG Resource card are "
        "included as candidates. Unresolved generic labels and ID alternatives remain in the "
        "JSON artifact for manual inspection; no names are guessed.",
        "",
        "## Summary",
        "",
        f"- {summary['questHelperOnlyMandatoryCandidateCount']} Quest Helper-only mandatory "
        f"card candidates across {summary['questsWithQuestHelperOnlyMandatoryCandidates']} quests.",
        f"- {summary['questHelperOnlyAlternativeCandidateCount']} additional Quest Helper-only "
        f"cards appear inside OR/alternative structures across "
        f"{summary['questsWithQuestHelperOnlyAlternativeCandidates']} quests.",
        f"- {summary['bronzemanOnlyItemCount']} Bronzeman-only item cards across "
        f"{summary['questsWithBronzemanOnlyItems']} quests.",
        f"- {summary['unresolvedQuestHelperLabelCount']} distinct-per-quest Quest Helper labels "
        "could not be safely mapped to one TCG card.",
        "",
        "## Priority review: Quest Helper-only mandatory candidates",
        "",
    ]
    if mandatory_rows:
        for row in mandatory_rows:
            lines.append(
                f"- **{row['quest']}**: {', '.join(row['questHelperOnlyMandatory'])}"
            )
    else:
        lines.append("- None.")

    lines.extend(
        [
            "",
            "## Secondary review: cards inside Quest Helper alternatives",
            "",
            "These must be reviewed as whole OR groups; no individual card below is necessarily "
            "mandatory.",
            "",
        ]
    )
    if alternative_rows:
        for row in alternative_rows:
            lines.append(
                f"- **{row['quest']}**: {', '.join(row['questHelperOnlyAlternatives'])}"
            )
    else:
        lines.append("- None.")

    lines.extend(["", "## Bronzeman-only items (do not remove automatically)", ""])
    if bronzeman_rows:
        for row in bronzeman_rows:
            lines.append(f"- **{row['quest']}**: {', '.join(row['bronzemanOnly'])}")
    else:
        lines.append("- None.")

    lines.extend(
        [
            "",
            "## Catalogue title differences",
            "",
            "Quest Helper-only titles:",
            "",
        ]
    )
    lines.extend(f"- {name}" for name in differences["questHelperOnlyNames"])
    lines.extend(["", "Bronzeman-only titles:", ""])
    lines.extend(f"- {name}" for name in differences["bronzemanOnlyNames"])
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

    report = compare(
        load_json(args.quest_helper_export),
        load_json(args.quest_cards),
        load_json(args.tracked_items),
    )
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.markdown_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    args.markdown_output.write_text(markdown(report), encoding="utf-8")


if __name__ == "__main__":
    main()
