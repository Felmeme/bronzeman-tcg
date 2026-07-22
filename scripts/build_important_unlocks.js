/**
 * Build the broad owner-editable Important Unlocks snapshot.
 *
 * Usage:
 *   node scripts/build_important_unlocks.js C:\\Users\\ocari\\Downloads\\card_categories.json
 *
 * The supplied export classifies Weapon / Armour. The bundled consumables snapshot
 * already separates food and potions. Tools take priority over Weapons: woodcutting
 * axes come from the exact runtime list and every * pickaxe is a mining tool, matching
 * Bronzeman's live gathering checks.
 */

const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const categoryPath = process.argv[2];
if (!categoryPath)
{
	throw new Error('Expected the path to card_categories.json');
}

const categories = JSON.parse(fs.readFileSync(categoryPath, 'utf8'));
const consumables = JSON.parse(fs.readFileSync(
	path.join(root, 'src/main/resources/consumables.json'), 'utf8'));
const trackedItems = JSON.parse(fs.readFileSync(
	path.join(root, 'src/main/resources/tracked_item_names.json'), 'utf8'));
const pluginSource = fs.readFileSync(
	path.join(root, 'src/main/java/com/bronzemantcg/BronzemanTcgPlugin.java'), 'utf8');

const axesMatch = pluginSource.match(/WOODCUTTING_AXES = new HashSet<>\(List\.of\(([\s\S]*?)\)\);/);
if (!axesMatch)
{
	throw new Error('Could not find WOODCUTTING_AXES in BronzemanTcgPlugin.java');
}
const woodcuttingAxes = new Set(Array.from(axesMatch[1].matchAll(/"([^"]+)"/g), match => match[1]));

const weapons = new Set(Object.entries(categories)
	.filter(([, tags]) => tags.includes('Weapon'))
	.map(([name]) => name));
const armour = new Set(Object.entries(categories)
	.filter(([, tags]) => tags.includes('Armour'))
	.map(([name]) => name));
const byLowerName = new Map(Object.keys(categories).map(name => [name.toLowerCase(), name]));
const trackedByLowerName = new Map(Object.values(trackedItems.entityToCards).flat()
	.map(name => [name.toLowerCase(), name]));

// The bundled consumables snapshot currently has four documented mojibake names. Prefer
// the tracked item snapshot's literal spelling so generated data validates at startup.
const trackedName = name => {
	const exact = trackedByLowerName.get(name.toLowerCase());
	if (exact)
	{
		return exact;
	}
	const decoded = Buffer.from(name, 'latin1').toString('utf8');
	return trackedByLowerName.get(decoded.toLowerCase()) || name;
};

for (const weapon of weapons)
{
	if (weapon.toLowerCase().endsWith(' pickaxe'))
	{
		woodcuttingAxes.add(weapon.toLowerCase());
	}
}
const tools = new Set(Array.from(woodcuttingAxes)
	.map(lower => byLowerName.get(lower))
	.filter(Boolean));
for (const tool of tools)
{
	weapons.delete(tool);
}

const sortNames = names => Array.from(names, trackedName).sort((a, b) => a.localeCompare(b));
const output = {
	_comment: 'Owner-editable Important Unlocks categories. Built from card_categories.json and consumables.json; every name is validated against the bundled item-card catalogue at startup. Tools take priority over Weapons.',
	categories: [
		{name: 'Tools', items: sortNames(tools)},
		{name: 'Weapons', items: sortNames(weapons)},
		{name: 'Armor', items: sortNames(armour)},
		{name: 'Potions', items: sortNames(consumables.potions)},
		{name: 'Food', items: sortNames(consumables.food)},
	]
};

const outputPath = path.join(root, 'src/main/resources/important_unlocks.json');
fs.writeFileSync(outputPath, `${JSON.stringify(output, null, 2)}\n`, 'utf8');
console.log(`Wrote ${outputPath}: ${output.categories.map(c => `${c.name}=${c.items.length}`).join(', ')}`);
