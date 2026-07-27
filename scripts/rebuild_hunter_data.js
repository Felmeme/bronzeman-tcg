const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const resourcePath = path.join(root, "src/main/resources/resource_nodes.json");
const itemPath = path.join(root, "src/main/resources/tracked_item_names.json");
const monsterPath = path.join(root, "src/main/resources/tracked_monster_names.json");

const snapshot = JSON.parse(fs.readFileSync(resourcePath, "utf8"));
const itemCards = JSON.parse(fs.readFileSync(itemPath, "utf8")).entityToCards;
const monsterCards = JSON.parse(fs.readFileSync(monsterPath, "utf8")).entityToCards;
const knownCards = new Set(
	[...Object.values(itemCards), ...Object.values(monsterCards)]
		.flat()
		.map((name) => name.toLowerCase())
);

function node(category, kind, name, options, groups, roles, notes)
{
	return {
		category,
		kind,
		name,
		options,
		requiredCardGroups: groups,
		groupRoles: roles,
		requireAll: true,
		notes
	};
}

const hunterNodes = [
	node(
		"hunter-birds", "inventory", "Bird snare", ["lay"],
		[["Bird snare"]], ["tool"],
		"Tools Only requires Bird snare. All Cards adds the reviewed area's bird "
			+ "Monster card where one exists plus guaranteed Bones, Raw bird meat and "
			+ "the normal feather; rumour-only Tailfeathers are excluded in code."
	),
	...[
		"Black warlock",
		"Moonlight moth",
		"Ruby harvest",
		"Sapphire glacialis",
		"Snowy knight",
		"Sunlight moth"
	].map((name) => node(
		"hunter-butterflies", "npc", name, ["catch"],
		[["Butterfly net", "Magic butterfly net"], ["Butterfly jar"]],
		["tool", "tool"],
		"Both modes require either net card plus Butterfly jar, including bare-handed catches. "
			+ "No butterfly or moth Monster cards exist."
	)),
	node(
		"hunter-chins", "inventory", "Box trap", ["lay"],
		[["Box trap"]], ["tool"],
		"Tools Only requires Box trap. All Cards adds both the reviewed area's Hunter "
			+ "Monster card and the corresponding caught chinchompa item card in code."
	),
	...[
		["Baby impling", "Baby impling jar"],
		["Crystal impling", "Crystal impling jar"],
		["Dragon impling", "Dragon impling jar"],
		["Earth impling", "Earth impling jar"],
		["Eclectic impling", "Eclectic impling jar"],
		["Essence impling", "Essence impling jar"],
		["Gourmet impling", "Gourmet impling jar"],
		["Lucky impling", "Lucky impling jar"],
		["Magpie impling", "Magpie impling jar"],
		["Nature impling", "Nature impling jar"],
		["Ninja impling", "Ninja impling jar"],
		["Young impling", "Young impling jar"]
	].map(([name, output]) => node(
		"hunter-implings", "npc", name, ["catch"],
		[["Magic butterfly net"], ["Impling jar"], [output]],
		["tool", "tool", "output"],
		"Tools Only requires Magic butterfly net + Impling jar even when catching "
			+ "bare-handed. All Cards additionally requires this impling's filled-jar card."
	)),
	node(
		"hunter-salamanders", "object", "Young tree", ["set-trap"],
		[["Rope"], ["Small fishing net"]], ["tool", "tool"],
		"Tools Only requires Rope + Small fishing net. All Cards adds the exact caught "
			+ "salamander item card from RuneLite's distinct unset-tree ObjectID."
	),
	node(
		"hunter-pitfalls", "npc", "Horned graahk", ["tease"],
		[["Teasing stick"], ["Horned graahk"], ["Big bones"], ["Raw graahk"]],
		["tool", "monster", "loot", "loot"],
		"All Cards includes the Monster card and guaranteed ordinary loot only. Graahk "
			+ "fur variants and the rumour-only Graahk horn spur are excluded."
	),
	node(
		"hunter-pitfalls", "npc", "Spined larupia", ["tease"],
		[["Teasing stick"], ["Big bones"], ["Raw larupia"]],
		["tool", "loot", "loot"],
		"No Spined larupia Monster card exists. Fur variants and the rumour-only "
			+ "Larupia ear are excluded."
	),
	node(
		"hunter-pitfalls", "npc", "Sabre-toothed kyatt", ["tease"],
		[["Teasing stick"], ["Big bones"], ["Raw kyatt"]],
		["tool", "loot", "loot"],
		"No Sabre-toothed kyatt Monster card exists. Fur variants and the rumour-only "
			+ "Kyatt tooth chip are excluded."
	),
	node(
		"hunter-pitfalls", "npc", "Sunlight antelope", ["tease"],
		[
			["Teasing stick"],
			["Sunlight antelope"],
			["Big bones"],
			["Raw sunlight antelope"],
			["Sunlight antelope antler"],
			["Sunlight antelope fur"],
			["Sunfire splinters"]
		],
		["tool", "monster", "loot", "loot", "loot", "loot", "loot"],
		"All normal outputs listed here are guaranteed. The rumour-only Antelope hoof "
			+ "shard is excluded."
	),
	node(
		"hunter-pitfalls", "npc", "Moonlight antelope", ["tease"],
		[
			["Teasing stick"],
			["Moonlight antelope"],
			["Big bones"],
			["Raw moonlight antelope"],
			["Moonlight antelope antler"],
			["Moonlight antelope fur"]
		],
		["tool", "monster", "loot", "loot", "loot", "loot"],
		"All normal outputs listed here are guaranteed. The rumour-only Antelope hoof "
			+ "shard is excluded."
	)
];

const replacedCategories = new Set([
	"hunter-birds",
	"hunter-butterflies",
	"hunter-chins",
	"hunter-implings",
	"hunter-salamanders",
	"hunter-pitfalls"
]);
const firstHunterIndex = snapshot.nodes.findIndex((entry) =>
	replacedCategories.has(entry.category));
snapshot.nodes = snapshot.nodes.filter((entry) =>
	!replacedCategories.has(entry.category));
snapshot.nodes.splice(firstHunterIndex, 0, ...hunterNodes);

const badCards = [];
for (const entry of hunterNodes)
{
	for (const group of entry.requiredCardGroups)
	{
		for (const card of group)
		{
			if (!knownCards.has(card.toLowerCase()))
			{
				badCards.push(`${entry.category}/${entry.name}: ${card}`);
			}
		}
	}
}
if (badCards.length)
{
	throw new Error(`Unknown Hunter card references:\n${badCards.join("\n")}`);
}

const keys = new Set();
for (const entry of hunterNodes)
{
	for (const option of entry.options)
	{
		const key = `${entry.kind}|${entry.name}|${option}`.toLowerCase();
		if (keys.has(key))
		{
			throw new Error(`Duplicate Hunter interaction key: ${key}`);
		}
		keys.add(key);
	}
}

fs.writeFileSync(resourcePath, `${JSON.stringify(snapshot, null, "\t")}\n`);
console.log(`Rebuilt ${hunterNodes.length} ordinary Hunter rules.`);
console.log("Validated every Hunter card reference and interaction key.");
