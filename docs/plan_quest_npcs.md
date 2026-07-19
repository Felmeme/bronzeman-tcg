# Plan: Quest NPC override (phase 2 - quest-state awareness, no new settings)

Agreed 2026-07-19. Owner's model: quest progression is the permit. No toggle -
players who want extra restriction simply choose not to interact (honor
system, consistent with the plugin's other accepted honor cases).

## The rule (applies automatically, every mode)

For a card-locked NPC that appears in our quest data:

| Quest state (best across all quests referencing the NPC) | Behaviour |
|---|---|
| NOT_STARTED | Normal restriction - hidden in Hide mode, swept in Prevent Interaction, exactly as any locked NPC. |
| IN_PROGRESS or FINISHED | **Shown + quest interactions**: visible in Hide mode; option sweep relaxed to Prevent Combat treatment (Talk-to, Mark, item-on-NPC work). **Attack stays card-gated always** (owner's explicit rule). |

Additionally, quest-interaction NODE rules (today only `quest-cots` / the
Guards' Mark option) waive their card requirement while their quest is
IN_PROGRESS - the generalised, automatic version of the old CotS toggle.

- OPEN (owner to confirm): does the relaxation apply in Prevent Interaction
  mode too, or Hide mode only? Plan assumes BOTH (the "no quest bricked by
  default settings" goal implies it), flagged for confirmation.
- OPEN (owner to confirm): node-rule waiver during FINISHED as well as
  IN_PROGRESS? Plan assumes IN_PROGRESS only (Mark is meaningless
  post-quest; keeps the waiver narrow).

## Retired
- `allowCotsGuards` config item + all four "guard" literal hooks (addEntity,
  two sweep exemptions, quest-cots case). Key unset in the one-shot
  migration. Guards behave per the general rule: CotS in progress -> visible
  + markable; not started -> normal locked Guard.

## Mechanics

1. **Quest -> NPC mapping**: derived at startup from quest_cards.json
   monsterCards (394 cards across 133 quests; card name -> NPC name via the
   existing bracket-stripping convention) plus quest-linked node categories
   (quest-cots -> "guard"). Built into npcName(lower) -> List<Quest> map.
2. **Quest enum matching**: our quest names (wiki-derived) matched to
   RuneLite's Quest enum by normalised getName(). Unmatched quests are
   logged at startup and their NPCs fall back to ALWAYS SHOWN (fail-open:
   a mismatch must never brick a quest; over-showing is the safe error).
   Miniquests not in the Quest enum fall into the same fail-open bucket.
3. **State caching**: Quest.getState(client) reads varps - too costly for
   addEntity (per frame per NPC). A cached Set<String> of currently-shown
   quest NPC names is recomputed on a GameTick cadence (every ~5 ticks,
   piggybacking the existing tick work) and on login. addEntity and both
   sweep paths consult only the cached set.
4. **Alias gap (known, accepted)**: NPCs fought under different names than
   their card (Cuthbert etc., backlog) won't match until aliases land -
   they behave as non-quest NPCs. Backlog item unchanged.

## Code touchpoints
- QuestCatalog: expose quest name -> monsterCards (already loaded).
- NEW small QuestNpcIndex (or plugin-internal): the mapping + cached
  shown-set + tick recompute.
- BronzemanTcgPlugin: addEntity (consult cache), shouldHideEntry +
  isRestrictedNpcInteraction (strict-sweep exemption becomes cache lookup;
  attack stripping still applies), evaluateNodeRule quest-cots case
  (state-driven waiver), migration (unset allowCotsGuards), remove toggle
  hooks.
- BronzemanTcgConfig: remove allowCotsGuards item; NPC restriction
  description gains a line about quest NPCs.

## Test plan
1. Hide mode, CotS not started, no Guard card: Guards hidden.
2. Start CotS: Guards appear; Mark works without the card; Attack (on
   attackable quest NPCs elsewhere) still blocked without card.
3. Finish a quest whose kill-NPC card is unowned: NPC visible (FINISHED
   shows), attack blocked, talk works.
4. Prevent Interaction mode + quest in progress: Talk-to survives on that
   quest's NPCs (pending OPEN #1 confirmation).
5. Unmatched-quest fallback: verify startup log lists mismatches and those
   NPCs are visible.
6. Old allowCotsGuards users: key unset, behaviour equivalent-or-better.

## Out of scope
- Quest walkthrough mining for more interaction nodes (backlog; the
  quest-cots pattern now generalises when they arrive).
- Alias resolution (backlog).
