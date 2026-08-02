#!/usr/bin/env python3
"""Validate the pinned Quest Helper prerequisite evidence and runtime snapshot."""

import argparse
import json
import xml.etree.ElementTree as ET
from pathlib import Path


VALID_STATES = {"FINISHED", "IN_PROGRESS"}
SOURCE_COLUMNS = ["sourceKey", "sourceName", "targetKey", "targetName", "state"]
RFD_SOURCE_KEYS = {
    "RECIPE_FOR_DISASTER_DWARF",
    "RECIPE_FOR_DISASTER_EVIL_DAVE",
    "RECIPE_FOR_DISASTER_FINALE",
    "RECIPE_FOR_DISASTER_LUMBRIDGE_GUIDE",
    "RECIPE_FOR_DISASTER_MONKEY_AMBASSADOR",
    "RECIPE_FOR_DISASTER_SIR_AMIK_VARZE",
    "RECIPE_FOR_DISASTER_SKRACH_UGLOGWEE",
    "RECIPE_FOR_DISASTER_START",
    "RECIPE_FOR_DISASTER_WARTFACE_AND_BENTNOZE",
}
SHIELD_SOURCE_KEY = "SHIELD_OF_ARRAV_BLACK_ARM_GANG"
EXPECTED_SHIELD_QUESTS = {
    "Defender of Varrock",
    "Ethically Acquired Antiquities",
    "Heroes' Quest",
}
EXPECTED_IN_PROGRESS = {
    ("Curse of the Empty Lord", "Desert Treasure I"),
    ("Curse of the Empty Lord", "The Restless Ghost"),
    ("Hopespear's Will", "The Restless Ghost"),
    ("Making History", "The Restless Ghost"),
}


def load(path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def sort_name(name):
    folded = name.casefold()
    return folded[4:] if folded.startswith("the ") else folded


def normalize_name(name):
    folded = name.strip().casefold()
    suffix = " (miniquest)"
    return folded[: -len(suffix)] if folded.endswith(suffix) else folded


def source_rows(source_data, errors):
    if source_data.get("schema") != 1:
        errors.append("source evidence must use schema 1")
    if source_data.get("columns") != SOURCE_COLUMNS:
        errors.append("source evidence columns do not match the extraction contract")

    parsed = []
    for index, row in enumerate(source_data.get("rows") or []):
        if not isinstance(row, list) or len(row) != len(SOURCE_COLUMNS):
            errors.append(f"invalid source evidence row {index}")
            continue
        if not all(isinstance(value, str) and value for value in row):
            errors.append(f"empty or non-string value in source evidence row {index}")
            continue
        parsed.append(tuple(row))

    if parsed != sorted(parsed):
        errors.append("source evidence rows are not deterministically sorted")
    if len(parsed) != len(set(parsed)):
        errors.append("source evidence contains duplicate rows")
    if len(parsed) != 394:
        errors.append(f"expected 394 raw Quest Helper rows, found {len(parsed)}")
    return parsed


def rows_from_test_xml(path):
    output = ET.parse(path).getroot().findtext("system-out", default="")
    rows = []
    for line in output.splitlines():
        if line.startswith("@@PREREQ\t"):
            rows.append(tuple(line.split("\t")[1:]))
    return sorted(rows)


def expected_snapshot(raw_rows, quest_names, errors):
    mapped = []
    for source_key, source_name, target_key, target_name, state in raw_rows:
        if source_key in RFD_SOURCE_KEYS:
            quest_name = "Recipe for Disaster"
        else:
            quest_name = quest_names.get(normalize_name(source_name))
        if quest_name is None:
            continue

        if target_key == SHIELD_SOURCE_KEY:
            prerequisite_name = "Shield of Arrav"
        else:
            prerequisite_name = quest_names.get(normalize_name(target_name))
        if prerequisite_name is None:
            errors.append(
                f"mapped source {source_key} has unknown prerequisite {target_key}"
            )
            continue
        if state not in VALID_STATES:
            errors.append(f"mapped source {source_key} has unsupported state {state}")
            continue
        mapped.append(
            (quest_name, source_key, prerequisite_name, target_key, state)
        )

    if len(mapped) != 259:
        errors.append(f"expected 259 catalog-mapped source rows, found {len(mapped)}")

    rfd_rows = [row for row in mapped if row[0] == "Recipe for Disaster"]
    if {row[1] for row in rfd_rows} != RFD_SOURCE_KEYS:
        errors.append("Recipe for Disaster contributor source keys changed")
    if len({(row[2], row[4]) for row in rfd_rows}) != 19:
        errors.append("Recipe for Disaster must collapse to 19 unique prerequisites")

    shield_quests = {row[0] for row in mapped if row[3] == SHIELD_SOURCE_KEY}
    if shield_quests != EXPECTED_SHIELD_QUESTS:
        errors.append("Shield of Arrav gang alias consumers changed")

    vale_rows = {(row[0], row[2]) for row in mapped if row[1] == "VALE_TOTEMS"}
    if vale_rows != {("Vale Totems (miniquest)", "Children of the Sun")}:
        errors.append("Vale Totems miniquest alias no longer resolves exactly")

    in_progress = {(row[0], row[2]) for row in mapped if row[4] == "IN_PROGRESS"}
    if in_progress != EXPECTED_IN_PROGRESS:
        errors.append("the exact IN_PROGRESS prerequisite set changed")

    grouped = {}
    for quest_name, source_key, prerequisite_name, target_key, state in mapped:
        quest = grouped.setdefault(
            quest_name, {"sourceKeys": set(), "prerequisites": {}}
        )
        quest["sourceKeys"].add(source_key)
        edge = quest["prerequisites"].setdefault(
            (prerequisite_name, state), set()
        )
        edge.add(target_key)

    quests = []
    for quest_name in sorted(grouped, key=sort_name):
        quest = grouped[quest_name]
        prerequisites = []
        for prerequisite_name, state in sorted(
            quest["prerequisites"], key=lambda edge: (sort_name(edge[0]), edge[1])
        ):
            prerequisites.append(
                {
                    "name": prerequisite_name,
                    "sourceKeys": sorted(
                        quest["prerequisites"][(prerequisite_name, state)]
                    ),
                    "state": state,
                }
            )
        quests.append(
            {
                "name": quest_name,
                "sourceKeys": sorted(quest["sourceKeys"]),
                "prerequisites": prerequisites,
            }
        )
    return quests, mapped


def validate(prerequisite_data, quest_data, source_data, test_xml=None):
    errors = []
    quest_source = quest_data.get("source") or {}
    prerequisite_source = prerequisite_data.get("source") or {}
    evidence_source = source_data.get("source") or {}

    if prerequisite_data.get("schema") != 1:
        errors.append("quest_prerequisites.json must use schema 1")
    for field in ("questHelperCommit", "questHelperVersion", "extraction"):
        values = {
            prerequisite_source.get(field),
            evidence_source.get(field),
        }
        if field != "extraction":
            values.add(quest_source.get(field))
        if len(values) != 1:
            errors.append(f"source {field} does not match the pinned evidence")

    quest_names = {}
    for quest in quest_data.get("quests") or []:
        name = quest.get("name")
        if not isinstance(name, str) or not name:
            errors.append("quest_cards.json contains an invalid quest name")
            continue
        normalized = normalize_name(name)
        if normalized in quest_names:
            errors.append(f"ambiguous normalized quest name: {name}")
        quest_names[normalized] = name

    raw_rows = source_rows(source_data, errors)
    if test_xml is not None and rows_from_test_xml(test_xml) != raw_rows:
        errors.append("checked source evidence differs from the supplied Quest Helper test XML")
    expected_quests, mapped = expected_snapshot(raw_rows, quest_names, errors)

    quests = prerequisite_data.get("quests") or []
    names = [quest.get("name") for quest in quests]
    if names != sorted(names, key=sort_name):
        errors.append("quest entries are not sorted ignoring a leading 'The '")
    if len(names) != len(set(names)):
        errors.append("duplicate quest entries")

    graph = {}
    edge_count = 0
    state_counts = {state: 0 for state in VALID_STATES}
    for quest in quests:
        name = quest.get("name")
        if normalize_name(name) not in quest_names:
            errors.append(f"unknown quest entry: {name}")
            continue
        source_keys = quest.get("sourceKeys") or []
        if not source_keys or source_keys != sorted(set(source_keys)):
            errors.append(f"invalid source keys for {name}")

        prerequisites = quest.get("prerequisites") or []
        prerequisite_names = [entry.get("name") for entry in prerequisites]
        if prerequisite_names != sorted(prerequisite_names, key=sort_name):
            errors.append(f"prerequisites are not sorted for {name}")

        seen = set()
        graph[name] = []
        for prerequisite in prerequisites:
            prerequisite_name = prerequisite.get("name")
            state = prerequisite.get("state")
            key = (prerequisite_name, state)
            if key in seen:
                errors.append(f"duplicate prerequisite for {name}: {key}")
            seen.add(key)
            if normalize_name(prerequisite_name) not in quest_names:
                errors.append(f"unknown prerequisite for {name}: {prerequisite_name}")
            if prerequisite_name == name:
                errors.append(f"self prerequisite: {name}")
            if state not in VALID_STATES:
                errors.append(f"invalid state for {name} -> {prerequisite_name}: {state}")
            else:
                state_counts[state] += 1
            prerequisite_keys = prerequisite.get("sourceKeys") or []
            if not prerequisite_keys or prerequisite_keys != sorted(set(prerequisite_keys)):
                errors.append(f"invalid source keys for {name} -> {prerequisite_name}")
            graph[name].append(prerequisite_name)
            edge_count += 1

    if quests != expected_quests:
        errors.append("runtime prerequisite snapshot differs from pinned source evidence")

    visited = set()
    visiting = []

    def visit(name):
        if name in visiting:
            cycle = visiting[visiting.index(name) :] + [name]
            errors.append("prerequisite cycle: " + " -> ".join(cycle))
            return
        if name in visited:
            return
        visiting.append(name)
        for prerequisite in graph.get(name, []):
            visit(prerequisite)
        visiting.pop()
        visited.add(name)

    for name in graph:
        visit(name)

    return errors, len(raw_rows), len(mapped), len(quests), edge_count, state_counts


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--prerequisites",
        type=Path,
        default=Path("src/main/resources/quest_prerequisites.json"),
    )
    parser.add_argument(
        "--quest-cards",
        type=Path,
        default=Path("src/main/resources/quest_cards.json"),
    )
    parser.add_argument(
        "--source-evidence",
        type=Path,
        default=Path("src/test/resources/quest_helper_prerequisites_source.json"),
    )
    parser.add_argument(
        "--quest-helper-results",
        type=Path,
        help="optional TEST-com.questhelper.PrerequisiteDumpTest.xml to verify",
    )
    args = parser.parse_args()

    errors, raw, mapped, quests, edges, states = validate(
        load(args.prerequisites),
        load(args.quest_cards),
        load(args.source_evidence),
        args.quest_helper_results,
    )
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        raise SystemExit(1)
    print(
        f"Validated {raw} raw rows, {mapped} mapped rows, {quests} quest entries "
        f"and {edges} prerequisite edges "
        f"({states['FINISHED']} finished, {states['IN_PROGRESS']} started)."
    )


if __name__ == "__main__":
    main()
