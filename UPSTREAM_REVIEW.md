# Bronzeman TCG upstream review build

Prepared locally on 2026-08-08. This is an unofficial developer review build.
Nothing was pushed, published, installed, submitted, or sent upstream.

## Public boundary

- Repository: `Felmeme/bronzeman-tcg`.
- Public `main`: `2bb41edd59c98bd1352c4980c9e0d82dc749e220`.
- Public `0.3.0`: `4eafbc44bd67164ffe97a3b70a2177df5d8ac524`.
- Those remain the only public branches; there are no open pull requests.
- Open issues are #9, #18, and #19. #18/#19 overlap the Rune Essence and Clay
  review families already disclosed in this series; none overlaps the finalized
  quest-item or quest-NPC corrections.
- Plugin Hub public master is `b6278f4b1c025e04bbc84d4296ff56f873e2db1a`
  and still pins Bronzeman `2bb41ed`.

Only public state is observable. Private branches, local/unpublished builds,
drafts, conversations, and future work cannot be seen and are not inferred.

## Important Task 59 correction

The earlier generic item-on-object and item-on-NPC claims were retracted after the
final handler trace. Public `main` already gates normal inventory `Use`; item-on-item
checks both widgets. The old local commits `c2342fa` and `3f72233` are absent from
this branch and absent from the review patch series. No target object receives an
invented card, and an allowed NPC does not receive a second card check. Direct
target actions such as `Place-cell`, ground `Light`, or trap `Lay` remain separate
event-path families.

## Finalized quest-item result

See `QUEST_ITEM_REVIEW_LEDGER.md` for all 13 decisions. The safe changes are:

- Temple of the Eye exact `Place-cell` and `Assemble` rules require Weak cell
  through Item Usage (`8790f68`, `a95fde1`).
- The existing generic SkillMulti keyboard gate is proved against exact Forsaken
  Tower, Zogre, and Sheep Shearer catalogue rules (`c9a0889`, `321ad46`, `0001bf9`).
- The optional Celastrus bark → Battlestaff route gets one exact Fletching recipe
  (`c13cf77`).
- Recruitment Drive source/destination ordering is already covered; `18b5097`
  makes that invariant directly testable without adding a rule.

No guessed rules were added for Conductor, Bow-sword, Airtight pot, Cold War direct
Use, Poison karambwan, Hair → Rope, or Funeral Pyre Build. Their exact missing
runtime facts and no-fix reasons are in the ledger.

## Finalized quest-NPC result

See `QUEST_NPC_REVIEW_LEDGER.md` for all 54 audited decisions. The shared
association engine (`86fe0cc`) retains 48 card-mapped runtime names, and the four
combat requirements (`091e553`) remain, with two material corrections:

- `030a496` removes the incorrect `startsQuest → alwaysShown` behavior. All 15
  starter rows (13 unique names) now remain card-gated while `NOT_STARTED`; normal
  noncombat quest exemption begins only after quest state changes. This also stops
  King Roald, Arianwyn, Sir Tiffy Cashien, and Prince Itzla Arkan from becoming
  globally exempt because another quest starts through the same NPC.
- Avan and both `Afflicted(Ulsquire)`/`Ulsquire Shauncy` runtime names are
  excluded because the generated OSRS TCG monster catalogue does not map those
  NPCs to cards. The review build no longer invents Man or Afflicted substitutes.

Generic runtime NPC names are an explicit review risk. The ledger marks them.

## Earlier source-backed families retained

The branch also retains the earlier independently reviewable families for Sailing
recipe materials, Hunter ground trap laying, dropped-log Firemaking, Crashed Star
Stardust, exact mining/woodcutting nodes, Runecrafting altar identity and Kourend
bind routes, pottery output handling, ordinary Firemaking routes, and Lead/Nickel
smelting. Firemaking's Tinderbox-only recipe policy remains unchanged; the separate
ground-log Item Usage fix enforces the card-backed dropped log.

## Test/build result

Final aggregate verification uses the normal upstream command:

```powershell
.\gradlew.bat clean test build --warning-mode all
```

The final package records the exact test count, zero-failure result, JAR SHA-256,
and compatibility replay. The JAR is produced only because the normal upstream
build produces one; it is labelled unofficial and is not intended for installation.

## Source pins

- Bronzeman public source: `2bb41edd59c98bd1352c4980c9e0d82dc749e220`.
- Quest Helper audit pin: `5ea99d5ea9ba3fb096ebe7b5ed02d80883e9819d`.
- Quest-NPC audit pin: `2bbeeb361a6c74f5366e7fa662619aed5b4f6269`.
- Final quest-item audit captured 2026-08-08; each ledger row includes its pinned
  Wiki revision or exact machine-evidence identifier.
