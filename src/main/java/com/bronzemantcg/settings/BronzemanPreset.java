package com.bronzemantcg.settings;

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
		"thievingMode", ThievingMode.COINS,
		"hamPickpocketingMode", HamPickpocketingMode.MEMBER_CARD,
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
		"thievingMode", ThievingMode.REQUIRE_ALL,
		"hamPickpocketingMode", HamPickpocketingMode.FULL_LOOT,
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
