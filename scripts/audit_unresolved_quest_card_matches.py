#!/usr/bin/env python3
"""Compare unresolved Quest Helper labels with the shipped TCG card catalogue.

This is deliberately review-only. Approximate matches are never written back
to quest_cards.json.
"""

import argparse
import difflib
import json
import re
from pathlib import Path


STOP_WORDS = {
    "a", "an", "and", "any", "for", "in", "of", "or", "the", "to", "with",
    "bring", "equipment", "gear", "some", "your",
}


def load(path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def normalize(value):
    value = (value or "").casefold()
    value = re.sub(r"\([^)]*\)", " ", value)
    value = re.sub(r"^\s*\d+\s*(?:x\s*)?", "", value)
    value = value.replace("&", " and ").replace("'", "")
    return " ".join(re.findall(r"[a-z0-9]+", value))


def meaningful_tokens(value):
    return {token for token in normalize(value).split() if token not in STOP_WORDS and len(token) > 2}


def score(left, left_tokens, right, right_tokens):
    if not left or not right:
        return 0.0, "none"
    if left == right:
        return 1.0, "normalized exact"
    padded_left = f" {left} "
    padded_right = f" {right} "
    if len(right) >= 5 and padded_right in padded_left:
        return 0.86, "card name appears in label"
    if len(left) >= 5 and padded_left in padded_right:
        return 0.82, "label appears in card name"
    overlap = left_tokens & right_tokens
    if not overlap:
        return 0.0, "none"
    ratio = difflib.SequenceMatcher(None, left, right).ratio()
    if overlap:
        ratio += min(0.08, 0.03 * len(overlap))
    return min(ratio, 0.95), "approximate"


def build(audit, tracked_items):
    cards = sorted(
        {card for values in tracked_items["entityToCards"].values() for card in values},
        key=str.casefold,
    )
    indexed_cards = [
        (card, normalize(card), meaningful_tokens(card)) for card in cards
    ]
    rows = []
    for unresolved in audit["unresolved"]:
        left = normalize(unresolved["label"])
        left_tokens = meaningful_tokens(unresolved["label"])
        ranked = []
        for card, card_norm, card_tokens in indexed_cards:
            result = score(left, left_tokens, card_norm, card_tokens)
            if result[0] >= 0.74:
                ranked.append((result, card))
        ranked.sort(key=lambda item: (-item[0][0], item[1].casefold()))
        strong = [
            {"card": card, "score": round(result[0], 3), "reason": result[1]}
            for result, card in ranked if result[1] == "normalized exact"
        ][:8]
        possible = [
            {"card": card, "score": round(result[0], 3), "reason": result[1]}
            for result, card in ranked if result[1] != "normalized exact"
        ][:5]
        status = "strong" if strong else "possible" if possible else "no plausible match"
        rows.append({**unresolved, "status": status, "strongMatches": strong, "possibleMatches": possible})
    return {
        "summary": {
            "unresolvedLabels": len(rows),
            "strongMatches": sum(row["status"] == "strong" for row in rows),
            "possibleMatches": sum(row["status"] == "possible" for row in rows),
            "noPlausibleMatch": sum(row["status"] == "no plausible match" for row in rows),
        },
        "rows": rows,
    }


def markdown(report):
    summary = report["summary"]
    lines = [
        "# Unresolved Quest Helper labels vs TCG cards",
        "",
        "Review only. Matches use the shipped `tracked_item_names.json`; no quest data was changed.",
        "Approximate matches are suggestions, not accepted mappings.",
        "",
        "## Summary",
        "",
        f"- Unresolved labels checked: {summary['unresolvedLabels']}.",
        f"- Strong catalogue matches: {summary['strongMatches']}.",
        f"- Possible catalogue matches: {summary['possibleMatches']}.",
        f"- No plausible card match: {summary['noPlausibleMatch']}.",
        "",
    ]
    for status, heading in (
        ("strong", "Strong catalogue matches"),
        ("possible", "Possible catalogue matches"),
        ("no plausible match", "No plausible card match"),
    ):
        lines.extend([f"## {heading}", ""])
        for row in report["rows"]:
            if row["status"] != status:
                continue
            matches = row["strongMatches"] or row["possibleMatches"]
            match_text = ", ".join(match["card"] for match in matches) or "none"
            constants = ", ".join(row.get("constantNames") or []) or "none"
            lines.append(
                f"- **{row['quest']} — {row['label']}**: cards: {match_text}; constants: `{constants}`."
            )
        lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--tracked-items", type=Path, required=True)
    parser.add_argument("--json-output", type=Path, required=True)
    parser.add_argument("--markdown-output", type=Path, required=True)
    args = parser.parse_args()
    report = build(load(args.audit), load(args.tracked_items))
    args.json_output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    args.markdown_output.write_text(markdown(report), encoding="utf-8")
    print(json.dumps(report["summary"], indent=2))


if __name__ == "__main__":
    main()
