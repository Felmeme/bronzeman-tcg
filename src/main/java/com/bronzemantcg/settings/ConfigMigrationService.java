package com.bronzemantcg.settings;

import com.bronzemantcg.BronzemanTcgConfig;
import com.bronzemantcg.catalog.CardRequirement;
import com.bronzemantcg.restriction.NpcVisibilityMode;
import com.bronzemantcg.restriction.BankingMode;
import com.bronzemantcg.restriction.CookingMode;
import com.bronzemantcg.restriction.CraftingMode;
import com.bronzemantcg.restriction.FarmingRakeMode;
import com.bronzemantcg.restriction.FishingRestrictionMode;
import com.bronzemantcg.restriction.FletchingMode;
import com.bronzemantcg.restriction.HerbloreMode;
import com.bronzemantcg.restriction.HunterMode;
import com.bronzemantcg.restriction.LockState;
import com.bronzemantcg.restriction.MiningMode;
import com.bronzemantcg.restriction.RunecraftingMode;
import com.bronzemantcg.restriction.SlayerMode;
import com.bronzemantcg.restriction.SmeltingMode;
import com.bronzemantcg.restriction.SmithingMode;
import com.bronzemantcg.restriction.ThievingPolicy.HamPickpocketingMode;
import com.bronzemantcg.restriction.ThievingPolicy.ThievingMode;
import com.bronzemantcg.restriction.WoodcuttingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * One-way upgrades from retired Bronzeman settings to their current equivalents.
 *
 * <p>The order, marker-first writes and raw stored strings are compatibility behaviour:
 * do not simplify them without an explicit migration review.</p>
 */
@Singleton
public final class ConfigMigrationService
{
	private static final String GROUP = BronzemanTcgConfig.GROUP;

	/**
	 * Effective defaults before TCG Locked became the baseline. Existing installs often
	 * have no stored value for a setting that still uses its old default, so those values
	 * must be written once before changing the defaults underneath them.
	 */
	private static final Map<String, String> PRE_TCG_LOCKED_DEFAULTS = values(
		"groundItemsMode", LockState.LOCKED,
		"bankingMode", BankingMode.DEPOSIT_ONLY,
		"woodcuttingMode", WoodcuttingMode.LOGS_ONLY,
		"miningMode", MiningMode.CARD_REQUIRED,
		"fishingMode", FishingRestrictionMode.CARD_REQUIRED,
		"cookingMode", CookingMode.INPUT_OUTPUT,
		"smeltingMode", SmeltingMode.BOTH,
		"smithingMode", SmithingMode.BOTH,
		"craftingMode", CraftingMode.BOTH,
		"restrictEnchanting", true,
		"requireCrushedGem", true,
		"fletchingMode", FletchingMode.PRODUCT_AND_MATERIALS,
		"herbloreMode", HerbloreMode.REQUIRE_ALL,
		"runecraftingMode", RunecraftingMode.TALISMAN_RUNES,
		"farmingRakeMode", FarmingRakeMode.BOTH,
		"compostMode", CardRequirement.CARD_REQUIRED,
		"slayerMode", SlayerMode.OFF);

	private final ConfigAccess config;

	@Inject
	public ConfigMigrationService(ConfigManager configManager)
	{
		this(new RuneLiteConfigAccess(configManager));
	}

	ConfigMigrationService(ConfigAccess config)
	{
		this.config = config;
	}

	/**
	 * Check before legacy migrations create their markers, distinguishing a genuinely
	 * fresh install from an existing player upgrading this release.
	 */
	public boolean preparePresetOnboarding()
	{
		if (Boolean.parseBoolean(config.get("presetOnboardingComplete")))
		{
			return false;
		}
		if (Boolean.parseBoolean(config.get("presetOnboardingPending")))
		{
			return true;
		}

		String[] existingInstallMarkers = {
			"npcVisibilityMigrated", "exemptListMigrated", "fishingModeMigrated",
			"hunterModeMigrated"
		};
		for (String marker : existingInstallMarkers)
		{
			if (config.get(marker) != null)
			{
				config.set("presetOnboardingComplete", true);
				return false;
			}
		}

		config.set("presetOnboardingPending", true);
		return true;
	}

	/** Run every legacy migration in its established startup order. */
	public void migrateLegacySettings(boolean freshInstall)
	{
		migrateExemptList();
		migrateNpcVisibility();
		migrateSkillToggles();
		migrateFishingMode();
		migrateHunterMode();
		migrateHamPickpocketingMode(freshInstall);
		migrateThievingMode();
		removeRetiredGeneralSettings();
		migrateTcgLockedDefaults(freshInstall);
	}

	private void migrateHamPickpocketingMode(boolean freshInstall)
	{
		if (Boolean.parseBoolean(config.get("hamPickpocketingModeMigrated")))
		{
			return;
		}
		config.set("hamPickpocketingModeMigrated", true);

		if (!freshInstall && config.get("hamPickpocketingMode") == null)
		{
			String oldThievingMode = config.get("thievingMode");
			HamPickpocketingMode mode;
			if (ThievingMode.OFF.name().equals(oldThievingMode))
			{
				mode = HamPickpocketingMode.OFF;
			}
			else if ("NPC_AND_LOOT".equals(oldThievingMode)
				|| ThievingMode.REQUIRE_ALL.name().equals(oldThievingMode))
			{
				mode = Boolean.parseBoolean(config.get("hamFullLoot"))
					? HamPickpocketingMode.FULL_LOOT
					: HamPickpocketingMode.MEMBER_AND_OUTFIT;
			}
			else
			{
				mode = HamPickpocketingMode.MEMBER_CARD;
			}
			config.set("hamPickpocketingMode", mode.name());
		}
		config.unset("hamFullLoot");
	}

	/** Rename the retired beta Coin-pouch modes without changing player intent. */
	private void migrateThievingMode()
	{
		if (Boolean.parseBoolean(config.get("thievingModeV1Migrated")))
		{
			return;
		}
		config.set("thievingModeV1Migrated", true);

		String stored = config.get("thievingMode");
		if ("COINS_POUCH".equals(stored))
		{
			config.set("thievingMode", ThievingMode.COINS.name());
		}
		else if ("NPC_ONLY".equals(stored))
		{
			config.set("thievingMode", ThievingMode.COINS_NPC.name());
		}
		else if ("NPC_AND_LOOT".equals(stored))
		{
			config.set("thievingMode", ThievingMode.REQUIRE_ALL.name());
		}
	}

	private void migrateTcgLockedDefaults(boolean freshInstall)
	{
		if (Boolean.parseBoolean(config.get("tcgLockedDefaultsMigrated")))
		{
			return;
		}

		// Mark first so a partial migration can never be repeated over later user changes.
		config.set("tcgLockedDefaultsMigrated", true);
		if (freshInstall)
		{
			return;
		}

		for (Map.Entry<String, String> entry : PRE_TCG_LOCKED_DEFAULTS.entrySet())
		{
			if (config.get(entry.getKey()) == null)
			{
				config.set(entry.getKey(), entry.getValue());
			}
		}
	}

	private void migrateNpcVisibility()
	{
		if (Boolean.parseBoolean(config.get("npcVisibilityMigrated")))
		{
			return;
		}
		config.set("npcVisibilityMigrated", true);

		String hide = config.get("hideLockedEntities");
		String attacks = config.get("restrictAttacks");
		NpcVisibilityMode mode;
		if (Boolean.parseBoolean(hide))
		{
			mode = NpcVisibilityMode.HIDE;
		}
		else if ("false".equals(attacks))
		{
			mode = NpcVisibilityMode.OFF;
		}
		else
		{
			mode = NpcVisibilityMode.PREVENT_COMBAT;
		}
		config.set("npcVisibilityMode", mode.name());

		config.unset("restrictAttacks");
		config.unset("restrictSpellCasts");
		config.unset("hideLockedEntities");

		if ("INSANITY".equals(config.get("masterFarmerMode")))
		{
			config.set("masterFarmerInsanity", true);
		}
		config.unset("masterFarmerMode");

		if ("false".equals(config.get("restrictLoot")))
		{
			config.set("groundItemsMode", LockState.UNLOCKED.name());
		}
		if ("false".equals(config.get("restrictEquipping"))
			|| "false".equals(config.get("restrictPotionDrinking")))
		{
			config.set("itemUsageMode", LockState.UNLOCKED.name());
		}
		if ("false".equals(config.get("restrictBuying")))
		{
			config.set("grandExchangeMode", LockState.UNLOCKED.name());
		}
		if ("false".equals(config.get("exemptCoins")))
		{
			config.set("coinMode", LockState.LOCKED.name());
		}
		if ("DROP".equals(config.get("forcedDropMode")))
		{
			config.set("bankingMode", BankingMode.OFF.name());
		}
		config.unset("restrictLoot");
		config.unset("restrictEquipping");
		config.unset("restrictPotionDrinking");
		config.unset("restrictItemUsage");
		config.unset("restrictBuying");
		config.unset("forcedDropMode");
		config.unset("exemptCoins");
		config.unset("hideLockedOptions");
		config.unset("allowCotsGuards");
	}

	private void migrateSkillToggles()
	{
		if ("false".equals(config.get("restrictFletching")))
		{
			config.set("fletchingMode", FletchingMode.OFF.name());
		}
		config.unset("restrictFletching");
		if ("PRODUCT".equals(config.get("fletchingMode")))
		{
			config.set("fletchingMode", FletchingMode.INPUT_ONLY.name());
		}

		if ("false".equals(config.get("restrictMining")))
		{
			config.set("miningMode", MiningMode.OFF.name());
		}
		config.unset("restrictMining");

		if ("false".equals(config.get("restrictWoodcutting")))
		{
			config.set("woodcuttingMode", WoodcuttingMode.OFF.name());
		}
		config.unset("restrictWoodcutting");

		if ("false".equals(config.get("restrictCrafting")))
		{
			config.set("craftingMode", CraftingMode.OFF.name());
		}
		config.unset("restrictCrafting");
		if ("OUTPUT_ONLY".equals(config.get("craftingMode")))
		{
			config.set("craftingMode", CraftingMode.INPUT_ONLY.name());
		}

		if ("false".equals(config.get("restrictCompost")))
		{
			config.set("compostMode", CardRequirement.NO_CARD.name());
		}
		config.unset("restrictCompost");

		if ("BARS".equals(config.get("smeltingMode")))
		{
			config.set("smeltingMode", SmeltingMode.ORE.name());
		}
		if ("ITEMS".equals(config.get("smithingMode")))
		{
			config.set("smithingMode", SmithingMode.BARS.name());
		}
		if ("CARD_REQUIRED".equals(config.get("woodcuttingMode")))
		{
			config.set("woodcuttingMode", WoodcuttingMode.LOGS_ONLY.name());
		}

		String oldFiremaking = config.get("firemakingMode");
		if ("BOTH".equals(oldFiremaking))
		{
			config.set("tinderboxMode", CardRequirement.CARD_REQUIRED.name());
		}
		else if ("OFF".equals(oldFiremaking) || "JUST_LOGS".equals(oldFiremaking))
		{
			config.set("tinderboxMode", CardRequirement.NO_CARD.name());
		}
		config.unset("firemakingMode");

		if ("ORE_ONLY".equals(config.get("miningMode")))
		{
			config.set("miningMode", MiningMode.CARD_REQUIRED.name());
		}

		boolean slMasters = "true".equals(config.get("restrictSlayerMasters"));
		boolean slMonsters = "true".equals(config.get("restrictSlayerMonsters"));
		if (slMasters && slMonsters)
		{
			config.set("slayerMode", SlayerMode.FULL.name());
		}
		else if (slMasters)
		{
			config.set("slayerMode", SlayerMode.MASTER.name());
		}
		config.unset("restrictSlayerMasters");
		config.unset("restrictSlayerMonsters");

		if ("false".equals(config.get("restrictCooking")))
		{
			config.set("cookingMode", CookingMode.OFF.name());
		}
		config.unset("restrictCooking");
		config.unset("restrictBurntFood");
		config.unset("burntFoodMode");

		if ("false".equals(config.get("restrictHerblore")))
		{
			config.set("herbloreMode", HerbloreMode.OFF.name());
		}
		config.unset("restrictHerblore");
	}

	private void migrateFishingMode()
	{
		if (Boolean.parseBoolean(config.get("fishingModeMigrated")))
		{
			return;
		}
		config.set("fishingModeMigrated", true);

		String stored = config.get("fishingMode");
		if ("ANY_OF".equals(stored))
		{
			config.set("fishingMode", FishingRestrictionMode.CARD_REQUIRED.name());
		}
		else if ("REQUIRE_ALL".equals(stored))
		{
			config.set("fishingMode", FishingRestrictionMode.ALL_CATCHES.name());
		}
	}

	private void migrateHunterMode()
	{
		if (Boolean.parseBoolean(config.get("hunterModeMigrated")))
		{
			return;
		}
		config.set("hunterModeMigrated", true);

		String birds = config.get("hunterBirdsMode");
		String butterflies = config.get("butterflyMode");
		String implings = config.get("implingMode");
		String chins = config.get("restrictChins");
		String salamanders = config.get("salamanderMode");
		String pitfalls = config.get("pitfallMode");

		boolean allOff = "OFF".equals(birds)
			&& "OFF".equals(butterflies)
			&& "OFF".equals(implings)
			&& "false".equals(chins)
			&& "OFF".equals(salamanders)
			&& "OFF".equals(pitfalls);
		boolean explicitlyStrict = "ALL_DROPS".equals(birds)
			|| "ITEMS_SALLY".equals(salamanders)
			|| "ALL".equals(pitfalls);
		if (allOff)
		{
			config.set("hunterMode", HunterMode.OFF.name());
		}
		else if (explicitlyStrict)
		{
			config.set("hunterMode", HunterMode.ALL_CARDS.name());
		}

		config.unset("hunterBirdsMode");
		config.unset("butterflyMode");
		config.unset("implingMode");
		config.unset("restrictChins");
		config.unset("salamanderMode");
		config.unset("pitfallMode");
	}

	private void removeRetiredGeneralSettings()
	{
		config.unset("chatFeedback");
		config.unset("allowInLms");
	}

	private void migrateExemptList()
	{
		if (Boolean.parseBoolean(config.get("exemptListMigrated")))
		{
			return;
		}
		config.set("exemptListMigrated", true);

		String raw = config.get("lootExemptNames");
		if (raw == null)
		{
			return;
		}
		boolean hadCoins = false;
		List<String> kept = new ArrayList<>();
		for (String entry : raw.split(","))
		{
			String trimmed = entry.trim();
			if ("coins".equalsIgnoreCase(trimmed))
			{
				hadCoins = true;
			}
			else if (!trimmed.isEmpty())
			{
				kept.add(trimmed);
			}
		}
		config.set("exemptCoins", hadCoins);
		config.set("lootExemptNames", String.join(", ", kept));
	}

	static Map<String, String> preTcgLockedDefaults()
	{
		return PRE_TCG_LOCKED_DEFAULTS;
	}

	private static Map<String, String> values(Object... pairs)
	{
		if ((pairs.length & 1) != 0)
		{
			throw new IllegalArgumentException("Migration settings must be key/value pairs");
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

	interface ConfigAccess
	{
		String get(String key);

		void set(String key, Object value);

		void unset(String key);
	}

	private static final class RuneLiteConfigAccess implements ConfigAccess
	{
		private final ConfigManager configManager;

		private RuneLiteConfigAccess(ConfigManager configManager)
		{
			this.configManager = configManager;
		}

		@Override
		public String get(String key)
		{
			return configManager.getConfiguration(GROUP, key);
		}

		@Override
		public void set(String key, Object value)
		{
			configManager.setConfiguration(GROUP, key, value);
		}

		@Override
		public void unset(String key)
		{
			configManager.unsetConfiguration(GROUP, key);
		}
	}
}
