package com.bronzemantcg;

import com.bronzemantcg.feature.LockedItemMarkMode;
import com.bronzemantcg.restriction.NpcVisibilityMode;
import com.bronzemantcg.catalog.CardRequirement;
import com.bronzemantcg.restriction.BankingMode;
import com.bronzemantcg.restriction.CookingMode;
import com.bronzemantcg.restriction.CraftingMode;
import com.bronzemantcg.restriction.FarmingRakeMode;
import com.bronzemantcg.restriction.FishingRestrictionMode;
import com.bronzemantcg.restriction.FletchingMode;
import com.bronzemantcg.restriction.FoodSettingsMode;
import com.bronzemantcg.restriction.HerbloreMode;
import com.bronzemantcg.restriction.HunterMode;
import com.bronzemantcg.restriction.LockState;
import com.bronzemantcg.restriction.MiningMode;
import com.bronzemantcg.restriction.RunecraftingMode;
import com.bronzemantcg.restriction.SailingUpgradeMode;
import com.bronzemantcg.restriction.SlayerMode;
import com.bronzemantcg.restriction.SmeltingMode;
import com.bronzemantcg.restriction.SmithingMode;
import com.bronzemantcg.restriction.ThievingPolicy.HamPickpocketingMode;
import com.bronzemantcg.restriction.ThievingPolicy.StallThievingMode;
import com.bronzemantcg.restriction.ThievingPolicy.ThievingMode;
import com.bronzemantcg.restriction.WoodcuttingMode;
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

	@ConfigSection(
			name = "Visuals",
			description = "How locked NPCs and items are shown in the game world.",
			position = 2
	)
	String visualsSection = "visualsSection";

	@ConfigSection(
			name = "External Plugins",
			description = "Integration with other RuneLite plugins.",
			position = 1
	)
	String externalPluginsSection = "externalPluginsSection";

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

	@ConfigSection(
			name = "Farming",
			description = "Patch and compost restrictions.",
			position = 5
	)
	String farmingSection = "farmingSection";

	@ConfigSection(
			name = "Firemaking",
			description = "Lighting fires requires the log (and optionally Tinderbox) cards.",
			position = 6
	)
	String firemakingSection = "firemakingSection";

	@ConfigSection(
			name = "Fishing",
			description = "Fishing restrictions.",
			position = 7
	)
	String fishingSection = "fishingSection";

	@ConfigSection(
			name = "Fletching",
			description = "Fletching restrictions.",
			position = 8
	)
	String fletchingSection = "fletchingSection";

	@ConfigSection(
			name = "Herblore",
			description = "Herblore restrictions.",
			position = 9
	)
	String herbloreSection = "herbloreSection";

	@ConfigSection(
			name = "Hunter",
			description = "Choose whether Hunter methods require no cards, their tools, or all "
				+ "relevant tool, creature and guaranteed-loot cards.",
			position = 10
	)
	String hunterSection = "hunterSection";

	@ConfigSection(
			name = "Mining",
			description = "Mining restrictions.",
			position = 11
	)
	String miningSection = "miningSection";

	@ConfigSection(
			name = "Runecrafting",
			description = "Runecrafting restrictions.",
			position = 12
	)
	String runecraftingSection = "runecraftingSection";

	@ConfigSection(
			name = "Sailing",
			description = "Boat upgrade and salvaging restrictions.",
			position = 13
	)
	String sailingSection = "sailingSection";


	@ConfigSection(
			name = "Slayer",
			description = "Slayer master restrictions.",
			position = 14
	)
	String slayerSection = "slayerSection";

	@ConfigSection(
			name = "Smithing",
			description = "Smelting bars and smithing items require ore, bar and product cards.",
			position = 15
	)
	String smithingSection = "smithingSection";

	@ConfigSection(
			name = "Thieving",
			description = "Pickpocketing restrictions.",
			position = 16
	)
	String thievingSection = "thievingSection";

	@ConfigSection(
			name = "Woodcutting",
			description = "Woodcutting restrictions.",
			position = 17
	)
	String woodcuttingSection = "woodcuttingSection";

//	@ConfigSection(
//			name = "Skill Options",
//			description = "Recipe restrictions for the remaining skills: making things requires the cards of what goes in and/or what comes out.",
//			position = 2
//	)
//	String skillOptionsSection = "skillOptionsSection";
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
		description = "NPCs with no card in the TCG catalog are never restricted."
			+ "<br>'Prevent Combat': the Attack option is hidden and offensive spells are blocked; "
			+ "talking and using items on the NPC still work."
			+ "<br>'Prevent Interaction': every menu option except Examine is removed, and items "
			+ "can't be used on the NPC."
			+ "<br>'Hide NPCs': locked NPCs are invisible."
			+ "<br>NPCs of quests you have started are always shown and talkable (Attack still "
			+ "needs the card)",
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
		keyName = "presetOnboardingComplete",
		name = "",
		description = "",
		hidden = true,
		section = generalSettings,
		position = 96
	)
	default boolean presetOnboardingComplete()
	{
		return false;
	}

	@ConfigItem(
		keyName = "presetOnboardingPending",
		name = "",
		description = "",
		hidden = true,
		section = generalSettings,
		position = 97
	)
	default boolean presetOnboardingPending()
	{
		return false;
	}

	@ConfigItem(
		keyName = "tcgLockedDefaultsMigrated",
		name = "",
		description = "",
		hidden = true,
		section = generalSettings,
		position = 95
	)
	default boolean tcgLockedDefaultsMigrated()
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
		return LockState.UNLOCKED;
	}

	@ConfigItem(
			keyName = "itemUsageMode",
			name = "Item Usage",
			description = "'Require Card': blocked item actions require their card; Drop and Destroy "
				+ "remain allowed."
				+ "<br>'No Card Needed': You can freely use items without their card.",
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
			description = "Controls only Eat and Drink inventory actions while Item Usage requires Cards."
					+ "<br>'Require Card': both actions require the item's Card."
					+ "<br>'Pots Only': Drink is allowed without the Card."
					+ "<br>'Food Only': Eat is allowed without the Card."
					+ "<br>'No Card Needed': both actions are allowed without the Card.",
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
					+ "<br>'Deposit Only': Deposits work, withdrawals stay blocked until the card unlocks."
					+ "<br>'Full Banking': Deposit and Withrdrawls allowed without needing card.",
			section = generalSettings,
			position = 6
	)
	default BankingMode bankingMode()
	{
		return BankingMode.FULL;
	}

	@ConfigItem(
			keyName = "grandExchangeMode",
			name = "Grand Exchange",
			description = "'Require Card': items whose card you have not collected can't be selected in "
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
		description = "'Require Card': the Coins item is restricted wherever Bronzeman can identify it."
			+ "<br>'No Card Needed': Coins are never restricted.",
		section = generalSettings,
		position = 8
	)
	default LockState coinMode()
	{
		return LockState.LOCKED;
	}

	@ConfigItem(
		keyName = "allowRemoteCatalog",
		name = "Download live catalogue",
		description = "Downloads the current card catalogue from the public OSRS TCG API."
			+ "<br>When disabled, Bronzeman uses its bundled Beta compatibility catalogue.",
		warning = "This plugin submits your IP address, and may submit various account data,"
			+ "<br>to a 3rd-party server not controlled or verified by Runelite developers.",
		section = externalPluginsSection,
		position = 0
	)
	default boolean allowRemoteCatalog()
	{
		return false;
	}

	@ConfigItem(
		keyName = "acceptSharedUnlocks",
		name = "TCG Locked Party Sharing",
		description = "Toggle on for group play while using TCG Locked."
			+ "<br>Use the `Party` plugin to share the cards you pull with your group members."
			+ "<br>Use the TCG Locked side panel to sync your group.",
		section = externalPluginsSection,
		position = 1
	)
	default boolean acceptSharedUnlocks()
	{
		return true;
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
		description = "Comma-separated and case-insensitive."
			+ "<br>Use * to match any number of characters, for example Rune* or *potion*."
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
		keyName = "showLockedMenuOptions",
		name = "Show Mouseover Options",
		description = "Also keep blocked tree and activity-NPC options visible."
			+ "<br>Other resource and ground-item options are already kept visible."
			+ "<br>Ordinary NPC options remain controlled by NPC Locks."
			+ "<br>The click is still blocked and chat explains which cards are missing.",
		section = generalSettings,
		position = 10
	)
	default boolean showLockedMenuOptions()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showBetaCollectionTab",
		name = "Show Beta tab",
		description = "Show the personal Beta collection snapshot in the side panel."
			+ "<br>Hiding the tab does not delete or change the saved snapshot.",
		section = generalSettings,
		position = 11
	)
	default boolean showBetaCollectionTab()
	{
		return true;
	}

	//----------------
	//Resource nodes
	//----------------
	@ConfigItem(
		keyName = "woodcuttingMode",
		name = "Woodcutting Options",
		description = "Block chopping trees until the respective logs card is collected."
			+ "<br>e.g. Oak tree needs Oak logs."
			+ "<br>'Tool Only' instead requires the axe you are carrying."
			+ "<br>'Card Required' needs both.",
		section = woodcuttingSection,
		position = 0
	)
	default WoodcuttingMode woodcuttingMode()
	{
		return WoodcuttingMode.TOOL_ONLY;
	}

	@ConfigItem(
		keyName = "miningMode",
		name = "Mining Options",
		description = "Block mining rocks until the respective ore card is collected."
			+ "<br>e.g. Copper rocks need Copper ore."
			+ "<br>'Tool Only' instead requires the pickaxe you are carrying."
			+ "<br>'Card Required' needs both.",
		section = miningSection,
		position = 0
	)
	default MiningMode miningMode()
	{
		return MiningMode.TOOL_ONLY;
	}

	@ConfigItem(
		keyName = "fishingMode",
		name = "Fishing Options",
		description = "Restrict fishing using the exact fishing spot and selected method."
			+ "<br>'Tools Only': every required card-backed tool, bait or consumable applicable "
			+ "to that method must be unlocked. Bare-handed harpooning remains permitted."
			+ "<br>'Tools + Any Fish': also requires any one carded catch from that method."
			+ "<br>'Tools + Fish': requires every carded catch from that method."
			+ "<br>'No Restrictions': no fishing restriction.",
		section = fishingSection,
		position = 0
	)
	default FishingRestrictionMode fishingMode()
	{
		return FishingRestrictionMode.TOOL_ONLY;
	}

	@ConfigItem(
		keyName = "fishingModeMigrated",
		name = "",
		description = "",
		hidden = true,
		section = fishingSection,
		position = 98
	)
	default boolean fishingModeMigrated()
	{
		return false;
	}

	//----------------
	//Cooking
	//----------------
	@ConfigItem(
		keyName = "cookingMode",
		name = "Cooking",
		description = "'No Restrictions': Cooking will not have extra restrictions."
			+ "<br>'Input Only': the raw food's card, e.g. Raw shrimps."
			+ "<br>'Input + Output': the raw AND cooked food cards, e.g. Raw shrimps and Shrimps.",
		section = cookingSection,
		position = 0
	)
	default CookingMode cookingMode()
	{
		return CookingMode.INPUT_ONLY;
	}

	//----------------
	//Firemaking
	//----------------
	@ConfigItem(
		keyName = "tinderboxMode",
		name = "Tinderbox Use",
		description = "'Card Required': lighting a fire requires the Tinderbox card."
			+ "<br>The logs themselves are gated when you obtain them (Woodcutting / loot), "
			+ "not here.",
		section = firemakingSection,
		position = 0
	)
	default CardRequirement tinderboxMode()
	{
		return CardRequirement.CARD_REQUIRED;
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
		return SmeltingMode.ORE;
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
		return SmithingMode.BARS;
	}

	//----------------
	//Crafting
	//----------------
	@ConfigItem(
		keyName = "craftingMode",
		name = "Crafting Options",
		description = "'No Restrictions': no crafting cards required."
			+ "<br>'Input Only': the material cards, e.g. Needle, Thread and Leather."
			+ "<br>'Input + Output': the materials AND the finished item's card.",
		section = craftingSection,
		position = 0
	)
	default CraftingMode craftingMode()
	{
		return CraftingMode.INPUT_ONLY;
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
		return false;
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
		description = "'No restrictions': fletching is never blocked."
			+ "<br>'Input Only': recipes only require the input item cards."
			+ "<br>'Input + Output': require all cards in fletching recipes.",
		section = fletchingSection,
		position = 0
	)
	default FletchingMode fletchingMode()
	{
		return FletchingMode.INPUT_ONLY;
	}

	@ConfigItem(
		keyName = "herbloreMode",
		name = "Herblore Options",
		description = "'Off': Herblore recipes are unrestricted."
			+ "<br>'Input Only': require every physical ingredient card."
			+ "<br>'Require Unfinished': additionally require the unfinished potion card "
			+ "when creating it."
			+ "<br>'Require All': additionally require every recipe's output card."
			+ "<br>Card names are dose-less, so any dose matches the one card.",
		section = herbloreSection,
		position = 1
	)
	default HerbloreMode herbloreMode()
	{
		return HerbloreMode.INPUT_ONLY;
	}

	@ConfigItem(
		keyName = "runecraftingMode",
		name = "Runecrafting",
		description = "Crafting at an altar requires essence + talisman (tiara counts) cards."
			+ "<br>'Talisman and Runes' additionally requires the crafted rune's card."
			+ "<br>Altars with no talisman (Astral/Blood/Soul) skip that part."
			+ "<br>Guardians of the Rift uses its supplied essence/portals, so only the "
			+ "crafted rune card applies there in the stricter mode.",
		section = runecraftingSection,
		position = 2
	)
	default RunecraftingMode runecraftingMode()
	{
		return RunecraftingMode.TALISMAN;
	}

	//----------------
	//Hunter
	//----------------
	@ConfigItem(
		keyName = "hunterMode",
		name = "Hunter Options",
		description = "'Tools Only': every supported method requires its normal tool cards."
			+ "<br>'All Cards': additionally requires the relevant creature, caught-item and "
			+ "guaranteed-loot cards."
			+ "<br>Hunter-rumour items are only used by the separate rumour-master setting.",
		section = hunterSection,
		position = 0
	)
	default HunterMode hunterMode()
	{
		return HunterMode.TOOLS_ONLY;
	}

	@ConfigItem(
		keyName = "hunterModeMigrated",
		name = "",
		description = "",
		hidden = true,
		section = hunterSection,
		position = 98
	)
	default boolean hunterModeMigrated()
	{
		return false;
	}

	@ConfigItem(
		keyName = "restrictHunterRumours",
		name = "Extreme: rumour masters",
		description = "Block each Hunters' Guild rumour master until you own the card of every creature "
			+ "they can assign."
			+ "<br>Creatures with no card are excluded from the requirement.",
		section = hunterSection,
		position = 1
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
		name = "Farming Options",
		description = "'Tools Only': the Rake or Seed dibber card."
			+ "<br>'Tools and Seeds': also requires the card of the seed being planted."
			+ "<br>'No Restrictions': raking and planting are never blocked.",
		section = farmingSection,
		position = 0
	)
	default FarmingRakeMode farmingRakeMode()
	{
		return FarmingRakeMode.TOOLS;
	}

	@ConfigItem(
		keyName = "compostMode",
		name = "Compost bins",
		description = "'Card Required': collecting compost from a bin requires any compost card.",
		section = farmingSection,
		position = 2
	)
	default CardRequirement compostMode()
	{
		return CardRequirement.NO_CARD;
	}

	//----------------
	//Slayer
	//----------------
	@ConfigItem(
		keyName = "slayerMode",
		name = "Slayer Options",
		description = "Using a slayer master (Talk-to/Assignment/Trade/Rewards)."
			+ "<br>'Require Slayer Master': needs that master's own NPC card."
			+ "<br>'Full Task List': also needs the card of every monster that master can assign."
			+ "<br>Boss and revenant assignment categories are excluded; reviewed bosses can "
			+ "still substitute for a normal higher-master task.",
		section = slayerSection,
		position = 0
	)
	default SlayerMode slayerMode()
	{
		return SlayerMode.MASTER;
	}

	@ConfigItem(
		keyName = "restrictSlayerSuperiors",
		name = "Include superiors",
		description = "'Full Task List' additionally demands each master's superior variant cards "
			+ "(Abhorrent spectre, King kurask...)."
			+ "<br>Superiors only spawn after unlocking Bigger and Badder with slayer points, so "
			+ "anyone this affects has already opted into the grind."
			+ "<br>No effect unless Slayer Options is 'Full Task List'. Superiors are always "
			+ "separately combat-locked by their own cards regardless of this setting.",
		section = slayerSection,
		position = 1
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
		name = "Pickpocketing Options",
		description = "'NPC Only': requires the target's NPC card when one exists."
			+ "<br>'Coins': requires the v1 Coins parent card."
			+ "<br>'Coins + NPC': also requires the target's NPC card when one exists."
			+ "<br>'Require All': requires all reviewed loot and the NPC card."
			+ "<br>H.A.M. Members and Master Farmers use their separate options.",
		section = thievingSection,
		position = 0
	)
	default ThievingMode thievingMode()
	{
		return ThievingMode.COINS;
	}

	@ConfigItem(
		keyName = "hamPickpocketingMode",
		name = "H.A.M. Members",
		description = "'No Restrictions': H.A.M. Members are unrestricted."
			+ "<br>'Member Card': requires the H.A.M. Member card."
			+ "<br>'Member + Outfit': also requires all seven H.A.M. outfit cards."
			+ "<br>'Full Loot': requires the member, outfit and all reviewed loot cards."
			+ "<br>The v1 Coins parent is never required.",
			section = thievingSection,
			position = 2
	)
	default HamPickpocketingMode hamPickpocketingMode()
	{
		return HamPickpocketingMode.MEMBER_CARD;
	}

	@ConfigItem(
		keyName = "masterFarmerInsanity",
		name = "Master Farmer Full Loot",
		description = "When enabled, require all 45 Master Farmer seeds. Master Farmers "
			+ "otherwise have no NPC-card or Coins requirement.",
		section = thievingSection,
		position = 3
	)
	default boolean masterFarmerInsanity()
	{
		return false;
	}

	@ConfigItem(
		keyName = "stallThievingMode",
		name = "Stalls & Chests",
		description = "'Off': no stall or thievable-chest restriction."
			+ "<br>'Any Of': owning any one card-backed loot item unlocks it."
			+ "<br>'All': every card-backed item on its loot table is required."
			+ "<br>Uncarded loot is ignored; unrelated storage and reward chests are unaffected.",
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
		description = "Fade items in your inventory and bank while their card is uncollected."
			+ "<br>'Fade + Icon' adds a small bank-filler badge on top of the faded sprite. ",
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
		description = "NPCs whose card you have not collected are greyed out in the world.",
		section = visualsSection,
		position = 1
	)
	default boolean tintLockedNpcs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tintLockedGroundItems",
		name = "Tint locked ground items grey",
		description = "Ground items whose card you have not collected are outlined in the world."
			+ "<br>Shown whether or not Ground Items requires a card, so you can always see "
			+ "what you have not collected."
			+ "<br>Uses the same outline colour, width and feathering as locked NPCs.",
		section = visualsSection,
		position = 2
	)
	default boolean tintLockedGroundItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "duelistCityMode",
		name = "Duelist City Mode",
		description = "IT'S TIME, TO...",
		section = visualsSection,
		position = 6
	)
	default boolean duelistCityMode()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "lockedOutlineColor",
		name = "Outline colour",
		description = "Colour (and opacity) of the locked NPC and ground-item outline.",
		section = visualsSection,
		position = 3
	)
	default Color lockedOutlineColor()
	{
		return new Color(60, 60, 60, 200);
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "lockedOutlineWidth",
		name = "Outline width",
		description = "Thickness of the locked NPC and ground-item outline in pixels.",
		section = visualsSection,
		position = 4
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
		position = 5
	)
	default int lockedOutlineFeather()
	{
		return 2;
	}

}
