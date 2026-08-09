# Bronzeman TCG upstream review build

Prepared 2026-08-08 as an offline, unofficial review series. Nothing in this
branch has been pushed, published, installed, or submitted upstream.

## Review base and public-state boundary

- Repository: <https://github.com/Felmeme/bronzeman-tcg>
- Review base: public `main` at
  `2bb41edd59c98bd1352c4980c9e0d82dc749e220` (`0.2.17`).
- Public comparison branch: `0.3.0` at
  `4eafbc44bd67164ffe97a3b70a2177df5d8ac524`.
- At the initial and final public checks, those were the only public branches,
  there were no open pull requests, and the Plugin Hub manifest still pinned the
  same public `main` revision.
- Open issues at the final check were #9 (NPC hiding feature), #18 (Rune Essence),
  and #19 (Clay Crafting, Thieving). None contains this patch series.
- Public `0.3.0` is seven commits ahead and 39 behind `main`, with merge base
  `0b6075d6d35d05dcec9b246667a2c58aca6e6210`.

Only public GitHub state was available. Private branches, local developer builds,
drafts, conversations, and unpublished work cannot be seen, so this review makes
no inference about them.

## Series at a glance

| Commit | Classification | Main | Public `0.3.0` |
|---|---|---|---|
| `febbe2c` Fix audited non-combat quest NPC access | confirmed data/index omission | clean independent cherry-pick | clean cherry-pick |
| `182aaf1` Add audited combat quest NPC requirements | confirmed quest-data omission | clean independent cherry-pick | clean cherry-pick |
| `7399e31` Block restricted SkillMulti keyboard shortcuts | reproduced event-path defect | clean independent cherry-pick | conflict in `BronzemanTcgPlugin.java` |
| `1e7e69f` Check exact Sailing upgrade recipe materials | confirmed recipe-policy/source mismatch | clean independent cherry-pick | conflict; `SidePanelSettingMetadata.java` is absent and the branch architecture differs |
| `46f3aea` Enforce Hunter rules when laying ground traps | owner-reproduced event-path defect | clean independent cherry-pick | conflict in `BronzemanTcgPlugin.java` |
| `1cb6667` Enforce ground log lighting restrictions | owner-reproduced event-path defect | clean independent cherry-pick | clean cherry-pick |
| `535287f` Require Stardust for crashed-star mining | confirmed mining-node data omission | clean independent cherry-pick; focused test passed | clean cherry-pick; focused test passed |

Every behavioral commit was also applied independently to the recorded public
`main`; none depends on another fix commit. A conflict against public `0.3.0`
means the idea needs a branch-native port, not that the public branch already
contains the fix.

The normal upstream command `gradlew clean test build --warning-mode all` passes
on the aggregate series: 77 tests, 0 failures, 0 errors, 0 skipped. The only
compiler note is the repository's pre-existing unchecked-operation note in
`BronzemanTcgPluginTest.java`.

## `febbe2c` — non-combat quest NPC associations

**Symptom and reproduction.** The quest NPC index was derived only from enemy
requirements in `quest_cards.json`. The pinned audit therefore found 48 confirmed
non-combat conversation/step NPC associations absent from the index. In addition,
two combat NPCs used to start quests needed the existing pre-start fail-open
behavior. With NPC restrictions active, an absent association could hide or block
a required conversation, while putting a conversation NPC in `Enemies` would
misrepresent the quest checklist.

**Expected behavior.** Audited non-combat quest NPCs remain available while their
associated quest is relevant. The 15 first-step associations must remain reachable
before RuneLite can report that the quest has started.

**Root cause.** `QuestNpcIndex` had no non-combat association source. Enemy
requirements are not a safe substitute for conversation/step metadata.

**Change.** Adds a small, separately sourced association catalogue, loads it into
the existing index, and retains the current fail-open model for `startsQuest`
entries. The JSON pins:

- quest audit `MisterTriangle/bronzeman-quest-npc-audit@2bbeeb361a6c74f5366e7fa662619aed5b4f6269`;
- Quest Helper `5ea99d5ea9ba3fb096ebe7b5ed02d80883e9819d`;
- Bronzeman `2bb41edd59c98bd1352c4980c9e0d82dc749e220`.

**Files.** `QuestNpcAssociationCatalog.java`, `QuestNpcIndex.java`,
`quest_npc_associations.json`, and `QuestNpcAssociationCatalogTest.java`.

**Before/after.** Before, only enemy-derived NPCs were indexed. After, 50 audited
quest/NPC pairs are loaded (48 omissions plus two starter exceptions), 15 are
explicit starter exceptions, duplicate pairs are rejected by the test, and
conversation NPCs do not appear as quest enemies.

**Tests and risk.** Focused tests verify counts, representative pairs, starter
flags, and uniqueness. Main and `0.3.0` both accept the commit cleanly. The
developer judgment is whether always showing the 15 starter NPC names is the
right approximation; the current index has no first-step progress state, so this
uses the existing safe reachability convention.

## `182aaf1` — audited combat quest NPC requirements

**Symptom and reproduction.** Four source-confirmed combat encounters were absent
from the quest requirements: Menaphite Shadow in Beneath Cursed Sands, Viyeldi in
Legends' Quest, Mogre in Skippy and the Mogres, and Metzli in The Final Dawn.
Readiness could therefore omit a required enemy card.

**Expected behavior.** These encounters appear under their quests' enemy
requirements and use the existing enemy-card semantics.

**Root cause.** Exact data omissions in `quest_cards.json`, distinct from the
non-combat indexing problem above.

**Files.** `quest_cards.json` and `QuestCatalogTest.java`.

**Before/after.** Before, all four requirements were absent. After, each exact NPC
card is required by the matching quest. No other quest metadata changes.

**Tests and risk.** The focused catalogue test finds every new enemy requirement.
The commit cherry-picks cleanly to both public branches. The remaining developer
judgment is ordinary audit acceptance; no runtime mechanism changes.

## `7399e31` — SkillMulti keyboard enforcement

**Symptom and reproduction.** Mouse clicks on a restricted product pass through
`MenuOptionClicked` and are consumed, but `1`–`9`, `0`, `A`–`H`, and Space can
select the same product through the SkillMulti client script without producing
that menu event. Joshua's standalone cooking proof reproduced product-specific
Salmon blocking and showed two false-positive traps: globally blocking when any
locked product was visible, and mistaking normal chatbox state for quantity entry.

**Expected behavior.** Only the shortcut assigned to a currently visible,
restricted product is consumed. Space follows only the remembered previous
selection. Digits entered at the real `Enter amount (` prompt, other product
shortcuts, Enter, Escape, and unrestricted products remain usable.

**Root cause.** The plugin explicitly relied on the menu-click pipeline, while the
game's SkillMulti shortcuts run through keyboard/client-script handling. The
mouse-only restriction decision was not reusable from a `KeyListener`.

**Change.** Registers a RuneLite `KeyListener`, snapshots the exact 18
`InterfaceID.Skillmulti.A` through `.R` product widgets each `ClientTick`, maps the
live shortcut order `1234567890ABCDEFGH`, and uses
`VarPlayerID.SKILLMULTI_PREVIOUSSELECTION` for Space. Visible widget item IDs,
listener arguments, text, names, actions, and bounded descendants are resolved to
the existing node/recipe evaluators. Recipe evaluation is factored so mouse and
keyboard share one decision. A visible `InterfaceID.Chatbox.MES_TEXT` beginning
`Enter amount (` disables shortcut interception while quantity input is active.

Source contract checked against RuneLite
`cde10d2886d6899f1906908a5e7eadbb6cf740e3`, including
`InterfaceID`, `MenuOptionClicked`, and `KeyManager`. The standalone proof resolved
RuneLite client `1.12.35`, and Joshua's controlled test validated the displayed
product-specific shortcut mapping.

**Files.** `BronzemanTcgPlugin.java`, `MakeInterfaceKeyboardPolicy.java`, and
`MakeInterfaceKeyboardPolicyTest.java`.

**Before/after.** Before, keyboard product selection bypassed the same restriction
that blocked the mouse. After, a key is consumed only when its resolved product's
existing policy reports missing cards; keyboard input is never synthesized or
replayed.

**Tests and risk.** Pure tests cover all slot boundaries, top-row/numpad/letter
mapping, the exact quantity-prompt guard, and the existing Salmon → Raw salmon
Cooking rule. Aggregate compile/tests pass. A final in-game check is still needed
for RuneLite key-event ordering and unusual widget layouts; this build does not
claim end-to-end verification. Public `0.3.0` needs a manual port because its
plugin class conflicts.

## `1e7e69f` — exact Sailing upgrade inputs

**Symptom and reproduction.** The existing Sailing interface rule treated a
generic OSRS TCG output/part name as the gate. That does not model what the game
actually consumes: for example, an Oak raft hull consumes Oak logs, Rope, and
Swamp tar; an Oak skiff consumes Oak hull parts, Iron nails, and Swamp tar; an Oak
sloop consumes Large oak hull parts, Iron nails, and Swamp tar.

**Expected behavior.** When the Sailing widget supplies its icon item ID, the
selected boat/tier/part recipe is evaluated against its actual consumed material
cards. `Parts` checks the primary consumed material; `Parts + Materials` and the
compatibility `Everything` value check every consumed material. `Off` remains off.

**Root cause.** The handler converted the clicked icon to an output item name and
ran a broad node rule whose roles did not distinguish boat size or exact recipe.
The policy labels also described outputs rather than inputs.

**Change.** Adds a 63-entry ID catalogue covering 21 installed hulls, 14 installed
keels, and 28 standard/large parts recipes. The exact-ID path runs before the
legacy product-name fallback. Current OSRS Wiki recipe data was checked on
2026-08-08, including <https://oldschool.runescape.wiki/w/Oak_hull>.

**Files.** `BronzemanTcgConfig.java`, `BronzemanTcgPlugin.java`,
`SailingUpgradeRecipeCatalog.java`, `SidePanelSettingMetadata.java`, and
`SailingUpgradeRecipeCatalogTest.java`.

**Before/after.** Before, a matching event could demand a non-consumed output card
and omit nails/tar/reinforcement materials. After, recognised icon IDs use the
exact raft/skiff/sloop or keel/parts inputs. The old name path remains as a
compatibility fallback for events without a recognised ID.

**Tests and risk.** Tests assert all 63 IDs and representative Oak, higher-tier,
workbench, nail, tar, Lead, and Cupronickel requirements. The data is high
confidence; live paid `Pay`/`Install` flows that expose no usable item ID are not
solved by this commit and require sanitized event capture. Public `0.3.0` needs a
branch-native port. Facilities such as the Bronze salvaging hook are deliberately
excluded because there is no Bronze-hook card and upstream policy is unresolved.

## `46f3aea` — ground Hunter trap placement

**Symptom and reproduction.** With Hunter `All Cards`, `Drop` a Bird snare and
then select the ground item's `Lay` option. The trap is placed even when the
inventory `Lay` rule would report missing area creature/loot cards.

**Expected behavior.** Ground `Lay` uses the same Hunter activity rule as inventory
`Lay`. `Drop` remains an allowed disposal action, and the check is independent of
the generic Ground Items/Take setting.

**Root cause.** Ground-item menu hiding and click handling were written for loot:
ordinary non-`Take` options returned before `ResourceNodeCatalog` evaluation.
The ground interaction therefore never reached `evaluateAreaTrapRule`.

**Change.** Recognises only the exact normalized ground option `Lay`, resolves the
ground item name, and reuses the existing inventory activity rule in both menu
hiding and click consumption before normal Take handling.

**Files.** `BronzemanTcgPlugin.java` and `HunterResourceDataTest.java`.

**Before/after.** Before, Drop → ground Lay bypassed Hunter policy. After, Bird
snare (and the already-catalogued Box trap) ground placement receives the same
mode/area evaluation as its inventory activity rule; Take and telegrab behavior is
unchanged.

**Tests and risk.** Focused tests verify tagged/plain `Lay` normalization and the
Bird snare activity rule. Joshua's ground placement proves the event family, but a
captured RuneLite event fixture and a controlled inventory-Lay game check would
strengthen coverage. Public `0.3.0` conflicts in the shared plugin handler.

## `1cb6667` — dropped-log Firemaking enforcement

**Symptom and reproduction.** Joshua confirmed: own the Tinderbox card, keep the
log card locked, and use Tinderbox on that log in inventory—the route is blocked.
Drop the same locked log and choose ground `Light`—the fire is lit.

**Expected behavior.** For logs already covered by a Tinderbox recipe, ground
`Light` must honor the same pinned Tinderbox-only Firemaking setting and also the
separate Item Usage lock on the log. This does not change the configured rule into
a general “all log cards required” Firemaking policy.

**Root cause.** Ground `Light` is a plain ground-item operation. It is neither the
inventory item-on-item Tinderbox recipe nor an inventory item operation, so it
bypassed both existing guards. This resembles Hunter only at the routing level;
it needs recipe evaluation plus Item Usage, not a Hunter node rule.

**Change.** Intercepts only plain ground-item `Light` for items that already have
a `Tinderbox` item-on-item recipe. It applies the Tinderbox-only recipe decision,
then independently applies Item Usage to the ground log. Matching menu entries are
hidden under the same conditions. Take, Lay, telegrab, uncovered ground items, and
other options are untouched.

**Files.** `BronzemanTcgPlugin.java` and `GroundFiremakingPolicyTest.java`.

**Before/after.** Before, a locked log escaped Item Usage after being dropped.
After, Joshua's exact unlocked-Tinderbox/locked-log case is blocked on the ground;
when Item Usage is off, the existing Tinderbox-only recipe policy remains intact.

**Tests and risk.** Tests prove tagged/plain `Light`, exclusion of Take/Lay, and
limitation to existing Tinderbox recipes. Main and public `0.3.0` accept the
commit cleanly. A synthetic RuneLite event fixture is still absent, so a final
in-game confirmation is recommended.

## `535287f` — Crashed Star Stardust rule

**Symptom and reproduction.** Under Mining `Tool + Ore`, `Mine` on a Shooting
Stars object named `Crashed Star` proceeds without the existing Stardust card.

**Expected behavior.** `Tool + Ore` checks Stardust plus the existing carried
pickaxe policy. `Tool Only` ignores Stardust, and `Off` remains unrestricted.
`Prospect` must stay available.

**Root cause.** The game-object handler already passes name, option, and tier ID to
the shared catalogue, but no Crashed Star node existed. A null lookup returns
allowed before Mining mode is evaluated. This is a fixable coverage omission, not
a RuneLite limitation; public history does not prove whether it was intentional.

**Change.** Adds one name-based Mining object rule: `Crashed Star` / `Mine` →
`Stardust`. All nine RuneLite object IDs—41020, 41021, and 41223 through 41229—share
that display name, so one fallback rule covers tier transitions without special
engine code. RuneLite object constants were checked at
`cde10d2886d6899f1906908a5e7eadbb6cf740e3`.

**Files.** `resource_nodes.json` and `CrashedStarMiningDataTest.java`.

**Before/after.** Before, both click consumption and menu hiding found no rule.
After, the existing Mining evaluator enforces Stardust in `Tool + Ore`; no rule is
registered for `Prospect`.

**Tests and risk.** Tests check the shared rule through every tier ID, missing and
owned Stardust states, and unrestricted Prospect. Focused tests pass after clean
independent cherry-picks to both public branches. The only developer judgment is
whether Shooting Stars should be an explicit exception to the established
card-backed Mining-yield pattern.

## Documentation commit — review ledger and order

**Symptom/reproduction.** No runtime symptom; this is the required non-behavioral
review handoff for the seven fix commits.

**Expected behavior and root cause.** It intentionally changes no plugin behavior.
The patch series needed a self-contained ledger and a safe suggested review order
that did not depend on the separate coordination repository.

**Files and before/after.** Adds only `UPSTREAM_REVIEW.md` and `REVIEW_ORDER.md`.
Before, the branch contained the fixes without developer-facing context. After,
every fix records its evidence, behavior, risk, tests, and branch compatibility.

**Tests, risk, compatibility, and judgment.** No code test is specific to this
commit; the complete build remains green. It applies as ordinary new Markdown on
both public branches, has no runtime risk, and can be dropped if review documents
are delivered out of tree instead.

## Items reviewed but deliberately not changed

- **Rune Essence, public issue #18 — unresolved/public overlap.** The issue is
  confirmed against 0.2.17, but generic altar naming and the precise carried
  essence/event route still need sanitized runtime evidence before changing the
  catalogue or evaluator. This series does not speculate.
- **Soft clay/Crafting and Thieving, public issue #19 — active public overlap.**
  The owner-discovered omissions overlap a newly opened upstream issue and the
  broad Skill Access inventory. Exact runtime IDs/interaction collisions have not
  all been proved, so no blanket data import was made.
- **Broader Skill Access omission inventory — regression corpus.** It remains
  useful for focused reproductions, but source-name similarity alone is not enough
  to add hundreds of rules.
- **Sailing facilities, including Bronze salvaging hook — policy unresolved.**
  The hook has material inputs but no matching Bronze-hook card, and the existing
  Salvaging toggle governs shipwreck output rather than facility construction.
- **Sailing Pay/Install without an item ID — technical evidence incomplete.** The
  exact recipe catalogue is ready, but an event/component mapping still needs a
  live sanitized capture if the initiating widget exposes no stable icon ID.
- **Quest-audit nested false positives — no defect.** Grip, Jonny the beard, and
  Weaponsmaster findings were corrected/classified by the audit rather than added
  as requirements.
- **GIM trading and quest reward claims — separate product/modeling concerns.**
  These do not establish a Bronzeman runtime defect and belong to the companion
  website's readiness model.
- **Other keyboard surfaces — known limitation.** This commit handles the exact
  standard SkillMulti shortcuts only; it does not claim generic interception of
  every custom interface or Grand Exchange keyboard behavior.

## Offline verification

From a clean checkout of the recorded `main` revision, apply any desired fix
patch independently, or apply the numbered series in order, then run:

```powershell
.\gradlew.bat clean test build --warning-mode all
```

The retained JAR is an explicitly unofficial local review artifact. It must not be
installed or distributed as an upstream release.
