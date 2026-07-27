---
name: run-bronzeman-tcg
description: Build, run, validate and debug the Bronzeman TCG RuneLite plugin. Use when asked to run/start/launch the plugin or dev client, build or test it, check or validate the restriction data (recipe_nodes/resource_nodes/quest_cards/important_unlocks), find rule collisions, or work out what cards a given in-game interaction requires.
---

# Run Bronzeman TCG

RuneLite plugin (Java 11 + Gradle). All paths below are relative to the repo root.

**It cannot be driven fully headlessly** — the "app" is a plugin inside the RuneScape client, so
seeing a restriction fire needs a real logged-in account + the OSRS TCG plugin. What you *can*
drive headlessly is the layer nearly every change touches: the **restriction data** and the
**lookup logic** that reads it. That's what `driver.mjs` is for.

## TL;DR

```bash
node .claude/skills/run-bronzeman-tcg/driver.mjs check
```

Run that after **any** data edit. It validates every card name and finds rule-key collisions
(exit 1 = problem). Then, if you touched Java:

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-11.0.31.11-hotspot" && ./gradlew build --no-daemon -q
```

Those two commands cover ~90% of work here. Everything below is detail.

## Prerequisites

Already installed on this machine — verified versions:

| Need | Version here | Used for |
|---|---|---|
| Temurin JDK 11 | 11.0.31 at `C:\Program Files\Eclipse Adoptium\jdk-11.0.31.11-hotspot` | build + client |
| Node | v22.19.0 | `driver.mjs` (zero deps, needs ≥18) |
| Python 3 | 3.13.7 (`py`) | the `scripts/*.py` data generators |

## 1. Agent path — the driver (do this first)

```bash
node .claude/skills/run-bronzeman-tcg/driver.mjs <command>
```

| Command | Does |
|---|---|
| `check` | `validate` + `collisions`. **Exit 1 on any problem.** Use in pre-commit / after data edits. |
| `validate` | Every card reference in every resource must exact-match the card catalogs. |
| `collisions` | Finds lookup keys shared by rules with *different* requirements — the "only the last rule is reachable" bug class. |
| `explain <kind> <name> [target]` | What a given click actually requires. |
| `stats` | Record counts per resource/category. |

`explain` is the fastest way to answer "why did/didn't this block?":

```bash
node .claude/skills/run-bronzeman-tcg/driver.mjs explain object "Young tree" set-trap
node .claude/skills/run-bronzeman-tcg/driver.mjs explain interface "Anchovies"
node .claude/skills/run-bronzeman-tcg/driver.mjs explain item-on-item "Knife" "banana"
```

Kinds: `interface`, `item-on-item`, `item-on-object`, `object`, `npc`, `fishing-spot`, `inventory`.
No match printed = that interaction is **not restricted** (which is often the bug).

**Why trust it:** the driver re-implements both catalogs' key-building. Its key counts match what
the running client reports on startup — `760` recipe keys and `575` node keys, identical to the
client's `Loaded 760 recipe rules` / `Loaded 575 resource node rules`. If those ever diverge, the
driver is out of date with the Java.

## 2. Build

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-11.0.31.11-hotspot" && ./gradlew build --no-daemon -q
```

Silence = success (this runs the unit tests too). Non-empty output that isn't a `Note:` line is a
real failure. `-q` hides noise; drop it if you need the task list.

## 3. Launch the dev client

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-11.0.31.11-hotspot" && ./gradlew run --no-daemon
```

Or double-click `launch-client.bat` (same thing, sets JAVA_HOME itself).

This opens a **real RuneLite window** and blocks. It boots to the login screen; going further needs
the owner's account. Startup takes ~10s to the window, ~45s for all plugins.

**Use `gradlew run` / `launch-client.bat` — not IntelliJ's green ▶ button.** The gradle `run` task
passes `--developer-mode --debug` (see `build.gradle:49`); IntelliJ's auto-generated config passes
no args, which silently costs you sideloaded plugins *and* all debug logging. To fix IntelliJ
instead: Run → Edit Configurations → Program arguments → `--developer-mode --debug`.

Startup lines that confirm a healthy launch (from a real run):

```
RuneLite 1.12.33 (launcher version unknown) starting up, args: --developer-mode --debug
n.r.c.e.ExternalPluginManager - Loading external plugin "bronzeman-tcg" jar "..."
com.bronzemantcg.CardNameCatalog   - Loaded 5149 tracked items from osrs-tcg snapshot
com.bronzemantcg.CardNameCatalog   - Loaded 1198 tracked NPCs from osrs-tcg snapshot
com.bronzemantcg.ResourceNodeCatalog - Loaded 575 resource node rules (45 Master Farmer seeds)
com.bronzemantcg.RecipeCatalog     - Loaded 760 recipe rules from snapshot
n.r.client.plugins.PluginManager   - Loaded plugin BronzemanTcgPlugin
```

Kill it (Windows — Ctrl-C won't reach a GUI child):

```bash
powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { \$_.CommandLine -match 'BronzemanTcgPluginTest|GradleWrapperMain' } | Select-Object ProcessId"
```

then `taskkill //F //PID <pid>` for each. Expect 2+ java PIDs (launcher + client).

## 4. In-game debugging = read the debug log

With `--debug` on, every gated click logs what it looked up. This is the diagnostic handle for
"the rule didn't fire" — it prints the *exact* menu string, which is the thing you must never guess:

| Log line | Tells you |
|---|---|
| `node lookup kind=… name='…' option='…' -> NO RULE` | the key the click produced, and whether any rule matched |
| `interface product raw='…' stripped='…' widgetItemId=…` | the real make-menu product string |
| `local stance: idle=… walk=…` | the local player's weapon-stance animation ids |

Ask the owner to paste those lines; key data off them, never off a guess.

## 5. Sideloading OSRS TCG (needed for real enforcement)

Bronzeman reads its collection from the separate OSRS TCG plugin.

1. Build that repo: `gradlew.bat jar` → `build/libs/runelite-tcg-<ver>.jar`
2. Copy the **jar** to `C:\Users\<you>\.runelite\sideloaded-plugins\` — **top level**. A source
   folder in there is ignored; only `.jar` files directly in that dir load.
3. Launch with `--developer-mode` (see above) — that flag is what enables the sideload scan.
4. Keep exactly one osrs-tcg jar there, or you get duplicate-class conflicts.

## Gotchas

- **Runtime "Loaded N rules" counts KEYS, not JSON records.** 512 recipe records → 760 keys (one
  per trigger target, plus interface catch-alls). Don't "fix" a mismatch against record counts.
- **A shared interface product name must be keyed by target.** `RecipeCatalog` withholds the
  `name|*` catch-all from any interface name used by >1 recipe (the knife menu labels every tier
  "Crossbow stock"). Without that, all tiers collapse to one key and only the last is reachable.
- **The dev build shadows the hub copy**, silently: `Skipping loading "bronzeman-tcg" from hub as a
  conflicting builtin external is present`. You are testing your local build, not the hub one.
- **Data files may contain trailing commas.** Gson tolerates them, `JSON.parse` doesn't — this has
  shipped before (`quest_cards.json`). The driver parses leniently; your ad-hoc scripts should too.
- **Card matching is case-insensitive but encoding-sensitive.** The client runs with
  `-Dfile.encoding=windows-1252`; `consumables.json` currently holds double-encoded `rosÃ©`
  (`c3 a9`) where the catalog holds `rosé` (`e9`), so those 4 names never match. Compare code
  points, not eyeballs.
- **Never rename a config `keyName`** — it's a public contract; renaming wipes players' settings.
  Display names and descriptions are safe to change.
- **Don't rely on `processResources` templating.** The plugin-hub packager doesn't run a plugin's
  custom resource-processing, so a stamped `${version}` token ships raw. (Cost a release once.)
- **Hub `commit=` line must have no trailing whitespace** — the packager demands exactly 40 chars
  and fails with `commit must be a full 40 character sha1sum`.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Sideloaded plugin absent, no debug lines | Launched without `--developer-mode --debug` (IntelliJ ▶). Use `gradlew run`. |
| `driver.mjs` → "Could not locate repo root" | Run it from inside the repo, or it walks up from the script — check you're not in a detached temp dir. |
| Rule "doesn't fire" in game | `explain` the exact kind/name/option from the `node lookup` line. Usually the real menu string ≠ the keyed string. |
| `ERROR com.osrstcg.persist.TcgStateStore - state save verification failed` | The **other** plugin's error, not ours. Known noise. |
| `ERROR o.j.server.WebSocketServer - Shutdown due to fatal error` | wikisync/DPS plugin in the owner's profile. Unrelated noise. |
| Build silent but jar missing | You ran with `-q`; the jar is `build/libs/bronzeman-tcg-<version>.jar`. |

## Known pre-existing findings (as of this skill's writing)

`driver.mjs check` currently exits 1. These are **real, pre-existing** — not caused by your change:

- **`object|young tree|set-trap` — 5 salamander rules share one key**, so only the last (`Tecu
  salamander`) is reachable. Net-trapping *any* salamander demands the Tecu card. Same bug class as
  the old Crossbow stock one; needs the rules split by a distinguishing target.
- **`object|baker's stall|steal-from` — duplicate rule** (`Bread/Cake` vs `Bread/Cake/Chocolate
  cake`). Harmless (last is a superset) but redundant.
- **7 unsatisfiable card names:** `Empty Sacks` + `Unlit candle` (quest_cards / Enlightened
  Journey), the placeholder string `Need to add rest of talismans` sitting in
  `important_unlocks` → Tools/Runecrafting, and 4 double-encoded `consumables.json` food names.

Re-run `check` before and after your edit and compare, so you only own what you changed.
