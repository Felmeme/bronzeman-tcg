# Quest Prerequisite Snapshot

`quest_prerequisites.json` supplies the quest-state half of the side panel's
readiness check. It is informational only: it does not block starting or
progressing quests in game.

## Source and scope

- Source: Quest Helper `4.16.1`, commit
  `5ea99d5ea9ba3fb096ebe7b5ed02d80883e9819d`, matching the pin recorded in
  `quest_cards.json`.
- Extraction: direct, top-level `QuestRequirement` entries returned by each
  helper's `getGeneralRequirements()` after requirement initialization.
- Evidence: 394 top-level rows captured in
  `src/test/resources/quest_helper_prerequisites_source.json` by the checked-in
  `scripts/quest_helper/PrerequisiteDumpTest.java`. Of those, 259 rows map to
  Bronzeman's quest catalogue and collapse to 116 entries with 258 unique
  edges. Of those edges, 254 require a finished quest and four accept an
  in-progress or finished quest.
- Non-quest general requirements such as skills, quest points and spellbooks
  are outside this snapshot. Quest requirements nested inside a mixed
  alternative are also excluded: for example, a skill-or-started-quest option
  is not an unconditional quest prerequisite.

The snapshot retains Quest Helper enum keys for traceability. Nine Recipe for
Disaster helpers with top-level quest requirements are consolidated into the
existing Bronzeman entry. Pirate Pete contributes no top-level edge: its Rum
Deal dependency is nested inside an ironman-only `42 Crafting OR started Rum
Deal` alternative, so treating it as unconditional would create a false
blocker. The Shield of Arrav Black Arm Gang helper resolves to RuneLite's
`Shield of Arrav` quest, and `Vale Totems` resolves to the catalogue's `Vale
Totems (miniquest)` title.

## Runtime behavior

RuneLite quest states are sampled on the client thread with the panel's
existing roughly 15-second quest refresh. `FINISHED` requirements need a
completed quest. `IN_PROGRESS` requirements accept either an in-progress or a
finished quest, matching Quest Helper semantics. Prerequisites appear as their
own checklist section and contribute to quest and category readiness totals;
owning or sharing a similarly named card cannot satisfy them.

## Validation

Run:

```text
python3 scripts/audit_quest_prerequisites.py
JAVA_HOME=/path/to/jdk-11 bash gradlew test --tests com.bronzemantcg.QuestCatalogTest --no-daemon
```

To reproduce the evidence, copy `scripts/quest_helper/PrerequisiteDumpTest.java`
into the matching package of a Quest Helper checkout at the pinned commit, run
that test, and additionally pass its result XML to the audit:

```text
python3 scripts/audit_quest_prerequisites.py \
  --quest-helper-results /path/to/TEST-com.questhelper.PrerequisiteDumpTest.xml
```

The audit checks the source pin, all 394 evidence rows, the exact
394 -> 259 -> 258 normalization, aliases, source keys, states, deterministic
ordering, canonical quest-name resolution, duplicates, self-references and
cycles. The catalogue test additionally verifies that prerequisite names map
to RuneLite runtime quest state and isolates card-ready quests with missing,
started and completed prerequisites.
