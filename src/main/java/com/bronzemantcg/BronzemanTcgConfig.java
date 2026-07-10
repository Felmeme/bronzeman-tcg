package com.bronzemantcg;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(BronzemanTcgConfig.GROUP)
public interface BronzemanTcgConfig extends Config
{
	String GROUP = "bronzemantcg";

	@ConfigItem(
		keyName = "restrictAttacks",
		name = "Restrict attacks",
		description = "Block attacking any NPC whose card you have not yet collected in the OSRS TCG plugin. "
			+ "NPCs with no card in the TCG catalog are never restricted.",
		position = 0
	)
	default boolean restrictAttacks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictSpellCasts",
		name = "Restrict spells/items on NPCs",
		description = "Also block casting spells on, or using items on, uncollected NPCs "
			+ "(prevents bypassing the restriction with magic or ranged via spell casts).",
		position = 1
	)
	default boolean restrictSpellCasts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictLoot",
		name = "Restrict loot pickup",
		description = "Block picking up (or telegrabbing) ground items whose card you have not yet "
			+ "collected in the OSRS TCG plugin. Items with no card in the TCG catalog are never restricted.",
		position = 2
	)
	default boolean restrictLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lootExemptNames",
		name = "Loot exempt list",
		description = "Comma-separated item names that are always lootable even without their card, "
			+ "e.g. universal drops that would make the early game unplayable. Case-insensitive.",
		position = 3
	)
	default String lootExemptNames()
	{
		return "Coins";
	}

	@ConfigItem(
		keyName = "chatFeedback",
		name = "Chat feedback",
		description = "Send a game chat message explaining why an action was blocked.",
		position = 4
	)
	default boolean chatFeedback()
	{
		return true;
	}

	// ------------------------------------------------------------------ items & economy

	@ConfigSection(
		name = "Items & economy",
		description = "Restrictions on holding, equipping, and acquiring items whose card is locked.",
		position = 5
	)
	String itemsSection = "itemsSection";

	@ConfigItem(
		keyName = "restrictEquipping",
		name = "Restrict equipping",
		description = "Block Wear/Wield/Equip on any inventory item whose card you have not collected. "
			+ "Items with no card are never restricted.",
		section = itemsSection,
		position = 0
	)
	default boolean restrictEquipping()
	{
		return true;
	}

	@ConfigItem(
		keyName = "forcedDropMode",
		name = "Forced drop",
		description = "Items in your inventory whose card is locked (e.g. quest rewards) can only be "
			+ "Dropped, Examined or Destroyed. 'Allow banking' additionally permits depositing them "
			+ "(the bank becomes a holding pen - withdrawing stays blocked until the card unlocks). "
			+ "Once the card is unlocked the item works normally.",
		section = itemsSection,
		position = 1
	)
	default ForcedDropMode forcedDropMode()
	{
		return ForcedDropMode.OFF;
	}

	@ConfigItem(
		keyName = "restrictBuying",
		name = "Restrict shop/GE buying",
		description = "Block buying items whose card is locked from shops, and block selecting them in "
			+ "the Grand Exchange search. Items with no card can always be bought. Note: GE blocking is "
			+ "best-effort - the search-result click is consumed, but keyboard-driven flows may bypass it.",
		section = itemsSection,
		position = 2
	)
	default boolean restrictBuying()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictPotionDrinking",
		name = "Restrict potion drinking",
		description = "Block drinking any potion whose card is locked. All four dose types map to the "
			+ "one card (cards are dose-less), so unlocking the card unlocks every dose.",
		section = itemsSection,
		position = 3
	)
	default boolean restrictPotionDrinking()
	{
		return true;
	}

	// ------------------------------------------------------------------ processing skills

	@ConfigSection(
		name = "Processing skills",
		description = "Recipe restrictions: making things requires the cards of what goes in and/or what comes out.",
		position = 6
	)
	String processingSection = "processingSection";

	@ConfigItem(
		keyName = "firemakingMode",
		name = "Firemaking",
		description = "'Just logs': lighting a fire requires the card of the specific logs being lit. "
			+ "'Logs + Tinderbox': additionally requires the Tinderbox card.",
		section = processingSection,
		position = 0
	)
	default FiremakingMode firemakingMode()
	{
		return FiremakingMode.JUST_LOGS;
	}

	@ConfigItem(
		keyName = "restrictEventLogs",
		name = "Include event logs",
		description = "Also apply the firemaking restriction to the 2014-event coloured logs "
			+ "(Blue/Green/Red/Purple/White logs).",
		section = processingSection,
		position = 1
	)
	default boolean restrictEventLogs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "smeltingMode",
		name = "Smelting",
		description = "Smelting a bar at a furnace requires: 'Ore' = the ore cards, 'Bars' = the bar card, "
			+ "'Both' = all of them.",
		section = processingSection,
		position = 2
	)
	default SmeltingMode smeltingMode()
	{
		return SmeltingMode.BOTH;
	}

	@ConfigItem(
		keyName = "smithingMode",
		name = "Smithing",
		description = "Smithing an item at an anvil requires: 'Bars' = the bar card, 'Items' = the "
			+ "product's card, 'Both' = both.",
		section = processingSection,
		position = 3
	)
	default SmithingMode smithingMode()
	{
		return SmithingMode.BOTH;
	}

	@ConfigItem(
		keyName = "restrictCrafting",
		name = "Restrict crafting",
		description = "Crafting (gems, leather, glass, jewellery, spinning, pottery, battlestaves) requires "
			+ "the input AND output item cards.",
		section = processingSection,
		position = 4
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
		section = processingSection,
		position = 5
	)
	default boolean restrictEnchanting()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictFletching",
		name = "Restrict fletching",
		description = "Fletching requires the input and output item cards (where input cards exist - "
			+ "arrowtips and most dart tips have no cards, so those recipes enforce the output).",
		section = processingSection,
		position = 6
	)
	default boolean restrictFletching()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictHerblore",
		name = "Restrict herblore",
		description = "Making potions requires the input cards (herb/unfinished/secondary) and the "
			+ "output potion card. Card names are dose-less, so any dose matches the one card.",
		section = processingSection,
		position = 7
	)
	default boolean restrictHerblore()
	{
		return true;
	}

	// ------------------------------------------------------------------ hunter

	@ConfigSection(
		name = "Hunter",
		description = "Hunting requires the gear cards (and optionally the creature cards) for each method.",
		position = 7
	)
	String hunterSection = "hunterSection";

	@ConfigItem(
		keyName = "hunterBirdsMode",
		name = "Birds & butterflies",
		description = "'Gear only': bird snaring needs the Bird snare card; catching butterflies needs "
			+ "Butterfly net (Magic butterfly net counts). 'All bird drops': additionally requires the "
			+ "creature cards (any-of for snares, since a laid snare can't know which bird lands).",
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
		description = "'Butterfly net only': catching implings needs a butterfly net card (Magic counts). "
			+ "'Net + jar': additionally requires the Impling jar card.",
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
		description = "Laying a box trap requires the Box trap card plus any chinchompa card "
			+ "(a laid trap can't know which species wanders in).",
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
		description = "'Rope + Net': setting a net trap needs the Rope and Small fishing net cards. "
			+ "'Items + Salamander': additionally requires the respective salamander's card.",
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
		description = "'Just tools': teasing a beast into a pitfall needs the tool cards (teasing stick, "
			+ "knife, any logs). 'All': additionally requires the beast's own card. Only Horned graahk "
			+ "and antelopes have cards - larupia/kyatt have none and are never restricted.",
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
			+ "they can assign (creatures with no card are excluded from the requirement).",
		section = hunterSection,
		position = 5
	)
	default boolean restrictHunterRumours()
	{
		return false;
	}

	// ------------------------------------------------------------------ gathering & slayer

	@ConfigSection(
		name = "Runecrafting, Farming & Slayer",
		description = "Altar, patch and slayer master restrictions.",
		position = 8
	)
	String gatheringSection = "gatheringSection";

	@ConfigItem(
		keyName = "runecraftingMode",
		name = "Runecrafting",
		description = "Crafting at an altar requires essence + talisman (tiara counts) cards; "
			+ "'Talisman and Runes' additionally requires the crafted rune's card. Altars with no "
			+ "talisman (Astral/Blood/Soul) skip that part.",
		section = gatheringSection,
		position = 0
	)
	default RunecraftingMode runecraftingMode()
	{
		return RunecraftingMode.TALISMAN_RUNES;
	}

	@ConfigItem(
		keyName = "farmingRakeMode",
		name = "Farming: raking",
		description = "Raking a patch requires the Rake card; 'Tools + Weeds' additionally requires the "
			+ "Weeds card.",
		section = gatheringSection,
		position = 1
	)
	default FarmingRakeMode farmingRakeMode()
	{
		return FarmingRakeMode.BOTH;
	}

	@ConfigItem(
		keyName = "farmingPlantMode",
		name = "Farming: planting",
		description = "Planting a seed requires the tool card; 'Tools + Seeds' adds the seed's card; "
			+ "'All' also requires the harvested produce's card. Harvesting itself is not intercepted "
			+ "(a patch object doesn't reveal its crop), so produce enforcement happens at plant time.",
		section = gatheringSection,
		position = 2
	)
	default FarmingPlantMode farmingPlantMode()
	{
		return FarmingPlantMode.ALL;
	}

	@ConfigItem(
		keyName = "restrictCompost",
		name = "Farming: compost bins",
		description = "Collecting compost from a bin requires any compost card (bin contents aren't "
			+ "distinguishable by object name).",
		section = gatheringSection,
		position = 3
	)
	default boolean restrictCompost()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictSlayerMasters",
		name = "Slayer: require masters",
		description = "Using a slayer master (Talk-to/Assignment/Trade/Rewards) is blocked until you own "
			+ "that master's own NPC card.",
		section = gatheringSection,
		position = 4
	)
	default boolean restrictSlayerMasters()
	{
		return false;
	}

	@ConfigItem(
		keyName = "restrictSlayerMonsters",
		name = "Slayer: require monsters",
		description = "Using a slayer master is blocked until you own EVERY card of the monsters that "
			+ "master can assign (bosses and revenants excluded - no representative cards).",
		section = gatheringSection,
		position = 5
	)
	default boolean restrictSlayerMonsters()
	{
		return false;
	}

	// ------------------------------------------------------------------ resource nodes (existing)

	@ConfigSection(
		name = "Resource nodes",
		description = "Block gathering from skill resource nodes until the card of the item they yield is collected.",
		position = 9
	)
	String resourceNodesSection = "resourceNodesSection";

	@ConfigItem(
		keyName = "restrictWoodcutting",
		name = "Restrict woodcutting",
		description = "Block chopping trees until the respective logs card is collected (e.g. Oak tree needs Oak logs).",
		section = resourceNodesSection,
		position = 0
	)
	default boolean restrictWoodcutting()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictMining",
		name = "Restrict mining",
		description = "Block mining rocks until the respective ore card is collected (e.g. Copper rocks need Copper ore).",
		section = resourceNodesSection,
		position = 1
	)
	default boolean restrictMining()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fishingMode",
		name = "Restrict fishing",
		description = "Fishing spots share one name everywhere, so each spot type lists every fish it can "
			+ "yield at any location. 'Any of': owning any one of those fish unlocks that spot type "
			+ "(e.g. Raw tuna unlocks all Harpoon spots, shark spots included). "
			+ "'Require ALL': the spot type stays locked until you own every fish it can yield "
			+ "(e.g. Harpoon needs Raw tuna, Raw swordfish AND Raw shark). 'Off': no fishing restriction.",
		section = resourceNodesSection,
		position = 2
	)
	default FishingRestrictionMode fishingMode()
	{
		return FishingRestrictionMode.ANY_OF;
	}

	@ConfigItem(
		keyName = "restrictPickpocketing",
		name = "Restrict pickpocketing",
		description = "Block pickpocketing NPCs until the cards of their loot are collected (e.g. Coins and coin pouch).",
		section = resourceNodesSection,
		position = 3
	)
	default boolean restrictPickpocketing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "masterFarmerMode",
		name = "Master Farmer",
		description = "Master Farmers give seeds, not coin pouches, so they get their own dial. "
			+ "'Coins+Pouch': same simple rule as other pickpocket targets. "
			+ "'Insanity': locked until you own EVERY seed card on his drop table. "
			+ "'Off': never restricted. Independent of the pickpocketing toggle above.",
		section = resourceNodesSection,
		position = 4
	)
	default MasterFarmerMode masterFarmerMode()
	{
		return MasterFarmerMode.COINS_POUCH;
	}

	@ConfigItem(
		keyName = "restrictCooking",
		name = "Restrict cooking",
		description = "Block using raw food on fires/ranges until the cooked version's card is collected.",
		section = resourceNodesSection,
		position = 5
	)
	default boolean restrictCooking()
	{
		return true;
	}
}
