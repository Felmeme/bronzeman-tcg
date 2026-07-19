package com.bronzemantcg;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(BronzemanTcgConfig.GROUP)
public interface BronzemanTcgConfig extends Config
{
	String GROUP = "bronzemantcg";

	@ConfigSection(
			name = "General Settings",
			description = "Restrictions on holding, equipping, and acquiring items whose card is locked.",
			position = 0
	)
	String generalSettings = "generalSettings";

//  Items and Economy tab, was used in earlier iterations. Commented out in case of potential use later.
//	@ConfigSection(
//			name = "Items & economy",
//			description = "Restrictions on holding, equipping, and acquiring items whose card is locked.",
//			position = #
//	)
//	String itemsSection = "itemsSection";

//	@ConfigSection(
//			name = "Resource nodes",
//			description = "Block gathering from skill resource nodes until the card of the item they yield is collected.",
//			position = 2
//	)
//	String resourceNodesSection = "resourceNodesSection";

	@ConfigSection(
			name = "Firemaking",
			description = "Lighting fires requires the log (and optionally Tinderbox) cards.",
			position = 6
	)
	String firemakingSection = "firemakingSection";

	@ConfigSection(
			name = "Smithing",
			description = "Smelting bars and smithing items require ore, bar and product cards.",
			position = 8
	)
	String smithingSection = "smithingSection";

	@ConfigSection(
			name = "Cooking",
			description = "Cooking raw food requires the cooked (and optionally burnt) item cards.",
			position = 3
	)
	String cookingSection = "cookingSection";

	@ConfigSection(
			name = "Crafting",
			description = "Crafting and enchanting require the input and output item cards.",
			position = 4
	)
	String craftingSection = "craftingSection";

//	@ConfigSection(
//			name = "Skill Options",
//			description = "Recipe restrictions for the remaining skills: making things requires the cards of what goes in and/or what comes out.",
//			position = 2
//	)
//	String skillOptionsSection = "skillOptionsSection";

	@ConfigSection(
			name = "Hunter",
			description = "Hunting requires the gear cards (and optionally the creature cards) for each method.",
			position = 7
	)
	String hunterSection = "hunterSection";

	@ConfigSection(
			name = "Farming",
			description = "Patch and compost restrictions.",
			position = 5
	)
	String farmingSection = "farmingSection";

	@ConfigSection(
			name = "Slayer",
			description = "Slayer master restrictions.",
			position = 9
	)
	String slayerSection = "slayerSection";

	@ConfigSection(
			name = "Thieving",
			description = "Pickpocketing restrictions.",
			position = 11
	)
	String thievingSection = "thievingSection";

	@ConfigSection(
			name = "Sailing",
			description = "Boat upgrade and salvaging restrictions.",
			position = 10
	)
	String sailingSection = "sailingSection";

	@ConfigSection(
			name = "Visuals",
			description = "How locked NPCs and items are shown in the game world.",
			position = 1
	)
	String visualsSection = "visualsSection";

	@ConfigSection(
			name = "Mining",
			description = "Mining restrictions.",
			position = 13
	)
	String miningSection = "miningSection";

	@ConfigSection(
			name = "Woodcutting",
			description = "Woodcutting restrictions.",
			position = 12
	)
	String woodcuttingSection = "woodcuttingSection";

	@ConfigSection(
			name = "Fishing",
			description = "Fishing restrictions.",
			position = 14
	)
	String fishingSection = "fishingSection";

	@ConfigSection(
			name = "Fletching",
			description = "Fletching restrictions.",
			position = 15
	)
	String fletchingSection = "fletchingSection";

	@ConfigSection(
			name = "Herblore",
			description = "Herblore restrictions.",
			position = 16
	)
	String herbloreSection = "herbloreSection";

	@ConfigSection(
			name = "Runecrafting",
			description = "Runecrafting restrictions.",
			position = 17
	)
	String runecraftingSection = "runecraftingSection";


//	@ConfigSection(
//			name = "Construction",
//			description = "Construction restrictions.",
//			position = #
//	)
//	String constructionSection = "constructionSection";

//	@ConfigSection(
//			name = "Agility",
//			description = "Agility restrictions.",
//			position = #
//	)
//	String agilitySection = "agilitySection";

	//----------------
	//General Settings
	//----------------
	@ConfigItem(
		keyName = "npcVisibilityMode",
		name = "NPC Locks",
		description = "'Prevent Combat': the Attack option is hidden and offensive spells are blocked; "
			+ "talking and using items on the NPC still work."
			+ "<br>'Prevent Interaction': every menu option except Examine is removed, and items "
			+ "can't be used on the NPC."
			+ "<br>'Hide NPCs': locked NPCs are invisible."
			+ "<br>NPCs of quests you have started are always shown and talkable (Attack still "
			+ "needs the card); slayer masters follow the Slayer section's own rules.",
		section = generalSettings,
		position = 1
	)
	default NpcVisibilityMode npcVisibilityMode()
	{
		return NpcVisibilityMode.PREVENT_COMBAT;
	}

	@ConfigItem(
		keyName = "npcVisibilityMigrated",
		name = "",
		description = "",
		hidden = true,
		section = generalSettings,
		position = 98
	)
	default boolean npcVisibilityMigrated()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groundItemsMode",
		name = "Ground Items",
		description = "'Require Card': hides options for picking up Ground Items"
			+ "<br>'No Card Needed': Ground items do not require their card to unlock.",
		section = generalSettings,
		position = 3
	)
	default LockState groundItemsMode()
	{
		return LockState.LOCKED;
	}

	@ConfigItem(
			keyName = "itemUsageMode",
			name = "Item Usage",
			description = "'Require Card': Items require their Card. Hides everything other than Drop/Destroy"
				+ "<br> No Card Needed: You can freely use items without their card.",
			section = generalSettings,
			position = 4
	)
	default LockState itemUsageMode()
	{
		return LockState.LOCKED;
	}

	@ConfigItem(
			keyName = "foodSettingsMode",
			name = "Food Settings",
			description = "'Require Card': Consumables are not usable without their card."
					+ "<br>'Pots Only': potions are usable without Cards. "
					+ "<br>'Food Only': food usable without Cards."
					+ "<br>'No Card Needed': both are usable without cards.",
			section = generalSettings,
			position = 5
	)
	default FoodSettingsMode foodSettingsMode()
	{
		return FoodSettingsMode.LOCKED;
	}

	@ConfigItem(
			keyName = "bankingMode",
			name = "Banking",
			description = "How locked inventory items interact with the bank while Item Usage is "
					+ "Locked."
					+ "<br>'Off': they can't be banked at all."
					+ "<br> 'Deposit Only': Deposits work, withdrawals stay blocked until the card unlocks."
					+ "<br> 'Full Banking': Deposit and Withrdrawls allowed without needing card.",
			section = generalSettings,
			position = 6
	)
	default BankingMode bankingMode()
	{
		return BankingMode.DEPOSIT_ONLY;
	}

	@ConfigItem(
			keyName = "grandExchangeMode",
			name = "Grand Exchange",
			description = "Locked: items whose card you have not collected can't be selected in "
					+ "the Grand Exchange search."
					+ "<br>Items with no card can always be bought.",
			section = generalSettings,
			position = 7
	)
	default LockState grandExchangeMode()
	{
		return LockState.LOCKED;
	}

	@ConfigItem(
		keyName = "coinMode",
		name = "Coin Settings",
		description = "Unlocked: Coins are never restricted (looting, buying, banking, etc.), "
			+ "regardless of the exempt list below."
			+ "<br>Locked: a true challenge - anything involving coins needs the card.",
		section = generalSettings,
		position = 8
	)
	default LockState coinMode()
	{
		return LockState.UNLOCKED;
	}

	@ConfigItem(
		keyName = "exemptListMigrated",
		name = "",
		description = "",
		hidden = true,
		section = generalSettings,
		position = 99
	)
	default boolean exemptListMigrated()
	{
		return false;
	}

	@ConfigItem(
		keyName = "lootExemptNames",
		name = "Exempt List",
		description = "Comma-separated, case-insensitive."
			+ "<br>Items and NPC names added to the list are never restricted even without their card."
			+ "<br>For universal items that would otherwise make the game unplayable."
			+ "<br>Use this list to add exceptions based on things like Foil Card Rules.",
		section = generalSettings,
		position = 9
	)
	default String lootExemptNames()
	{
		return "";
	}

	@ConfigItem(
		keyName = "chatFeedback",
		name = "Chat feedback",
		description = "Send a game chat message explaining why an action was blocked.",
		section = generalSettings,
		position = 10
	)
	default boolean chatFeedback() { return true; }

	@ConfigItem(
		keyName = "allowInLms",
		name = "Allow Last Man Standing",
		description = "Lift all restrictions while inside a Last Man Standing match, since LMS hands "
			+ "you temporary gear and supplies you don't own.",
		section = generalSettings,
		position = 11
	)
	default boolean allowInLms()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showConflictMessage",
		name = "Conflict plugins message",
		description = "Warn in chat when another plugin that restricts the same actions is "
			+ "enabled alongside this one, repeating every 30 minutes while both stay on."
			+ "<br>Turn off if you're deliberately running both and don't need the reminder.",
		section = generalSettings,
		position = 14
	)
	default boolean showConflictMessage()
	{
		return true;
	}

	//----------------
	//Resource nodes
	//----------------
	@ConfigItem(
		keyName = "restrictWoodcutting",
		name = "Restrict woodcutting",
		description = "Block chopping trees until the respective logs card is collected."
			+ "<br>e.g. Oak tree needs Oak logs.",
		section = woodcuttingSection,
		position = 0
	)
	default boolean restrictWoodcutting()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictMining",
		name = "Restrict mining",
		description = "Block mining rocks until the respective ore card is collected."
			+ "<br>e.g. Copper rocks need Copper ore.",
		section = miningSection,
		position = 0
	)
	default boolean restrictMining()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fishingMode",
		name = "Restrict fishing",
		description = "Fishing spots share one name everywhere, so each spot type lists every fish it can "
			+ "yield at any location."
			+ "<br>'Any of': owning any one of those fish unlocks that spot type "
			+ "(e.g. Raw tuna unlocks all Harpoon spots, shark spots included)."
			+ "<br>'Require ALL': the spot type stays locked until you own every fish it can yield "
			+ "(e.g. Harpoon needs Raw tuna, Raw swordfish AND Raw shark)."
			+ "<br>'Off': no fishing restriction.",
		section = fishingSection,
		position = 0
	)
	default FishingRestrictionMode fishingMode()
	{
		return FishingRestrictionMode.ANY_OF;
	}

	//----------------
	//Cooking
	//----------------
	@ConfigItem(
		keyName = "restrictCooking",
		name = "Restrict cooking",
		description = "Block using raw food on fires/ranges until the cooked version's card is collected.",
		section = cookingSection,
		position = 0
	)
	default boolean restrictCooking() {	return true; }

	@ConfigItem(
		keyName = "restrictBurntFood",
		name = "Require burnt food",
		description = "Also require the burnt version's card to cook something."
			+ "<br>Food that burns into the generic 'Burnt fish' has no card, so it is never affected."
			+ "<br>No effect while 'Restrict cooking' is off.",
		section = cookingSection,
		position = 1
	)
	default boolean restrictBurntFood()
	{
		return false;
	}

	//----------------
	//Firemaking
	//----------------
	@ConfigItem(
		keyName = "firemakingMode",
		name = "Restrict firemaking",
		description = "'Just logs': lighting a fire requires the card of the specific logs being lit."
			+ "<br>'Logs + Tinderbox': additionally requires the Tinderbox card.",
		section = firemakingSection,
		position = 0
	)
	default FiremakingMode firemakingMode()
	{
		return FiremakingMode.JUST_LOGS;
	}

	@ConfigItem(
		keyName = "restrictEventLogs",
		name = "Include event logs",
		description = "Also apply the firemaking restriction to the 2014-event coloured logs."
			+ "<br>Blue/Green/Red/Purple/White logs.",
		section = firemakingSection,
		position = 1
	)
	default boolean restrictEventLogs()
	{
		return true;
	}

	//----------------
	//Smithing
	//----------------
	@ConfigItem(
		keyName = "smeltingMode",
		name = "Smelting",
		description = "Smelting a bar at a furnace requires:"
			+ "<br>'Ore' = the ore cards, 'Bars' = the bar card, 'Both' = all of them.",
		section = smithingSection,
		position = 0
	)
	default SmeltingMode smeltingMode()
	{
		return SmeltingMode.BOTH;
	}

	@ConfigItem(
		keyName = "smithingMode",
		name = "Smithing",
		description = "Smithing an item at an anvil requires:"
			+ "<br>'Bars' = the bar card, 'Items' = the product's card, 'Both' = both."
			+ "<br>The Hammer card is always required while enabled.",
		section = smithingSection,
		position = 1
	)
	default SmithingMode smithingMode()
	{
		return SmithingMode.BOTH;
	}

	//----------------
	//Crafting
	//----------------
	@ConfigItem(
		keyName = "restrictCrafting",
		name = "Restrict crafting",
		description = "Crafting requires the input AND output item cards."
			+ "<br>Covers gems, leather, glass, jewellery, spinning, pottery and battlestaves.",
		section = craftingSection,
		position = 0
	)
	default boolean restrictCrafting()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictEnchanting",
		name = "Restrict enchanting",
		description = "Enchanting jewellery requires the unenchanted item's card AND the enchanted "
			+ "product's card.",
		section = craftingSection,
		position = 1
	)
	default boolean restrictEnchanting()
	{
		return true;
	}

	@ConfigItem(
		keyName = "requireCrushedGem",
		name = "Require crushed gem",
		description = "Also require the Crushed gem card to cut gems that can shatter."
			+ "<br>Only applies to the gems that can actually be crushed; the rest are unaffected."
			+ "<br>No effect while 'Restrict crafting' is off.",
		section = craftingSection,
		position = 2
	)
	default boolean requireCrushedGem()
	{
		return false;
	}

	//----------------
	//Skill Options
	//----------------
	@ConfigItem(
		keyName = "fletchingMode",
		name = "Fletching",
		description = "Blocked at the fletching interface's product click; using a knife on logs "
			+ "opens the menu freely."
			+ "<br>'Product': each step needs the finished item's card (unstrung bows count as "
			+ "their strung bow). 'Product + Materials': also needs the carded inputs - logs, "
			+ "bow string, arrow shafts, feathers. Arrowtips and dart tips have no cards and "
			+ "never gate anything.",
		section = fletchingSection,
		position = 0
	)
	default FletchingMode fletchingMode()
	{
		return FletchingMode.PRODUCT_AND_MATERIALS;
	}

	@ConfigItem(
		keyName = "restrictHerblore",
		name = "Restrict herblore",
		description = "Making potions requires the input cards (herb/unfinished/secondary) and the "
			+ "output potion card."
			+ "<br>Card names are dose-less, so any dose matches the one card.",
		section = herbloreSection,
		position = 1
	)
	default boolean restrictHerblore()
	{
		return true;
	}

	@ConfigItem(
		keyName = "runecraftingMode",
		name = "Runecrafting",
		description = "Crafting at an altar requires essence + talisman (tiara counts) cards."
			+ "<br>'Talisman and Runes' additionally requires the crafted rune's card."
			+ "<br>Altars with no talisman (Astral/Blood/Soul) skip that part.",
		section = runecraftingSection,
		position = 2
	)
	default RunecraftingMode runecraftingMode()
	{
		return RunecraftingMode.TALISMAN_RUNES;
	}

	//----------------
	//Hunter
	//----------------
	@ConfigItem(
		keyName = "hunterBirdsMode",
		name = "Birds & butterflies",
		description = "'Gear only': bird snaring needs the Bird snare card; catching butterflies needs "
			+ "Butterfly net (Magic butterfly net counts)."
			+ "<br>'All bird drops': additionally requires the creature cards "
			+ "(any-of for snares, since a laid snare can't know which bird lands).",
		section = hunterSection,
		position = 0
	)
	default HunterBirdsMode hunterBirdsMode()
	{
		return HunterBirdsMode.NET_ONLY;
	}

	@ConfigItem(
		keyName = "implingMode",
		name = "Implings",
		description = "'Butterfly net only': catching implings needs a butterfly net card (Magic counts)."
			+ "<br>'Net + jar': additionally requires the Impling jar card.",
		section = hunterSection,
		position = 1
	)
	default ImplingMode implingMode()
	{
		return ImplingMode.BOTH;
	}

	@ConfigItem(
		keyName = "restrictChins",
		name = "Chinchompas",
		description = "Laying a box trap requires the Box trap card plus any chinchompa card."
			+ "<br>A laid trap can't know which species wanders in.",
		section = hunterSection,
		position = 2
	)
	default boolean restrictChins()
	{
		return true;
	}

	@ConfigItem(
		keyName = "salamanderMode",
		name = "Salamanders",
		description = "'Rope + Net': setting a net trap needs the Rope and Small fishing net cards."
			+ "<br>'Items + Salamander': additionally requires the respective salamander's card.",
		section = hunterSection,
		position = 3
	)
	default SalamanderMode salamanderMode()
	{
		return SalamanderMode.ROPE_NET;
	}

	@ConfigItem(
		keyName = "pitfallMode",
		name = "Pitfalls",
		description = "'Just tools': teasing a beast into a pitfall needs the tool cards "
			+ "(teasing stick, knife, any logs)."
			+ "<br>'All': additionally requires the beast's own card."
			+ "<br>Only Horned graahk and antelopes have cards - larupia/kyatt have none and are "
			+ "never restricted.",
		section = hunterSection,
		position = 4
	)
	default PitfallMode pitfallMode()
	{
		return PitfallMode.ALL;
	}

	@ConfigItem(
		keyName = "restrictHunterRumours",
		name = "Extreme: rumour masters",
		description = "Block each Hunters' Guild rumour master until you own the card of every creature "
			+ "they can assign."
			+ "<br>Creatures with no card are excluded from the requirement.",
		section = hunterSection,
		position = 5
	)
	default boolean restrictHunterRumours()
	{
		return false;
	}

	//----------------
	//Farming
	//----------------
	@ConfigItem(
		keyName = "farmingRakeMode",
		name = "Raking",
		description = "Raking a patch requires the Rake card."
			+ "<br>'Tools + Weeds' additionally requires the Weeds card.",
		section = farmingSection,
		position = 0
	)
	default FarmingRakeMode farmingRakeMode()
	{
		return FarmingRakeMode.BOTH;
	}

	@ConfigItem(
		keyName = "farmingPlantMode",
		name = "Planting",
		description = "Planting a seed requires the tool card."
			+ "<br>'Tools + Seeds' adds the seed's card. 'All' also requires the harvested produce's card.",
		section = farmingSection,
		position = 1
	)
	default FarmingPlantMode farmingPlantMode()
	{
		return FarmingPlantMode.ALL;
	}

	@ConfigItem(
		keyName = "restrictCompost",
		name = "Compost bins",
		description = "Collecting compost from a bin requires any compost card.",
		section = farmingSection,
		position = 2
	)
	default boolean restrictCompost()
	{
		return true;
	}

	//----------------
	//Slayer
	//----------------
	@ConfigItem(
		keyName = "restrictSlayerMasters",
		name = "Require masters",
		description = "Using a slayer master (Talk-to/Assignment/Trade/Rewards) is blocked until you own "
			+ "that master's own NPC card.",
		section = slayerSection,
		position = 0
	)
	default boolean restrictSlayerMasters()
	{
		return false;
	}

	@ConfigItem(
		keyName = "restrictSlayerMonsters",
		name = "Require monsters",
		description = "Using a slayer master is blocked until you own EVERY card of the monsters that "
			+ "master can assign."
			+ "<br>Bosses and revenants excluded - no representative cards.",
		section = slayerSection,
		position = 1
	)
	default boolean restrictSlayerMonsters()
	{
		return false;
	}

	@ConfigItem(
		keyName = "restrictSlayerSuperiors",
		name = "Include superiors",
		description = "'Require monsters' additionally demands each master's superior variant cards "
			+ "(Abhorrent spectre, King kurask...)."
			+ "<br>Superiors only spawn after unlocking Bigger and Badder with slayer points, so "
			+ "anyone this affects has already opted into the grind."
			+ "<br>No effect while 'Require monsters' is off. Superiors are always separately "
			+ "combat-locked by their own cards regardless of this setting.",
		section = slayerSection,
		position = 2
	)
	default boolean restrictSlayerSuperiors()
	{
		return false;
	}

	//----------------
	//Thieving
	//----------------
	@ConfigItem(
		keyName = "thievingMode",
		name = "Pickpocketing",
		description = "'Coins + Pouch': pickpocketing an NPC requires Coins and Coin pouch. "
			+ "<br>'Coins, Pouch and NPC Card': additionally requires the card of the NPC being pickpocketed."
			+ "<br> 'All': requires all loot from NPCs loot table and NPC Card of the NPC being pickpocketed."
			+ "<br> Insanity H.A.M. Members and Master Farmers are not included in All.",
		section = thievingSection,
		position = 0
	)
	default ThievingMode thievingMode()
	{
		return ThievingMode.COINS_POUCH;
	}

	@ConfigItem(
			keyName = "hamFullLoot",
			name = "H.A.M. Insanity",
			description = "With All mode, require all 37 pickpocket items instead of the Ham Outfit pieces.",
			section = thievingSection,
			position = 2
	)
	default boolean hamFullLoot()
	{
		return false;
	}

	@ConfigItem(
			keyName = "masterFarmerInsanity",
			name = "Master Farmer",
			description = "With All mode, require all 45 Master Farmer seeds.",
			section = thievingSection,
			position = 3
	)
	default boolean masterFarmerInsanity()
	{
		return false;
	}

	@ConfigItem(
		keyName = "stallThievingMode",
		name = "Stalls",
		description = "'Off': no stall restriction. Stalls with no card-backed loot are never restricted."
			+ "<br>'Any of': owning any one loot card unlocks the stall."
			+ "<br>'All items': the stall stays locked until you own every card-backed loot item.",
		section = thievingSection,
		position = 1
	)
	default StallThievingMode stallThievingMode()
	{
		return StallThievingMode.ANY_OF;
	}

	//----------------
	//Sailing
	//----------------
	@ConfigItem(
		keyName = "sailingUpgradeMode",
		name = "Boat upgrades",
		description = "Installing a hull or keel tier requires cards."
			+ "<br>'Parts': the tier's part card (e.g. Oak hull parts)."
			+ "<br>'Parts + Materials': also the underlying material card (Oak plank, Bronze bar)."
			+ "<br>'Everything': additionally the log card and the Large part variant's card."
			+ "<br>Masts, helms, cannons and cargo holds have no part cards and are never restricted.",
		section = sailingSection,
		position = 0
	)
	default SailingUpgradeMode sailingUpgradeMode()
	{
		return SailingUpgradeMode.PARTS;
	}

	@ConfigItem(
		keyName = "restrictSalvaging",
		name = "Salvaging",
		description = "Salvaging a shipwreck requires the card of the salvage type that wreck tier yields."
			+ "<br>e.g. Barracuda shipwrecks need the Barracuda salvage card.",
		section = sailingSection,
		position = 1
	)
	default boolean restrictSalvaging()
	{
		return true;
	}

	//----------------
	//Visuals
	//----------------
	@ConfigItem(
		keyName = "lockedItemMarkMode",
		name = "Locked Item Indicator",
		description = "Fade items in your inventory and bank while their card is uncollected, "
			+ "so locked items are obvious at a glance."
			+ "<br>Fade + Icon adds a small bank-filler badge on top of the faded sprite. ",
		section = visualsSection,
		position = 0
	)
	default LockedItemMarkMode lockedItemMarkMode()
	{
		return LockedItemMarkMode.TRANSPARENT_ICON;
	}

	@ConfigItem(
		keyName = "tintLockedNpcs",
		name = "Tint locked NPCs grey",
		description = "NPCs whose card you have not collected are greyed out in the world, "
			+ "so you can see at a glance what you can't fight yet.",
		section = visualsSection,
		position = 3
	)
	default boolean tintLockedNpcs()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "lockedOutlineColor",
		name = "Outline colour",
		description = "Colour (and opacity) of the locked-NPC outline.",
		section = visualsSection,
		position = 4
	)
	default Color lockedOutlineColor()
	{
		return new Color(60, 60, 60, 200);
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "lockedOutlineWidth",
		name = "Outline width",
		description = "Thickness of the locked-NPC outline in pixels.",
		section = visualsSection,
		position = 5
	)
	default int lockedOutlineWidth()
	{
		return 2;
	}

	@Range(max = 6)
	@ConfigItem(
		keyName = "lockedOutlineFeather",
		name = "Outline feather",
		description = "How softly the outline fades at its edge (0 = hard line).",
		section = visualsSection,
		position = 6
	)
	default int lockedOutlineFeather()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "showTcgStatsOverlay",
		name = "TCG stats overlay",
		description = "Plain-text overlay showing your OSRS TCG credits and distinct cards "
			+ "collected, read from the TCG plugin's saved state (displayed with its creator's "
			+ "blessing)."
			+ "<br>Shows nothing until the TCG plugin has data. Alt+drag to reposition.",
		section = visualsSection,
		position = 1
	)
	default boolean showTcgStatsOverlay()
	{
		return false;
	}
}
