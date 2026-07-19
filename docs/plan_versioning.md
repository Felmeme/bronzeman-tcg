# Plan: Version control discipline (agreed 2026-07-19, NOT yet implemented)

State found: version bumped exactly once (0.1.0 at scaffold -> 0.2.0 on
2026-07-16) across ~11 hub releases; everything since, including the settings
overhaul, shipped as "0.2.0". The hub displays the gradle `version` when set
(osrs-tcg shows 0.16.1 this way), so bumps are user-visible.

## Scheme: 0.MINOR.PATCH
- MINOR: any release with new behaviour - features, settings changes, data
  additions that alter gameplay. 0.3.0, 0.4.0...
- PATCH: fixes and corrections only - bugfixes, data typos, description
  wording. 0.3.1, 0.3.2...
- OWNER ADJUSTMENT 2026-07-19: the settings overhaul shipped live as 0.2.0
  before numbering began; it keeps that number. Versioning starts with the
  NEXT release: **0.2.1**, a patch carrying the version machinery plus a
  settings bugfix. 0.3.0 arrives with the next feature release; 1.0.0 is
  reserved for a future deliberate milestone.

## Mechanics
1. **build.gradle `version` is the single source of truth.** The bump lands
   in the SAME commit as the release content, so hub hash and version always
   travel together.
2. **Welcome message shows it**: "[Bronzeman TCG] v0.3.0 - Active - ...".
   Implementation: gradle processResources expands the version into a small
   bundled properties/text resource the plugin reads at startup - no
   hand-maintained constant to drift. (Fallback if the hub's standard build
   fights expansion: a constant in code with a loud comment pairing it to
   build.gradle.)
3. **CHANGELOG.md in the repo root**, newest first, one entry per release:
   version, date, short bullets. Doubles as the source for Discord posts.
   Light backfill only: 0.1.0 (initial hub release), 0.2.0 (quest sweep era,
   with a one-line note that several unversioned updates shipped under it),
   then full entries from 0.3.0 onward.

## Process changes (the part that keeps it consistent)
- Release flow becomes: bump version + changelog entry -> commit (with the
  release) -> push -> `git log -1 --format=%H` -> hub PR -> Discord post
  from the changelog entry. Documented in CLAUDE.md's release-flow section.
- The assistant's handover for ANY release-bound work always includes the
  suggested next version number alongside the suggested commit message, and
  flags whether it's a MINOR or PATCH by the rules above.
- If a release ships without a bump, the next release fixes it - no
  retroactive renumbering, ever (hub pins are immutable).

## Implementation steps (when approved)
1. build.gradle: version 0.2.0 -> 0.3.0 (+ processResources expansion).
2. Plugin: read version resource; prepend "v{version}" to the welcome line.
3. CHANGELOG.md: backfill + 0.3.0 entry (settings overhaul, quest NPCs,
   thieving tiers, item marking, loot data).
4. CLAUDE.md: release flow + "always bump" rule.
5. Ships as a small immediate follow-up release (or folds into the current
   hub PR by re-bumping its commit hash, owner's choice at the time).

## Test
- Build; welcome line shows v0.3.0; hub manifest shows 0.3.0 after release.
