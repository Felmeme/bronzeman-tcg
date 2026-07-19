# Plan: Skills sweep to 0.3.0 (master roadmap, agreed 2026-07-19)

Owner decisions:
- ORDER: broken first, then missing rules, then test passes.
- CARD-GAP POLICY (standing): where intermediates have no cards (unstrung
  bows, arrow tips...), requirements fall back to the NEAREST CARDED items;
  uncarded things never gate anything. Consistent with untracked-never-
  restricted. Genuine gap lists still go to the upstream card-gap report.
- WORKFLOW: assistant implements everything this sweep (plan-first per
  session, owner tests + commits). Owed lessons (fletching dropdown,
  resource_nodes editing) RESUME AFTER 0.3.0.
- 0.3.0 BAR: every skill fixed AND owner-tested in-game. Only externally
  blocked items (Time Tracking interop, upstream cards) may carry over as
  documented exceptions. Releases stay 0.2.x per session batch until then.

Each session: open with that skill's mini-plan + owner quiz (symptoms,
option strings, difficulty knobs), implement, owner test pass, 0.2.x release.

## Phase 1 - BROKEN
1. **Fishing** - "ALL setting currently not fully blocking spots." Session
   starts with diagnosis: which spots/settings leak (owner to reproduce with
   node-lookup debug log). Also carries the owner's WIP: dark crab re-add +
   lobster/dark-crab split by NPC ID (see backlog note).
2. **Sailing** - build not gated at the signboard (only by item acquisition);
   plan: intercept the signboard build interface. Salvaging broken (option
   strings were always GUESSED - capture real ones in-game, see DEFERRED
   sailing notes). Bounties, cannon combat untested/unscoped - quiz owner on
   intended rules before adding.
3. **Fletching** - apply nearest-carded policy to the 35 recipes (knife+log
   -> interface triggers, two-stage bow chain, arrows without tip cards);
   regenerate recipe data; add the FletchingMode difficulty dropdown
   (assistant-implemented now, teaching resumes post-0.3.0).

## Phase 2 - MISSING RULES
4. **Thieving: Varlamore** - Wealthy Citizens gated behind House Keys card;
   owner wants a 3rd difficulty option + better feedback (quiz at session).
5. **Agility** - ticket-granting courses (Brimhaven) unblocked; Sepulchre
   needs Hallowed Mark requirement. New category likely; quiz on which
   courses beyond these two.
6. **Construction** - currently only gated via item acquisition; needs
   build-interface gating (plank/product cards?). Design session - quiz on
   desired depth (rooms? furniture? flatpacks?).
7. **Farming: harvesting** - tie harvest locks to Time Tracking plugin state
   (read its ConfigManager like osrs-tcg's) to know patch contents. If
   interop proves unstable, documented-exception candidate.

## Phase 3 - TEST PASSES (owner in-game, assistant fixes fallout same session)
8. **Hunter** - full pass, implings first (owner flagged them "particularly").
9. **Slayer** - points/reset flows untested; RULING NEEDED: NPC Contact
   (spellbook) currently lets players talk to locked masters - block or
   accept as honor-system? Also GotR under Runecrafting.
10. **Runecrafting** - GotR untested (portal, deposits, rewards?).
11. **Smithing** - Giant's Foundry unexamined; re-verify Blast Furnace.
12. **Mining** - Duke/Weiss/Calcified Deposits etc: research agent checks
    which have cards + node coverage, then spot-test.
13. **Herblore / Crafting / Firemaking (non-tinderbox paths)** - grouped
    verification session; expected mostly-working.
14. Woodcutting, Cooking - DONE, no session.

## Standing notes
- Every fix session ships as a 0.2.x patch with changelog entry.
- Data research (mining specials, agility courses, GotR, foundry) goes to
  background agents with the wiki-etiquette rules and exact-match validation.
- Known cross-cutting backlog that may surface during sweep: Cuthbert/Metzli
  aliases, upstream card-gap report, U+FFFD name encoding fix.
