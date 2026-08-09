# Quest-NPC review ledger

The noncombat engine is `86fe0cc`; the four combat requirements are `091e553`.
`030a496` corrects starter semantics: a carded starter remains card-gated while
`NOT_STARTED`, then the quest-state association permits noncombat progress after
the quest starts. Attack is never waived. Avan and both Ulsquire runtime names
are deliberately excluded because the generated OSRS TCG monster catalogue has
no card mapping for those NPCs. No substitute NPC card is invented. Item-on-NPC
actions still rely on the initial inventory Item Usage gate for the used item;
there is no invented second NPC-card check.

Evidence: `A01` = quest-NPC audit `01_CURRENT_CARD_NONCOMBAT_MISSED.csv`; `A02`
= `02_CURRENT_CARD_COMBAT_MISSED.csv`; `A05` =
`05_CURRENT_CARD_REQUIRED_TO_START.csv`; `M` = finalized
`entity_menu_options.csv`; `H` = archived less-obvious behavior handoff.

| # | Quest | NPC / action | Decision | Evidence |
|---:|---|---|---|---|
| 1 | A Porcine of Interest | Spria — Talk | Permit noncombat after start | A01/H |
| 2 | Darkness of Hallowvale | King Roald — Talk | Permit after start; no global exemption | A01/H |
| 3 | Darkness of Hallowvale | Vampyre Juvinate — Talk | Permit after start | A01/H |
| 4 | Darkness of Hallowvale | Vyrewatch — Talk | Permit after start | A01/H |
| 5 | Death to the Dorgeshuun | Zanik — Ask/Talk | Permit after start | A01/H |
| 6 | Devious Minds | Monk — starter Talk | Card-gated pre-start; permit after start; generic-name scope noted | A01/A05/H |
| 7 | Devious Minds | Sir Tiffy Cashien — Talk | Permit after start; no global exemption from other starter rows | A01/H |
| 8 | Dragon Slayer II | King Roald — Talk | Permit after start | A01/H |
| 9 | Family Crest | Avan — Talk | Excluded: Avan has no current OSRS TCG NPC-card mapping; no Man-card substitute is invented | A01/M/H |
| 10 | Fight Arena | Head Guard — Talk | Permit after start | A01/H |
| 11 | Garden of Tranquillity | Wise Old Man — Talk | Permit after start | A01/H |
| 12 | Hopespear's Will | Ghost — starter Talk | Card-gated pre-start; permit after start; generic-name scope noted | A01/A05/H |
| 13 | In Search of the Myreque | Vanstrom Klause — starter Talk | Card-gated pre-start; permit after start | A01/A05/H |
| 14 | Land of the Goblins | Zanik — Talk | Permit after start | A01/H |
| 15 | Lost City | Warrior — starter Talk | Card-gated pre-start; permit after start; mapped Warrior variants remain any-of | A01/A05/H |
| 16 | Making Friends with My Arm | Wise Old Man — Talk | Permit after start | A01/H |
| 17 | Merlin's Crystal | Sir Gawain — Talk | Permit after start | A01/H |
| 18 | Merlin's Crystal | Sir Lancelot — Talk | Permit after start | A01/H |
| 19 | Monkey Madness I | Monkey — Talk | Permit after start; generic-name scope noted | A01/H |
| 20 | Monkey Madness II | Nieve — Talk | Permit after start | A01/H |
| 21 | Mourning's End Part I | Arianwyn — Talk | Permit after start; MEP II starter no longer makes her global | A01/H |
| 22 | Mourning's End Part I | Essyllt — Talk | Permit after start | A01/H |
| 23 | Mourning's End Part II | Arianwyn — starter Talk | Card-gated pre-start; permit after start | A01/A05/H |
| 24 | Mourning's End Part II | Essyllt — Talk | Permit after start | A01/H |
| 25 | Murder Mystery | Guard — starter Talk | Card-gated pre-start; permit after start; generic-name scope noted | A01/A05/H |
| 26 | Priest in Peril | King Roald — starter Talk | Card-gated pre-start; permit after start | A01/A05/H |
| 27 | Recruitment Drive | Sir Tiffy Cashien — Talk | Permit after start | A01/H |
| 28 | RFD — Sir Amik Varze | Wise Old Man — Talk | Permit after start | A01/H |
| 29 | Secrets of the North | Guard — starter Talk | Card-gated pre-start; permit after start; generic-name scope noted | A01/A05/H |
| 30 | Secrets of the North | Hazeel — Talk | Permit after start | A01/H |
| 31 | Shades of Mort'ton | Afflicted(Ulsquire) / Ulsquire Shauncy — serum/Talk | Excluded: neither runtime name has its own current OSRS TCG NPC-card mapping; no Afflicted-card substitute is invented | A01/M/H |
| 32 | Sins of the Father | Vanescula Drakan — Talk | Permit after start | A01/H |
| 33 | Sleeping Giants | Kovac/Hill Giant — starter Strike/Talk | Kovac starter card-gated pre-start; permit relevant noncombat state after start; transformed form remains a manual fixture | A01/A05/M/H |
| 34 | Swan Song | Wise Old Man — Talk | Permit after start | A01/H |
| 35 | The Feud | Bandit — Talk | Permit after start; generic-name scope noted | A01/H |
| 36 | The Feud | Menaphite Thug — Question | Permit after start | A01/H |
| 37 | The Feud | Snake — item-on-NPC | Permit target after start; used item remains governed by initial Item Usage | A01/H |
| 38 | The General's Shadow | General Khazard — starter Talk | Card-gated pre-start; permit after start | A01/A05/H |
| 39 | The Grand Tree | Glough — Talk | Permit after start | A01/H |
| 40 | The Grand Tree | Shipyard worker — Talk | Permit after start | A01/H |
| 41 | The Lost Tribe | Sigmund — starter Talk | Card-gated pre-start; permit after start | A01/A05/H |
| 42 | The Slug Menace | Sir Tiffy Cashien — starter Talk | Card-gated pre-start; permit after start | A01/A05/H |
| 43 | Twilight's Promise | Metzli, Teokan of Ranul — Talk | Permit noncombat after start; attacks gated | A01/H |
| 44 | Twilight's Promise | Prince Itzla Arkan — Talk | Permit after start; Heart starter no longer makes him global | A01/H |
| 45 | Wanted! | Sir Tiffy Cashien — starter Talk | Card-gated pre-start; permit after start | A01/A05/H |
| 46 | What Lies Below | Surok Magis — Talk | Permit after start | A01/H |
| 47 | While Guthix Sleeps | Idria — Give/Talk | Permit target after start; used item remains Item-Usage gated | A01/H |
| 48 | While Guthix Sleeps | Thaerisk — Talk | Permit after start | A01/H |
| 49 | Temple of Ikov | Lucien — starter Talk / later combat | Card-gated pre-start; permit noncombat after start; attack gated | A05/H |
| 50 | The Heart of Darkness | Prince Itzla Arkan — starter Talk / later combat | Card-gated pre-start; permit noncombat after start; attack gated | A05/H |
| 51 | Beneath Cursed Sands | Menaphite Shadow — fight | Add exact enemy requirement | A02 |
| 52 | Legends' Quest | Viyeldi — kill | Add exact enemy requirement | A02 |
| 53 | Skippy and the Mogres | Mogre — fight | Add exact enemy requirement | A02 |
| 54 | The Final Dawn | Metzli, Teokan of Ranul — defeat | Add exact enemy requirement; noncombat association never waives attack | A02 |

The shared association loader and its 48 card-mapped runtime-name rows are one
implementation family, but every audited decision—including excluded no-card
NPCs—is listed above for row-level acceptance or rejection.
The four combat JSON additions are likewise listed individually. Generic names
(`Monk`, `Ghost`, `Warrior`, `Guard`, `Monkey`, `Bandit`, `Snake`) can affect every
NPC with that runtime name; this is explicit review risk, not hidden certainty.
