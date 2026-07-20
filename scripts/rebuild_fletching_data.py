"""Rebuild the "fletching" category of recipe_nodes.json from the researched
interface-vs-instant matrix (docs/fletching_actions.json, docs/fletching_report.md).

Fixes the "KEYING PROBLEMS" flagged in docs/fletching_report.md:
  1. Knife-on-logs actions (shafts/bows/crossbow stocks/ogre shaft) are
     owner-verified to open the make interface, not item-on-item - re-keyed
     as kind:"interface" for per-product granularity.
  2. Bow stringing is a separate interface action from knife-on-logs (two-stage
     chain); the old data conflated them onto the knife step.
  3. Bolt feathering gained a Make-X interface per a 31 Jul 2024 wiki change
     note (default-vs-toggle unverified) - interface twins added alongside the
     existing item-on-item entries, same pattern as the cooking interface-twin
     fix, so whichever fires, the block still works.
  4. Adds the "MISSING fletching product families" from the report: crossbows,
     javelins, gem-tipped bolts, ogre/brutal arrows, broad ammo, atlatl darts
     (Varlamore). Tier pairings (crossbow stock/limbs, gem-bolt bases) were
     wiki-verified during this pass, not guessed.

Standing card-gap policy (docs/plan_skills_sweep.md): uncarded intermediates
(unstrung bows/crossbows, arrowtips, dart/javelin/bolt tips, flighted ogre
arrow, plain Ogre arrow) never appear as required input groups and are never
enforced as output - requirements fall back to the nearest CARDED ancestor.

Run: py scripts/rebuild_fletching_data.py
"""
import json

PATH = "D:/ClaudeFolder/bronzeman-tcg/src/main/resources/recipe_nodes.json"

INTERFACE = "interface"
ITEM_ON_ITEM = "item-on-item"


def interface_recipe(name, inputs, output, notes):
    return {
        "category": "fletching",
        "inputs": inputs,
        "output": output,
        "trigger": {"kind": INTERFACE, "name": name, "targets": []},
        "notes": notes,
    }


def item_recipe(used, target, inputs, output, notes):
    return {
        "category": "fletching",
        "inputs": inputs,
        "output": output,
        "trigger": {"kind": ITEM_ON_ITEM, "name": used, "targets": [target]},
        "notes": notes,
    }


def build():
    recipes = []

    # ---- Arrow shaft: knife-on-logs, owner-verified interface. EVERY log
    # tier offers shafts (owner-observed: Willow 45, Yew 75 - quantity scales
    # with tier), so the product click cannot know which log card to demand:
    # output-only gate on the Arrow shaft card, no log input group. Keyed
    # singular + plural; the quantity in the menu string ("x45") is stripped
    # by the plugin's interface-product normalizer. ----
    for shaft_key in ("Arrow shaft", "Arrow shafts"):
        recipes.append(interface_recipe(
            shaft_key, [], "Arrow shaft",
            "Owner-verified: knife-on-logs opens the make interface, and every "
            "log tier offers shafts (qty scales with tier), so the product "
            "click can't identify the log - output-only gate on Arrow shaft. "
            "Plural twin keyed; plugin strips the xN quantity."))

    # ---- Bows: two-stage chain. GROUND TRUTH from the owner's debug-log
    # capture (2026-07-20): the knife menu fires PLAIN product names
    # ("node lookup kind=interface name='Willow shortbow'"), NOT "(u)" names.
    # So the plain interface key = STAGE 1 CARVING: tier's Logs only, no
    # output gate (owner ruling - the strung card gates stringing, and the
    # unstrung product itself has no card).
    # STAGE 2 STRINGING is intercepted at the item-on-item click instead
    # (Bow string used on the "(u)" item - the unstrung item's own name is
    # reliable there), carrying the full chain: logs + Bow string + strung
    # card as output. If a player owns everything and reaches the stringing
    # interface, its product click harmlessly re-hits the carving rule. ----
    BOW_TIERS = [
        ("", "Logs"),
        ("Oak", "Oak logs"),
        ("Willow", "Willow logs"),
        ("Maple", "Maple logs"),
        ("Yew", "Yew logs"),
        ("Magic", "Magic logs"),
    ]
    for prefix, logs in BOW_TIERS:
        for base in ("Shortbow", "Longbow"):
            # Card/item names capitalise only the first word: "Magic longbow".
            product = f"{prefix} {base.lower()}" if prefix else base
            recipes.append(interface_recipe(
                product, [[logs]], None,
                "Stage 1 carving: knife menu fires the plain product name "
                "(owner debug-log verified). Tier's Logs only; no output gate "
                "(owner ruling 2026-07-20). Bow string NOT required here."))
            recipes.append(item_recipe(
                "Bow string", f"{product} (u)", [[logs], ["Bow string"]], product,
                "Stage 2 stringing, intercepted at the bow-string-on-unstrung "
                "item-on-item click (the unstrung item name is reliable there; "
                "the stringing interface's own product string shows the plain "
                "name, which is the carving key). Nearest-carded: "
                f"{logs} + Bow string; output = strung {product}."))

    # ---- Crossbow stocks: same knife-on-logs family, all carded products. ----
    STOCK_TIERS = [
        ("Logs", "Wooden stock", "Bronze crossbow", "Bronze limbs"),
        ("Oak logs", "Oak stock", "Blurite crossbow", "Blurite limbs"),
        ("Willow logs", "Willow stock", "Iron crossbow", "Iron limbs"),
        ("Teak logs", "Teak stock", "Steel crossbow", "Steel limbs"),
        ("Maple logs", "Maple stock", "Mithril crossbow", "Mithril limbs"),
        ("Mahogany logs", "Mahogany stock", "Adamant crossbow", "Adamantite limbs"),
        ("Yew logs", "Yew stock", "Runite crossbow", "Runite limbs"),
        ("Magic logs", "Magic stock", "Dragon crossbow", "Dragon limbs"),
    ]
    for logs, stock, crossbow, limbs in STOCK_TIERS:
        recipes.append(interface_recipe(
            stock, [[logs]], stock,
            "knife-on-logs family (generalises the owner-verified mechanic). "
            f"{stock} feeds into the {crossbow} chain via {limbs}."))

    # ---- Wooden shields (Forestry, 2023): knife-on-logs family, spotted by
    # the owner in the knife menu (2026-07-20) - missing from the research
    # matrix entirely. Wiki-verified (Oak_shield page): the family STARTS at
    # Oak (2 logs each, Fletching 27) - there is NO fletchable shield from
    # plain Logs (the classic Wooden shield is unrelated). 1 per craft, so
    # the menu string should be the plain name. ----
    SHIELD_TIERS = [
        ("Oak logs", "Oak shield"),
        ("Willow logs", "Willow shield"),
        ("Maple logs", "Maple shield"),
        ("Yew logs", "Yew shield"),
        ("Magic logs", "Magic shield"),
        ("Redwood logs", "Redwood shield"),
    ]
    for logs, shield in SHIELD_TIERS:
        recipes.append(interface_recipe(
            shield, [[logs]], shield,
            "Forestry wooden shield, knife-on-logs family (owner spotted the "
            "menu option; family absent from the original research matrix). "
            "Requires the tier's logs + the shield card."))

    # ---- Javelin shafts: knife-on-logs product missed by the research
    # matrix, found during the 2026-07-20 verification sweep (wiki: 15 per
    # plain log, Fletching 3). Output-only gate like arrow shafts; the menu
    # shows a leading count which the plugin strips. ----
    for shaft_key in ("Javelin shaft", "Javelin shafts"):
        recipes.append(interface_recipe(
            shaft_key, [], "Javelin shaft",
            "Knife-on-logs product (wiki-verified: 15 per plain log) missed "
            "by the research matrix. Output-only gate on the Javelin shaft "
            "card; leading-count menu string handled by the plugin."))

    # ---- Ogre arrow shaft: knife-on-logs family (inferred), Achey tree logs
    # itself is uncarded so no input group is needed - output-only gate.
    # Plural twin for the same reason as Arrow shaft (made in batches). ----
    for shaft_key in ("Ogre arrow shaft", "Ogre arrow shafts"):
        recipes.append(interface_recipe(
            shaft_key, [], "Ogre arrow shaft",
            "knife-on-logs family, inferred from the owner-verified mechanic. "
            "Achey tree logs are uncarded, so this is an output-only gate. "
            "Plural twin keyed until the exact menu string is confirmed."))

    # ---- Bow stringing / crossbow stringing feed into: crossbow assembly.
    # Limbs-on-stock -> Crossbow (u) is never gated (uncarded product); nearest
    # -carded policy requires Stock + Limbs at the FINAL stringing step.
    # Stringing is UNVERIFIED whether it's item-on-item or interface (only
    # leans interface by analogy to bows) - both kinds registered so whichever
    # the real client uses, the block still fires. ----
    for logs, stock, crossbow, limbs in STOCK_TIERS:
        inputs = [[stock], [limbs], ["Crossbow string"]]
        notes = (
            "UNVERIFIED whether crossbow stringing is item-on-item or opens an "
            "interface (leans interface by analogy to owner-verified bow "
            "stringing) - both trigger kinds registered defensively. "
            "Nearest-carded: requires stock+limbs, not the uncarded Crossbow (u)."
        )
        recipes.append(interface_recipe(crossbow, inputs, crossbow, notes))
        recipes.append(item_recipe(
            "Crossbow string", f"{crossbow} (u)", inputs, crossbow, notes))
        if crossbow == "Runite crossbow":
            # Naming trap (wiki-verified 2026-07-20): the unstrung item is
            # "Runite crossbow (u)" (matching the card) but the FINISHED item
            # is "Rune crossbow" - so the finished-product interface string
            # needs its own key. The card stays "Runite crossbow"; the
            # in-game "Rune crossbow" name is untracked and can't be a card.
            recipes.append(interface_recipe("Rune crossbow", inputs, crossbow, notes
                + " Finished-name twin: item is 'Rune crossbow', card is 'Runite crossbow'."))

    # ---- Headless arrow: unchanged, owner-verified-safe lean instant. ----
    recipes.append(item_recipe(
        "Feather", "arrow shaft", [["Arrow shaft"], ["Feather"]], "Headless arrow",
        "Feather on arrow shafts = headless arrows. UNVERIFIED interface-vs-"
        "instant (no Make-X wiki note) - lean instant kept from prior data."))

    # ---- Metal/amethyst/dragon arrows: unchanged, arrowtips uncarded. ----
    METAL_ARROWS = ["Bronze", "Iron", "Steel", "Mithril", "Adamant", "Rune",
                     "Dragon", "Amethyst"]
    for tier in METAL_ARROWS:
        recipes.append(item_recipe(
            "Headless arrow", f"{tier.lower()} arrowtips", [["Headless arrow"]],
            f"{tier} arrow",
            f"Attach {tier} arrowtips (arrowtip card absent) to headless arrows. "
            "UNVERIFIED interface-vs-instant (no Make-X note) - lean instant."))

    # ---- Darts: unchanged, owner-verified default-instant (opt-in Make-X
    # toggle is an accepted honor-system edge, no twin added). ----
    DART_TIERS = ["Bronze", "Iron", "Steel", "Mithril", "Adamant", "Rune", "Dragon"]
    for tier in DART_TIERS:
        recipes.append(item_recipe(
            "Feather", f"{tier.lower()} dart tip", [["Feather"]], f"{tier} dart",
            f"Feather {tier} dart tips (dart-tip card absent) into darts. "
            "Owner-verified INSTANT by default; opt-in Make-X toggle is an "
            "accepted honor-system edge (not covered)."))

    # ---- Metal bolts: item-on-item kept (in case Make-X is a toggle, not
    # default-on) PLUS an interface twin (2024 wiki change note confirms the
    # make interface exists for bolt feathering; default-vs-toggle unverified).
    # Added Blurite as a 7th tier (missing from the old 35; feeds Jade bolts). ----
    BOLT_TIERS = ["Bronze", "Iron", "Steel", "Mithril", "Adamant", "Runite", "Blurite"]
    for tier in BOLT_TIERS:
        output = f"{tier} bolts"
        notes = (
            f"Feather unfinished {tier} bolts (unf card absent) into bolts. "
            "Wiki (31 Jul 2024): 'Make-X option when fletching bolts' - interface "
            "twin added alongside the item-on-item entry; default-vs-toggle "
            "unverified so both are covered."
        )
        recipes.append(item_recipe(
            "Feather", f"{tier.lower()} bolts (unf)", [["Feather"]], output, notes))
        recipes.append(interface_recipe(output, [["Feather"]], output, notes))

    # ---- Gem-tipped bolts: wiki-verified base metal-bolt tier per gem
    # (varies by gem, NOT a single "metal base" as the matrix placeholder
    # suggested - confirmed per-gem via individual wiki pages this pass). ----
    GEM_BOLTS = [
        ("Opal", "Bronze bolts"),
        ("Jade", "Blurite bolts"),
        ("Pearl", "Iron bolts"),
        ("Red topaz", "Steel bolts", "Topaz bolts"),
        ("Sapphire", "Mithril bolts"),
        ("Emerald", "Mithril bolts"),
        ("Ruby", "Adamant bolts"),
        ("Diamond", "Adamant bolts"),
        ("Dragonstone", "Runite bolts"),
        ("Onyx", "Runite bolts"),
    ]
    for entry in GEM_BOLTS:
        tip_prefix, base = entry[0], entry[1]
        output = entry[2] if len(entry) > 2 else f"{tip_prefix} bolts"
        recipes.append(item_recipe(
            base, f"{tip_prefix.lower()} bolt tips", [[base]], output,
            f"{tip_prefix} bolt tips (tip card absent) attach to {base} "
            f"(wiki-verified this pass). UNVERIFIED interface-vs-instant - "
            "lean instant per the matrix."))

    # ---- Javelins: heads uncarded, shaft carded. ----
    JAVELIN_TIERS = ["Bronze", "Iron", "Steel", "Mithril", "Adamant", "Rune",
                      "Dragon", "Amethyst"]
    for tier in JAVELIN_TIERS:
        recipes.append(item_recipe(
            f"{tier.lower()} javelin heads", "javelin shaft", [["Javelin shaft"]],
            f"{tier} javelin",
            f"{tier} javelin heads (heads card absent) on javelin shaft. "
            "MISSING family, added this pass. UNVERIFIED interface-vs-instant - "
            "lean instant per the matrix."))

    # ---- Ogre/brutal arrows: flighted ogre arrow and plain Ogre arrow are
    # both uncarded, so brutal arrows fall back to the nearest carded
    # ancestor (Ogre arrow shaft) plus the (carded) metal nails. ----
    BRUTAL_TIERS = [
        ("Bronze nails", "Bronze brutal"),
        ("Iron nails", "Iron brutal"),
        ("Steel nails", "Steel brutal"),
        ("Black nails", "Black brutal"),
        ("Mithril nails", "Mithril brutal"),
        ("Adamantite nails", "Adamant brutal"),
        ("Rune nails", "Rune brutal"),
    ]
    for nails, brutal in BRUTAL_TIERS:
        recipes.append(item_recipe(
            nails, "flighted ogre arrow", [["Ogre arrow shaft"], [nails]], brutal,
            f"{nails} on flighted ogre arrow (uncarded) -> {brutal}. "
            "Nearest-carded: requires Ogre arrow shaft (flighted is uncarded), "
            "plus the nails themselves (carded). MISSING family, added this "
            "pass. UNVERIFIED interface-vs-instant - lean instant."))

    # ---- Broad ammo: broad arrowheads and unfinished broad bolts ARE carded
    # (unlike gem/dart/arrow tips), so both sides of the recipe are enforced. ----
    recipes.append(item_recipe(
        "Broad arrowheads", "headless arrow", [["Headless arrow"], ["Broad arrowheads"]],
        "Broad arrows",
        "Broad arrowheads on headless arrow -> broad arrows. MISSING family, "
        "added this pass. UNVERIFIED interface-vs-instant - lean instant. "
        "Requires Broader Fletching slayer unlock in-game."))
    broad_bolt_notes = (
        "Feather unfinished broad bolts into broad bolts. Both items ARE carded "
        "(unlike plain unf bolts). MISSING family, added this pass. Wiki 2024 "
        "Make-X note plausibly applies here too - interface twin added "
        "defensively alongside item-on-item."
    )
    recipes.append(item_recipe(
        "Feather", "unfinished broad bolts", [["Unfinished broad bolts"], ["Feather"]],
        "Broad bolts", broad_bolt_notes))
    recipes.append(interface_recipe(
        "Broad bolts", [["Unfinished broad bolts"], ["Feather"]], "Broad bolts",
        broad_bolt_notes))
    recipes.append(item_recipe(
        "Amethyst bolt tips", "broad bolts", [["Broad bolts"]],
        "Amethyst broad bolts",
        "Amethyst bolt tips (the real item name, wiki-verified 2026-07-20 - "
        "an earlier cut keyed the nonexistent 'Amethyst broad bolt tips') on "
        "broad bolts. Tip card absent. UNVERIFIED interface-vs-instant - "
        "lean instant."))

    # ---- Atlatl darts (Varlamore): both intermediate and product are carded.
    # Chain wiki-verified 2026-07-20: knife on Ent branches (Vale Totems
    # reward, 100 shafts per branch, Fletching 74) -> shafts + Feathers ->
    # headless -> + tips -> darts. Ent branch IS carded. ----
    recipes.append({
        "category": "fletching",
        "inputs": [["Ent branch"]],
        "output": "Atlatl dart shaft",
        "trigger": {"kind": ITEM_ON_ITEM, "name": "Knife",
            "targets": ["ent branch", "ent branches"]},
        "notes": "Knife on Ent branch -> 100 atlatl dart shafts (wiki-verified;"
            " singular/plural item name unverified so both targets keyed)."
            " UNVERIFIED interface-vs-instant - lean instant, interface twin"
            " registered too.",
    })
    for shaft_key in ("Atlatl dart shaft", "Atlatl dart shafts"):
        recipes.append(interface_recipe(
            shaft_key, [["Ent branch"]], "Atlatl dart shaft",
            "Interface twin for the knife-on-Ent-branch action (100 per "
            "branch, so a leading-count menu string is plausible)."))
    recipes.append(item_recipe(
        "Feather", "atlatl dart shaft", [["Atlatl dart shaft"], ["Feather"]],
        "Headless atlatl dart",
        "Feather on atlatl dart shafts -> headless (wiki-verified 2026-07-20: "
        "20 shafts + 20 feathers at Fletching 74). UNVERIFIED interface-vs-"
        "instant - lean instant, dart-family analogy."))
    recipes.append(item_recipe(
        "Headless atlatl dart", "atlatl dart tips", [["Headless atlatl dart"]],
        "Atlatl dart",
        "Atlatl dart tips (tip card absent) on headless atlatl dart -> atlatl "
        "dart. MISSING family (Varlamore), added this pass. UNVERIFIED "
        "interface-vs-instant - lean instant, dart-family analogy."))

    return recipes


def main():
    with open(PATH, encoding="utf-8") as f:
        data = json.load(f)

    before = len(data["recipes"])
    kept = [r for r in data["recipes"] if r.get("category") not in ("fletching", "feltching")]
    removed = before - len(kept)

    new_fletching = build()
    data["recipes"] = kept + new_fletching

    with open(PATH, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent="\t", ensure_ascii=False)
        f.write("\n")

    print(f"Removed {removed} old fletching/feltching entries.")
    print(f"Wrote {len(new_fletching)} new fletching recipes.")
    print(f"Total recipes: {len(data['recipes'])}")


if __name__ == "__main__":
    main()
