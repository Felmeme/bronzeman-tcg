# Sailing restriction data — mechanics findings & node report

Scope: boat hull/keel upgrades, shipwreck salvaging, harpoonfish. All card names
exact-matched against `osrs-tcg/.../Card.json` (6376 cards). Deliverable
`sailing_nodes.json` = 11 nodes + 14 `interfaceProducts` entries, parse-verified,
55/55 card references exact-match.

Sources (OSRS Wiki, oldschool.runescape.wiki): Shipbuilding, Hull parts, Keel
parts, Boat schematics, Shipwright, Shipwrights' Workbench, Salvage, Shipwreck
salvaging, Small/Large shipwreck object pages, Raw harpoonfish, Harpoonfish.

---

## 1. Boat upgrades (`sailing-upgrades`) — HOW IT ACTUALLY WORKS

Two-stage flow, both verified:

1. **Make the parts** at a **Shipwrights' Workbench** (hammer + saw). This is a
   make-style production interface. Product = the part item, whose name **equals
   the card name exactly**: regular hull parts from 5 planks (`Oak hull parts` =
   5× Oak plank), large hull parts from 5 regular parts (`Large oak hull parts`);
   keel parts from 5 bars on an anvil (`Bronze keel parts` = 5× Bronze bar),
   dragon keel parts from 2× Dragon metal sheet.
2. **Install the tier** at the **`Boat schematics`** object in the Shipyard,
   right-click option **`Modify`** (both verified from the Boat schematics infobox).
   Reached by "Customise Boat" from a shipwright. Installing consumes hull/keel
   **parts** (10 regular for a Skiff, 16 large for a Sloop). The Raft has no keel.

### Trigger-shape decision (VERIFIED vs GUESSED)
- Object + option `Boat schematics` / `Modify` — **VERIFIED** (infobox).
- The build interface's **per-tier product string** (does the confirm click's
  menu target read `Oak hull` / `Bronze keel`?) — **GUESSED / UNVERIFIED**. The
  wiki does not expose interface target text. I keyed `interfaceProducts` on
  `"<Tier> hull"` / `"<Tier> keel"` per the owner's install-centric design, and
  flagged this in the `Boat schematics` marker node's notes.
- **Robust fallback (recommended if the client doesn't emit hull-name targets):**
  key the make-interface fallback on the **workbench part products**, which are
  1:1 card-name matches (`Oak hull parts`, `Large oak hull parts`, …). Listed in
  the `Shipwrights' Workbench` marker node notes. Whichever interface the plugin
  actually hooks, one of these two keyings will fire — verify in-client and keep
  the matching one (avoid enabling both to prevent double-gating the same chain).

I did NOT emit per-tier `kind:"object"` nodes on `Boat schematics` — the tier is
only known *inside* the interface, so per-tier object nodes would collide on the
same object name. Per-tier gating lives entirely in `interfaceProducts`. The two
`kind:"object"` entries are **non-gating station markers** (empty
`requiredCardGroups`) carrying the expected product names in `notes`, exactly as
the brief requested.

### Three role-tagged groups per tier (14 tiers: 7 hull + 7 keel)
`groups=[[part],[material],[large]]`, `roles=["part","material","large"]`,
`requireAll=true`. Config modes drop groups by role: Parts = part only;
Parts+Materials = +material; Everything = +large.

| Tier | part (verified) | material used | large (verified) |
|---|---|---|---|
| Wooden hull | Wooden hull parts | **Logs** | Large wooden hull parts |
| Oak hull | Oak hull parts | Oak logs | Large oak hull parts |
| Teak hull | Teak hull parts | Teak logs | Large teak hull parts |
| Mahogany hull | Mahogany hull parts | Mahogany logs | Large mahogany hull parts |
| Camphor hull | Camphor hull parts | Camphor logs | Large camphor hull parts |
| Ironwood hull | Ironwood hull parts | Ironwood logs | Large ironwood hull parts |
| Rosewood hull | Rosewood hull parts | Rosewood logs | Large rosewood hull parts |
| Bronze keel | Bronze keel parts | Bronze bar | Large bronze keel parts |
| Iron keel | Iron keel parts | Iron bar | Large iron keel parts |
| Steel keel | Steel keel parts | Steel bar | Large steel keel parts |
| Mithril keel | Mithril keel parts | Mithril bar | Large mithril keel parts |
| Adamant keel | Adamant keel parts | **Adamantite bar** | Large adamant keel parts |
| Rune keel | Rune keel parts | **Runite bar** | Large rune keel parts |
| Dragon keel | Dragon keel parts | **Dragon metal sheet** | Large dragon keel parts |

Every material has an exact card; **no material group omitted.**

### ⚠️ TOP DISCUSSION ITEM — hull material: logs vs planks
The owner design (CLAUDE.md) and the brief say the hull "material" role = **logs**
(`Oak logs`, …). But the game does **not** consume logs to make hull parts — it
consumes **planks** (`Oak hull parts` = 5× Oak plank; the log→plank sawmill step
is separate). **Plank cards all exist**: `Plank`, `Oak plank`, `Teak plank`,
`Mahogany plank`, `Camphor plank`, `Ironwood plank`, `Rosewood plank`. I shipped
**logs** to honour the stated design, but **planks are the mechanically accurate
"underlying material."** Swap is trivial (one column). Owner decides — this is a
deliberate design call, not a data error. Keels have no such issue: keel parts
consume bars/sheets directly, so the material column is exact.

Naming corrections applied vs the brief's shorthand: `Adamantite bar` (not
"Adamant bar"), `Runite bar` (not "Rune bar"), and dragon keel material =
`Dragon metal sheet` (not a "bar"; 2 per part, exclusive drop from Frost dragons
/ Lavastryke wyrms).

Deliberately excluded (no part cards / cosmetic / double-gating), per owner:
masts, sails, helms, cannons, cargo holds, braziers, flags, paints, trims,
schematics unlock items (`Rosewood hull schematic`, `Dragon keel schematic`).

---

## 2. Salvaging (`sailing-salvage`) — mapping CONFIRMED, no corrections

Owner's 8-tier mapping is **100% correct** against the Salvage page. One node per
wreck, `kind:"object"`, `requiredCardGroups=[[<salvage>]]`, `requireAll=true`.

| Wreck object | Salvage card | Sailing lvl | name confidence |
|---|---|---|---|
| Small shipwreck | Small salvage | 15 | HIGH (object page opened) |
| Fisherman's shipwreck | Fishy salvage | 26 | MED-HIGH (Salvage table) |
| Barracuda shipwreck | Barracuda salvage | 35 | MED-HIGH (Salvage table) |
| Large shipwreck | Large salvage | 53 | HIGH (object page opened) |
| Pirate shipwreck | Plundered salvage | 64 | MED-HIGH (Salvage table) |
| Mercenary shipwreck | Martial salvage | 73 | MED-HIGH (Salvage table) |
| Fremennik shipwreck | Fremennik salvage | 80 | MED-HIGH (Salvage table) |
| Merchant shipwreck | Opulent salvage | 87 | MED-HIGH (Salvage table) |

**Menu-option caveat (verify in-client):** shipwreck objects are Scenery
(`kind:"object"` correct). Their **static infobox lists only `Inspect`**. The
actual salvage is done with a **salvaging hook** (a boat facility, not a held
item), and active wrecks expose a gather option. I modelled the gather option as
**`Salvage`** (high confidence from activity naming and "the Salvage option"
wiki wording), but the exact menu-option string should be confirmed against a
live client. If it turns out to be item-on-object with the hook, convert these to
`kind:"item-on-object"` with `name:"Salvaging hook"` and the wreck in options.

---

## 3. Harpoonfish (`fishing`) — folds into existing harpoon union

`Raw harpoonfish` is caught at **Tempoross Cove** harpoon-only spots. The NPC is
plain **`Fishing spot`**, fishing option **`Harpoon`** (level 35 Fishing). **No
separate Sailing deep-sea harpoonfish spot exists** — so it folds into the
existing `npc | Fishing spot | harpoon` union rather than getting its own node.

Emitted node = the **updated union**, superseding the existing
`resource_nodes.json` node (was `[Raw tuna, Raw swordfish, Raw shark]`,
`requireAll=false`):
`[Raw tuna, Raw swordfish, Raw shark, Raw harpoonfish, Crystallised harpoonfish]`.
Expressed in the new schema as a single OR-group with `requireAll=true` (=
"own ANY yielded fish unlocks the spot", identical semantics to the old
`requireAll=false` flat list).

- **`Raw harpoonfish`** — added (primary, brief-mandated).
- **`Crystallised harpoonfish`** — added: same spot + same `Harpoon` option
  (1/3 chance with a crystal harpoon), cleanly interceptable. Judgment call —
  drop it if you want the union to mirror the old set plus only raw harpoonfish.
- **`Big harpoonfish`** — **excluded**: it comes from the **Tempoross reward
  pool** (Ruins of Unkah), not fished at a spot, so not interceptable via a
  fishing-spot click.

**Integration note:** this is an EDIT to an existing resource node, not a new
sailing node — the plugin should merge it into the current harpoon union, not
add a duplicate `Fishing spot`/`harpoon` node.

---

## Open questions for owner
1. **Hull material = logs (shipped) or planks (accurate)?** Plank cards all exist. (design call)
2. **interfaceProducts key** = build product `"Oak hull"`/`"Bronze keel"` (shipped, UNVERIFIED) vs workbench part product `"Oak hull parts"`/`"Large oak hull parts"` (verified 1:1). Confirm which target string the client emits.
3. **Salvage menu option** — confirm `Salvage` vs `Inspect` vs item-on-object with the salvaging hook.
4. **Crystallised harpoonfish** in the union — keep or drop?
5. **Non-gating station marker nodes** (empty `requiredCardGroups`) — confirm the plugin loader tolerates zero groups; if not, drop them and keep the product-name docs here.
