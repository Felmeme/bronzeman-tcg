# Suggested review order

The patch series is based on public `main` `2bb41ed`. Review documentation is
supplied outside the behavioral `format-patch` series so an old interim docs
commit does not create noise.

## Foundation and exact quest cases

1. `95ae56f` — SkillMulti keyboard policy (foundation for keyboard case proofs).
2. `8790f68` — exact direct-target Item Usage policy.
3. `a95fde1` — Temple of the Eye Weak cell `Place-cell`/`Assemble` rules; depends
   on `8790f68`.
4. `c9a0889` — Forsaken Tower exact existing keyboard rule proof; depends on
   `95ae56f`, no behavior data change.
5. `321ad46` — Zogre singular/plural shaft keyboard proof; depends on `95ae56f`.
6. `0001bf9` — Sheep Shearer Wool/Ball of wool keyboard proof; depends on
   `95ae56f`.
7. `c13cf77` — optional Celastrus bark → Battlestaff recipe; keyboard protection
   depends on `95ae56f`, mouse recipe enforcement does not.
8. `18b5097` — Recruitment Drive bidirectional item-on-item regression proof;
   intentionally adds no restriction.

## Quest NPCs

9. `86fe0cc` — shared noncombat association catalogue/loader.
10. `030a496` — required starter-state correction; depends on `86fe0cc`.
11. `8b2ae81` — Avan/Ulsquire exact runtime-name aliases; depends on `86fe0cc`.
12. `091e553` — four combat quest requirements; independent of the noncombat
    catalogue.

Every NPC decision is separately listed in `QUEST_NPC_REVIEW_LEDGER.md`; the
catalogue commit is one shared loader/data family, not 50 undocumented decisions.

## Earlier retained families

Review the remaining commits in chronological patch order. The smallest data-only
families are Crashed Star, mining/woodcutting nodes, Runecrafting object identity,
pottery recipes, ordinary Firemaking recipes, and Lead/Nickel smelting. Sailing,
Hunter, keyboard interception, and ground-item event routing have the broader
runtime surfaces and should receive live in-game fixtures before release.

## Excluded candidates

Do not infer missing rules from the seven quest-item blockers in
`QUEST_ITEM_REVIEW_LEDGER.md`. In particular, no product name was guessed for
Conductor, Bow-sword, Airtight pot, Poison karambwan, or Hair → Rope, and no item
consumption was inferred from Cold War `Use` or Funeral Pyre `Build` menu text.
