# Fletching: interface-vs-instant matrix (Bronzeman TCG data research)

Question: for every fletching creation action, does it open the "make" chatbox
interface (product/quantity) or happen INSTANTLY on item-on-item? This decides
keying: **interface actions must be keyed at the product click (`kind:"interface"`);
instant actions at the item-on-item click (`kind:"item-on-item"`).**

Sources: OSRS Wiki plain pages (cached locally, no query params, ~1 req/sec):
Fletching, Dart, Arrow, Bolts, Javelin, Ogre_arrow, Atlatl_dart. Owner-verified
facts taken as ground truth. Where the wiki neither states nor implies the UI,
the action is marked **UNVERIFIED** with the lean.

## The two hard wiki facts found (change notes)
- **Darts** — 5 Apr 2023: "Players can now toggle a 'Make-x' interface for
  fletching darts." => darts are **INSTANT by default** (matches owner), with an
  **opt-in** toggle that switches them to the interface.
- **Bolts** — 31 Jul 2024: "There is now a 'Make-X' option when fletching bolts."
  => feathering unfinished bolts now uses the **make interface**. (Wording does
  not say whether it's always-on or a toggle -> left UNVERIFIED on default.)

No equivalent Make-X change note exists for arrows, headless arrows, javelins,
crossbow assembly, ogre/brutal arrows, or broad arrows — the basis for leaning
those toward INSTANT.

## Verdict matrix

| Action | interface | product card | note |
|---|---|---|---|
| Knife on logs -> arrow shafts | **TRUE** (verified) | yes | product-select make menu |
| Knife on logs -> shortbow(u)/longbow(u) | **TRUE** (verified) | no (u items) | unstrung; strung bow is a 2nd action |
| Knife on logs -> crossbow stock | **TRUE** (verified) | yes | stock items carded |
| Knife on Achey logs -> ogre arrow shaft | TRUE (inferred, knife-on-logs family) | yes | |
| Bow string on unstrung bow -> strung bow | **TRUE** (verified) | yes | all tiers, short & long |
| Feather on dart tip -> darts | **FALSE** (verified) | yes | default instant; opt-in Make-X toggle |
| Feather on unf metal bolts -> bolts | TRUE (2024 note) | yes | default on/toggle UNVERIFIED |
| Feather on arrow shaft -> headless arrow | UNVERIFIED (lean instant) | yes | |
| Arrowtips on headless -> metal/amethyst/dragon arrow | UNVERIFIED (lean instant) | yes | tips uncarded |
| Javelin heads on javelin shaft -> javelin | UNVERIFIED (lean instant) | yes | MISSING from 35 |
| Gem bolt tips on bolts -> gem bolts | UNVERIFIED (lean instant) | yes | MISSING from 35 |
| Limbs on stock -> crossbow(u) | UNVERIFIED (lean instant/single) | no | MISSING from 35 |
| Crossbow string on crossbow(u) -> crossbow | UNVERIFIED (**lean interface**, parallels bow stringing) | yes | MISSING from 35 |
| Feather on ogre arrow shaft -> flighted ogre arrow | UNVERIFIED (lean instant) | no | MISSING from 35 |
| Wolfbone tips on flighted -> ogre arrow | UNVERIFIED (lean instant) | **no** | MISSING; product uncarded |
| Metal nails on flighted -> brutal arrows | UNVERIFIED (lean instant) | yes | MISSING; e.g. "Bronze brutal" carded |
| Broad arrowheads on headless -> broad arrows | UNVERIFIED (lean instant) | yes | MISSING; needs Broader Fletching |
| Feather on unf broad bolts -> broad bolts | UNVERIFIED (lean interface, 2024 note) | yes | MISSING |
| Amethyst broad bolt tips -> amethyst broad bolts | UNVERIFIED (lean instant) | yes | MISSING |
| Atlatl dart tips on headless atlatl dart -> atlatl dart | UNVERIFIED (lean instant, dart-family) | yes | MISSING (Varlamore) |
| Feather on atlatl dart shaft -> headless atlatl dart | UNVERIFIED (inputs unconfirmed) | yes | MISSING (Varlamore) |

(52 rows enumerated per-tier in fletching_actions.json: 10 interface / 42 not.)

## Coverage vs the existing 35 fletching recipes

The 35 current recipes are **ALL keyed `item-on-item`**. They cover exactly:
- Arrow shaft (knife on logs)
- 12 bows: short & long for normal/oak/willow/maple/yew/magic (keyed to knife-on-logs)
- Headless arrow (feather + shaft)
- 8 metal/amethyst/dragon arrows (arrowtips on headless)
- 7 darts (feather + dart tip)
- 6 metal bolts (feather + unf bolts)

### KEYING PROBLEMS in the existing 35 (highest priority)
1. **Knife-on-logs recipes are mis-keyed** (13 recipes: Arrow shaft + all 12
   bows). Owner-verified that knife-on-logs OPENS THE INTERFACE, so these need
   `kind:"interface"` at the product click — not `item-on-item`. Blocking the
   item-on-item (knife-on-logs) click is coarse: it would block ALL products of
   that log (e.g. shafts you CAN make) to stop one you can't. Interface keying
   gives per-product granularity.
2. **Bow recipes conflate two interface actions.** Knife-on-logs yields the
   **unstrung** bow (Shortbow (u)/Longbow (u), no cards); the strung bow needs a
   separate bow-string-on-unstrung interface action. The 35 attach the strung
   product name directly to the knife step. Decide whether to model both steps or
   keep the shortcut.
3. **Metal bolts should be interface (2024 Make-X).** The 6 bolt recipes are
   keyed item-on-item but feathering bolts now uses the make interface. Likely
   needs interface twins (same pattern as the cooking interface-twin fix), pending
   owner confirming default vs toggle in-game.
4. **Darts keying is correct** (item-on-item), matching the verified default-instant
   behavior. Caveat to note in-game: if a player enables the dart Make-X toggle,
   creation moves to the interface and the item-on-item block behavior should be
   re-checked.
5. Metal/amethyst/dragon **arrows and headless arrows**: item-on-item keying is
   the safe lean (no Make-X note) but is UNVERIFIED — worth a quick in-game check.

## MISSING fletching product families (not in the 35 at all)
- **Javelins** — bronze/iron/steel/mithril/adamant/rune/dragon/amethyst (heads on shaft).
- **Crossbows** — limbs+stock -> crossbow(u), then crossbow string -> crossbow
  (all tiers incl. dragon); plus crossbow **stocks from logs** (carded) at the
  knife-on-logs step.
- **Gem-tipped bolts** — opal/jade/pearl/topaz/sapphire/emerald/ruby/diamond/
  dragonstone/onyx (+ their dragon variants).
- **Ogre & brutal arrows** — ogre arrow shaft, flighted ogre arrow, ogre arrow,
  and the 7 brutal tiers (Bronze..Rune "* brutal").
- **Broad ammo** — broad arrows, broad bolts, amethyst broad bolts.
- **Atlatl darts (Varlamore, 2024)** — atlatl dart + headless atlatl dart.

## Card-gap notes (tracked_item_names.json, exact lowercase match)
- **Uncarded intermediates** (cannot be used as requirements): all metal
  `* arrowtips`, all `* dart tip`, all `* javelin heads`, all `* bolts (unf)`,
  all gem `* bolt tips`, `* crossbow (u)`, all `shortbow (u)`/`longbow (u)`,
  `flighted ogre arrow`, `wolfbone arrowtips`, `atlatl dart tips`.
- **Uncarded PRODUCTS** (rare — product itself cannot be gated):
  - `Ogre arrow` (plain) has **no card** — only its upstream `Ogre arrow shaft`
    is carded. Per the untracked-never-restricted rule, plain ogre arrows are
    freely makeable; best partial gate is the shaft/logs upstream.
  - `Flighted ogre arrow` has no card (intermediate).
  - Unstrung bows/crossbows have no cards.
- **Carded and safe to gate**: all strung bows, all darts, all metal bolts, all
  gem bolts, all javelins (+ shaft), all crossbows (+ limbs, stocks, string), all
  brutal arrows, broad arrows/bolts/arrowheads/unfinished broad bolts/amethyst
  broad bolts, atlatl dart + headless atlatl dart, headless arrow, arrow shaft,
  all metal/amethyst/dragon arrows.

## Recommended in-game verifications for the owner (the UNVERIFIED set)
1. Feather on arrow shaft (headless) and arrowtips on headless (arrows):
   interface or instant?
2. Bolts feathering: does Make-X appear by default, and does it also apply to
   gem bolt-tipping?
3. Crossbow: does stringing open an interface (expected yes)? Does limbs-on-stock?
4. Javelins, broad arrows, ogre/brutal arrows, atlatl darts: interface or instant?
