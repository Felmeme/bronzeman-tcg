const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const root = path.resolve(__dirname, "..");
const defaults = {
	"--cards": "C:/Users/ocari/Downloads/Card.with-ids.json",
	"--id-review": "C:/Users/ocari/AppData/Local/Temp/bronzeman-card-id-review.json",
	"--verified-cards": "D:/ClaudeFolder/RuneLite Research/card-id-pass/output/Card.with-ids.json",
	"--monster-drops": "C:/Users/ocari/Downloads/monster_drops.json",
	"--item-sources": "C:/Users/ocari/Downloads/item_sources.json",
	"--categories": "C:/Users/ocari/Downloads/card_categories.json",
	"--details": "C:/Users/ocari/Downloads/card_details.json",
};

function argumentsFrom(commandLine) {
	const result = {...defaults};
	for (let index = 0; index < commandLine.length; index += 2) {
		if (!result.hasOwnProperty(commandLine[index]) || !commandLine[index + 1]) {
			throw new Error(`Unknown or incomplete argument: ${commandLine[index]}`);
		}
		result[commandLine[index]] = commandLine[index + 1];
	}
	return result;
}

function readJson(file) {
	return JSON.parse(fs.readFileSync(file, "utf8").replace(/^\uFEFF/, ""));
}

function sameIds(left, right) {
	return JSON.stringify(left || []) === JSON.stringify(right || []);
}

const inputs = argumentsFrom(process.argv.slice(2));
const verifiedCards = readJson(inputs["--verified-cards"]);
const suppliedCards = fs.existsSync(inputs["--cards"]) ? readJson(inputs["--cards"]) : null;
const monsterDrops = readJson(inputs["--monster-drops"]);
const itemSources = readJson(inputs["--item-sources"]);
const categories = readJson(inputs["--categories"]);
const details = readJson(inputs["--details"]);
const verifiedByName = new Map(verifiedCards.map(card => [card.name, card]));
const cardNames = new Set(verifiedCards.map(card => card.name));
const resourceNames = new Set(verifiedCards
	.filter(card => card.category.includes("Resource"))
	.map(card => card.name));
const monsterNames = new Set(verifiedCards
	.filter(card => card.category.includes("Monster"))
	.map(card => card.name));

const generatedIdAdjustments = (suppliedCards || []).flatMap(card => {
	const verified = verifiedByName.get(card.name);
	if (!verified || (sameIds(card.itemIds, verified.itemIds) && sameIds(card.npcIds, verified.npcIds))) {
		return [];
	}
	return [{
		card: card.name,
		suppliedItemIds: card.itemIds || [],
		verifiedItemIds: verified.itemIds || [],
		suppliedNpcIds: card.npcIds || [],
		verifiedNpcIds: verified.npcIds || [],
	}];
});
const idAdjustments = generatedIdAdjustments.length > 0
	? generatedIdAdjustments
	: (fs.existsSync(inputs["--id-review"]) ? readJson(inputs["--id-review"]) : []);

const dropKeys = new Set();
for (const [monster, data] of Object.entries(monsterDrops)) {
	for (const drop of data.drops) {
		dropKeys.add(`${monster}\u001f${drop.card}\u001f${Boolean(drop.fromRdt)}`);
	}
}

const oneSidedSources = [];
const usedIn = new Map();
let hiddenIngredientRelationships = 0;
for (const [item, data] of Object.entries(itemSources)) {
	if (data.production) {
		for (const ingredient of data.production.ingredients) {
			if (resourceNames.has(ingredient.item)) {
				if (!usedIn.has(ingredient.item)) {
					usedIn.set(ingredient.item, []);
				}
				usedIn.get(ingredient.item).push({card: item, quantity: ingredient.quantity});
			} else {
				hiddenIngredientRelationships++;
			}
		}
	}
	for (const [field, fromRdt] of [["monsters", false], ["rdtMonsters", true]]) {
		for (const source of data[field]) {
			const key = `${source.card}\u001f${item}\u001f${fromRdt}`;
			if (!dropKeys.has(key)) {
				oneSidedSources.push({
					monster: source.card,
					item,
					sourceType: fromRdt ? "rare-drop-table" : "normal",
					rarity: source.rarity,
					fraction: source.fraction,
				});
			}
		}
	}
}

const cards = verifiedCards.map(card => {
	const extraCategories = categories[card.name];
	const base = {
		name: card.name,
		type: monsterNames.has(card.name) ? "monster" : "resource",
		ids: card.npcIds || card.itemIds || [],
		examine: details[card.name]?.examine || card.examine || "",
		categories: [...new Set([
			...(card.category || []).slice(1),
			...(Array.isArray(extraCategories)
				? extraCategories : (extraCategories ? [extraCategories] : [])),
		])],
	};
	if (base.type === "monster") {
		const data = monsterDrops[card.name];
		base.combatLevel = data.combatLevel;
		base.slayerLevel = data.slayerLevel;
		base.drops = data.drops;
		return base;
	}

	const source = itemSources[card.name];
	const markConfirmation = (entries, fromRdt) => entries.map(entry => ({
		...entry,
		confirmedByMonsterDrops: dropKeys.has(`${entry.card}\u001f${card.name}\u001f${fromRdt}`),
	}));
	base.sources = {
		monsters: markConfirmation(source.monsters, false),
		rareDropTable: markConfirmation(source.rdtMonsters, true),
		gathering: source.gathering,
		production: source.production ? {
			...source.production,
			ingredients: source.production.ingredients.map(ingredient => ({
				...ingredient,
				hasCard: cardNames.has(ingredient.item),
			})),
		} : null,
		usedIn: usedIn.get(card.name) || [],
		spawns: source.spawns,
		shops: source.shops,
		clueTiers: source.clueTiers,
	};
	return base;
});

const imageConflicts = verifiedCards.flatMap(card => {
	const detailUrl = details[card.name]?.imageUrl;
	return detailUrl && card.imageUrl && detailUrl !== card.imageUrl
		? [{
			card: card.name,
			supplied: card.imageUrl,
			details: detailUrl,
			resolution: card.name === "Mystic smoke staff"
				? "Resolved: the item page confirms item ID 12000. The details URL names the correct Mystic smoke staff; the supplied URL names Smoke battlestaff. Runtime artwork uses RuneLite item sprite 12000."
				: "Do not bundle either URL; use RuneLite item sprites by verified item ID.",
		}]
		: [];
});

const knowledge = {
	schemaVersion: 1,
	cards,
};
const audit = {
	summary: {
		cards: cards.length,
		resources: resourceNames.size,
		monsters: monsterNames.size,
		idAdjustments: idAdjustments.length,
		oneSidedSourceRelationships: oneSidedSources.length,
		hiddenNonCardIngredientRelationships: hiddenIngredientRelationships,
		imageConflicts: imageConflicts.length,
	},
	policy: {
		oneSidedSources: "Retained with confirmedByMonsterDrops=false; never silently promoted to confirmed.",
		images: "Remote image URLs are not bundled. Resource artwork will use RuneLite item sprites by verified ID.",
		nonCardIngredients: "Retained with hasCard=false so the UI can hide them and show a hidden count.",
	},
	idAdjustments,
	oneSidedSources,
	imageConflicts,
};

const resourcePath = path.join(root, "src/main/resources/card_knowledge.json.gz");
const auditPath = path.join(root, "docs/card_knowledge_audit.json");
fs.writeFileSync(resourcePath, zlib.gzipSync(Buffer.from(JSON.stringify(knowledge)), {level: 9, mtime: 0}));
fs.writeFileSync(auditPath, `${JSON.stringify(audit, null, 2)}\n`);
console.log(JSON.stringify({
	resourcePath,
	resourceBytes: fs.statSync(resourcePath).size,
	auditPath,
	...audit.summary,
}, null, 2));
