#!/usr/bin/env node
"use strict";

/**
 * Rebuilds only the "herblore" recipes from docs/herblore_actions.json.
 *
 * The source matrix is a cached extraction of the single OSRS Wiki Herblore
 * table. Mastering Mixology, order-dependent multi-herb recipes and recipes
 * whose intermediate item name is not established are deliberately excluded.
 */
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const sourcePath = path.join(root, "docs", "herblore_actions.json");
const recipePath = path.join(root, "src", "main", "resources", "recipe_nodes.json");
const cardPath = path.join(root, "src", "main", "resources", "tracked_item_names.json");

const readJson = file => JSON.parse(fs.readFileSync(file, "utf8").replace(/^\uFEFF/, ""));
const source = readJson(sourcePath);
const snapshot = readJson(recipePath);
const cardSnapshot = readJson(cardPath);
const cards = new Map();
for (const values of Object.values(cardSnapshot.entityToCards))
{
	for (const card of values)
	{
		cards.set(card.toLowerCase(), card);
	}
}

const herbs = new Map([
	["Guam leaf", "Guam potion"],
	["Marrentill", "Marrentill potion"],
	["Tarromin", "Tarromin potion"],
	["Harralander", "Harralander potion"],
	["Ranarr weed", "Ranarr potion"],
	["Toadflax", "Toadflax potion"],
	["Irit leaf", "Irit potion"],
	["Avantoe", "Avantoe potion"],
	["Kwuarm", "Kwuarm potion"],
	["Huasca", "Huasca potion"],
	["Snapdragon", "Snapdragon potion"],
	["Cadantine", "Cadantine potion"],
	["Lantadyme", "Lantadyme potion"],
	["Dwarf weed", "Dwarf weed potion"],
	["Torstol", "Torstol potion"],
]);

const specialIntermediates = new Map([
	["Elkhorn coral", "Elkhorn potion"],
	["Pillar coral", "Pillar potion"],
	["Umbral coral", "Umbral potion"],
]);

const excluded = new Map([
	["Imp repellent", "many interchangeable flowers; no exact trigger family yet"],
	["Relicym's balm", "order-dependent two-herb quest recipe"],
	["Guthix rest tea", "order-independent four-herb tea recipe"],
	["Guthix balance", "multi-stage recipe whose target changes after each ingredient"],
	["Sanfew serum", "multi-stage recipe whose target changes after each ingredient"],
	["Magic essence", "uncarded output and unverified Star flower vial target name"],
	["Weapon poison+", "uncarded intermediate target name is not established"],
	["Weapon poison++", "uncarded intermediate target name is not established"],
]);

const recipes = [];
function tracked(name)
{
	return name == null ? null : cards.get(name.toLowerCase()) || null;
}

function addRecipe(inputs, output, triggerName, targets, interfaceName, interfaceTargets,
	stage, notes)
{
	const inputGroups = inputs
		.map(group => (Array.isArray(group) ? group : [group]).map(tracked).filter(Boolean))
		.filter(group => group.length > 0);
	const cardOutput = tracked(output);
	if (inputGroups.length === 0 && !cardOutput)
	{
		return;
	}
	const common = {
		category: "herblore",
		stage,
		inputs: inputGroups,
		output: cardOutput,
		notes,
	};
	recipes.push({
		...common,
		trigger: {
			kind: "item-on-item",
			name: triggerName,
			targets: targets.map(value => value.toLowerCase()),
		},
	});
	recipes.push({
		...common,
		trigger: {
			kind: "interface",
			name: interfaceName,
			targets: interfaceTargets.map(value => value.toLowerCase()),
		},
	});
}

// Ordinary unfinished potions.
for (const [herb, potion] of herbs)
{
	addRecipe([herb, "Vial of water"], potion, herb, ["Vial of water"],
		`${potion} (unf)`, ["Vial of water"], "unfinished",
		"Standard unfinished potion; source: OSRS Wiki Herblore table.");
}

// Alternate-base intermediates used by later standard recipes.
addRecipe(["Toadflax", "Coconut milk"], "Toadflax potion", "Toadflax", ["Coconut milk"],
	"Toadflax potion (unf)", ["Coconut milk"], "unfinished",
	"Antidote+ unfinished potion.");
addRecipe(["Irit leaf", "Coconut milk"], "Irit potion", "Irit leaf", ["Coconut milk"],
	"Irit potion (unf)", ["Coconut milk"], "unfinished",
	"Antidote++ unfinished potion.");
addRecipe(["Cadantine", "Vial of blood"], "Cadantine blood potion", "Cadantine",
	["Vial of blood"], "Cadantine blood potion (unf)", ["Vial of blood"], "unfinished",
	"Bastion/Battlemage unfinished potion.");
for (const [primary, potion] of specialIntermediates)
{
	addRecipe([primary, "Vial of water"], potion, primary, ["Vial of water"],
		`${potion} (unf)`, ["Vial of water"], "unfinished",
		"Varlamore unfinished potion; source: OSRS Wiki Herblore table.");
}

for (const row of source.recipes)
{
	if (excluded.has(row.output))
	{
		continue;
	}

	if (row.output === "Super combat potion")
	{
		addRecipe(["Super attack", "Super strength", "Super defence", "Torstol"],
			row.output, "Torstol", ["Super attack", "Super strength", "Super defence"],
			row.output, ["Torstol"], "upgrade",
			"Super combat potion; all three four-dose super potions and Torstol are required.");
		continue;
	}

	if (row.ingredients.length === 2)
	{
		addRecipe(row.ingredients, row.output, row.ingredients[1], [row.ingredients[0]],
			row.output, [row.ingredients[1]], "upgrade",
			"Two-item standard Herblore upgrade; source: OSRS Wiki Herblore table.");
		continue;
	}

	const [base, primary, secondary] = row.ingredients;
	let intermediate = herbs.get(primary) || specialIntermediates.get(primary);
	if (base === "Vial of blood" && primary === "Cadantine")
	{
		intermediate = "Cadantine blood potion";
	}
	if (!intermediate || !secondary || secondary.includes(" x2 ")
		|| secondary.includes(" Nail beast nails"))
	{
		continue;
	}

	const secondaryAlternatives = secondary === "Cave nightshade Nightshade"
		? ["Cave nightshade", "Nightshade"] : [secondary];
	for (const secondaryName of secondaryAlternatives)
	{
		const unfinishedItem = `${intermediate} (unf)`;
		addRecipe([intermediate, secondaryAlternatives], row.output, secondaryName,
			[unfinishedItem], row.output, [secondaryName], "finished",
			"Standard finished potion; source: OSRS Wiki Herblore table. Card names are dose-less.");
	}
}

const keys = new Map();
for (const recipe of recipes)
{
	for (const target of recipe.trigger.targets)
	{
		const key = `${recipe.trigger.kind}|${recipe.trigger.name.toLowerCase()}|${target}`;
		if (keys.has(key))
		{
			throw new Error(`Herblore trigger collision: ${key}`);
		}
		keys.set(key, recipe);
	}
}

const withoutHerblore = snapshot.recipes.filter(recipe => recipe.category !== "herblore");
const insertionIndex = withoutHerblore.findIndex(recipe => recipe.category === "fletching");
withoutHerblore.splice(insertionIndex < 0 ? withoutHerblore.length : insertionIndex, 0, ...recipes);
snapshot.recipes = withoutHerblore;
fs.writeFileSync(recipePath, JSON.stringify(snapshot, null, "\t") + "\n");

const interactionCounts = recipes.reduce((counts, recipe) =>
{
	counts[recipe.trigger.kind] = (counts[recipe.trigger.kind] || 0) + 1;
	return counts;
}, {});
console.log(`Rebuilt ${recipes.length / 2} Herblore recipes across ${recipes.length} interactions.`);
console.log(`Generated ${interactionCounts["item-on-item"]} item-on-item and `
	+ `${interactionCounts.interface} interface rules.`);
console.log(`Excluded ${excluded.size} explicitly unverified multi-stage recipes.`);
