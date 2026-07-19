# Plan: NPC Visibility dropdown (settings consolidation, phase 1)

## REFINEMENT (owner review of first implementation, 2026-07-19)
1. Tier renames (constants AND labels - nothing has shipped, so both are free):
   PREVENT_COMBAT -> PREVENT_COMBAT ("Prevent Combat"),
   PREVENT_INTERACTION -> PREVENT_INTERACTION ("Prevent Interaction"). HIDE unchanged.
2. Menu-option hiding becomes INHERENT to the tiers: the NPC branch of
   shouldHideEntry no longer sits behind the hideLockedOptions toggle.
   Prevent Combat always hides Attack; Prevent Interaction always hides
   everything but Examine (scope confirmed: everything, not just
   attack/talk-to). hideLockedOptions survives gating ONLY the non-NPC
   stripping (ground items, objects, inventory) until phase 3; its
   description must drop the NPC wording.
3. Click-blocking stays as backup behind the hidden options in all tiers
   (keyboard flows, missed menu passes) - confirmed.
4. Hide NPCs already matches the old entity+clickbox removal; no change.
5. Confirmed: Prevent Interaction blocks item-on-NPC as well as casts (the
   item allowance exists only in Prevent Combat). Note this is click-consume
   only - "Use item ->" can't be menu-hidden since the option belongs to the
   item, not the NPC.

Agreed 2026-07-18. Goal: no quest NPC is ever hidden or unreachable on default
settings, and three NPC toggles collapse into one dropdown. Phase 2 (separate
plan) adds quest-state awareness; phase 3 revisits non-NPC option hiding.

## New model

One dropdown, `npcVisibilityMode`, General Settings position 1, enum
`NpcVisibilityMode`:

| Value | Label | Behaviour for card-locked NPCs |
|---|---|---|
| OFF | Off | Unrestricted. |
| PREVENT_COMBAT | Prevent Combat | **Default.** Attack option hidden + blocked; offensive spell casts blocked; **item-on-NPC ALLOWED** (quest interactions); Talk-to and all other options untouched. |
| PREVENT_INTERACTION | Prevent Interaction | Every menu option removed/blocked except Examine. |
| HIDE | Hide NPCs | NPC renderables removed (current hide behaviour); implies Prevent Interaction for anything still clickable. |

Tint: `tintLockedNpcs` toggle SURVIVES (default on), suppressed in HIDE mode
as today. Outline colour/width/feather stay.

### Spell vs item split (the "Disable Attack isn't the best name" resolution)
`WIDGET_TARGET_ON_NPC` currently blocks casts and item-uses with one flag
(BronzemanTcgPlugin ~1633). The code already distinguishes them elsewhere
(`CAST_PREFIX` + `isSelectedWidgetSpell()`, ~1010). PREVENT_COMBAT blocks the
spell case only; PREVENT_INTERACTION blocks both.

### Exceptions (unchanged by the dropdown)
- **Slayer masters**: governed solely by the Slayer section rules in every
  tier — the generic option-stripping must skip them (owner's explicit rule).
- **Pickpocketing / Master Farmer**: thieving section still governs the
  Pickpocket option itself. NOTE: in PREVENT_INTERACTION, the generic sweep removes
  Pickpocket on locked NPCs too (all-but-Examine is the tier's meaning) —
  accepted, it's the harsh tier.
- **CotS `allowCotsGuards`**: survives until phase 2's quest-state override.
- **LMS / unreadable-state stand-down**: applies to every tier as today.

## Settings removed
- `restrictAttacks` (boolean, General 1) — replaced by dropdown.
- `restrictSpellCasts` (boolean, General 2) — absorbed into tier semantics.
- `hideLockedEntities` (boolean, Visuals) — becomes the HIDE tier.
- `hideLockedOptions` — KEPT for ground items/objects/inventory, but its NPC
  branch now obeys the dropdown instead. Revisit in phase 3.

## Migration (one-time, `npcVisibilityMigrated` hidden flag, exempt-list pattern)
Read the OLD stored values raw via ConfigManager before defaults interfere:
- `hideLockedEntities` == true            -> HIDE
- else `restrictAttacks` stored as false  -> OFF
- else                                    -> PREVENT_COMBAT
Nothing maps to PREVENT_INTERACTION (new severity). After mapping, unset the retired
keys. `restrictSpellCasts=false` users: their cast-freedom is lost only in
tiers >= PREVENT_COMBAT; accepted (announce in Discord).

## Code touchpoints
- NEW `NpcVisibilityMode.java` (4-value enum, house style).
- `BronzemanTcgConfig`: dropdown replaces two General items; remove the
  hideLockedEntities item; hidden migration flag.
- `BronzemanTcgPlugin`:
  - `addEntity` (~450): `hideLockedEntities()` -> `mode == HIDE`.
  - `shouldHideEntry` NPC branch (~530): tier-dependent option stripping,
    slayer-master exemption.
  - block switch (~1617): NPC_*_OPTION attack -> tier >= PREVENT_COMBAT;
    non-attack options -> tier >= PREVENT_INTERACTION; WIDGET_TARGET_ON_NPC -> spell
    check + tier logic per table.
  - `migrateNpcVisibility()` called from startUp.
- `BronzemanTcgOverlay` (~51): `hideLockedEntities()` -> `mode == HIDE`.

## Test plan (dev client)
1. Migration: set each old combo on a profile, update, confirm mapped tier.
2. PREVENT_COMBAT (default): locked Man - Attack gone/blocked, Talk-to works,
   Use-item-on works, Cast Wind Strike blocked. Tint visible.
3. PREVENT_INTERACTION: locked Man - only Examine remains; slayer master Talk-to
   still present with slayer rules active.
4. HIDE: NPC invisible, tint off, no orphan clickboxes.
5. OFF: everything works on locked NPCs.
6. LMS: all tiers stand down in a match.
7. Quest smoke test: a quest item-on-NPC interaction on a carded NPC works
   on defaults.

## Out of scope (later phases)
- Quest-state override (kill-required NPCs attackable while quest active).
- Non-NPC option-hiding consolidation.
- Retiring allowCotsGuards.
