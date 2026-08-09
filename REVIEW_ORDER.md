# Suggested review order

All seven behavioral commits were independently cherry-picked onto the recorded
public `main`. They can be accepted, rejected, or reordered independently. The
documentation commit is non-behavioral.

1. `535287f` **Require Stardust for crashed-star mining** — smallest data-only
   omission; all tiers share one public object name.
2. `182aaf1` **Add audited combat quest NPC requirements** — four isolated quest
   data additions.
3. `febbe2c` **Fix audited non-combat quest NPC access** — separate source-backed
   association catalogue; review the 15 starter fail-open flags carefully.
4. `46f3aea` **Enforce Hunter rules when laying ground traps** — small routing
   correction reusing an existing Hunter rule.
5. `1cb6667` **Enforce ground log lighting restrictions** — exact owner-reproduced
   route; check the deliberate split between Tinderbox policy and Item Usage.
6. `7399e31` **Block restricted SkillMulti keyboard shortcuts** — broader runtime
   surface and the most important manual in-game verification target.
7. `1e7e69f` **Check exact Sailing upgrade recipe materials** — the data is exact,
   but paid/customisation routes without usable item IDs still need event capture.

## Drop and branch notes

- Dropping either quest commit does not affect the other.
- Hunter and Firemaking both touch ground-item dispatch, but each was rebuilt and
  tested as an independent `main` cherry-pick; neither requires the other.
- Keyboard and Sailing both touch interface handling, but each independently
  applies to `main`.
- Public `0.3.0` accepts the two quest commits, Firemaking, and Crashed Star
  cleanly. Keyboard and Hunter conflict in `BronzemanTcgPlugin.java`; Sailing
  conflicts with the branch architecture and references
  `SidePanelSettingMetadata.java`, which that branch does not contain. Port those
  three by behavior rather than resolving blindly.
- Review unresolved/public-overlap items in `UPSTREAM_REVIEW.md` before expanding
  any data set. In particular, do not fold the whole Skill Access inventory into
  one patch.

## Fast verification

```powershell
.\gradlew.bat clean test build --warning-mode all
```

Expected for the complete behavioral series: 77 tests, no failures/errors/skips,
and the normal upstream `build/libs/bronzeman-tcg-0.2.17.jar` output. The packaged
copy is unofficial and is not intended for installation or publication.
