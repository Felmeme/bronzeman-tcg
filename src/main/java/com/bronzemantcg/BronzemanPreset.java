package com.bronzemantcg;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Built-in gameplay presets. Visual, integration and personal exception settings are excluded. */
enum BronzemanPreset
{
	TCG_LOCKED("TCG Locked", values(
		"npcVisibilityMode", NpcVisibilityMode.PREVENT_COMBAT,
		"groundItemsMode", LockState.UNLOCKED,
		"itemUsageMode", LockState.LOCKED,
		"foodSettingsMode", FoodSettingsMode.LOCKED,
		"bankingMode", BankingMode.FULL,
		"grandExchangeMode", LockState.LOCKED,
		"coinMode", LockState.LOCKED,
		"woodcuttingMode", WoodcuttingMode.TOOL_ONLY,
		"miningMode", MiningMode.TOOL_ONLY,
		"fishingMode", FishingRestrictionMode.TOOL_ONLY,
		"cookingMode", CookingMode.INPUT_ONLY,
		"burntFoodMode", BurntFoodMode.OFF,
		"tinderboxMode", CardRequirement.CARD_REQUIRED,
		"smeltingMode", SmeltingMode.ORE,
		"smithingMode", SmithingMode.BARS,
		"craftingMode", CraftingMode.INPUT_ONLY,
		"restrictEnchanting", false,
		"requireCrushedGem", false,
		"fletchingMode", FletchingMode.INPUT_ONLY,
		"herbloreMode", HerbloreMode.INPUT_ONLY,
		"runecraftingMode", RunecraftingMode.TALISMAN,
		"hunterMode", HunterMode.TOOLS_ONLY,
		"restrictHunterRumours", false,
		"farmingRakeMode", FarmingRakeMode.TOOLS,
		"compostMode", CardRequirement.NO_CARD,
		"slayerMode", SlayerMode.MASTER,
		"restrictSlayerSuperiors", false,
		"thievingMode", ThievingMode.COINS_POUCH,
		"hamFullLoot", false,
		"masterFarmerInsanity", false,
		"stallThievingMode", StallThievingMode.ANY_OF,
		"sailingUpgradeMode", SailingUpgradeMode.PARTS,
		"restrictSalvaging", true)),
	MAXIMUM("Maximum Restrictions", values(
		"npcVisibilityMode", NpcVisibilityMode.PREVENT_INTERACTION,
		"groundItemsMode", LockState.LOCKED,
		"itemUsageMode", LockState.LOCKED,
		"foodSettingsMode", FoodSettingsMode.LOCKED,
		"bankingMode", BankingMode.OFF,
		"grandExchangeMode", LockState.LOCKED,
		"coinMode", LockState.LOCKED,
		"woodcuttingMode", WoodcuttingMode.LOGS_ONLY,
		"miningMode", MiningMode.CARD_REQUIRED,
		"fishingMode", FishingRestrictionMode.ALL_CATCHES,
		"cookingMode", CookingMode.INPUT_OUTPUT,
		"burntFoodMode", BurntFoodMode.REQUIRE_CARD,
		"tinderboxMode", CardRequirement.CARD_REQUIRED,
		"smeltingMode", SmeltingMode.BOTH,
		"smithingMode", SmithingMode.BOTH,
		"craftingMode", CraftingMode.BOTH,
		"restrictEnchanting", true,
		"requireCrushedGem", true,
		"fletchingMode", FletchingMode.PRODUCT_AND_MATERIALS,
		"herbloreMode", HerbloreMode.REQUIRE_ALL,
		"runecraftingMode", RunecraftingMode.TALISMAN_RUNES,
		"hunterMode", HunterMode.ALL_CARDS,
		"restrictHunterRumours", true,
		"farmingRakeMode", FarmingRakeMode.BOTH,
		"compostMode", CardRequirement.CARD_REQUIRED,
		"slayerMode", SlayerMode.FULL,
		"restrictSlayerSuperiors", true,
		"thievingMode", ThievingMode.NPC_AND_LOOT,
		"hamFullLoot", true,
		"masterFarmerInsanity", true,
		"stallThievingMode", StallThievingMode.REQUIRE_ALL,
		"sailingUpgradeMode", SailingUpgradeMode.EVERYTHING,
		"restrictSalvaging", true));

	private final String label;
	private final Map<String, String> settings;

	BronzemanPreset(String label, Map<String, String> settings)
	{
		this.label = label;
		this.settings = settings;
	}

	String getLabel()
	{
		return label;
	}

	Map<String, String> getSettings()
	{
		return settings;
	}

	private static Map<String, String> values(Object... pairs)
	{
		if ((pairs.length & 1) != 0)
		{
			throw new IllegalArgumentException("Preset settings must be key/value pairs");
		}
		Map<String, String> result = new LinkedHashMap<>();
		for (int index = 0; index < pairs.length; index += 2)
		{
			Object value = pairs[index + 1];
			result.put((String) pairs[index], value instanceof Enum
				? ((Enum<?>) value).name() : String.valueOf(value));
		}
		return Collections.unmodifiableMap(result);
	}
}
