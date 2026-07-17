# Quest required-enemy re-derivation — report for owner review

**Report-only.** No plugin files were changed. This is a proposed patch set to `src/main/resources/quest_cards.json` `monsterCards`, for you to accept/reject. All 183 full quests re-derived (23 miniquests skipped). Every "Resolved card" below exact-matches `Card.json`.

## TL;DR
- The existing extraction captured **100% of the `{{Quest details}}` `kills=` links that resolve cleanly** — my link-vs-data diff found **zero** clean-resolve misses. So genuine gaps are only where the required NPC is (a) named in `kills=` *prose* with no link, or (b) linked under a wiki title that differs from the in-game / card name (bracketed / "The …" / phase-name combat variants — the Adala class).
- **4 NEW required-kill cards found, across 4 quests. 2 are the clean bracketed-combat-variant class (Adala, The Draugen); 2 more (Cuthbert, Metzli) resolve to a card but carry an in-game-name caveat.**

---

## 1. NEW findings (missing from `monsterCards`, card exists)

| Quest | In-game NPC name | Resolved card | Already in data? | Confidence | Evidence |
|---|---|---|---|---|---|
| Death on the Isle | Adala | **Adala** | No (data = `[]`) | high | `kills=` is prose only ("Two low-level enemies must be fought…"), so nothing was extracted. Walkthrough: Adala (lvl 49) "starts attacking the player"; image "…fighting Adala". Wiki *Adala* page has an Infobox Monster (name=Adala) → menu name "Adala" → snapshot `adala` → card **Adala**. The confirmed Adala-style miss. |
| The Fremennik Trials | The Draugen | **The Draugen** | No (data has only the *optional* council warriors) | high | `kills=` lists `[[Draugen]] (level 69)` as non-optional; requirement "Ability to defeat a level-69 Draugen"; walkthrough "find the Draugen… and defeat it". Link `[[Draugen]]` → article **The Draugen** (Infobox name=The Draugen) → snapshot `the draugen`. |
| The Ribbiting Tale of a Lily Pad Labour Dispute | Cuthbert, Lord of Dread | **Cuthbert** | No (data = `[]`) | medium | Sole `kills=` entry `[[Cuthbert, Lord of Dread]] (level 1)` → required boss. Link title didn't resolve; page is *Cuthbert* (SwitchInfobox boss form "Cuthbert, Lord of Dread"). Card **Cuthbert** resolves via snapshot `cuthbert`. **Caveat:** works only if the right-click menu reads "Cuthbert"; if it shows the full title it won't match the snapshot — verify in-game. |
| The Final Dawn | Augur Metzli | **Metzli, Teokan of Ranul** | No (data has the 4 earlier fights, misses the climax) | medium | `kills=` climax `[[Augur Metzli]] (lvl-396)`. Link → article **Metzli, Teokan of Ranul** ("Augur Metzli" is a phase). Card resolves via snapshot `metzli, teokan of ranul`. **Caveat:** the fought phase's infobox name is "Augur Metzli", so the menu may read "Augur Metzli" (not in snapshot) — potential card-name/gating gap, verify in-game. |

## 2. Bracketed / name-variant combat cases specifically (the Adala class)

These are the whole point of the pass — required kills whose wiki link/title ≠ in-game/card name, which prose-and-link extraction structurally misses:

| Quest | Wiki link in source | In-game / card form | Resolved card | Notes |
|---|---|---|---|---|
| Death on the Isle | *(none — prose "two low-level enemies")* | Adala | **Adala** | Clean snapshot match. |
| The Fremennik Trials | `[[Draugen]]` | The Draugen | **The Draugen** | Wiki "Draugen" → in-game "The Draugen"; clean snapshot match. |
| The Ribbiting Tale… | `[[Cuthbert, Lord of Dread]]` | Cuthbert / "Cuthbert, Lord of Dread" | **Cuthbert** | Card is bare "Cuthbert"; in-game menu name to confirm. |
| The Final Dawn | `[[Augur Metzli]]` | "Augur Metzli" phase of Metzli, Teokan of Ranul | **Metzli, Teokan of Ranul** | Card is the boss base name; fought phase named "Augur Metzli". |

## 3. Unresolved required kills (required to fight, but NO card exists)

Informational — these can never be gated (untracked → never restricted). Candidates for the upstream card-gap report to osrs-tcg.

| Quest | NPC | Why it's here |
|---|---|---|
| Death on the Isle | Naiatli | 2nd of the "two low-level enemies"; you "attack her with a prop sword… until she dies". No card. |
| Wanted! | Solus Dellagar | Required final boss (`kills=[[Solus Dellagar]]`). No card (data only has the Black Knight variants). |
| The Fremennik Trials | Koschei the Deathless | Required combat trial ("the player must defeat Koschei", first three forms). No card. |
| The Blood Moon Rises | Wyrd | Required boss (`kills=[[Wyrd]] (level 564)`). No card. |
| The Blood Moon Rises | Sanguidae | Required ("Several level 106 Sanguidae"). No card. |
| The Blood Moon Rises | Webbed-winged Crow | Required ("Several level 98"). No card. |
| The Blood Moon Rises | Venator | Required ("A level 200 Venator"). No card. |
| The Blood Moon Rises | Ancient feral vyre | Required ("Four Ancient feral vyres"). No card. |
| Hazeel Cult | Alomone | Conditional (lvl 13, only on Ceril's side). No card. |
| The Red Reef | Named pirate captains (Oaky Doak, Boatswain Bill Teak, Mister U., Captain Ruban Acer, Old Jack, Mute Jack, Bloody Jack, Captain Jack) | Crew of the 2 pirate ships; **avoidable** by sinking the ships. No cards. Generic Pirate / Black Eye Bethel / Giant lobster already in data. |

## 4. Methodology

1. Batch-fetched all 183 full-quest wikitexts via the MediaWiki `revisions` API (50/req, 4 calls, redirects followed). Cached in `scratchpad/wikicache/` + `quest_wikitext.json`. All 183 resolved, no redirects/misses.
2. Extracted the `{{Quest details}}` `kills=` param (full multiline, bracket-depth aware) for every quest, plus all `[[wikilinks]]` within it. Also swept fight/boss section headers and whole-article links as a secondary net.
3. Resolution against the snapshot: lowercase → strip `#anchors` → replace `_`→space → strip trailing `(brackets)` → lookup in `entityToCards`; fall back to a direct `Card.json` exact-name match. (Fixed an underscore/anchor/plural normalisation bug that initially hid several links.)
4. Diffed resolved cards vs each quest's existing `monsterCards`.
5. Required-vs-not judged from structured signals + walkthrough verbs ("defeat/attack/must fight" = required; ally-kills-in-cutscene, avoidable, or conditional = noted separately).

## 5. Judgement calls / caveats

- **`kills=` links were already 100% captured** — the whole-article combat-keyword sweep produced ~114 quests of candidates that are almost entirely false positives (dialogue NPCs near combat words, joke wiki names like "Cow31337Killer"/"PKMaster0036", "Dawn (music track)"). I did **not** promote any of these; only `kills=`-param-backed or explicit walkthrough-required kills made the cut. This keeps precision high but means a required NPC that appears in neither `kills=` nor an unambiguous walkthrough instruction could still be missed.
- **Cuthbert & Metzli in-game names are the main risk.** Both cards resolve, but their fought forms have SwitchInfobox display names ("Cuthbert, Lord of Dread" / "Augur Metzli") that differ from the card/snapshot key. If the right-click menu uses those full/phase names, the card won't gate them even after adding it — a snapshot/card-naming issue worth an in-game check before shipping.
- **Rag and Bone Man I / II** (`kills=` "see below") were checked: their `monsterCards` already match the bone-collection targets; the extra whole-article link matches are noise. Left unchanged.
- The Red Reef / Blood Moon Rises no-card kills are expected gaps for very recent content, not extraction bugs.

---

## Owner disposition (2026-07-16)

- **APPLIED**: Adala (Death on the Isle) and The Draugen (The Fremennik Trials)
  — both resolve cleanly through the monster snapshot, so blocking and the
  panel work.
- **HELD** pending real in-game combat name (adding the card now would claim a
  requirement the plugin can't enforce — the exact silent-miss failure mode we
  avoid): Cuthbert (fought as "Cuthbert, Lord of Dread") and Metzli (fought as
  "Augur Metzli"). To ship: confirm the right-click name via a node-lookup
  capture or SwitchInfobox, then add a snapshot alias name -> card and the
  quest entry together.
- **UPSTREAM CARD-GAP REPORT candidates** (required quest kills with no card,
  quests stay completable as untracked): Naiatli, Solus Dellagar, Koschei the
  Deathless, the five Blood Moon Rises bosses (Wyrd, Sanguidae, Webbed-winged
  Crow, Venator, Ancient feral vyre), conditional Alomone, and The Red Reef's
  named pirate captains.
