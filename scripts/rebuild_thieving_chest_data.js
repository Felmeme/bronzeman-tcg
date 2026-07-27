const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const resourcePath = path.join(root, "src/main/resources/resource_nodes.json");
const itemPath = path.join(root, "src/main/resources/tracked_item_names.json");
const snapshot = JSON.parse(fs.readFileSync(resourcePath, "utf8"));
const itemCards = JSON.parse(fs.readFileSync(itemPath, "utf8")).entityToCards;
const knownCards = new Set(
	Object.values(itemCards).flat().map((name) => name.toLowerCase())
);

const seeds = [
	"Limpwurt seed", "Strawberry seed", "Marrentill seed", "Jangerberry seed",
	"Tarromin seed", "Wildblood seed", "Watermelon seed", "Harralander seed",
	"Snape grass seed", "Ranarr seed", "Whiteberry seed", "Mushroom spore",
	"Toadflax seed", "Belladonna seed", "Irit seed", "Poison ivy seed",
	"Avantoe seed", "Cactus seed", "Kwuarm seed", "Potato cactus seed",
	"Snapdragon seed", "Cadantine seed", "Lantadyme seed", "Dwarf weed seed",
	"Torstol seed"
];
const fourGems = [
	"Uncut sapphire", "Uncut emerald", "Uncut ruby", "Uncut diamond"
];

const chests = [
	{
		name: "Underwater chest",
		objectIds: [30971],
		options: ["search"],
		cards: [
			"Mermaid's tear", "Glistening tear", "Numulite",
			"Unidentified small fossil", "Unidentified medium fossil",
			"Unidentified large fossil", "Unidentified rare fossil"
		],
		source: "Chest (Underwater); searchable open-state ObjectID FOSSIL_CHEST_OPEN"
	},
	{
		name: "10 coin chest",
		objectIds: [11735],
		options: ["search for traps"],
		cards: ["Coins"],
		source: "Chest (10 coins); ObjectID TRAPCHEST1"
	},
	{
		name: "Nature rune chest",
		objectIds: [11736],
		options: ["search for traps"],
		cards: ["Nature rune", "Coins"],
		source: "Chest (nature runes); ObjectID TRAPCHEST2"
	},
	{
		name: "Isle of Souls chest",
		objectIds: [40739],
		options: ["picklock"],
		cards: [
			"Coins", "Feather", "Chocolate bar", "Bird snare", "Box trap",
			"Grimy guam leaf", "Grimy lantadyme", "Grimy ranarr weed",
			...fourGems, "Mithril pickaxe", "Mithril axe", "Adamant scimitar",
			"Mind rune", "Nature rune", "Death rune", "Dark key"
		],
		source: "Chest (Isle of Souls Dungeon); clue scroll excluded (no card)"
	},
	{
		name: "Rusty pirate chest",
		objectIds: [60511, 60512],
		options: ["picklock"],
		cards: [
			"Medallion fragment", "Steel dagger", "Steel scimitar",
			"Bronze cannonball", "Iron cannonball", "Steel cannonball",
			...fourGems, "Elkhorn frag", "Pillar frag", "Umbral frag", "Coins",
			"Silver bar", "Gold bar", "Mind rune", "Sapphire necklace",
			"Emerald necklace"
		],
		source: "Rusty chest; Medallion fragment#1 folds to its card name; clue excluded"
	},
	{
		name: "Aldarin Villas chest",
		objectIds: [54773],
		options: ["picklock"],
		cards: [
			"Coins", "Silver bar", "Gold bar", ...fourGems, "Spinach roll",
			"Blessed bone shards", "Eclipse red", ...seeds
		],
		source: "Chest (Aldarin Villas); clue scroll excluded (no card)"
	},
	{
		name: "50 coin chest",
		objectIds: [11737],
		options: ["search for traps"],
		cards: ["Coins"],
		source: "Chest (50 coins); ObjectID TRAPCHEST3"
	},
	{
		name: "Steel arrowtips chest",
		objectIds: [11742],
		options: ["search for traps"],
		cards: ["Coins"],
		source: "Chest (steel arrowtips); Steel arrowtips has no card"
	},
	{
		name: "Dorgesh-Kaan average chest",
		objectIds: [22697, 22698],
		options: ["pick-lock"],
		cards: [
			"Coins", "Oil lantern", "Bullseye lantern", "Mining helmet",
			"Cave goblin wire", "Rope", "Frog-leather body", "Frog-leather chaps",
			"Newcomer map", "Bone bolts", "Dorgeshuun crossbow", "Bone dagger",
			"Hammer", "Big bones", "Ham hood", "Ham logo", "Ham shirt",
			"Ham robe", "Ham gloves", "Ham cloak", "Ham boots", "Spade", "Bucket",
			"Air rune", "Mind rune", "Water rune", "Earth rune", "Fire rune",
			"Body rune", "Cosmic rune", "Chaos rune", "Law rune", "Nature rune",
			"Death rune", "Dorgesh-kaan sphere", "Unpowered orb", "Empty light orb",
			"Light orb", "Air talisman", "Water talisman", "Earth talisman",
			"Fire talisman"
		],
		source: "Chest (Dorgesh-Kaan Average); easy clue excluded (no card)"
	},
	{
		name: "Tarnished pirate chest",
		objectIds: [60514, 60515],
		options: ["picklock"],
		cards: [
			"Mithril dagger", "Mithril scimitar", "Iron cannonball",
			"Steel cannonball", "Mithril cannonball", ...fourGems, "Pillar frag",
			"Elkhorn frag", "Umbral frag", "Coins", "Silver bar", "Gold bar",
			"Chaos rune", "Emerald necklace", "Ruby necklace"
		],
		source: "Tarnished chest; clue scroll excluded (no card)"
	},
	{
		name: "Blood rune chest",
		objectIds: [11738],
		options: ["search for traps"],
		cards: ["Blood rune", "Coins"],
		source: "Chest (blood runes); ObjectID TRAPCHEST4"
	},
	{
		name: "Stone chest",
		objectIds: [34429],
		options: ["picklock"],
		cards: [
			...seeds, "Coins", "Xerician fabric", "Lizardman fang",
			"Uncut sapphire", "Uncut ruby", "Xeric's talisman"
		],
		source: "Stone chest; bolt tips and clue scroll excluded (no cards); "
			+ "inert Xeric's talisman folds to the base card"
	},
	{
		name: "Ardougne Castle chest",
		objectIds: [11739],
		options: ["search for traps"],
		cards: ["Coins", "Raw shark", "Adamantite ore", "Uncut sapphire"],
		source: "Chest (Ardougne Castle); ObjectID TRAPCHEST5"
	},
	{
		name: "Reinforced pirate chest",
		objectIds: [60517, 60518],
		options: ["picklock"],
		cards: [
			"Adamant dagger", "Adamant scimitar", "Steel cannonball",
			"Mithril cannonball", "Adamant cannonball", ...fourGems, "Umbral frag",
			"Elkhorn frag", "Pillar frag", "Coins", "Silver bar", "Gold bar",
			"Death rune", "Ruby necklace", "Diamond necklace"
		],
		source: "Reinforced chest; clue scroll excluded (no card)"
	},
	{
		name: "Dorgesh-Kaan rich chest",
		objectIds: [22681],
		options: ["pick-lock"],
		cards: [
			"Uncut sapphire", "Uncut emerald", "Uncut ruby", "Uncut diamond",
			"Uncut opal", "Uncut jade", "Uncut red topaz", "Bullseye lantern",
			"Mining helmet", "Frog-leather chaps", "Frog-leather body", "Iron bar",
			"Cave goblin wire", "Light orb", "Empty light orb"
		],
		source: "Chest (Dorgesh-Kaan Rich)"
	},
	{
		name: "Rogues' Castle chest",
		objectIds: [26757],
		options: ["search for traps"],
		cards: [
			"Nature rune", "Red spiders' eggs", "Law rune", "Coal", "Coins",
			"Vile ashes", "Uncut diamond", "Uncut emerald",
			"Blighted ancient ice sack", "Iron ore", "Chaos rune", "Death rune",
			"Blighted manta ray", "Blighted anglerfish", "Uncut sapphire",
			"Prayer potion", "Dragonstone"
		],
		source: "Chest (Rogues' Castle); Prayer potion dose folded; clue excluded"
	}
];

const chestNodes = chests.map((chest) => ({
	category: "thieving-chests",
	kind: "object",
	name: chest.name,
	objectIds: chest.objectIds,
	options: chest.options,
	requiredCardGroups: [chest.cards],
	groupRoles: ["loot"],
	requireAll: false,
	notes: `${chest.source}. Plain OSRS Wiki page audited 2026-07-26.`
}));

for (const chest of chests)
{
	if (!chest.cards.length)
	{
		throw new Error(`${chest.name} has no card-backed loot`);
	}
	for (const card of chest.cards)
	{
		if (!knownCards.has(card.toLowerCase()))
		{
			throw new Error(`${chest.name} references unknown card: ${card}`);
		}
	}
}

const keys = new Set();
for (const chest of chests)
{
	for (const objectId of chest.objectIds)
	{
		for (const option of chest.options)
		{
			const key = `object|#${objectId}|${option}`.toLowerCase();
			if (keys.has(key))
			{
				throw new Error(`Duplicate chest interaction: ${key}`);
			}
			keys.add(key);
		}
	}
}

snapshot.nodes = snapshot.nodes.filter((entry) => entry.category !== "thieving-chests");
let insertionIndex = -1;
for (let i = 0; i < snapshot.nodes.length; i++)
{
	if (snapshot.nodes[i].category === "thieving-stalls")
	{
		insertionIndex = i + 1;
	}
}
snapshot.nodes.splice(insertionIndex, 0, ...chestNodes);
fs.writeFileSync(resourcePath, `${JSON.stringify(snapshot, null, "\t")}\n`);

console.log(`Rebuilt ${chestNodes.length} thievable chest rules.`);
console.log(`Validated ${keys.size} object-ID/menu-option combinations.`);
