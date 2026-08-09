# Suggested review order

The patch series is based on public `main` `2bb41ed`. Review documentation is
supplied outside the behavioral `format-patch` series so an old interim docs
commit does not create noise.

## Foundation and exact quest cases

1. `7399e31` — SkillMulti keyboard policy (foundation for keyboard case proofs).
2. `f54b34e` — exact direct-target Item Usage policy.
3. `e902070` — Temple of the Eye Weak cell `Place-cell`/`Assemble` rules; depends
   on `f54b34e`.
4. `e590333` — Forsaken Tower exact existing keyboard rule proof; depends on
   `7399e31`, no behavior data change.
5. `8251c90` — Zogre singular/plural shaft keyboard proof; depends on `7399e31`.
6. `26e9fb8` — Sheep Shearer Wool/Ball of wool keyboard proof; depends on
   `7399e31`.
7. `9e5c175` — optional Celastrus bark → Battlestaff recipe; keyboard protection
   depends on `7399e31`, mouse recipe enforcement does not.
8. `bb9b589` — Recruitment Drive bidirectional item-on-item regression proof;
   intentionally adds no restriction.

## Quest NPCs

9. `febbe2c` — shared noncombat association catalogue/loader.
10. `cea6842` — required starter-state correction; depends on `febbe2c`.
11. `3a780e0` — Avan/Ulsquire exact runtime-name aliases; depends on `febbe2c`.
12. `182aaf1` — four combat quest requirements; independent of the noncombat
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
