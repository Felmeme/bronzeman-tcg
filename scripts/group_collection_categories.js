/**
 * Add navigation subcategories to the largest flat Collection groups.
 *
 * This updates only Weapons, Armor, and Unenchanted Jewellery. Every input
 * card is assigned exactly once; names which do not match a stable item-type
 * pattern stay visible under an explicit Other group.
 */
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const resourcePath = path.join(root, 'src/main/resources/important_unlocks.json');
const snapshot = JSON.parse(fs.readFileSync(resourcePath, 'utf8'));

const rules = {
	Weapons: [
		['Ranged', /\b(?:bow|crossbow|dart|knife|thrownaxe|javelin|chinchompa|blowpipe|ballista|atlatl|salamander)\b/i],
		['Magic', /\b(?:staff|battlestaff|wand|sceptre|trident)\b/i],
		['Melee', /./],
	],
	Armor: [
		['Head', /\b(?:helm|helmet|hood|hat|mask|coif|mitre|headband|crown|faceguard|goggles)\b/i],
		['Body', /\b(?:body|top|shirt|jacket|chainbody|chestplate|torso|hauberk|tunic)\b/i],
		['Legs', /\b(?:legs|platelegs|plateskirt|skirt|bottom|chaps|trousers|shorts|greaves|tassets)\b/i],
		['Hands', /\b(?:gloves|gauntlets|bracers)\b/i],
		['Feet', /\b(?:boots|shoes|sandals)\b/i],
		['Shields', /\b(?:shield|defender|ward|buckler)\b/i],
		['Capes', /\b(?:cape|cloak|quiver)\b/i],
		['Jewellery', /\b(?:amulet|necklace|ring|bracelet)\b/i],
		['Other Armor', /./],
	],
	'Unenchanted Jewellery': [
		['Amulets', /\bamulet\b/i],
		['Bracelets', /\bbracelet\b/i],
		['Necklaces', /\bnecklace\b/i],
		['Rings', /\bring\b/i],
		['Other Jewellery', /./],
	],
};

for (const category of snapshot.categories)
{
	const categoryRules = rules[category.name];
	if (!categoryRules)
	{
		continue;
	}
	if (!Array.isArray(category.items))
	{
		throw new Error(`${category.name} is no longer a flat category`);
	}

	const before = [...category.items];
	const grouped = new Map(categoryRules.map(([name]) => [name, []]));
	for (const card of before)
	{
		const match = categoryRules.find(([, pattern]) => pattern.test(card));
		if (!match)
		{
			throw new Error(`No ${category.name} group for ${card}`);
		}
		grouped.get(match[0]).push(card);
	}

	category.subcategories = categoryRules
		.map(([name]) => ({name, items: grouped.get(name)}))
		.filter(group => group.items.length > 0);
	delete category.items;

	const after = category.subcategories.flatMap(group => group.items);
	const beforeSorted = [...before].sort((a, b) => a.localeCompare(b));
	const afterSorted = [...after].sort((a, b) => a.localeCompare(b));
	if (JSON.stringify(beforeSorted) !== JSON.stringify(afterSorted)
		|| new Set(after).size !== after.length)
	{
		throw new Error(`${category.name} membership changed during grouping`);
	}

	console.log(`${category.name}: ${category.subcategories
		.map(group => `${group.name}=${group.items.length}`).join(', ')}`);
}

fs.writeFileSync(resourcePath, `${JSON.stringify(snapshot, null, 2)}\n`, 'utf8');
